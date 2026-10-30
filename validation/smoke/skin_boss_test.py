#!/usr/bin/env python3
"""The Halloween tower's floor 5 boss comes out wearing its skin (the aspect from its CobbleRaids definition).

Floors 1-4 are cleared by operator command (as milestone_floor_test does); floor 5 is the real boss start. Checks:
  * the boss that starts is the milestone's definition (cobbleraids:halloween_mimikyu);
  * the boss entity in the cell carries the aspect snobble_halloween_mimikyu;
  * no CobbleTowers exception.

Needs the Halloween tower open outside its window: SMOKE_JAVA_OPTS gets -Dcobbletowers.ignoreWindows=true.

    python validation/smoke/skin_boss_test.py --server-dir <rig> --java <jdk21 java> [--jar <dev build>]
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
from floor_encounter_test import FIRST_MOVE, give_party, start_battle_bot, tell_bot, wait_for  # noqa: E402

BOT = f"TSk{int(time.time()) % 100000}"
TOWER = "cobbletowers:halloween"
WANT_DEFINITION = "halloween_mimikyu"
WANT_ASPECT = "snobble_halloween_mimikyu"
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
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--node-modules", type=Path, default=None)
    args = parser.parse_args()

    if "ignoreWindows" not in os.environ.get("SMOKE_JAVA_OPTS", ""):
        os.environ["SMOKE_JAVA_OPTS"] = (os.environ.get("SMOKE_JAVA_OPTS", "") + " -Dcobbletowers.ignoreWindows=true").strip()

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
        rig = server_dir.parent
        bot = start_battle_bot(rig, BOT, FIRST_MOVE)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for _ in range(90):
                if BOT in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("the bot never joined")
            clear_tower(rcon)
            give_party(rcon, BOT)

            def play(command: str) -> None:
                rcon.command(f"execute as {BOT} run cobbletowers play {command}")

            play(f"tower {TOWER}")
            play("confirm")
            play("start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", 1, seconds=40):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)

            for floor in range(1, 5):
                rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
                rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                if not wait_state(rcon, "INTERMISSION", floor, seconds=20):
                    raise RuntimeError(f"floor {floor} never reached its intermission: {run_line(rcon)}")
                # Hollow's Eve votes its draft (route stop, then maybe a room) instead of the ready flow
                for _ in range(2):
                    rcon.command(f"execute as {BOT} run cobbletowers runs draft vote 1")
                    time.sleep(1.0)
                rcon.command(f"cobbletowers runs advance {run} intermission_complete")
                rcon.command(f"cobbletowers runs advance {run} next_floor_confirmed")
                if not wait_state(rcon, "FLOOR_READY", floor + 1, seconds=40):
                    raise RuntimeError(f"floor {floor + 1} never got ready: {run_line(rcon)}")
                if floor + 1 == 5:
                    tell_bot(rig, BOT, "FIGHT")
                    tell_bot(rig, BOT, f"MOVE {FIRST_MOVE}")
                    wait_for(rig / "bot" / f"{BOT}.log", r"AUTOFIGHT on", seconds=30)
                rcon.command(f"cobbletowers runs encounter {run}")
                if not wait_state(rcon, "ENCOUNTER_ACTIVE", floor + 1, seconds=40):
                    raise RuntimeError(f"floor {floor + 1} never opened: {run_line(rcon)}")

            boss_line = ""
            deadline = time.time() + 300
            while time.time() < deadline and not boss_line:
                for line in server.read_log().splitlines():
                    if re.search(r"Floor 5 boss \S+ started at level", line):
                        boss_line = line
                        break
                time.sleep(1)
            print(f"  boss line: {boss_line.strip()[:160] or '<none>'}")
            results.append(Result("the floor 5 boss started", bool(boss_line), "no 'Floor 5 boss ... started' line"))
            results.append(Result(f"it is {WANT_DEFINITION}", WANT_DEFINITION in boss_line, boss_line.strip()[:160]))

            nbt = rcon.command('data get entity @e[type=cobblemon:pokemon,nbt={Pokemon:{Species:"cobblemon:mimikyu"}},limit=1] Pokemon')
            print(f"  boss entity: {nbt[:1500]}")
            results.append(Result(f"the boss entity carries {WANT_ASPECT}", WANT_ASPECT in nbt, nbt[:300]))

            log = server.read_log()
            results.append(Result("no CobbleTowers exception during any of it",
                                  "\tat com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("skin boss run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(r.name) for r in results)
    failed = sum(1 for r in results if not r.passed)
    for r in results:
        print(f"  [{'PASS' if r.passed else 'FAIL'}] {r.name:<{width}}  {r.detail if not r.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
