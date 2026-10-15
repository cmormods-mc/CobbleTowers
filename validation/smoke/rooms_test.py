#!/usr/bin/env python3
"""Proves the four P45 intermission rooms on a live run (dev jar; Polymer and WebGUI in mods/).

Each floor's room is forced with `runs draft room <ROOM>` (dev seam) so the test does not wait on the seed: Forge on floor 1 (every player pays and
gets a battle relic), Library on 2 (a free scouting relic), Mystic Pool on 3 (swaps a held relic) and Vault on 4 (a purse or an ambush). The
choices, ready-ups and floor changes are the real player path as the bot; floors are cleared by operator event.

    python validation/smoke/rooms_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TRm{int(time.time()) % 100000}"
TOWER = "cobbletowers:neutral"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
ROOMS = [("FORGE", r"pays (\d+) CobbleDollars; the smith hands over"), ("LIBRARY", r"You study late and take"),
         ("POOL", r"The pool takes .* and gives"), ("VAULT", r"The vault opens: \d+ CobbleDollars each|An ambush! The vault is a trap")]


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


def balance(rcon: Rcon) -> int:
    match = re.search(r"CobbleDollars: (\d+)", rcon.command(f"execute as {BOT} run cobbletowers runs vendor"))
    return int(match.group(1)) if match else -1


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
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-rooms-bot.log", "w", encoding="utf-8", errors="replace")
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
            rcon.command(f"cobbletowers runs vendor credit {BOT} 5000")

            def play(command: str) -> None:
                rcon.command(f"execute as {BOT} run cobbletowers play {command}")

            play(f"tower {TOWER}")
            play("confirm")
            play("start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", 1, seconds=40):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)

            for floor, (room, pattern) in enumerate(ROOMS, start=1):
                rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
                rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                if not wait_state(rcon, "INTERMISSION", floor, seconds=20):
                    raise RuntimeError(f"floor {floor} never reached its intermission: {run_line(rcon)}")
                time.sleep(1)
                before_balance = balance(rcon)
                rcon.command(f"cobbletowers runs draft room {room}")
                rooms_before = server.read_log().count("opened an EVENT room")
                play("pick 1")   # the ordinary draft settles and the forced room opens behind it
                time.sleep(2)
                log = server.read_log()
                opened = re.findall(rf"opened an EVENT room \({room}\) at floor {floor}", log)
                results.append(Result(f"floor {floor}: the {room} room opens", log.count("opened an EVENT room") > rooms_before and bool(opened),
                                      f"no '{room}' room line: " + "; ".join(l for l in log.splitlines() if "EVENT room" in l)[-300:]))
                choices_before = log.count("event choice")
                play("pick 1")
                time.sleep(2)
                log = server.read_log()
                chosen = [l for l in log.splitlines() if "event choice" in l][-1:] or [""]
                results.append(Result(f"floor {floor}: choosing it does what the {room} says",
                                      log.count("event choice") == choices_before + 1 and re.search(pattern, chosen[0]) is not None, chosen[0][-240:]))
                after_balance = balance(rcon)
                if room == "FORGE":
                    paid = re.search(pattern, chosen[0])
                    cost = int(paid.group(1)) if paid else -1
                    results.append(Result("the Forge took its price from the wallet", cost > 0 and before_balance - after_balance == cost,
                                          f"{before_balance} -> {after_balance}, price {cost}"))
                if room == "VAULT" and "The vault opens" in chosen[0]:
                    results.append(Result("the Vault purse reached the wallet", after_balance > before_balance, f"{before_balance} -> {after_balance}"))
                if floor < len(ROOMS):
                    play("ready")
                    if not wait_state(rcon, "ENCOUNTER_ACTIVE", floor + 1, seconds=40):
                        raise RuntimeError(f"floor {floor + 1} never opened: {run_line(rcon)}")

            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the run still reads back after four rooms", run in shown or "floor" in shown, shown.strip()[:200]))
            log = server.read_log()
            results.append(Result("no CobbleTowers exception during any of it", "	at com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("rooms run", False, repr(exc)))
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
