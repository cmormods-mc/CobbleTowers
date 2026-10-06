#!/usr/bin/env python3
"""Proves /tower watch (P36e): a second player looks in on a live run in spectator mode, is left alone while it lasts, and is sent home -- in the
game mode they had -- when the run ends or when they choose to stop.

    python validation/smoke/watch_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import os
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

SUFFIX = int(time.time()) % 100000
RUNNER = f"TWr{SUFFIX}"
WATCHER = f"TWw{SUFFIX}"
TOWER = "cobbletowers:test"
TOWER_DIM = "cobbletowers:tower"


def game_type(rcon: Rcon, name: str) -> str:
    out = rcon.command(f"data get entity {name} playerGameType")
    return out.strip().rsplit(" ", 1)[-1]


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
    bots = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        for name, label in ((RUNNER, "runner"), (WATCHER, "watcher")):
            bots.append(start_bot(name, server_port(server_dir), node_modules, server_dir / "logs" / f"towers-watch-{label}.log"))
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (RUNNER, WATCHER):
                if not wait_online(rcon, name):
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            rcon.command(f"pokegiveother {RUNNER} glaceon level=100")
            rcon.command(f"gamemode survival {WATCHER}")

            def as_player(name: str, command: str) -> None:
                rcon.command(f"execute as {name} run cobbletowers play {command}")

            # --- nothing to watch yet --------------------------------------------------------------------------------------
            as_player(WATCHER, f"watch {RUNNER}")
            time.sleep(1)
            results.append(Result("watching someone who is not in a run does nothing",
                                  dimension(rcon, WATCHER) == "minecraft:overworld", dimension(rcon, WATCHER)))

            # --- a live run ------------------------------------------------------------------------------------------------
            as_player(RUNNER, f"tower {TOWER}")
            as_player(RUNNER, "confirm")
            as_player(RUNNER, "start")
            wait_for(lambda: dimension(rcon, RUNNER) == TOWER_DIM, 40)
            run = ""
            for _ in range(20):
                run = run_with_state(rcon, ("ENCOUNTER_ACTIVE",))
                if run:
                    break
                time.sleep(1)
            results.append(Result("the runner is in a live run", bool(run), rcon.command("cobbletowers runs list")[:200]))

            before = position(rcon, WATCHER)
            as_player(WATCHER, f"watch {RUNNER}")
            inside = wait_for(lambda: dimension(rcon, WATCHER) == TOWER_DIM, 10)
            results.append(Result("a watcher is taken into the tower", inside, dimension(rcon, WATCHER)))
            results.append(Result("in spectator mode", game_type(rcon, WATCHER) == "3", game_type(rcon, WATCHER)))
            results.append(Result("next to the player they asked for",
                                  near(position(rcon, RUNNER), position(rcon, WATCHER), 8.0),
                                  f"{position(rcon, RUNNER)} vs {position(rcon, WATCHER)}"))
            results.append(Result("the log records the watch", "is watching" in server.read_log(), "no 'is watching' line"))

            time.sleep(12)
            results.append(Result("the exit sweep leaves a watcher alone while the run is live",
                                  dimension(rcon, WATCHER) == TOWER_DIM, dimension(rcon, WATCHER)))

            # --- stopping on purpose ---------------------------------------------------------------------------------------
            as_player(WATCHER, "unwatch")
            home = wait_for(lambda: dimension(rcon, WATCHER) == "minecraft:overworld", 15)
            results.append(Result("/tower unwatch sends the watcher home", home, dimension(rcon, WATCHER)))
            results.append(Result("in the mode they had", game_type(rcon, WATCHER) == "0", game_type(rcon, WATCHER)))
            results.append(Result("to where they started", near(before, position(rcon, WATCHER), 4.0),
                                  f"{before} vs {position(rcon, WATCHER)}"))

            # --- the run ending under a watcher ------------------------------------------------------------------------------
            as_player(WATCHER, f"watch {RUNNER}")
            wait_for(lambda: dimension(rcon, WATCHER) == TOWER_DIM, 10)
            rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
            rcon.command(f"cobbletowers runs advance {run} rewards_banked")
            wait_for(lambda: "INTERMISSION" in rcon.command("cobbletowers runs list"), 20)
            time.sleep(2)
            as_player(RUNNER, "cashout")
            sent = wait_for(lambda: dimension(rcon, WATCHER) == "minecraft:overworld", 30)
            results.append(Result("when the run ends the watcher is sent home too", sent, dimension(rcon, WATCHER)))
            results.append(Result("back in survival", game_type(rcon, WATCHER) == "0", game_type(rcon, WATCHER)))
            results.append(Result("and so is the runner", wait_for(lambda: dimension(rcon, RUNNER) == "minecraft:overworld", 30),
                                  dimension(rcon, RUNNER)))
            time.sleep(3)
            results.append(Result("no cell was left unfit for reuse", quarantined(rcon) == 0, f"{quarantined(rcon)} quarantined"))
            results.append(Result("no CobbleTowers exception during any of it",
                                  "\tat com.cobbletowers" not in server.read_log(), "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("watch run", False, repr(exc)))
    finally:
        for bot in bots:
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
