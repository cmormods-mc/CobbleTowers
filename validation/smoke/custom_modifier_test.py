#!/usr/bin/env python3
"""Proves P29's custom modifiers against a real server.

Two players run through the lobby to floor 1's intermission (cleared by operator event). Then:

  * Black Market halves what the vendor charges, read from `runs vendor` (the same price a purchase would take);
  * granting Glass Cannon and Field Hospital is accepted and the next floor still opens (the battle operations
    Glass Cannon queues ride a real floor battle's start without being refused);
  * the server logs no CobbleTowers error along the way.

The Wheel's odds and the heal are covered by unit tests; a headless bot has no party worth healing.

    python validation/smoke/custom_modifier_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
from floor_encounter_test import TOWER, give_party  # noqa: E402
from intermission_test import play, run_line, wait_state, clear_floor, UUID_RE  # noqa: E402

STAMP = int(time.time()) % 100000
A = f"TCa{STAMP}"
B = f"TCb{STAMP}"


def full_heal_price(rcon: Rcon, player: str) -> int:
    reply = rcon.command(f"execute as {player} run cobbletowers runs vendor")
    found = re.search(r"Full Heal -- (\d+) CobbleDollars", reply)
    return int(found.group(1)) if found else -1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    rig = server_dir.parent
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    node_modules = rig / "bot" / "node_modules"
    bots: list[subprocess.Popen] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        for name in (A, B):
            handle = open(server_dir / "logs" / f"towers-custom-{name}.log", "w", encoding="utf-8", errors="replace")
            bots.append(subprocess.Popen(["node", str(HERE / "joinbot.js"), name, str(server_port(server_dir))],
                                         stdout=handle, stderr=subprocess.STDOUT, env=env))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (A, B):
                for _ in range(90):
                    if name in rcon.command("list"):
                        break
                    time.sleep(1)
                else:
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            for name in (A, B):
                give_party(rcon, name)
            play(rcon, A, f"tower {TOWER}")
            play(rcon, A, f"invite {B}")
            play(rcon, B, f"accept {A}")
            play(rcon, A, "start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=40, floor=1):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)
            clear_floor(rcon, run)
            if not wait_state(rcon, "INTERMISSION", seconds=20, floor=1):
                raise RuntimeError("never reached the intermission: " + run_line(rcon))

            before = full_heal_price(rcon, A)
            results.append(Result("the vendor lists a Full Heal price", before > 0, str(before)))
            granted = rcon.command(f"cobbletowers runs grant {run} cobbletowers:black_market")
            results.append(Result("Black Market can be granted", "Granted" in granted, granted))
            after = full_heal_price(rcon, A)
            expected = max(1, (before + 1) // 2)
            results.append(Result("Black Market halves the vendor's price", after == expected,
                                  f"{before} -> {after}, expected {expected}"))

            for modifier in ("glass_cannon", "field_hospital", "fortunes_wheel"):
                reply = rcon.command(f"cobbletowers runs grant {run} cobbletowers:{modifier}")
                results.append(Result(f"{modifier} can be granted", "Granted" in reply, reply))
            play(rcon, A, "pick 1")
            play(rcon, B, "pick 1")
            play(rcon, A, "ready")
            play(rcon, B, "ready")
            opened = wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=60, floor=2)
            results.append(Result("floor 2 opens with all four custom modifiers held", opened, run_line(rcon)))
            text = server.log.read_text(encoding="utf-8", errors="replace")
            errors = [l for l in text.splitlines() if "ERROR" in l and "cobbletowers" in l.lower()]
            results.append(Result("no CobbleTowers error was logged", not errors, "; ".join(errors)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("custom modifier run", False, repr(exc)))
    finally:
        for bot in bots:
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
