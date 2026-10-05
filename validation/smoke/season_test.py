#!/usr/bin/env python3
"""Proves seasons (P36a): a cycle posts to the season and all-time boards, the season ends into the Hall exactly once, the off-season
freezes the season board, the next season starts empty, and the off switch restores the all-time world.

The calendar is moved with the operator's day pin (`trialadmin day`), and floors are cleared by operator command; the drafts,
picks and ready-ups are the real player path as the bot. The anchor is Monday 2026-10-05, so 12 October is week 2 of season 1,
16 November is the first day of the off-season, and 23 November starts season 2.

    python validation/smoke/season_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TSn{int(time.time()) % 100000}"
TOWER = "cobbletowers:test"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"


def run_line(rcon: Rcon) -> str:
    """The newest run in `runs list`. RCON joins the list onto one line, so a second cycle would otherwise read the first's run."""
    text = " ".join(rcon.command("cobbletowers runs list").split())
    starts = [match.start() for match in re.finditer(UUID_RE, text)]
    return text[starts[-1]:].strip() if starts else ""


def wait_state(rcon: Rcon, state: str, floor: int, seconds: int = 60) -> bool:
    deadline = time.time() + seconds
    while time.time() < deadline:
        line = run_line(rcon)
        if state in line and f"floor {floor}" in line:
            return True
        time.sleep(1)
    return False


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
    bot = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-season-bot.log", "w", encoding="utf-8", errors="replace")
        bot = subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(server_port(server_dir))],
                               stdout=handle, stderr=subprocess.STDOUT, env=env)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for _ in range(90):
                if BOT in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("the bot never joined")
            clear_tower(rcon)
            rcon.command(f"pokegiveother {BOT} glaceon level=100")

            def play(command: str) -> None:
                rcon.command(f"execute as {BOT} run cobbletowers play {command}")

            def say(command: str) -> str:
                return rcon.command(f"execute as {BOT} run cobbletowers play {command}")

            def admin(command: str) -> str:
                return rcon.command(f"cobbletowers {command}")

            def cycle() -> None:
                """Plays one four-floor cycle of the Test tower by operator command, ending at its cycle-end intermission."""
                play(f"tower {TOWER}")
                play("start")
                if not wait_state(rcon, "ENCOUNTER_ACTIVE", 1, seconds=40):
                    raise RuntimeError("floor 1 never opened: " + run_line(rcon))
                run = re.search(UUID_RE, run_line(rcon)).group(1)
                for floor in range(1, 5):
                    rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
                    rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                    if not wait_state(rcon, "INTERMISSION", floor, seconds=20):
                        raise RuntimeError(f"floor {floor} never reached its intermission: {run_line(rcon)}")
                    time.sleep(2)
                    if floor == 4:
                        break
                    for card in (1, 1, 2):
                        play(f"pick {card}")
                    play("ready")
                    if not wait_state(rcon, "ENCOUNTER_ACTIVE", floor + 1, seconds=40):
                        raise RuntimeError(f"floor {floor + 1} never opened: {run_line(rcon)}")
                time.sleep(3)
                rcon.command(f"cobbletowers runs advance {run} abandon_requested")
                time.sleep(8)

            admin("masteryadmin clearboards")
            admin("seasonadmin clear")

            # --- inside season 1 (the anchor is Monday 2026-10-05) -------------------------------------------------------
            admin("trialadmin day 2026-10-12")
            status = admin("seasonadmin status")
            results.append(Result("the calendar puts 12 October in week 2 of season 1",
                                  "Season 1: The Rising Tide" in status and "week 2 of 6" in status, status.strip()[:200]))
            dry = admin("seasonadmin finalize dry")
            results.append(Result("nothing is finalisable while the season runs", "Nothing to finalise" in dry, dry.strip()[:160]))

            cycle()
            seasonal = say(f"leaderboard difficulty {TOWER}")
            alltime = say(f"leaderboard difficulty alltime {TOWER}")
            results.append(Result("a cycle clear posts to the season board (the default view)",
                                  "Season 1: The Rising Tide" in seasonal and BOT in seasonal, seasonal.strip()[:240]))
            results.append(Result("and to the all-time board", "all-time" in alltime and BOT in alltime, alltime.strip()[:240]))

            # --- the season ends: the off-season begins on 16 November ----------------------------------------------------
            admin("trialadmin day 2026-11-16")
            admin("seasonadmin check")
            time.sleep(2)
            hall = say("hall")
            results.append(Result("the Hall of Fame records season 1 with its winner",
                                  "Season 1: The Rising Tide" in hall and BOT in hall, hall.strip()[:300]))
            results.append(Result("finalisation ran once", server.read_log().count("Season 1 (The Rising Tide) finalised") == 1,
                                  f"{server.read_log().count('finalised')} finalised line(s)"))
            again = admin("seasonadmin finalize dry")
            results.append(Result("running it again finds nothing to do", "Nothing to finalise" in again, again.strip()[:160]))
            off = admin("seasonadmin status")
            results.append(Result("the status shows the off-season and the next start",
                                  "Off-season" in off and "Season 2: Deep Roots" in off and "2026-11-23" in off, off.strip()[:240]))

            cycle()
            frozen = say(f"leaderboard difficulty {TOWER}")
            alltime2 = say(f"leaderboard difficulty alltime {TOWER}")
            results.append(Result("an off-season result leaves the finished season's board frozen",
                                  frozen.count(BOT) == 1, f"{frozen.count(BOT)} entries on the season board"))
            results.append(Result("and posts to the all-time board", alltime2.count(BOT) == 2, f"{alltime2.count(BOT)} entries all-time"))

            # --- season 2 begins on 23 November --------------------------------------------------------------------------
            admin("trialadmin day 2026-11-23")
            admin("seasonadmin check")
            time.sleep(1)
            fresh = say(f"leaderboard difficulty {TOWER}")
            results.append(Result("season 2 starts with an empty board",
                                  "Season 2: Deep Roots" in fresh and "no entries yet" in fresh, fresh.strip()[:240]))
            kept = say(f"leaderboard difficulty alltime {TOWER}")
            results.append(Result("and the all-time board kept everything", kept.count(BOT) == 2, f"{kept.count(BOT)} entries"))
            old = say("hall 1")
            results.append(Result("season 1 is still in the Hall", "Season 1: The Rising Tide" in old, old.strip()[:200]))
            results.append(Result("season 2's start was announced once",
                                  server.read_log().count("Season 2 (Deep Roots) began") == 1,
                                  f"{server.read_log().count('Season 2 (Deep Roots) began')} announcement(s)"))

            # --- the off switch ------------------------------------------------------------------------------------------
            admin("seasonadmin disable")
            off_status = admin("seasonadmin status")
            plain = say(f"leaderboard difficulty {TOWER}")
            results.append(Result("with seasons off the world reads as all-time",
                                  "switched off" in off_status and "Season" not in plain and plain.count(BOT) == 2,
                                  (off_status + plain).strip()[:240]))
            admin("seasonadmin enable")
            admin("trialadmin day off")

            log = server.read_log()
            results.append(Result("no CobbleTowers exception during any of it",
                                  "	at com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("season run", False, repr(exc)))
    finally:
        if bot is not None:
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
