#!/usr/bin/env python3
"""Proves Echoes (P35): a regional milestone offers an Echo Duel room, the duel is a real exhibition battle that ends and is recorded, and a player can see and opt out of their own Echo.

Floors are cleared by operator command; the drafts, picks and ready-ups are the real player path as the bot.

    python validation/smoke/echo_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TEc{int(time.time()) % 100000}"
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
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-echo-bot.log", "w", encoding="utf-8", errors="replace")
        bot = subprocess.Popen(["node", str(HERE / "battlebot.js"), BOT, str(server_port(server_dir))],
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

            def pending_log(text: str) -> bool:
                return text in server.read_log()

            rcon.command("cobbletowers echoes clear")
            rcon.command('cobbletowers echoes add Ghost cobbletowers:tideforge '
                         'garchomp level=100 nature=jolly ability=roughskin moves=earthquake,outrage,swordsdance,stoneedge '
                         'held_item=cobblemon:rocky_helmet')
            # The same Echo on Neutral, which must never use it.
            rcon.command('cobbletowers echoes add Ghost cobbletowers:neutral garchomp level=100 nature=jolly moves=earthquake')

            play(f"tower {TOWER}")
            play("confirm")
            play("start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", 1, seconds=40):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)

            for floor in range(1, 6):
                rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
                rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                if not wait_state(rcon, "INTERMISSION", floor, seconds=20):
                    raise RuntimeError(f"floor {floor} never reached its intermission: {run_line(rcon)}")
                time.sleep(2)
                if floor < 5:
                    for _ in range(3):   # ordinary draft, then perhaps an event room
                        play("pick 1")
                    play("ready")
                    if not wait_state(rcon, "ENCOUNTER_ACTIVE", floor + 1, seconds=40):
                        raise RuntimeError(f"floor {floor + 1} never opened: {run_line(rcon)}")
                    continue
                play("pick 1")   # the ordinary draft
                play("pick 1")   # the relic
                time.sleep(2)
                results.append(Result("a regional milestone opens the Echo Duel room",
                                      pending_log("opened an EVENT room (ECHO_DUEL) at floor 5"), "no Echo Duel room line"))
                results.append(Result("an ordinary floor never offered the duel",
                                      server.read_log().count("(ECHO_DUEL)") == 1, f"{server.read_log().count('(ECHO_DUEL)')} duel rooms"))
                # The floors were cleared by operator command, so the floor's own battle is still open; a real clear ends it.
                rcon.command(f"cobbletowers echoes endbattle {BOT}")
                time.sleep(2)
                play("pick 1")   # fight
                time.sleep(3)
                log = server.read_log()
                results.append(Result("choosing to fight starts the duel", "began an Echo duel at floor 5 for 1 player(s)" in log,
                                      "no 'began an Echo duel' line"))
                results.append(Result("the duel is a real battle against the Echo's Pokemon",
                                      re.search(r"battle \S+ started \(Echo duel\): \S+ vs garchomp at level", log) is not None, "no battle against garchomp"))
                # Resolved by the bot fighting it (battlebot): the run is not held shut for ever.
                deadline = time.time() + 150
                while time.time() < deadline and "Echo duel of run" not in server.read_log():
                    time.sleep(2)
                log = server.read_log()
                results.append(Result("the duel ends and is recorded", "Echo duel of run" in log and "ended:" in log,
                                      "the duel never ended"))
                play("ready")
                results.append(Result("with the duel over the team can ready up and move on",
                                      wait_state(rcon, "ENCOUNTER_ACTIVE", 6, seconds=60), run_line(rcon)))
                listed = rcon.command("cobbletowers echoes list")
                results.append(Result("the Echo counts the challenger who met it", "faced 1" in listed, listed.strip()[:200]))
                break

            log = server.read_log()
            results.append(Result("a run never met the Echo outside the duel (no floor fill)",
                                  "fills opponent" not in log, "an Echo filled a floor slot"))
            results.append(Result("no CobbleTowers exception during any of it",
                                  "	at com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))

            # Opt-out and the player's own view.
            mine = rcon.command(f"cobbletowers echoes record {BOT} cobbletowers:rootvale")
            results.append(Result("an Echo of the player's own team can be recorded", "Recorded an Echo of" in mine, mine.strip()[:160]))
            seen = say("echo")
            results.append(Result("the player can see their Echo", "rootvale" in seen, seen.strip()[:200]))
            off = say("echo off")
            results.append(Result("opting out removes it at once", "1 Echo(es) of yours removed" in off, off.strip()[:200]))
            after = say("echo")
            results.append(Result("and they are told they are opted out", "opted out" in after, after.strip()[:200]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("echo run", False, repr(exc)))
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
