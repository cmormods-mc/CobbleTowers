#!/usr/bin/env python3
"""Proves intermission event rooms (P34b): a room may open behind the ordinary draft, holds the floor, and applies its choice.

Floors are cleared by operator command; the drafts, picks and ready-ups are the real player path as the bot.

    python validation/smoke/event_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TRl{int(time.time()) % 100000}"
TOWER = "cobbletowers:neutral"
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
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-event-bot.log", "w", encoding="utf-8", errors="replace")
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

            play(f"tower {TOWER}")
            play("confirm")
            play("start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", 1, seconds=40):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)

            rooms = 0
            for floor in range(1, 5):
                rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
                rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                if not wait_state(rcon, "INTERMISSION", floor, seconds=20):
                    raise RuntimeError(f"floor {floor} never reached its intermission: {run_line(rcon)}")
                time.sleep(2)
                before = server.read_log().count("opened an EVENT room")
                play("pick 1")   # the ordinary draft; an event room may open behind it (seeded, about half the time)
                time.sleep(2)
                if server.read_log().count("opened an EVENT room") > before:
                    rooms += 1
                    play("ready")
                    time.sleep(3)
                    results.append(Result(f"floor {floor}: an open event room holds the intermission shut",
                                          "INTERMISSION" in run_line(rcon), run_line(rcon)))
                    choices = server.read_log().count("event choice")
                    play("pick 1")
                    time.sleep(2)
                    results.append(Result(f"floor {floor}: the event choice is applied",
                                          server.read_log().count("event choice") == choices + 1, "no 'event choice' line"))
                play("ready")
                if not wait_state(rcon, "ENCOUNTER_ACTIVE", floor + 1, seconds=40):
                    raise RuntimeError(f"floor {floor + 1} never opened: {run_line(rcon)}")

            log = server.read_log()
            results.append(Result("at least one event room appeared in four floors (50% each; rerun if this alone fails)",
                                  rooms > 0, "no room rolled"))
            results.append(Result("no CobbleTowers exception during any of it",
                                  "	at com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("event run", False, repr(exc)))
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
