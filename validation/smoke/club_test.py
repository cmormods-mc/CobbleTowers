#!/usr/bin/env python3
"""Proves clubs (P35): found, invite, join, owner-only actions, a regional cycle counts toward the score and the week, the weekly claim, leave and disband.

Floors are cleared by operator command; the drafts, picks and ready-ups are the real player path as the bot.

    python validation/smoke/club_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TCl{int(time.time()) % 100000}"
BOT2 = BOT + "b"
TOWER = "cobbletowers:tideforge"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"


def run_line(rcon: Rcon) -> str:
    for line in rcon.command("cobbletowers runs list").splitlines():
        if re.search(UUID_RE, line):
            return line.strip()
    return ""


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
    bot2 = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-club-bot.log", "w", encoding="utf-8", errors="replace")
        bot = subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(server_port(server_dir))],
                               stdout=handle, stderr=subprocess.STDOUT, env=env)

        handle2 = open(server_dir / "logs" / "towers-club-bot2.log", "w", encoding="utf-8", errors="replace")
        bot2 = subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT2, str(server_port(server_dir))],
                                stdout=handle2, stderr=subprocess.STDOUT, env=env)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for _ in range(90):
                online = rcon.command("list")
                if BOT in online and BOT2 in online:
                    break
                time.sleep(1)
            else:
                raise RuntimeError("the bots never joined")
            clear_tower(rcon)
            rcon.command(f"pokegiveother {BOT} glaceon level=100")
            rcon.command("cobbletowers clubs clear")
            rcon.command("cobbletowers echoes clear")

            def play(command: str, who: str = BOT) -> None:
                rcon.command(f"execute as {who} run cobbletowers play {command}")

            def say(command: str, who: str = BOT) -> str:
                return rcon.command(f"execute as {who} run cobbletowers play {command}")

            made = say("club create Tidal_Crew tc")
            results.append(Result("a player can found a club", "created" in made, made.strip()[:160]))
            dup = say("club create tidal_crew xx", BOT2)
            results.append(Result("a club name is unique, ignoring case", "taken" in dup, dup.strip()[:160]))
            bad = say("club create x", BOT2)
            results.append(Result("a bad name is refused", "letters, digits" in bad, bad.strip()[:160]))

            early = say("club accept Tidal_Crew", BOT2)
            results.append(Result("nobody can join without an invite", "no open invite" in early, early.strip()[:160]))
            say(f"club invite {BOT2}")
            joined = say("club accept Tidal_Crew", BOT2)
            results.append(Result("an invited player can join", "You joined" in joined, joined.strip()[:160]))
            kick = say(f"club kick {BOT}", BOT2)
            results.append(Result("only the owner can remove a member", "Only the club's owner" in kick, kick.strip()[:160]))
            ban = say("club banner red")
            ban2 = say("club banner blue", BOT2)
            results.append(Result("the owner sets a banner and a member cannot",
                                  "Banner set to red" in ban and "Only the club's owner" in ban2, (ban + ban2).strip()[:200]))

            # A real regional cycle by BOT alone counts toward the club's score and week.
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
            results.append(Result("a regional cycle clear raises the club's score", re.search(r"score [1-9]\d*", info) is not None,
                                  info.strip()[:240]))
            results.append(Result("and counts toward the weekly goal", "1/12" in info, info.strip()[:240]))

            top = say("club top")
            results.append(Result("the club board lists the club", "Tidal_Crew" in top, top.strip()[:200]))

            early_claim = say("club claim")
            results.append(Result("the weekly reward cannot be claimed before the goal", "not met" in early_claim, early_claim.strip()[:160]))
            rcon.command("cobbletowers clubs addclears Tidal_Crew 11")
            c1 = say("club claim")
            c2 = say("club claim")
            c3 = say("club claim", BOT2)
            results.append(Result("once the goal is met each member claims once",
                                  "+300" in c1 and "already claimed" in c2 and "+300" in c3, (c1 + c2 + c3).strip()[:240]))

            left = say("club leave", BOT2)
            gone = say("club", BOT2)
            results.append(Result("a member can leave", "You left" in left and "not in a club" in gone, (left + gone).strip()[:200]))
            end = say("club disband")
            results.append(Result("the owner can disband the club", "disbanded" in end, end.strip()[:160]))

            log = server.read_log()
            results.append(Result("no CobbleTowers exception during any of it",
                                  "	at com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("echo run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        if bot2 is not None:
            bot2.kill()
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
