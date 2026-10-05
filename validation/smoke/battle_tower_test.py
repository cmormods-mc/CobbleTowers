#!/usr/bin/env python3
"""Plays one of the four real towers, whose ten floors are ten halls of one pasted Battle Tower building (P27).

Derived from test_tower_test.py. --tower picks the tower, --floors how many floors to clear (a first-floor-only run is a
fast check of the paste and the anchors; the full ten floors is the real proof).

Original description follows.

The tower is a single structure, poke_tower (31x64x31): a stack of sealed circular chambers. Every floor names the same
structure and differs only in its anchors, so the cell is pasted once and each floor teleports the party to its own
level. What this proves on a real server:

  * the building pastes and every floor's anchors are accepted as playable (the runtime check, not just the offline one);
  * the invisible lights the converter added are really in the world;
  * on each of the four floors the player is put on that floor's level -- 18 blocks apart, in the same column -- and a
    battle starts there;
  * all four floors resolve with a bot fighting them, and the run reaches its end.

    python validation/smoke/test_tower_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from, wait_online,
)
from floor_encounter_test import (  # noqa: E402
    FIRST_MOVE, begin_floor, give_party, start_battle_bot, tell_bot, wait_for, wait_for_floor,
)

FLOOR_Y = 64                       # CellGrid.FLOOR_Y: where a structure's y=0 sits
STANDING = [31, 42, 53, 64, 76, 88, 100, 111, 124, 141]   # each floor's standing level inside the Battle Tower
BOT = f"TT{int(time.time()) % 100000}"


def position(rcon: Rcon) -> tuple[float, float, float] | None:
    out = rcon.command(f"data get entity {BOT} Pos")
    numbers = re.findall(r"(-?\d+(?:\.\d+)?)d", out)
    return (float(numbers[0]), float(numbers[1]), float(numbers[2])) if len(numbers) >= 3 else None


def dimension(rcon: Rcon) -> str:
    return rcon.command(f"data get entity {BOT} Dimension")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--tower", default="neutral")
    parser.add_argument("--floors", type=int, default=10)
    args = parser.parse_args()

    tower_id = f"cobbletowers:{args.tower}"
    stand_levels = STANDING[:args.floors]
    server_dir = args.server_dir.resolve()
    rig = server_dir.parent
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bot: subprocess.Popen | None = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        boot = server.read_log()
        results.append(Result(f"the {args.tower} tower loaded without a problem",
                              "all references resolved" in boot,
                              "; ".join(l for l in boot.splitlines() if "tower definition" in l)[:300]))
        bot = start_battle_bot(rig, BOT, FIRST_MOVE)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon, seconds=90) and BOT not in rcon.command("list"):
                raise RuntimeError(f"{BOT} never joined")
            rcon.command("execute in cobbletowers:tower run forceload remove all")
            clear_tower(rcon)
            give_party(rcon, BOT)

            run = run_id_from(rcon.command(f"cobbletowers runs create {tower_id} {BOT}"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            tell_bot(rig, BOT, "FIGHT")
            tell_bot(rig, BOT, f"MOVE {FIRST_MOVE}")
            wait_for(rig / "bot" / f"{BOT}.log", r"AUTOFIGHT on", seconds=30)

            first_xz: tuple[float, float] | None = None
            for floor, stand in enumerate(stand_levels, start=1):
                begun = begin_floor(rcon, run)
                time.sleep(2)
                results.append(Result(f"floor {floor}: it started with an opponent", "opponent" in begun, begun.strip()[:160]))

                where = position(rcon)
                expected_y = FLOOR_Y + stand
                in_tower = "cobbletowers:tower" in dimension(rcon)
                results.append(Result(f"floor {floor}: the player stands on its level (y {expected_y}) in the tower dimension",
                                      where is not None and in_tower and abs(where[1] - expected_y) <= 1.0,
                                      f"at {where}, tower dimension: {in_tower}"))
                if where is not None:
                    if first_xz is None:
                        first_xz = (where[0], where[2])
                    results.append(Result(f"floor {floor}: and in the same column as floor 1 (one pasted building)",
                                          abs(where[0] - first_xz[0]) < 1.5 and abs(where[2] - first_xz[1]) < 4.0,
                                          f"{(where[0], where[2])} vs {first_xz}"))

                if floor == 1:
                    log = server.read_log()
                    prepared = re.search(r"Cell \d+ prepared with cobbletowers:battle_tower_" + args.tower + r" at .* in (\d+) ms", log)
                    results.append(Result("the building pasted and was judged playable at runtime", bool(prepared)
                                          and "not playable" not in log, "no 'prepared with battle_tower' line, or a playable error"))
                    pasted = prepared
                    print("  paste:", pasted[0][:200] if pasted else "<no timing line>")

                # THIS floor's outcome, by number and run: the log still holds every earlier floor's "cleared" line, and the
                # default pattern matches the first of them straight away.
                outcome = wait_for_floor(server, rig, [BOT], seconds=480,
                                         pattern=rf"Floor {floor} of run {run} (cleared(?! its)|wiped the party)")
                print(f"  floor {floor} outcome: {outcome or '<none>'}")
                results.append(Result(f"floor {floor}: it resolved, and was cleared", "cleared" in (outcome or ""), outcome or "no outcome"))
                if "cleared" not in (outcome or ""):
                    break

                if floor < len(stand_levels):
                    # A tower floor is fought with what the party has left, and a lone bot runs out by floor 6.
                    rcon.command(f"pokeheal {BOT}")
                    # A relic draft or an event room (P34, P34b) can open behind the ordinary draft; each force settles one,
                    # and a force with nothing open is refused harmlessly.
                    for _ in range(3):
                        rcon.command(f"cobbletowers runs draft force {run}")
                    rcon.command(f"cobbletowers runs advance {run} intermission_complete")
                    rcon.command(f"cobbletowers runs advance {run} next_floor_confirmed")
                    tell_bot(rig, BOT, "FIGHT")
                    tell_bot(rig, BOT, f"MOVE {FIRST_MOVE}")
                    wait_for(rig / "bot" / f"{BOT}.log", r"AUTOFIGHT on", seconds=30)

            time.sleep(3)
            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("after the last floor played the run reached its end", any(s in shown for s in ("COMPLETED", "COMPLETING", "INTERMISSION", "FLOOR_RESOLVING")),
                                  shown.strip()[:200]))
            bad = [line for line in server.read_log().splitlines() if "com.cobbletowers" in line and "ERROR" in line]
            results.append(Result("no CobbleTowers error on the way", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("test tower run", False, repr(exc)))
    finally:
        if bot:
            bot.kill()
        server.stop()

    print()
    width = max(len(r.name) for r in results)
    failed = sum(1 for r in results if not r.passed)
    for r in results:
        print(f"  [{'PASS' if r.passed else 'FAIL'}] {r.name:<{width}}  {r.detail if not r.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
