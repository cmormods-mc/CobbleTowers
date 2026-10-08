#!/usr/bin/env python3
"""A SOLO player drops during the boss fight and comes back inside CobbleRaids' hold window.

They must resume the fight (the participant is still in the battle), not be turned into a spectator of it:
  * the drop is logged, the rejoin logs "resumes the boss fight" and never "as a spectator";
  * the floor then resolves (cleared or wiped) and nothing from CobbleTowers or the Showdown runtime threw.

    python validation/smoke/boss_rejoin_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    FIRST_MOVE, FILLERS, LEAD, TOWER, begin_floor, give_party, start_battle_bot, tell_bot, wait_for, wait_for_floor,
)

STAMP = int(time.time()) % 100000
NAME = f"TBr{STAMP}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--lead-level", type=int, default=0,
                        help="give a lead of this level instead of the usual 100, so the boss lives long enough to be rejoined mid-fight")
    parser.add_argument("--away", type=float, default=0.5, help="seconds the player stays away")
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    rig = server_dir.parent
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bots: dict[str, subprocess.Popen] = {}
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bots[NAME] = start_battle_bot(rig, NAME, FIRST_MOVE)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon, seconds=90) and NAME not in rcon.command("list"):
                raise RuntimeError(f"{NAME} never joined")
            rcon.command("execute in cobbletowers:tower run forceload remove all")
            clear_tower(rcon)
            if args.lead_level:
                rcon.command(f"pokegiveother {NAME} {LEAD} level={args.lead_level}")
                for _ in range(FILLERS):
                    rcon.command(f"pokegiveother {NAME} magikarp level=1")
            else:
                give_party(rcon, NAME)

            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            tell_bot(rig, NAME, "FIGHT")
            tell_bot(rig, NAME, f"MOVE {FIRST_MOVE}")
            if not wait_for(rig / "bot" / f"{NAME}.log", r"AUTOFIGHT on", seconds=30):
                raise RuntimeError("bot never armed")
            begin_floor(rcon, run)

            boss = wait_for_floor(server, rig, [NAME], seconds=240,
                                  pattern=r"Floor \d+ boss \S+ started at level \d+")
            results.append(Result("the boss started", bool(boss), "no boss start line"))
            if not boss:
                raise RuntimeError("no boss, nothing to test")
            mark = len(server.read_log().splitlines())

            bots[NAME].kill()
            print(f"  killed {NAME} as the boss appeared")
            time.sleep(args.away)  # the test boss dies within seconds unless --lead-level is low
            bots[NAME] = start_battle_bot(rig, NAME, FIRST_MOVE)
            tell_bot(rig, NAME, "FIGHT")
            tell_bot(rig, NAME, f"MOVE {FIRST_MOVE}")
            time.sleep(15)

            lines = server.read_log().splitlines()[mark:]
            resumed = [l for l in lines if "resumes the boss fight" in l]
            spectated = [l for l in lines if "as a spectator" in l]
            results.append(Result("the rejoin resumed the boss fight", len(resumed) == 1, str([l[:110] for l in lines if "rejoined" in l])))
            results.append(Result("the rejoin did not become a spectator", not spectated, str(spectated)[:200]))

            outcome = wait_for_floor(server, rig, [NAME], seconds=420)
            print(f"  floor outcome: {outcome or '<none>'}")
            results.append(Result("the floor resolved after the rejoin", bool(outcome), "no cleared/wiped line"))

            lines = server.read_log().splitlines()[mark:]
            bad = [line for line in lines if re.search(r"ConcurrentModification|Multi threaded|NullPointer|IllegalState", line)
                   or "Exception while ticking a battle" in line or "Missing interpretation" in line
                   or ("com.cobbletowers" in line and re.search(r"ERROR", line))]
            results.append(Result("nothing threw", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("boss disconnect run", False, repr(exc)))
    finally:
        for bot in bots.values():
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
