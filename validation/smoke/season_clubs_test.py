#!/usr/bin/env python3
"""Proves what seasons add to clubs and Echoes (P36c): a club's season score, the club board, the Hall's club board, the top club's prestige
banner and permanent mark, an Echo stamped with its season and kept past the season's end, and a clean season 2.

The calendar is pinned with the operator's day pin: 12 October is season 1 (spotlight Tideforge), 16 November the off-season, and 23 November
season 2. The cycle is cleared by operator command on Tideforge, so it also runs a real reward banking with the spotlight weights in place.

    python validation/smoke/season_clubs_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TSc{int(time.time()) % 100000}"
TOWER = "cobbletowers:tideforge"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"


def run_line(rcon: Rcon) -> str:
    """The newest run in `runs list` (RCON joins the list onto one line)."""
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
        handle = open(server_dir / "logs" / "towers-seasonclubs-bot.log", "w", encoding="utf-8", errors="replace")
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

            admin("masteryadmin clearboards")
            admin("seasonadmin clear")
            admin("clubs clear")
            admin("echoes clear")
            admin("trialadmin day 2026-10-12")

            made = say("club create Tidal_Crew tc")
            results.append(Result("a club is founded in season 1", "created" in made, made.strip()[:160]))
            early = say("club banner gold")
            results.append(Result("the prestige banner is not available before it is earned",
                                  "not a banner colour" in early and "gold" not in early.split("Try:")[-1], early.strip()[:240]))

            # --- a real Tideforge cycle in season 1 ---------------------------------------------------------------------
            play(f"tower {TOWER}")
            play("start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", 1, seconds=40):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)
            for floor in range(1, 11):
                rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
                rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                if not wait_state(rcon, "INTERMISSION", floor, seconds=20):
                    raise RuntimeError(f"floor {floor} never reached its intermission: {run_line(rcon)}")
                time.sleep(2)
                if floor == 10:
                    break
                for card in (1, 1, 2):
                    play(f"pick {card}")
                play("ready")
                if not wait_state(rcon, "ENCOUNTER_ACTIVE", floor + 1, seconds=40):
                    raise RuntimeError(f"floor {floor + 1} never opened: {run_line(rcon)}")
            time.sleep(3)

            info = say("club")
            score = re.search(r"season 1 score (\d+), all-time (\d+)", info)
            results.append(Result("the club has a season score and the all-time score beside it",
                                  bool(score) and int(score.group(1)) > 0 and score.group(1) == score.group(2), info.strip()[:240]))
            season_top = say("club top")
            results.append(Result("the club board shows the season's by default",
                                  "season 1" in season_top and "Tidal_Crew" in season_top, season_top.strip()[:240]))
            all_top = say("club top alltime")
            results.append(Result("and the all-time one on request", "all-time" in all_top and "Tidal_Crew" in all_top, all_top.strip()[:240]))
            echoes = admin("echoes list")
            results.append(Result("the top-ten run recorded an Echo stamped with season 1",
                                  "season 1" in echoes and BOT in echoes, echoes.strip()[:240]))

            # --- the season ends --------------------------------------------------------------------------------------------
            rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            time.sleep(8)
            admin("trialadmin day 2026-11-16")
            admin("seasonadmin check")
            time.sleep(2)
            hall = say("hall")
            results.append(Result("the Hall of Fame holds the season's club board with the club and its member",
                                  "Club board" in hall and "Tidal_Crew" in hall and BOT in hall, hall.strip()[:400]))
            after = say("club")
            results.append(Result("the champion club is honoured and has the gold banner unlocked",
                                  "Season 1: champion" in after and "gold" in after, after.strip()[:300]))
            banner = say("club banner gold")
            results.append(Result("and can now set it", "Banner set to gold" in banner, banner.strip()[:160]))
            log = server.read_log()
            results.append(Result("the log records the award once",
                                  log.count("finished season 1 in champion place and unlocks the gold banner") == 1,
                                  f"{log.count('unlocks the gold banner')} award line(s)"))
            echoes_after = admin("echoes list")
            results.append(Result("the Echo outlives its season", BOT in echoes_after, echoes_after.strip()[:200]))

            # --- season 2 starts clean ----------------------------------------------------------------------------------------
            admin("trialadmin day 2026-11-23")
            admin("seasonadmin check")
            time.sleep(1)
            fresh = say("club top")
            results.append(Result("season 2's club board starts empty", "season 2" in fresh and "No club has scored yet" in fresh,
                                  fresh.strip()[:240]))
            kept = say("club top alltime")
            results.append(Result("while the all-time board keeps the club", "Tidal_Crew" in kept, kept.strip()[:200]))
            track = say("season track")
            results.append(Result("the champion's mark is a permanent cosmetic on the next season's track view",
                                  "s1:club gold" in track, track.strip()[:400]))
            info2 = say("club")
            results.append(Result("the club's honours and banner survive into season 2",
                                  "Season 1: champion" in info2 and "season 2 score 0" in info2, info2.strip()[:300]))

            log = server.read_log()
            results.append(Result("no CobbleTowers exception during any of it, banking under the spotlight included",
                                  "\tat com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))
            admin("trialadmin day off")
    except Exception as exc:  # noqa: BLE001
        results.append(Result("season clubs run", False, repr(exc)))
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
