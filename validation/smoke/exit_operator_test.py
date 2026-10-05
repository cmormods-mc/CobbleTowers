#!/usr/bin/env python3
"""Proves an operator in creative mode is sent home when their own run ends, and is still left alone when exploring (P20 fix).

Found by the owner on a real server: cashing out as an operator in creative mode left them in an empty dimension. The exit sweep
exempted every operator in creative or spectator, even after their own run ended, and after the beat the run's cell was reset around them.
The exemption now lifts for a minute after the operator's own run ends, and a cell is never reset with its participants still inside.

    python validation/smoke/exit_operator_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from exit_test import dimension, near, position, quarantined, run_with_state, start_bot, wait_for, wait_online  # noqa: E402
from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)

BOT = f"TXo{int(time.time()) % 100000}"
TOWER = "cobbletowers:test"
TOWER_DIM = "cobbletowers:tower"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"


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

    results: list[Result] = []
    server = Server(server_dir, java)
    bot = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bot = start_bot(BOT, server_port(server_dir), node_modules, server_dir / "logs" / "towers-exitop-bot.log")
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon, BOT):
                raise RuntimeError("the bot never joined")
            clear_tower(rcon)
            rcon.command(f"pokegiveother {BOT} glaceon level=100")
            rcon.command(f"op {BOT}")
            rcon.command(f"gamemode creative {BOT}")

            def play(command: str) -> None:
                rcon.command(f"execute as {BOT} run cobbletowers play {command}")

            before = position(rcon, BOT)
            results.append(Result("the operator starts in the overworld, in creative",
                                  dimension(rcon, BOT) == "minecraft:overworld", dimension(rcon, BOT)))

            # --- the reported case: an operator in creative cashes out --------------------------------------------------
            play(f"tower {TOWER}")
            play("start")
            inside = wait_for(lambda: dimension(rcon, BOT) == TOWER_DIM, 40)
            results.append(Result("a started run puts the operator in the tower", inside, dimension(rcon, BOT)))
            run = ""
            for _ in range(20):
                run = run_with_state(rcon, ("ENCOUNTER_ACTIVE",))
                if run:
                    break
                time.sleep(1)
            rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
            rcon.command(f"cobbletowers runs advance {run} rewards_banked")
            at_intermission = wait_for(lambda: run in rcon.command("cobbletowers runs list") and "INTERMISSION" in rcon.command("cobbletowers runs list"), 20)
            results.append(Result("the floor is cleared to its intermission", at_intermission, rcon.command("cobbletowers runs list")[:200]))
            time.sleep(2)
            play("cashout")
            home = wait_for(lambda: dimension(rcon, BOT) == "minecraft:overworld", 25)
            results.append(Result("after cashing out the operator is sent back to the overworld", home, dimension(rcon, BOT)))
            after = position(rcon, BOT)
            results.append(Result("to where they started", near(before, after, 4.0), f"{before} vs {after}"))
            time.sleep(3)
            results.append(Result("no cell was left unfit for reuse", quarantined(rcon) == 0, f"{quarantined(rcon)} quarantined"))
            log = server.read_log()
            results.append(Result("the log records sending them out of the tower",
                                  "out of the tower back to minecraft:overworld" in log, "no 'back to minecraft:overworld' line"))

            # --- exploring on purpose is still allowed, straight after: the run's end only marks who was inside it -----------
            mode_before = rcon.command(f"data get entity {BOT} playerGameType").strip()
            moved = rcon.command(f"execute in {TOWER_DIM} run tp {BOT} 0 100 0").strip()
            # The tower dimension is a void: an explorer with nothing under them falls out of the world and respawns in the overworld, which
            # is not what this checks. A slab of stone under the arrival point lets the sweep be the only thing that could move them.
            rcon.command(f"execute in {TOWER_DIM} run fill -2 99 -2 2 99 2 minecraft:stone")
            entered = wait_for(lambda: dimension(rcon, BOT) == TOWER_DIM, 5)
            time.sleep(10)
            results.append(Result("an operator in creative with no run who walks into the tower dimension is left alone",
                                  entered and dimension(rcon, BOT) == TOWER_DIM,
                                  f"entered={entered} now={dimension(rcon, BOT)} game type before: {mode_before}; tp said: {moved}"))
            rcon.command(f"execute in minecraft:overworld run tp {BOT} 0 100 0")

            results.append(Result("no CobbleTowers exception during any of it",
                                  "\tat com.cobbletowers" not in server.read_log(), "a CobbleTowers stack frame is in the log"))
            rcon.command(f"deop {BOT}")
    except Exception as exc:  # noqa: BLE001
        results.append(Result("exit operator run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
