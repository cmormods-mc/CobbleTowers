#!/usr/bin/env python3
"""Proves the operator `runs winfloor` command: it wins the floor under way through the normal result path.

One bot starts a solo run. While its first floor is being fought:

  * `runs winfloor <player>` answers that the floor was won;
  * the floor's rewards are banked (the ledger has the floor cleared) and the run moves past the floor;
  * with no floor under way the command says so instead of doing anything.

    python validation/smoke/win_floor_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import os
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
from floor_encounter_test import TOWER, give_party  # noqa: E402
from exit_test import start_bot, wait_online, wait_for, dimension, run_with_state, quarantined  # noqa: E402

A = f"TWa{int(time.time()) % 100000}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--node-modules", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    node_modules = args.node_modules or (server_dir.parent / "bot" / "node_modules")
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    port = server_port(server_dir)
    bots: dict[str, subprocess.Popen] = {}
    password = read_password(server_dir)
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bots[A] = start_bot(A, port, node_modules, server_dir / "logs" / f"towers-winfloor-{A}.log")

        with Rcon("127.0.0.1", 25575, password) as rcon:
            if not wait_online(rcon, A):
                raise RuntimeError(f"{A} never joined")
            clear_tower(rcon)
            give_party(rcon, A)
            time.sleep(2)

            idle = rcon.command(f"cobbletowers runs winfloor {A}")
            results.append(Result("no run: the command refuses", "not in a tower run" in idle, idle[:120]))

            rcon.command(f"execute as {A} run cobbletowers play tower {TOWER}")
            rcon.command(f"execute as {A} run cobbletowers play confirm")
            rcon.command(f"execute as {A} run cobbletowers play start")
            inside = wait_for(lambda: dimension(rcon, A) == "cobbletowers:tower", 40)
            results.append(Result("a started run puts the player in the tower", inside, dimension(rcon, A)))
            fighting = wait_for(lambda: bool(run_with_state(rcon, ("ENCOUNTER_ACTIVE",))), 40)
            results.append(Result("the first floor is under way", fighting, rcon.command("cobbletowers runs list")[:160]))
            run = run_with_state(rcon, ("ENCOUNTER_ACTIVE",))

            won = rcon.command(f"cobbletowers runs winfloor {A}")
            results.append(Result("winfloor reports the floor won", "Floor won" in won, won[:160]))

            moved = wait_for(lambda: not run_with_state(rcon, ("ENCOUNTER_ACTIVE",)), 20)
            results.append(Result("the run moves past the floor", moved, rcon.command("cobbletowers runs list")[:160]))
            ledger = rcon.command(f"execute as {A} run cobbletowers runs reward show")
            results.append(Result("the floor's clear is in the ledger", "FLOOR_CLEARED floor 1" in ledger, ledger[:200]))

            again = rcon.command(f"cobbletowers runs winfloor {A}")
            results.append(Result("between floors the command refuses", "no floor under way" in again, again[:120]))
            time.sleep(2)
            results.append(Result("no cell quarantined", quarantined(rcon) == 0, rcon.command("cobbletowers cells list")[:200]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("win floor run", False, repr(exc)))
    finally:
        for bot in bots.values():
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
