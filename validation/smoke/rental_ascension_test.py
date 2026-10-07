#!/usr/bin/env python3
"""Rentals and AscensionLib, live: a lent team fights with ascension profiles, and none of it can be upgraded.

Needs a rig with AscensionLib installed (testserver-ascend). A bot drafts and starts a rental run, then:

  * every Pokemon of the lent team has an ascension profile (the library's own /ascend inspect says so), which is what a battle's
    ascension effects are built from;
  * the library refuses to open the upgrade screen for a rental (/ascend craft), because every rental carries the craft lock;
  * the run ends and the rentals (and with them the lock) are gone.

    python validation/smoke/rental_ascension_test.py --server-dir <rig>/testserver-ascend --java <jdk21 java> \\
        [--jar <CobbleTowers jar>] [--ascension-jar <AscensionLib jar>]
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import rental_test as rt  # noqa: E402
from rcon import Rcon  # noqa: E402
from run_durability_test import Result, Server, clear_tower, install_jar, read_password, reset_tower_world, server_port  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("SMOKE_JAVA", "java")))
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--ascension-jar", type=Path, default=None)
    parser.add_argument("--node-modules", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    node_modules = args.node_modules or (server_dir.parent / "bot" / "node_modules")
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    if args.ascension_jar:
        for old in (server_dir / "mods").glob("AscensionLib-*.jar"):
            old.unlink()
        shutil.copy2(args.ascension_jar.resolve(), server_dir / "mods" / args.ascension_jar.name)
    if not list((server_dir / "mods").glob("AscensionLib-*.jar")):
        print("This rig has no AscensionLib jar; pass --ascension-jar.")
        return 2
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, args.java)
    port = server_port(server_dir)
    password = read_password(server_dir)
    bot: subprocess.Popen | None = None

    def rcon() -> Rcon:
        return Rcon("127.0.0.1", 25575, password)

    def as_bot(r: Rcon, command: str) -> str:
        return r.command(f"execute as {rt.BOT} run {command}")

    try:
        server.start()
        server.wait_until_ready()
        bot = rt.start_bot(port, node_modules, server_dir / "logs" / "towers-rental-ascension-bot.log")
        with rcon() as r:
            for _ in range(90):
                if rt.BOT in r.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("the bot never joined")
            time.sleep(3)
            clear_tower(r)
            r.command(f"pokegiveother {rt.BOT} glaceon level=100")
            time.sleep(1)

        with rcon() as r:
            run = rt.start_rental_run_for(r)
            results.append(Result("a rental run starts", bool(run), r.command("cobbletowers runs list")[:200]))
            time.sleep(2)
            profiled, said = 0, []
            for slot in range(1, 7):
                said.append(as_bot(r, f"ascend inspect {slot}").strip()[:80])
                if said[-1] and "No ascension profile" not in said[-1] and "empty" not in said[-1]:
                    profiled += 1
            results.append(Result("every Pokemon of the lent team has an ascension profile", profiled == 6, "; ".join(said)))
            refusal = as_bot(r, "ascend craft 1")
            results.append(Result("the upgrade screen refuses to open for a rental, saying why", "on loan" in refusal, refusal.strip()[:160]))
            log = server.read_log()
            results.append(Result("AscensionLib never refused to profile a rental", "did not profile rental" not in log
                                  and "Profiling rental" not in log, "see the server log"))

            if run:
                r.command(f"cobbletowers runs advance {run} abandon_requested")
            time.sleep(8)
            left = r.command(f"cobbletowers play rentals {rt.BOT}")
            results.append(Result("ending the run deletes the rentals", "0 rentals" in left, left[:160]))
    except Exception as exc:  # noqa: BLE001
        import traceback
        traceback.print_exc()
        results.append(Result("rental ascension run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        server.stop()

    print()
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<70} {'' if result.passed else result.detail}")
    failed = [r for r in results if not r.passed]
    print(f"\n{len(results) - len(failed)}/{len(results)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
