#!/usr/bin/env python3
"""Proves season points and the free track (P36b): a regional cycle clear earns points (30 in the spotlight region), operator points
cross track steps and grant them exactly once, step 30 hands over the four season trim templates and the cosmetics, the data pack
knows the season trim pattern and the tags, and the track command reports it.

The calendar is pinned inside season 1 (spotlight: Tideforge) with the operator's day pin; the cycle is cleared by operator command and
the drafts, picks and ready-ups are the real player path as the bot.

    python validation/smoke/season_points_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TSp{int(time.time()) % 100000}"
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
        handle = open(server_dir / "logs" / "towers-points-bot.log", "w", encoding="utf-8", errors="replace")
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

            def templates() -> int:
                """How many season 1 trim templates the bot is carrying."""
                out = rcon.command(f"data get entity {BOT} Inventory")
                total = 0
                for match in re.finditer(r"\{[^{}]*?\}", out):
                    text = match.group(0)
                    if "cobbletowers:season_trim_template_1" in text:
                        count = re.search(r"count: (\d+)", text)
                        total += int(count.group(1)) if count else 1
                return total

            admin("seasonadmin clear")
            admin("trialadmin day 2026-10-12")
            status = admin("seasonadmin status")
            results.append(Result("season 1 is running with Tideforge in the spotlight",
                                  "Season 1: The Rising Tide" in status and "Spotlight region: Tideforge" in status, status.strip()[:240]))
            empty = say("season track")
            results.append(Result("the track starts at nothing", "step 0/30, 0/2250 points" in empty, empty.strip()[:240]))

            # --- a real regional cycle (spotlight): 30 points -------------------------------------------------------------
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
            after_clear = say("season track")
            log = server.read_log()
            total = int(re.search(r"(\d+)/2250 points", after_clear).group(1))
            # Daily contracts that the operator-cleared floors happened to complete add 5 each, so the total is 30 plus those.
            results.append(Result("a spotlight regional clear earned exactly 30 points",
                                  "earned 30 season point(s) from REGIONAL_CLEAR" in log, "no 30-point regional award in the log"))
            results.append(Result("the rest of the total is contract points (5 each), nothing else",
                                  total >= 30 and (total - 30) % 5 == 0, f"total {total}"))
            results.append(Result("the day's tally is shown", f"{total}/80 earned today" in after_clear, after_clear.strip()[:240]))
            away = 75 - total
            results.append(Result("the next steps are listed with what they give",
                                  f"step 1 in {away} point(s): 50 CobbleDollars" in after_clear, after_clear.strip()[:300]))

            # --- crossing steps: enough to reach exactly step 2 -----------------------------------------------------------
            to_step_2 = 150 - total
            added = admin(f"seasonadmin points {BOT} {to_step_2}")
            time.sleep(1)
            log = server.read_log()
            results.append(Result("operator points are added outside the cap", f"Added {to_step_2} season points" in added, added.strip()[:160]))
            results.append(Result("each step reached is granted once",
                                  log.count("reached season 1 track step 1:") == 1 and log.count("reached season 1 track step 2:") == 1,
                                  f"step 1 x{log.count('reached season 1 track step 1:')}, step 2 x{log.count('reached season 1 track step 2:')}"))
            mid = say("season track")
            results.append(Result("the track reports step 2", "step 2/30, 150/2250 points" in mid, mid.strip()[:240]))
            admin(f"seasonadmin points {BOT} 1")
            log = server.read_log()
            results.append(Result("a point that crosses no step grants nothing again",
                                  log.count("reached season 1 track step 2:") == 1 and "track step 3:" not in log, "a step was granted twice"))

            # --- the whole track: 2250 total -------------------------------------------------------------------------------
            admin(f"seasonadmin points {BOT} 5000")
            time.sleep(2)
            log = server.read_log()
            granted = sorted({int(n) for n in re.findall(r"reached season 1 track step (\d+):", log)})
            results.append(Result("every one of the 30 steps was granted, each once",
                                  granted == list(range(1, 31)) and all(
                                      log.count(f"reached season 1 track step {n}:") == 1 for n in range(1, 31)),
                                  f"granted {granted}"))
            done = say("season track")
            results.append(Result("the track reports complete with the earned cosmetics",
                                  "step 30/30" in done and "The whole track is done" in done and "s1:badge" in done.replace(" ", "_")
                                  or ("badge" in done and "title champion" in done and "banner 3" in done),
                                  done.strip()[:400]))
            results.append(Result("step 30 handed over four season 1 trim templates", templates() == 4, f"{templates()} templates"))
            admin(f"seasonadmin points {BOT} 100")
            results.append(Result("nothing is granted again once the track is done", templates() == 4, f"{templates()} templates"))

            # --- the data pack knows the trim -------------------------------------------------------------------------------
            helm = rcon.command(f'give {BOT} cobbletowers:challenger_helmet[trim={{pattern:"cobbletowers:season_1",material:"minecraft:gold"}}]')
            results.append(Result("a tower helmet can carry the season 1 trim", "Gave 1" in helm, helm.strip()[:200]))
            tagged = rcon.command(f"execute if items entity {BOT} container.* #minecraft:trim_templates")
            results.append(Result("the season template is in the trim_templates tag", "Test passed" in tagged or "passed" in tagged.lower(),
                                  tagged.strip()[:160]))
            armor = rcon.command(f"execute if items entity {BOT} container.* #minecraft:trimmable_armor")
            results.append(Result("the tower armor is in the trimmable_armor tag", "passed" in armor.lower(), armor.strip()[:160]))
            bad = rcon.command(f'give {BOT} cobbletowers:challenger_helmet[trim={{pattern:"cobbletowers:season_99",material:"minecraft:gold"}}]')
            results.append(Result("a season with no pattern is refused (the check is not vacuous)", "Gave 1" not in bad, bad.strip()[:200]))

            log = server.read_log()
            results.append(Result("no CobbleTowers exception during any of it",
                                  "	at com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))
            admin("trialadmin day off")
    except Exception as exc:  # noqa: BLE001
        results.append(Result("season points run", False, repr(exc)))
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
