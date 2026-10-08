#!/usr/bin/env python3
"""Proves claiming on the battle tracks (P37) against a real server: the shipped mastery track loads through the datapack reload, a level
can be claimed only once it is reached, a claim pays the CobbleDollars the track names, a second claim is refused, claim-all collects
the rest and then reports nothing left; season steps reached with auto_claim off are not granted until claimed, and the same rules hold.

Claims run through {@code /cobbletowers trackadmin}, which calls the same code as the Progress tab's buttons. The calendar is pinned
inside season 1 with the operator's day pin and the config is written (auto_claim off) before the server starts and restored after.

    python validation/smoke/track_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TTr{int(time.time()) % 100000}"
TOWER = "cobbletowers:neutral"
FIRST_ACHIEVEMENT = "depth_1"


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
    # The owner's config adds a level 2 reward of 7 CobbleDollars on top of the shipped track.
    config.write_text('{"auto_claim": false, "mastery": {"levels": [{"level": 2, "grants": '
                      '[{"item": "cobbletowers:cobble_dollar", "amount": 7}]}]}}', encoding="utf-8")

    results: list[Result] = []
    server = Server(server_dir, java)
    bot = None
    try:
        print(f"Booting server ({server.log})")
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

            def admin(command: str) -> str:
                return rcon.command(f"cobbletowers {command}")

            def wallet() -> int:
                return balance_of(admin(f"trackadmin wallet {BOT}"))

            def claim(lane: str, number: int) -> str:
                return admin(f"trackadmin claim {BOT} {lane} {TOWER} {number}").strip()

            def claim_all(lane: str) -> str:
                return admin(f"trackadmin claimall {BOT} {lane} {TOWER}").strip()

            if admin("trackadmin").startswith("Incorrect argument"):
                raise RuntimeError("trackadmin is not registered: the server is not running this build's jar")
            admin("seasonadmin clear")
            admin("trialadmin day 2026-10-12")
            log = server.read_log()
            results.append(Result("the shipped mastery track loaded", "mastery track" not in log.lower() or "could not be read" not in log,
                                  "the built-in mastery track could not be read"))

            # --- mastery ----------------------------------------------------------------------------------------------------
            start = wallet()
            results.append(Result("a new wallet is empty", start == 0, f"wallet {start}"))
            early = claim("mastery", 1)
            results.append(Result("level 1 cannot be claimed before it is reached", "not reached" in early, early))

            admin(f"masteryadmin grant {BOT} {TOWER} {FIRST_ACHIEVEMENT}")
            first = claim("mastery", 1)
            time.sleep(1)
            after_first = wallet()
            results.append(Result("a reached level can be claimed", first == "Claimed.", first))
            results.append(Result("and pays the 50 CobbleDollars the track names", after_first == 50, f"wallet {after_first}"))
            again = claim("mastery", 1)
            results.append(Result("a second claim of the same level is refused", "already claimed" in again, again))
            results.append(Result("and pays nothing more", wallet() == 50, f"wallet {wallet()}"))
            beyond = claim("mastery", 3)
            results.append(Result("a level with no reward cannot be claimed", "no reward" in beyond or "not reached" in beyond, beyond))
            admin(f"masteryadmin grant {BOT} {TOWER} depth_10")
            owner = claim("mastery", 2)
            results.append(Result("a reward added in config/cobbletowers-tracks.json is claimable", owner == "Claimed.", owner))
            results.append(Result("and pays the 7 CobbleDollars the config names", wallet() == 57, f"wallet {wallet()}"))

            admin(f"masteryadmin grant {BOT} {TOWER} all")
            collected = claim_all("mastery")
            time.sleep(1)
            richer = wallet()
            results.append(Result("claim-all collects the remaining levels", collected.startswith("Claimed ") and richer > 50,
                                  f"{collected} / wallet {richer}"))
            nothing = claim_all("mastery")
            results.append(Result("and then reports nothing left to claim", nothing == "Nothing to claim.", nothing))
            results.append(Result("without paying again", wallet() == richer, f"wallet {wallet()} after {richer}"))

            # --- season, auto_claim off -------------------------------------------------------------------------------------
            before_season = wallet()
            admin(f"seasonadmin points {BOT} 600")
            time.sleep(1)
            results.append(Result("reaching season steps with auto_claim off grants nothing by itself",
                                  wallet() == before_season, f"wallet {wallet()} vs {before_season}"))
            season_first = claim("season", 1)
            results.append(Result("a reached season step can be claimed", season_first == "Claimed.", season_first))
            season_again = claim("season", 1)
            results.append(Result("a season step cannot be claimed twice", "claimed" in season_again.lower()
                                  and season_again != "Claimed.", season_again))
            season_far = claim("season", 30)
            results.append(Result("a season step not yet reached is refused", season_far != "Claimed.", season_far))
            season_all = claim_all("season")
            results.append(Result("claim-all collects the other reached steps", season_all.startswith("Claimed "), season_all))
            season_none = claim_all("season")
            results.append(Result("and then has nothing left", season_none == "Nothing to claim.", season_none))

            log = server.read_log()
            results.append(Result("no CobbleTowers exception during any of it",
                                  "\tat com.cobbletowers" not in log and "Mixin apply" not in log,
                                  "a CobbleTowers or mixin error is in the log"))
            admin("trialadmin day off")
    except Exception as exc:  # noqa: BLE001
        results.append(Result("track run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        print("Stopping server")
        server.stop()
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
