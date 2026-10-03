#!/usr/bin/env python3
"""Proves P20: players are sent home when they have no live place in a run, and no cell is lost doing it.

Found by `bot_stress_test.py`, which logged `Cell N quarantined: 1 player(s) still inside` after every run it
ended: nothing in the mod ever moved a player out of the tower dimension, so each finished run reset its cell
around the player and quarantined it. Two bots start a run through the lobby (P16), then:

  * one player leaves the run: they are back in the overworld at their starting place within seconds, while
    the other is still inside;
  * the run ends: the other player is told, waits out the beat, and is sent back to where they started, and
    **no cell is quarantined**;
  * a player disconnects inside the tower and logs back in after their run ended: they are moved out on
    login rather than left in a reset void.

    python validation/smoke/exit_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import math
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

STAMP = int(time.time()) % 100000
A = f"TXa{STAMP}"
B = f"TXb{STAMP}"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"


def start_bot(name: str, port: int, node_modules: Path, log: Path) -> subprocess.Popen:
    env = dict(os.environ, NODE_PATH=str(node_modules))
    handle = open(log, "w", encoding="utf-8", errors="replace")
    return subprocess.Popen(["node", str(HERE / "joinbot.js"), name, str(port)],
                            stdout=handle, stderr=subprocess.STDOUT, env=env)


def wait_online(rcon: Rcon, name: str, seconds: int = 90) -> bool:
    for _ in range(seconds):
        if name in rcon.command("list"):
            return True
        time.sleep(1)
    return False


def dimension(rcon: Rcon, name: str) -> str:
    out = rcon.command(f"data get entity {name} Dimension")
    found = re.search(r'"([a-z_:]+)"', out)
    return found.group(1) if found else out.strip()


def position(rcon: Rcon, name: str) -> tuple[float, float, float]:
    out = rcon.command(f"data get entity {name} Pos")
    numbers = re.findall(r"(-?\d+\.?\d*)d", out)
    return tuple(float(n) for n in numbers[:3]) if len(numbers) >= 3 else (math.nan,) * 3


def near(a: tuple[float, float, float], b: tuple[float, float, float], blocks: float = 3.0) -> bool:
    return all(not math.isnan(v) for v in a + b) and math.dist(a, b) <= blocks


def wait_for(predicate, seconds: int = 20) -> bool:
    for _ in range(seconds * 2):
        if predicate():
            return True
        time.sleep(0.5)
    return False


def run_with_state(rcon: Rcon, states: tuple[str, ...]) -> str:
    listing = rcon.command("cobbletowers runs list")
    for found in re.finditer(UUID_RE + r"\s+\S+\s+(\w+)\s+floor", listing):
        if found.group(2) in states:
            return found.group(1)
    return ""


def quarantined(rcon: Rcon) -> int:
    found = re.search(r"(\d+) quarantined", rcon.command("cobbletowers cells list"))
    return int(found.group(1)) if found else -1


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
    port = server_port(server_dir)
    bots: dict[str, subprocess.Popen] = {}
    password = read_password(server_dir)
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        for name in (A, B):
            bots[name] = start_bot(name, port, node_modules, server_dir / "logs" / f"towers-exit-{name}.log")

        with Rcon("127.0.0.1", 25575, password) as rcon:
            for name in (A, B):
                if not wait_online(rcon, name):
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            give_party(rcon, A)
            give_party(rcon, B)
            time.sleep(2)
            home_a, home_b = position(rcon, A), position(rcon, B)
            dim_a = dimension(rcon, A)
            results.append(Result("both players start in the overworld", dim_a == "minecraft:overworld" and dimension(rcon, B) == dim_a,
                                  f"{dim_a} {home_a} {home_b}"))

            rcon.command(f"execute as {A} run cobbletowers play tower {TOWER}")
            rcon.command(f"execute as {A} run cobbletowers play invite {B}")
            rcon.command(f"execute as {B} run cobbletowers play accept {A}")
            rcon.command(f"execute as {A} run cobbletowers play start")
            inside = wait_for(lambda: dimension(rcon, A) == "cobbletowers:tower" and dimension(rcon, B) == "cobbletowers:tower", 40)
            results.append(Result("a started run puts both players in the tower", inside, f"{dimension(rcon, A)} {dimension(rcon, B)}"))

            # --- one player leaves, the other stays -----------------------------------------------------
            rcon.command(f"execute as {A} run cobbletowers runs leave")
            out_a = wait_for(lambda: dimension(rcon, A) == "minecraft:overworld", 10)
            results.append(Result("a player who leaves is sent out within seconds", out_a, dimension(rcon, A)))
            results.append(Result("and lands where they started", near(position(rcon, A), home_a), f"{position(rcon, A)} vs {home_a}"))
            results.append(Result("the player who stayed is still in the tower", dimension(rcon, B) == "cobbletowers:tower", dimension(rcon, B)))

            # --- the run ends: the rest are sent home after the beat --------------------------------------
            run = run_with_state(rcon, ("ENCOUNTER_ACTIVE", "RECOVERY_REQUIRED", "FLOOR_READY", "PREPARING"))
            if run:
                rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            time.sleep(1)
            still_there = dimension(rcon, B) == "cobbletowers:tower"
            results.append(Result("the beat: a player is not moved the instant the run ends", still_there or not run, dimension(rcon, B)))
            out_b = wait_for(lambda: dimension(rcon, B) == "minecraft:overworld", 15)
            results.append(Result("after the beat the player is sent home", out_b, dimension(rcon, B)))
            results.append(Result("and lands where they started", near(position(rcon, B), home_b), f"{position(rcon, B)} vs {home_b}"))
            time.sleep(2)
            results.append(Result("no cell was quarantined by any of it", quarantined(rcon) == 0, rcon.command("cobbletowers cells list")[:200]))

            # --- a player disconnects inside, and logs back in after the run ended -----------------------
            clear_tower(rcon)
            rcon.command(f"execute as {B} run cobbletowers play tower {TOWER}")
            rcon.command(f"execute as {B} run cobbletowers play start")
            wait_for(lambda: dimension(rcon, B) == "cobbletowers:tower", 40)
            bots.pop(B).kill()
            for _ in range(40):
                if B not in rcon.command("list"):
                    break
                time.sleep(0.5)
            time.sleep(8)   # the run is over (the lone player dropped) and the beat has passed
            bots[B] = start_bot(B, port, node_modules, server_dir / "logs" / f"towers-exit-{B}-again.log")
            if not wait_online(rcon, B):
                raise RuntimeError("the second bot never rejoined")
            back = wait_for(lambda: dimension(rcon, B) == "minecraft:overworld", 15)
            results.append(Result("a player who logs in after their run ended is moved out of the tower", back, dimension(rcon, B)))
            results.append(Result("and lands where they started", near(position(rcon, B), home_b), f"{position(rcon, B)} vs {home_b}"))
            time.sleep(2)
            results.append(Result("still no cell quarantined", quarantined(rcon) == 0, rcon.command("cobbletowers cells list")[:200]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("exit run", False, repr(exc)))
    finally:
        for bot in bots.values():
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
