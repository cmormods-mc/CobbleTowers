#!/usr/bin/env python3
"""A quick check that a rental run adds nothing to the player's Pokedex (P33), without the slow crash scenarios of rental_test.py.

Cobblemon marks anything that enters a party as OWNED in the player's Pokedex, and servers hang rewards and ranks on Pokedex
progress, so a lent Pokemon that registered would be a leak into a real collection. The mod cancels Cobblemon's Pokedex update for a
rental; this proves it: the bot's Pokedex before a rental run, during it, and after it must be the same.

    python validation/smoke/rental_dex_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

import rental_test as rt  # noqa: E402
from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    node_modules = server_dir.parent / "bot" / "node_modules"
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    port = server_port(server_dir)
    password = read_password(server_dir)
    bot: subprocess.Popen | None = None
    try:
        server.start()
        server.wait_until_ready()
        bot = rt.start_bot(port, node_modules, server_dir / "logs" / "towers-rental-dex-bot.log")
        with Rcon("127.0.0.1", 25575, password) as rcon:
            for _ in range(90):
                if rt.BOT in rcon.command("list"):
                    break
                time.sleep(1)
            time.sleep(3)
            clear_tower(rcon)
            rcon.command(f"pokegiveother {rt.BOT} glaceon level=100")
            for _ in range(5):
                rcon.command(f"pokegiveother {rt.BOT} magikarp level=1")
            time.sleep(2)
            before = rt.dex(rcon)
            results.append(Result("the Pokedex starts with only what the bot was given", before == "glaceon:OWNED,magikarp:OWNED", before))

            rt.play(rcon, f"tower {rt.TOWER}")
            rt.play(rcon, "playlist rental")
            drafted = rt.draft_team(rcon)
            results.append(Result("a team drafts", drafted, rt.play(rcon, "draft")[:200]))
            rt.play(rcon, "start")
            rt.wait_for(lambda: rt.live_run(rcon) != "", 40)
            run = rt.live_run(rcon)
            results.append(Result("the rental run starts with six rentals", bool(run) and len(rt.rentals_listed(rcon)) == 6,
                                  str(rt.rentals_listed(rcon))))
            during = rt.dex(rcon)
            results.append(Result("six lent species added nothing to the Pokedex", during == before, f"before {before} | during {during}"))

            rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            rt.wait_for(lambda: rt.rentals_listed(rcon) == [], 20)
            after = rt.dex(rcon)
            results.append(Result("and nothing after the run ended", after == before and rt.rentals_listed(rcon) == [],
                                  f"before {before} | after {after}"))
            clear_tower(rcon)
            rcon.command(f"cobbletowers play rentals {rt.BOT} stray")
            time.sleep(2)
            stray = rt.dex(rcon)
            results.append(Result("a stray rental added nothing either", stray == before, f"before {before} | stray {stray}"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("rental pokedex run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        server.stop()

    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name.ljust(width)}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
