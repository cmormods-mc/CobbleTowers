#!/usr/bin/env python3
"""Proves P17's cell rebuild: a run that reaches floor 5 is standing in a `boss_arena`, not floor 4's arena.

Before P17 a run's floor was pasted into its cell once, at allocation, so the F5 milestone (a different
structure from the ordinary floors) would have been fought in the wrong room. Nothing ever played past
floor 1, so nobody saw it.

Getting to floor 5 by fighting would take far too long for a smoke test, so the floors are cleared by
operator command (`runs advance ... encounter_resolved_cleared`, `rewards_banked`) while everything else
-- the intermission, the draft, ready, the countdown, the next-floor open and the rebuild itself -- is the
real player path, driven as the bot. The observable is the line `CellPreparer` logs when it pastes a
structure: `prepared with cobbletowers:boss_arena` appears only if the cell was rebuilt (the warm pool
only builds floor 1's structure, so it cannot be the source).

    python validation/smoke/milestone_floor_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TMf{int(time.time()) % 100000}"
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
        handle = open(server_dir / "logs" / "towers-milestone-bot.log", "w", encoding="utf-8", errors="replace")
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

            before = server.read_log().count("prepared with cobbletowers:boss_arena")
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
                play("pick 1")   # a draft may or may not be open; an unneeded pick is refused harmlessly
                play("ready")
                if not wait_state(rcon, "ENCOUNTER_ACTIVE", floor + 1, seconds=40):
                    raise RuntimeError(f"floor {floor + 1} never opened: {run_line(rcon)}")
                results.append(Result(f"floor {floor + 1} opens on its own after ready", True, ""))

            log = server.read_log()
            rebuilt = log.count("prepared with cobbletowers:boss_arena") - before
            results.append(Result("the run's cell was rebuilt as a boss arena for the floor 5 milestone",
                                  rebuilt >= 1, f"{rebuilt} boss_arena preparation(s) in the log"))
            results.append(Result("the floor 5 boss floor then actually began a battle or boss encounter",
                                  "Floor 5 of run" in log, "no 'Floor 5 of run' line"))
            results.append(Result("no CobbleTowers exception during any of it",
                                  "	at com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("milestone floor run", False, repr(exc)))
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
