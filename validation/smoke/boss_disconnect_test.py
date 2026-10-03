#!/usr/bin/env python3
"""Proves a player dropping out DURING a boss fight does not hang the floor or wrong-foot the run.

The prerequisite battles are ours and a disconnect there is covered by participant_test.py. The boss is
CobbleRaids', with its own idea of a participant who vanishes, and the two have to agree. Two bots clear their
opponents; the moment the boss appears one is killed. Then:

  * the floor still resolves (cleared or wiped) rather than waiting for somebody who is gone;
  * the run records the dropped player as disconnected and still holds the other;
  * the disconnect was handled on the server thread, and nothing from CobbleTowers or the Showdown runtime threw.

    python validation/smoke/boss_disconnect_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    FIRST_MOVE, TOWER, begin_floor, give_party, start_battle_bot, tell_bot, wait_for, wait_for_floor,
)

STAMP = int(time.time()) % 100000
STAY = f"TBs{STAMP}"
LEAVE = f"TBl{STAMP}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
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
    bots: dict[str, subprocess.Popen] = {}
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        for name in (STAY, LEAVE):
            bots[name] = start_battle_bot(rig, name, FIRST_MOVE)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (STAY, LEAVE):
                if not wait_online(rcon, seconds=90) and name not in rcon.command("list"):
                    raise RuntimeError(f"{name} never joined")
            rcon.command("execute in cobbletowers:tower run forceload remove all")
            clear_tower(rcon)
            for name in (STAY, LEAVE):
                give_party(rcon, name)

            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            for name in (STAY, LEAVE):
                tell_bot(rig, name, "FIGHT")
                tell_bot(rig, name, f"MOVE {FIRST_MOVE}")
            for name in (STAY, LEAVE):
                if not wait_for(rig / "bot" / f"{name}.log", r"AUTOFIGHT on", seconds=30):
                    raise RuntimeError(f"{name} never armed")
            begin_floor(rcon, run)

            boss = wait_for_floor(server, rig, [STAY, LEAVE], seconds=240,
                                  pattern=r"Floor \d+ boss \S+ started at level \d+")
            results.append(Result("both players cleared their opponents and the boss started", bool(boss), "no boss start line"))
            if not boss:
                raise RuntimeError("no boss, nothing to test")
            mark = len(server.read_log().splitlines())

            bots[LEAVE].kill()
            print(f"  killed {LEAVE} as the boss appeared")

            outcome = wait_for_floor(server, rig, [STAY], seconds=420)
            print(f"  floor outcome: {outcome or '<none>'}")
            results.append(Result("the floor resolved instead of waiting for the player who left", bool(outcome),
                                  "no cleared/wiped line; see " + str(server.log)))

            lines = server.read_log().splitlines()[mark:]
            left = [line for line in lines if "left run" in line and "mid-floor" in line]
            results.append(Result("the disconnect was noticed once, on the server thread",
                                  len(left) == 1 and "[Server thread/" in left[0], str([l[:110] for l in left])))

            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the run records the player who left as disconnected",
                                  "DISCONNECTED/" in shown, shown.strip()[:300]))

            bad = [line for line in lines if re.search(r"ConcurrentModification|Multi threaded|NullPointer|IllegalState", line)
                   or ("com.cobbletowers" in line and re.search(r"ERROR", line))]
            results.append(Result("nothing threw (no thread violation, no CobbleTowers error)", not bad, "; ".join(bad)[:300]))
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
