#!/usr/bin/env python3
"""Proves P31's mastery and leaderboards against a real server, on the four-floor test tower.

A solo player clears a whole cycle (floors cleared by operator event, everything else the real player path). Then:

  * the cycle clear is recorded (active time, flawless, difficulty score) and unlocks the achievements it meets, announced
    and listed by `/tower mastery`, with the level, the rank and the perk that level earns;
  * the perk is real: the vendor's Full Heal costs less once the level reaches 5;
  * the leaderboards hold the run: speed, difficulty and clears (solo), each row naming the ruleset and tower revisions;
  * ascending adds the Ascension board entry and the depth achievement;
  * a two-player run is ranked in team mode, with both names, and is not mixed into the solo board;
  * the operator tools reset mastery and clear the boards.

    python validation/smoke/mastery_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)
from floor_encounter_test import give_party  # noqa: E402
from intermission_test import play, run_line, wait_state, clear_floor, UUID_RE  # noqa: E402

TOWER = "cobbletowers:test"
STAMP = int(time.time()) % 100000
A = f"TMa{STAMP}"
B = f"TMb{STAMP}"


def as_player(rcon: Rcon, name: str, command: str) -> str:
    return rcon.command(f"execute as {name} run cobbletowers play {command}")


def full_heal_price(rcon: Rcon, player: str) -> int:
    reply = rcon.command(f"execute as {player} run cobbletowers runs vendor")
    found = re.search(r"Full Heal -- (\d+) CobbleDollars", reply)
    return int(found.group(1)) if found else -1


def wait_new_run(rcon: Rcon, old: set[str], state: str, seconds: int) -> str:
    deadline = time.time() + seconds
    while time.time() < deadline:
        listing = rcon.command("cobbletowers runs list")
        for run_id, run_state in re.findall(UUID_RE + r"\s+\S+\s+([A-Z_]+)", listing):
            if run_id not in old and run_state == state:
                return run_id
        time.sleep(2)
    return ""


def run_cycle(rcon: Rcon, run: str, players: list[str]) -> None:
    """Clears floors 1-4 by operator event, picking and readying between floors; stops at the cycle-end intermission."""
    for floor in range(1, 5):
        clear_floor(rcon, run)
        for _ in range(40):
            if re.search(rf"{run}\s+\S+\s+INTERMISSION", rcon.command("cobbletowers runs list")):
                break
            time.sleep(1)
        else:
            raise RuntimeError(f"floor {floor} never reached its intermission")
        if floor < 4:
            for _ in range(2):   # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
                for name in players:
                    play(rcon, name, "pick 1")
            for name in players:
                play(rcon, name, "ready")
            for _ in range(60):
                if re.search(rf"{run}\s+\S+\s+ENCOUNTER_ACTIVE", rcon.command("cobbletowers runs list")):
                    break
                time.sleep(1)
            else:
                raise RuntimeError(f"floor {floor + 1} never opened")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    rig = server_dir.parent
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    node_modules = rig / "bot" / "node_modules"
    bots: list[subprocess.Popen] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        for name in (A, B):
            handle = open(server_dir / "logs" / f"towers-mastery-{name}.log", "w", encoding="utf-8", errors="replace")
            bots.append(subprocess.Popen(["node", str(HERE / "joinbot.js"), name, str(server_port(server_dir))],
                                         stdout=handle, stderr=subprocess.STDOUT, env=env))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (A, B):
                for _ in range(90):
                    if name in rcon.command("list"):
                        break
                    time.sleep(1)
                else:
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            rcon.command("cobbletowers masteryadmin clearboards")
            for name in (A, B):
                rcon.command(f"cobbletowers masteryadmin reset {name} {TOWER}")
                give_party(rcon, name)

            fresh = as_player(rcon, A, f"mastery {TOWER}")
            results.append(Result("a new player starts Unranked with no perks",
                                  "level 0 (Unranked)" in fresh and "perks: none yet" in fresh, fresh.strip()[:200]))

            # ---- a solo cycle --------------------------------------------------------------------------------
            play(rcon, A, f"tower {TOWER}")
            play(rcon, A, "confirm")
            play(rcon, A, "start")
            first = wait_new_run(rcon, set(), "ENCOUNTER_ACTIVE", 60)
            if not first:
                raise RuntimeError("the solo run never opened floor 1")
            run_cycle(rcon, first, [A])
            time.sleep(2)
            text = server.read_log()
            cleared = re.search(rf"Run {first} cleared a cycle of {TOWER} at Ascension 0: (\d+) ms, flawless=(\w+), score (\d+), (\d+) severe", text)
            results.append(Result("the cycle clear was recorded (time, flawless, score)", cleared is not None,
                                  "no 'cleared a cycle' line"))
            if cleared:
                results.append(Result("with no faint it is flawless, and the score is the solo handicap plus its modifiers",
                                      cleared.group(2) == "true" and int(cleared.group(3)) >= 15, cleared.group(0)))

            sheet = as_player(rcon, A, f"mastery {TOWER}")
            expected = ["First Ascent", "Spotless", "Lone Wolf", "Solitary Perfection", "Brisk", "Swift", "Lightning", "Blitz"]
            missing = [n for n in expected if f"[x] {n}" not in sheet]
            results.append(Result("the clear unlocked the achievements it meets", not missing,
                                  f"missing {missing}; {sheet.strip()[:300]}"))
            level = re.search(r"level (\d+) \((\w+)\)", sheet)
            results.append(Result("the level is the number unlocked, and eight earns Silver",
                                  level is not None and int(level.group(1)) >= 8 and level.group(2) == "Silver",
                                  level.group(0) if level else sheet.strip()[:200]))
            results.append(Result("and Silver's perk is listed", "vendor prices -3%" in sheet, sheet.strip()[:200]))

            price = full_heal_price(rcon, A)
            results.append(Result("the perk is real: Full Heal (25) is cheaper by 3%", 0 < price <= 24, f"price {price}"))

            speed = rcon.command(f"execute as {A} run cobbletowers play leaderboard speed {TOWER}")
            results.append(Result("the speed board holds the run in solo mode, with the revisions",
                                  A in speed.split("Team")[0] and "[ruleset r" in speed and "tower r" in speed, speed.strip()[:300]))
            difficulty = rcon.command(f"execute as {A} run cobbletowers play leaderboard difficulty {TOWER}")
            results.append(Result("the difficulty board holds it", A in difficulty and "score " in difficulty, difficulty.strip()[:300]))
            clears = rcon.command(f"execute as {A} run cobbletowers play leaderboard clears {TOWER}")
            results.append(Result("the clears board shows one cycle", A in clears and "1 cycle" in clears, clears.strip()[:300]))

            # ---- ascend: depth board and depth achievement ---------------------------------------------------
            for name in [A]:
                play(rcon, name, "pick 1")
                # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
                play(rcon, name, "pick 1")
            play(rcon, A, "ready")
            opened = False
            for _ in range(60):
                if re.search(rf"{first}\s+\S+\s+ENCOUNTER_ACTIVE", rcon.command("cobbletowers runs list")):
                    opened = True
                    break
                time.sleep(1)
            results.append(Result("readying up at the cycle's end ascends", opened, ""))
            time.sleep(2)
            depth = rcon.command(f"execute as {A} run cobbletowers play leaderboard ascension {TOWER}")
            results.append(Result("entering Ascension 1 puts the run on the Ascension board",
                                  "Ascension 1" in depth and A in depth.split("Team")[0], depth.strip()[:300]))
            sheet = as_player(rcon, A, f"mastery {TOWER}")
            results.append(Result("and unlocks the depth achievement", "[x] Ascendant" in sheet, sheet.strip()[:300]))

            # ---- a team run is ranked apart --------------------------------------------------------------------
            rcon.command(f"cobbletowers runs advance {first} abandon_requested")
            time.sleep(8)
            play(rcon, A, f"tower {TOWER}")
            play(rcon, A, f"invite {B}")
            play(rcon, B, f"accept {A}")
            play(rcon, A, "confirm")
            play(rcon, A, "start")
            second = wait_new_run(rcon, {first}, "ENCOUNTER_ACTIVE", 60)
            if not second:
                raise RuntimeError("the team run never opened floor 1: " + rcon.command("cobbletowers runs list"))
            run_cycle(rcon, second, [A, B])
            time.sleep(2)
            team = rcon.command(f"execute as {A} run cobbletowers play leaderboard difficulty {TOWER}")
            team_part = team.split("Team")[-1]
            results.append(Result("a two-player clear is ranked under Team with both names, not under Solo",
                                  A in team_part and B in team_part, team.strip()[:400]))
            results.append(Result("and the solo board kept its own entry", A in team.split("Team")[0], team.strip()[:400]))
            clears = rcon.command(f"execute as {A} run cobbletowers play leaderboard clears {TOWER}")
            results.append(Result("the clears board counts A's second cycle and B's first",
                                  "2 cycles" in clears and "1 cycle" in clears, clears.strip()[:300]))

            # ---- operator tools --------------------------------------------------------------------------------
            rcon.command(f"cobbletowers masteryadmin reset {A} {TOWER}")
            sheet = as_player(rcon, A, f"mastery {TOWER}")
            results.append(Result("an operator reset puts a player back to Unranked", "level 0 (Unranked)" in sheet, sheet.strip()[:200]))
            rcon.command("cobbletowers masteryadmin clearboards")
            empty = rcon.command(f"execute as {A} run cobbletowers play leaderboard speed {TOWER}")
            results.append(Result("and clearing the boards empties them", "no entries yet" in empty, empty.strip()[:200]))

            bad = [l for l in server.read_log().splitlines() if "ERROR" in l and "cobbletowers" in l.lower()]
            results.append(Result("no CobbleTowers error was logged", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("mastery run", False, repr(exc)))
    finally:
        for bot in bots:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  "
              f"{result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
