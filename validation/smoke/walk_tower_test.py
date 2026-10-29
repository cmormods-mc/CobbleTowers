#!/usr/bin/env python3
"""Proves P55's walkable Hollow's Eve against a real server: a team climbs from floor 1 to floor 10 on foot, and the cycle's end is left by teleport.

Two real (headless, physics-on) players start a Halloween run. Floors are cleared by operator event (as intermission_test.py does), then everything
between floors is the real thing: gate A opens when a floor is won, the vendor stands in its room, the walk to the stair is made with real
collision, gate B opens when the team is ready, the stair is climbed, and the next floor begins by itself when both players reach the arena door,
without anyone being teleported. The last floor of the cycle has no stair, so readying up there starts the next cycle by teleport.

Needs the Halloween tower open outside its window (-Dcobbletowers.ignoreWindows=true), RCON on port 25575, and mineflayer and vec3 on NODE_PATH.

    python validation/smoke/walk_tower_test.py --attach --server-dir <rig>/server [--floors 10]

--attach drives a server that is already running (the usual way on a rig that boots its own way); without it the harness boots one.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port  # noqa: E402
from floor_encounter_test import give_party  # noqa: E402
from intermission_test import play, run_line, wait_state, show, UUID_RE  # noqa: E402

TOWER = "cobbletowers:halloween"
STAMP = int(time.time()) % 100000
A = f"TWa{STAMP}"
B = f"TWb{STAMP}"
IN_TOWER = "execute in cobbletowers:tower"
FLOORS_DIR = HERE.parent.parent / "src" / "main" / "resources" / "data" / "cobbletowers" / "cobbletowers" / "floors" / "halloween"
SIZE_X = 97


def layout_of(floor: int) -> dict:
    return json.loads((FLOORS_DIR / f"floor_{floor:02d}.json").read_text())["layout"]


def mirrored(floor: int) -> bool:
    return floor % 2 == 0


def mx(floor: int, x: float) -> float:
    return SIZE_X - 1 - x if mirrored(floor) else x


class Walker:
    """A headless player with real physics. Answers are counted, so a command waits for its own answer and not an earlier one."""

    def __init__(self, name: str, port: int, node_modules: Path, log: Path):
        env = dict(os.environ, NODE_PATH=str(node_modules))
        self.name = name
        self.log = log
        self.handle = open(log, "w", encoding="utf-8", errors="replace")
        self.process = subprocess.Popen(["node", str(HERE / "walkbot.js"), name, str(port)], stdin=subprocess.PIPE, stdout=self.handle,
                                        stderr=subprocess.STDOUT, env=env, text=True)
        self.sent = 0

    def say(self, line: str) -> None:
        self.process.stdin.write(line + "\n")
        self.process.stdin.flush()

    def answers(self) -> list[str]:
        self.handle.flush()
        text = self.log.read_text(encoding="utf-8", errors="replace")
        return [m.group(0) for m in re.finditer(r"(ARRIVED|TIMEOUT) [\d.\-]+ [\d.\-]+ [\d.\-]+", text)]

    def go(self, x: float, z: float, seconds: int) -> None:
        self.sent += 1
        self.say(f"goto {x} {z} {seconds}")

    def answered(self) -> str | None:
        found = self.answers()
        return found[self.sent - 1] if len(found) >= self.sent else None


def position(rcon: Rcon, name: str) -> tuple[float, float, float]:
    out = rcon.command(f"data get entity {name} Pos")
    nums = re.findall(r"(-?\d+(?:\.\d+)?)d", out)
    if len(nums) < 3:
        raise RuntimeError(f"no position for {name}: {out}")
    return float(nums[0]), float(nums[1]), float(nums[2])


def block_is(rcon: Rcon, origin: tuple[int, int, int], local: tuple[int, int, int], block: str) -> bool:
    x, y, z = (origin[i] + local[i] for i in range(3))
    return "Test passed" in rcon.command(f"{IN_TOWER} if block {x} {y} {z} {block}")


def gate_state(rcon: Rcon, origin: tuple[int, int, int], box: dict, block: str) -> bool:
    """Every sampled block of the gate's box is {block}: the corners and the middle tell shut from open."""
    low, high = box["min"], box["max"]
    middle = [(low[i] + high[i]) // 2 for i in range(3)]
    samples = [tuple(low), tuple(high), tuple(middle), (low[0], high[1], high[2]), (high[0], low[1], low[2])]
    return all(block_is(rcon, origin, s, block) for s in samples)


def wait_for(condition, seconds: float, step: float = 1.0) -> bool:
    deadline = time.time() + seconds
    while time.time() < deadline:
        if condition():
            return True
        time.sleep(step)
    return False


def walk(walkers: list[Walker], origin: tuple[int, int, int], points: list[tuple[float, float]], seconds: int = 30) -> list[str]:
    """Both players walk each point in turn, with real collision. Returns what went wrong, empty when every point was reached."""
    problems: list[str] = []
    for x, z in points:
        for w in walkers:
            w.go(origin[0] + x + 0.5, origin[2] + z + 0.5, seconds)
        deadline = time.time() + seconds + 10
        while time.time() < deadline and not all(w.answered() for w in walkers):
            time.sleep(0.25)
        for w in walkers:
            answer = w.answered() or "NO ANSWER"
            if not answer.startswith("ARRIVED"):
                problems.append(f"{w.name} at ({x}, {z}): {answer}")
    return problems


def win_floor(rcon: Rcon, floor: int) -> bool:
    """The operator win, repeated: a floor with extra opponents (a modifier) starts the next wave when one is won, so one command is not enough."""
    time.sleep(3)   # let the floor's battles start before they are ended
    for _ in range(8):
        rcon.command(f"cobbletowers runs winfloor {A}")
        if wait_state(rcon, "INTERMISSION", seconds=6, floor=floor):
            return True
    return False


def route_to_vendor(floor: int) -> list[tuple[float, float]]:
    """From the arena door, across the arena clear of the braziers, through gate A and into the intermission room, in the floor's own frame."""
    return [(mx(floor, x), z) for x, z in ((30, 45), (46, 43), (48, 38), (48, 31))]


def route_through_door(door_x: int, floor: int) -> list[tuple[float, float]]:
    """From in front of a door through it, its chamber and its back doorway into the corridor and on east to gate B."""
    return [(door_x, 26), (door_x, 12), (door_x, 7), (door_x, 3), (mx(floor, 66), 3)]


def route_to_stair(floor: int) -> list[tuple[float, float]]:
    """Along the corridor, through gate B, to the north end of the stair hall and up the stair to the floor above."""
    return [(mx(floor, x), z) for x, z in ((82, 3), (86, 4), (86, 16.5))]


def route_to_arena(next_floor: int) -> list[tuple[float, float]]:
    """Own-frame waypoints of the floor above: from the top of the stair down the arrival hall to just inside the arena door."""
    return [(mx(next_floor, x), z) for x, z in ((10, 24), (10, 48), (21.5, 48))]


def chosen_lane(rcon: Rcon, floor: int) -> int:
    """The lane the vote settled on, from the run report; -1 when this floor's draft is not a route draft (a relic at a boss floor, an event)."""
    report = show(rcon)
    drafts = [line for line in report.splitlines() if f"draft at floor {floor}" in line]
    if not drafts or "(ROUTE" not in drafts[0]:
        return -1
    found = re.search(r"route: lane (\d)", report)
    return int(found.group(1)) if found else -1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--attach", action="store_true", help="drive a server that is already running")
    parser.add_argument("--floors", type=int, default=10, help="how many floors to play (2 to 10)")
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    rig = server_dir.parent
    node_modules = Path(os.environ.get("NODE_PATH", str(rig / "bot" / "node_modules")).split(os.pathsep)[0])
    port = server_port(server_dir)
    results: list[Result] = []
    server = None
    walkers: list[Walker] = []

    def check(name: str, passed: bool, detail: str = "") -> bool:
        results.append(Result(name, bool(passed), detail))
        print(("PASS  " if passed else "FAIL  ") + name + ("  " + detail if detail and not passed else ""), flush=True)
        return bool(passed)

    try:
        if not args.attach:
            java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
            if "ignoreWindows" not in os.environ.get("SMOKE_JAVA_OPTS", ""):
                os.environ["SMOKE_JAVA_OPTS"] = (os.environ.get("SMOKE_JAVA_OPTS", "") + " -Dcobbletowers.ignoreWindows=true").strip()
            if args.jar:
                install_jar(server_dir, args.jar.resolve())
            reset_tower_world(server_dir)
            server = Server(server_dir, java)
            server.start()
            server.wait_until_ready()
        for name in (A, B):
            walkers.append(Walker(name, port, node_modules, server_dir / "logs" / f"towers-walk-{name}.log"))
        by_name = {w.name: w for w in walkers}

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (A, B):
                if not wait_for(lambda n=name: n in rcon.command("list"), 90):
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            for name in (A, B):
                give_party(rcon, name)
                rcon.command(f"effect give {name} minecraft:resistance infinite 4 true")

            play(rcon, A, f"tower {TOWER}")
            play(rcon, A, f"invite {B}")
            play(rcon, B, f"accept {A}")
            play(rcon, A, "confirm")
            play(rcon, A, "start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=60, floor=1):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)

            # where the structure was pasted: the first fighter stands exactly on the entry anchor, the second two blocks on
            entry = layout_of(1)["entry"]
            first = min((position(rcon, n) for n in (A, B)), key=lambda p: p[0])
            origin = (round(first[0] - entry["x"] - 0.5), round(first[1] - entry["y"]), round(first[2] - entry["z"] - 0.5))
            check("floor 1 begins with the team at the entry anchor of the arena",
                  abs(first[0] - (origin[0] + entry["x"] + 0.5)) < 0.01 and abs(first[2] - (origin[2] + entry["z"] + 0.5)) < 0.01, f"origin {origin}, at {first}")

            top = max(2, min(args.floors, 10))
            for floor in range(1, top + 1):
                lay = layout_of(floor)
                at = f"floor {floor}"
                check(f"{at}: gate A is shut while the floor is fought", gate_state(rcon, origin, lay["gate_a"], "minecraft:iron_bars"))
                if "gate_b" in lay:
                    check(f"{at}: gate B is shut too", gate_state(rcon, origin, lay["gate_b"], "minecraft:iron_bars"))
                if not win_floor(rcon, floor):
                    raise RuntimeError(f"{at} never reached its intermission: " + run_line(rcon))
                check(f"{at}: winning it opens gate A", gate_state(rcon, origin, lay["gate_a"], "minecraft:air"))
                vendor = lay["exit"]
                near = rcon.command(f"{IN_TOWER} positioned {origin[0] + vendor['x']} {origin[1] + vendor['y']} {origin[2] + vendor['z']} "
                                    f"if entity @e[type=minecraft:villager,tag=cobbletowers_vendor,distance=..3]")
                check(f"{at}: the vendor stands in the room behind gate A", "passed" in near.lower(), near[:80])

                doors = lay["doors"]
                check(f"{at}: all three doors are shut and dark while the floor is fought",
                      all(gate_state(rcon, origin, d["open"], "minecraft:iron_bars") and gate_state(rcon, origin, d["frame"], "minecraft:crying_obsidian")
                          for d in doors))
                # the real walk through gate A to the intermission room (the team is where the fight left it, at the arena door)
                problems = walk(list(by_name.values()), origin, route_to_vendor(floor))
                check(f"{at}: both players walk through gate A into the intermission room", not problems, "; ".join(problems)[:200])
                pedestals = rcon.command(f"{IN_TOWER} if entity @e[tag=cobbletowers_pedestal_hitbox]")
                for _ in range(2):
                    play(rcon, A, "pick 1")
                    play(rcon, B, "pick 1")
                time.sleep(2)
                lit = [i for i, d in enumerate(doors) if gate_state(rcon, origin, d["open"], "minecraft:air")]
                if len(lit) == 1:
                    lane = lit[0]
                    check(f"{at}: the door of the chosen route lights up", gate_state(rcon, origin, doors[lane]["frame"], "minecraft:shroomlight"))
                    others = [d for i, d in enumerate(doors) if i != lane]
                    check(f"{at}: it is the only one that opens, and the other two stay shut and dark",
                          all(gate_state(rcon, origin, d["open"], "minecraft:iron_bars") and gate_state(rcon, origin, d["frame"], "minecraft:crying_obsidian")
                              for d in others))
                else:
                    lane = -1   # a draft with no lanes (a relic, a lock-in): the middle door is opened for the team when it sets out
                    check(f"{at}: no door opens before the vote has a route", not lit, f"open doors {lit}")
                play(rcon, A, "ready")
                play(rcon, B, "ready")

                if "gate_b" not in lay:
                    # the last floor of a cycle has no stair: the next cycle begins by teleport
                    check(f"{at}: the cycle's last floor has no stair gate", True)
                    if floor < 10 or top < 10:
                        break
                    opened = wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=40, floor=11)
                    check("the next cycle begins on floor 11", opened, run_line(rcon)[-100:])
                    entry1 = layout_of(1)["entry"]
                    here = min((position(rcon, n) for n in (A, B)), key=lambda p: p[0])
                    check("and by teleport, to the entry anchor of floor 1",
                          abs(here[0] - (origin[0] + entry1["x"] + 0.5)) < 0.05 and abs(here[1] - (origin[1] + entry1["y"])) < 0.05,
                          f"at {here}")
                    check("and the doors and gates of floor 1, left open by the first cycle, are shut again",
                          all(gate_state(rcon, origin, d["open"], "minecraft:iron_bars") and gate_state(rcon, origin, d["frame"], "minecraft:crying_obsidian")
                              for d in layout_of(1)["doors"])
                          and gate_state(rcon, origin, layout_of(1)["gate_a"], "minecraft:iron_bars")
                          and gate_state(rcon, origin, layout_of(1)["gate_b"], "minecraft:iron_bars"))
                    break

                opened = wait_for(lambda: gate_state(rcon, origin, lay["gate_b"], "minecraft:air"), 20)
                check(f"{at}: everyone ready opens gate B after the countdown", opened)
                line = run_line(rcon)
                check(f"{at}: the next floor waits for the climb", "INTERMISSION" in line and f"floor {floor}" in line, line[-120:])
                if lane < 0:
                    lane = next((i for i, d in enumerate(doors) if gate_state(rcon, origin, d["open"], "minecraft:air")), -1)
                    check(f"{at}: a floor with no route lanes opens a door of its own", lane >= 0)

                # through the chosen door, along the corridor and up the stair, then down the arrival hall to the next arena
                door_x = doors[lane]["pedestal"]["x"]
                problems = walk(list(by_name.values()), origin, route_through_door(door_x, floor) + route_to_stair(floor), seconds=30)
                check(f"{at}: both players walk through the chosen door, its chamber and the corridor and climb the stair", not problems, "; ".join(problems)[:240])
                gained = [position(rcon, n)[1] - origin[1] for n in (A, B)]
                want = layout_of(floor + 1)["entry"]["y"]
                check(f"{at}: the stair rises ten blocks", all(abs(y - want) < 0.6 for y in gained), f"y {gained}, wanted {want}")

                problems = walk(list(by_name.values()), origin, route_to_arena(floor + 1), seconds=40)
                check(f"floor {floor + 1}: the team walks from the stair to the arena door", not problems, "; ".join(problems)[:240])
                if not wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=20, floor=floor + 1):
                    raise RuntimeError(f"floor {floor + 1} never opened after the walk: " + run_line(rcon))
                # nobody was moved: they stand where they stopped, three blocks from the anchor, not on it
                nxt = layout_of(floor + 1)["entry"]
                spots = [position(rcon, n) for n in (A, B)]
                stayed = all(abs(s[0] - (origin[0] + mx(floor + 1, 21.5) + 0.5)) < 2.0 for s in spots)
                check(f"floor {floor + 1}: begins where the team stands, nobody is teleported", stayed, f"at {spots}, anchor {nxt}")
        print()
    finally:
        for walker in walkers:
            walker.process.kill()
        if server:
            server.stop()

    failed = [r for r in results if not r.passed]
    print(f"{len(results) - len(failed)}/{len(results)} passed")
    for r in failed:
        print(f"FAIL  {r.name}  {r.detail}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
