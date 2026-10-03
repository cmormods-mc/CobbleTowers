#!/usr/bin/env python3
"""Proves a party that drops all at once is handled one player at a time, on the server thread.

Fabric fires ServerPlayConnectionEvents.DISCONNECT at the head of the connection's own disconnect handling, on
whichever thread noticed the socket close. For an abrupt close that is a Netty network thread, and a party whose
connections fail together lands on SEVERAL Netty threads at the same moment -- every one of them running tower code
over plain, unsynchronised maps and read-modify-write run records. That is a data race: two players' updates can
overwrite each other, or a map can throw mid-iteration.

Four bots are put in one run, then all four are killed in the same instant. What must hold:

  * every disconnect was handled on the server thread (the log line names its thread);
  * the run recorded all four as disconnected -- no player's update lost to another's;
  * nothing from CobbleTowers threw.

    python validation/smoke/disconnect_race_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from,
)
from floor_encounter_test import FIRST_MOVE, TOWER, give_party, start_battle_bot  # noqa: E402
from participant_test import wait_joined  # noqa: E402
from vendor_test import to_intermission_no_battle  # noqa: E402

STAMP = int(time.time()) % 100000
BOTS = [f"TR{STAMP}{letter}" for letter in "abcd"]
ROUNDS = 3


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
    bots: list[subprocess.Popen] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()

        for round_number in range(1, ROUNDS + 1):
            names = [f"{name}{round_number}" for name in BOTS]
            names = [name[:16] for name in names]
            bots = [start_battle_bot(rig, name, FIRST_MOVE) for name in names]
            with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
                for name in names:
                    if not wait_joined(rcon, name):
                        raise RuntimeError(f"{name} never joined")
                rcon.command("execute in cobbletowers:tower run forceload remove all")
                clear_tower(rcon)
                for name in names:
                    give_party(rcon, name)

                run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
                rcon.command(f"cobbletowers runs advance {run} party_submitted")
                rcon.command(f"cobbletowers runs advance {run} party_validated")
                rcon.command(f"cobbletowers runs allocate {run}")
                to_intermission_no_battle(rcon, run)
                mark = len(server.read_log().splitlines())

                # All four at once: the point is the same instant, so no waiting between kills.
                for bot in bots:
                    bot.kill()
                deadline = time.time() + 90
                while time.time() < deadline and not re.search(r"There are 0 of", rcon.command("list")):
                    time.sleep(1)
                time.sleep(2)

                lines = server.read_log().splitlines()[mark:]
                left = [line for line in lines if re.search(r"Player \S+ left run " + re.escape(run), line)]
                off_thread = [line for line in left if "[Server thread/" not in line]
                results.append(Result(f"round {round_number}: all four disconnects were noticed", len(left) == 4,
                                      f"{len(left)} line(s): {[l[:90] for l in left]}"))
                results.append(Result(f"round {round_number}: every one was handled on the server thread",
                                      not off_thread, "; ".join(l[:70] for l in off_thread)))

                shown = rcon.command(f"cobbletowers runs show {run}")
                disconnected = len(re.findall(r"DISCONNECTED/", shown))
                results.append(Result(f"round {round_number}: the run recorded all four as disconnected (none lost)",
                                      disconnected == 4, f"{disconnected} of 4: {shown.strip()[:300]}"))

                bad = [line for line in lines if re.search(r"ConcurrentModification|NullPointer|IllegalState", line)
                       or ("com.cobbletowers" in line and re.search(r"ERROR", line))]
                results.append(Result(f"round {round_number}: nothing from CobbleTowers threw", not bad, "; ".join(bad)[:300]))

                rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            for bot in bots:
                bot.kill()
            bots = []
    except Exception as exc:  # noqa: BLE001
        results.append(Result("disconnect race run", False, repr(exc)))
    finally:
        for bot in bots:
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
