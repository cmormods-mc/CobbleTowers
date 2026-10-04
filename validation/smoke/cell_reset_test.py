#!/usr/bin/env python3
"""A released cell must be completely empty, whatever was built in it (P27).

The reset used to sweep a 64x64 box 17 blocks tall, so the upper floors of a taller build were left standing in the
cell and showed inside the next run's tower. This builds the Test Tower (64 tall), ends the run, and checks a block near
its top -- far above the old sweep -- is gone; then builds the Battle Tower in the same cell.

    python validation/smoke/cell_reset_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    Result, Server, clear_tower, install_jar, read_password, reset_tower_world, run_id_from, wait_online,
)
from floor_encounter_test import FIRST_MOVE, give_party, start_battle_bot  # noqa: E402

BOT = f"CR{int(time.time()) % 100000}"
# poke_tower.mcfunction: `setblock ~0 ~58 ~12 minecraft:blackstone` -- 58 blocks up, where a 17-block sweep never reaches.
PROBE = (0, 58, 12, "minecraft:blackstone")


def probe(rcon: Rcon, origin: tuple[int, int, int]) -> bool:
    x, y, z = origin[0] + PROBE[0], origin[1] + PROBE[1], origin[2] + PROBE[2]
    return "passed" in rcon.command(f"execute in cobbletowers:tower if block {x} {y} {z} {PROBE[3]}")


def allocate(rcon: Rcon, tower: str) -> str:
    run = run_id_from(rcon.command(f"cobbletowers runs create {tower} {BOT}"))
    rcon.command(f"cobbletowers runs advance {run} party_submitted")
    rcon.command(f"cobbletowers runs advance {run} party_validated")
    rcon.command(f"cobbletowers runs allocate {run}")
    return run


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
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
    bot: subprocess.Popen | None = None
    try:
        server.start()
        server.wait_until_ready()
        bot = start_battle_bot(rig, BOT, FIRST_MOVE)
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon, seconds=90) and BOT not in rcon.command("list"):
                raise RuntimeError(f"{BOT} never joined")
            clear_tower(rcon)
            give_party(rcon, BOT)

            run = allocate(rcon, "cobbletowers:test")
            time.sleep(2)
            log = server.read_log()
            built = re.search(r"Cell (\d+) prepared with cobbletowers:poke_tower at \S*\{x=(-?\d+), y=(\d+), z=(-?\d+)\}", log)
            results.append(Result("the Test Tower was built", bool(built), "no 'prepared with poke_tower' line"))
            if not built:
                raise RuntimeError("no build to test against")
            cell = built[1]
            origin = (int(built[2]), int(built[3]), int(built[4]))
            results.append(Result("a block 58 up (above the old 17-block sweep) is in the world", probe(rcon, origin),
                                  "probe block missing"))

            rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            deadline = time.time() + 40
            while time.time() < deadline and f"Cell {cell} released" not in server.read_log():
                time.sleep(1)
            results.append(Result("the cell was released", f"Cell {cell} released" in server.read_log(), "no release line"))
            results.append(Result("and the block 58 up is gone (the whole building was cleared)", not probe(rcon, origin),
                                  "the old build is still standing above the old sweep"))

            run2 = allocate(rcon, "cobbletowers:neutral")
            time.sleep(3)
            log = server.read_log()
            results.append(Result("the Battle Tower then builds in the same cell and is playable",
                                  f"Cell {cell} prepared with cobbletowers:battle_tower_neutral" in log and "not playable" not in log,
                                  "no battle_tower prepare line, or a playable error"))
            bad = [l for l in log.splitlines() if "com.cobbletowers" in l and "ERROR" in l]
            results.append(Result("no CobbleTowers error", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("cell reset run", False, repr(exc)))
    finally:
        if bot:
            bot.kill()
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
