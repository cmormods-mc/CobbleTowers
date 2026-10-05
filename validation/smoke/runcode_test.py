#!/usr/bin/env python3
"""Proves run codes (P35): a run prints a code, and entering it starts the same run (same seed, same first opponent).

Floors are cleared by operator command; the drafts, picks and ready-ups are the real player path as the bot.

    python validation/smoke/runcode_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TRc{int(time.time()) % 100000}"
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
        handle = open(server_dir / "logs" / "towers-code-bot.log", "w", encoding="utf-8", errors="replace")
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

            def start_run() -> tuple[str, str]:
                """Starts the lobby's run; returns (its id, the first battle line it logged)."""
                mark = len(server.read_log())
                play("start")
                if not wait_state(rcon, "ENCOUNTER_ACTIVE", 1, seconds=40):
                    raise RuntimeError("floor 1 never opened: " + run_line(rcon))
                run_id = re.search(UUID_RE, run_line(rcon)).group(1)
                time.sleep(3)
                new = server.read_log()[mark:]
                battle = re.search(r"battle \S+ started: \S+ vs (\S+) at level (\d+)", new)
                return run_id, battle.group(0).split(": ", 1)[1] if battle else ""

            def code_of(run_id: str) -> str:
                found = re.search(rf"Run {run_id} started with code (\S+)", server.read_log())
                return found.group(1) if found else ""

            play(f"tower {TOWER}")
            first, first_battle = start_run()
            code = code_of(first)
            results.append(Result("a started run logs a run code", code.startswith("CT1-"), code or "no code line"))
            results.append(Result("the player can ask for it again", code in say("code"), say("code")))
            rcon.command(f"cobbletowers runs advance {first} abandon_requested")
            time.sleep(8)

            refused = say("code CT1-bogus-std-0-1-0")
            results.append(Result("a damaged code is refused", "not a valid run code" in refused, refused.strip()[:160]))

            accepted = say(f"code {code}")
            results.append(Result("a valid code is accepted", "Run code accepted" in accepted, accepted.strip()[:200]))
            second, second_battle = start_run()
            results.append(Result("the second run carries the same code, so the same seed",
                                  code_of(second) == code, f"{code} vs {code_of(second)}"))
            results.append(Result("and its first opponent is the same species and level as the first run's",
                                  bool(first_battle) and first_battle == second_battle, f"{first_battle!r} vs {second_battle!r}"))

            log = server.read_log()
            results.append(Result("no CobbleTowers exception during any of it",
                                  "	at com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("run code run", False, repr(exc)))
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
