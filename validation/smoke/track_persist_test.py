#!/usr/bin/env python3
"""Proves the battle tracks (P37) survive a hard kill, migrate a world that already has season progress, and honour the off-season
claim window.

Three boots, the server killed (not stopped) after the first two so only what was saved before each claim returned can survive:

  1. auto_claim ON: season steps are granted as they are reached (the old behaviour), and mastery level 1 is claimed. Killed.
  2. auto_claim OFF: the steps granted before count as claimed (no second payout), the mastery claim persisted, and further
     steps reached now wait to be claimed. Killed with them unclaimed.
  3. The calendar is pinned in the off-season after season 1: the waiting steps are still claimable (and pay once); then pinned
     into season 2: what was left has lapsed and pays nothing.

The calendar anchor is the default (season 1 runs 2026-10-05 to 2026-11-15, off-season to 11-22).

    python validation/smoke/track_persist_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    Result, Server, install_jar, reset_tower_world, read_password, server_port,
)

BOT = f"TTp{int(time.time()) % 100000}"
TOWER = "cobbletowers:neutral"
IN_SEASON = "2026-10-12"
OFF_SEASON = "2026-11-17"
NEXT_SEASON = "2026-11-24"


def balance_of(shown: str) -> int:
    found = re.search(r"CobbleDollars: (\d+)", shown)
    return int(found.group(1)) if found else -1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
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

    config = server_dir / "config" / "cobbletowers-tracks.json"
    backup = config.with_suffix(".json.bak")
    if config.exists():
        backup.write_bytes(config.read_bytes())
    config.parent.mkdir(parents=True, exist_ok=True)

    results: list[Result] = []

    def check(name: str, passed: bool, detail: str = "") -> None:
        results.append(Result(name, bool(passed), detail))

    def session(auto_claim: bool, work, kill: bool) -> None:
        """Boots a server with the config, joins a bot, runs work(admin), then kills or stops the server."""
        config.write_text('{"auto_claim": %s}' % ("true" if auto_claim else "false"), encoding="utf-8")
        server = Server(server_dir, java)
        bot = None
        try:
            print(f"Booting server, auto_claim={auto_claim} ({server.log})")
            server.start()
            server.wait_until_ready()
            env = dict(os.environ, NODE_PATH=str(node_modules))
            handle = open(server_dir / "logs" / "towers-track-bot.log", "w", encoding="utf-8", errors="replace")
            bot = subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(server_port(server_dir))],
                                   stdout=handle, stderr=subprocess.STDOUT, env=env)
            with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
                for _ in range(90):
                    if BOT in rcon.command("list"):
                        break
                    time.sleep(1)
                else:
                    raise RuntimeError("the bot never joined")
                work(lambda command: rcon.command(f"cobbletowers {command}"))
        finally:
            if bot is not None:
                bot.kill()
            if kill and server.process:
                print("Killing the server (no clean save)")
                server.process.kill()
                server.process.wait()
            else:
                server.stop()

    state: dict[str, int] = {}

    def wallet(admin) -> int:
        return balance_of(admin(f"trackadmin wallet {BOT}"))

    def claim(admin, lane: str, number: int) -> str:
        return admin(f"trackadmin claim {BOT} {lane} {TOWER} {number}").strip()

    def claim_all(admin, lane: str) -> str:
        return admin(f"trackadmin claimall {BOT} {lane} {TOWER}").strip()

    def first_boot(admin) -> None:
        admin("seasonadmin clear")
        admin(f"trialadmin day {IN_SEASON}")
        admin(f"seasonadmin points {BOT} 600")
        time.sleep(2)
        granted = wallet(admin)
        check("auto_claim on grants reached season steps by itself", granted > 0, f"wallet {granted}")
        admin(f"masteryadmin grant {BOT} {TOWER} depth_1")
        check("mastery level 1 claims", claim(admin, "mastery", 1) == "Claimed.")
        time.sleep(1)
        state["after_first"] = wallet(admin)
        check("and pays on top of the steps", state["after_first"] == granted + 50, f"wallet {state['after_first']} vs {granted}")

    def second_boot(admin) -> None:
        admin(f"trialadmin day {IN_SEASON}")
        check("the wallet survived the hard kill", wallet(admin) == state["after_first"],
              f"wallet {wallet(admin)} vs {state['after_first']}")
        again = claim(admin, "mastery", 1)
        check("the mastery claim survived the hard kill (refused as already claimed)", "already claimed" in again, again)
        step = claim(admin, "season", 1)
        check("a step granted before claiming existed counts as claimed", "already claimed" in step, step)
        check("claim-all finds nothing for those steps", claim_all(admin, "season") == "Nothing to claim.")
        check("and pays nothing", wallet(admin) == state["after_first"], f"wallet {wallet(admin)}")
        admin(f"seasonadmin points {BOT} 3000")
        time.sleep(2)
        check("steps reached with auto_claim off wait to be claimed", wallet(admin) == state["after_first"],
              f"wallet {wallet(admin)} vs {state['after_first']}")

    def third_boot(admin) -> None:
        admin(f"trialadmin day {OFF_SEASON}")
        check("the wallet is the same after the second kill", wallet(admin) == state["after_first"], f"wallet {wallet(admin)}")
        paid = 0
        for number in range(1, 60):
            if claim(admin, "season", number) == "Claimed.":
                paid = number
                break
        time.sleep(1)
        check("in the off-season a step reached in season 1 can still be claimed", paid > 0, "no step was claimable")
        after_one = wallet(admin)
        check("the same step cannot be claimed twice", "already claimed" in claim(admin, "season", paid))
        # A step's prize need not be CobbleDollars, so the wallet is not the proof; the next step still claiming is, and
        # it leaves the ones after it waiting for the lapse below.
        check("the next step claims too", claim(admin, "season", paid + 1) == "Claimed.")
        after_one = wallet(admin)
        admin(f"trialadmin day {NEXT_SEASON}")
        late = claim_all(admin, "season")
        time.sleep(1)
        check("once season 2 starts the unclaimed steps have lapsed", late == "Nothing to claim.", late)
        check("and pay nothing", wallet(admin) == after_one, f"wallet {wallet(admin)} vs {after_one}")
        admin("trialadmin day off")

    try:
        session(True, first_boot, kill=True)
        session(False, second_boot, kill=True)
        session(False, third_boot, kill=False)
    except Exception as exc:  # noqa: BLE001
        results.append(Result("track persistence run", False, repr(exc)))
    finally:
        if backup.exists():
            backup.replace(config)
        else:
            config.unlink(missing_ok=True)

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
