#!/usr/bin/env python3
"""Nothing may drop on the ground in the tower dimension (P27); the overworld is unaffected.

Summons an unowned item and an experience orb in the tower dimension (what a defeated opponent's loot looks like to the
server) and in the overworld as a control, and counts what is left.

    python validation/smoke/drop_guard_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import Result, Server, install_jar, read_password, reset_tower_world  # noqa: E402


def count(rcon: Rcon, dimension: str, selector: str) -> int:
    # A bare @e looks in EVERY dimension, so a count would mix the two; a position and distance confine it to the
    # dimension named by `execute in`.
    selector = selector.replace("]", ",x=300,y=100,z=300,distance=..8]")
    out = rcon.command(f"execute in {dimension} if entity {selector}")
    return 0 if "Test failed" in out else int("".join(c for c in out if c.isdigit()) or 0)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()
    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    try:
        server.start()
        server.wait_until_ready()
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            tower, overworld = "cobbletowers:tower", "minecraft:overworld"
            rcon.command(f"execute in {tower} run forceload add 300 300")
            rcon.command(f"execute in {overworld} run forceload add 300 300")
            time.sleep(2)
            for dimension in (tower, overworld):
                rcon.command(f'execute in {dimension} run summon item 300 100 300 {{Item:{{id:"minecraft:diamond",count:1}},NoGravity:1b}}')
                rcon.command(f"execute in {dimension} run summon experience_orb 300 100 300 {{NoGravity:1b}}")
            time.sleep(2)
            tower_items = count(rcon, tower, "@e[type=item]")
            tower_orbs = count(rcon, tower, "@e[type=experience_orb]")
            world_items = count(rcon, overworld, "@e[type=item]")
            world_orbs = count(rcon, overworld, "@e[type=experience_orb]")
            results.append(Result("an unowned item in the tower dimension is removed", tower_items == 0, f"{tower_items} left"))
            results.append(Result("an experience orb in the tower dimension is removed", tower_orbs == 0, f"{tower_orbs} left"))
            results.append(Result("the same item in the overworld is untouched (control)", world_items >= 1, f"{world_items} found"))
            results.append(Result("the same orb in the overworld is untouched (control)", world_orbs >= 1, f"{world_orbs} found"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("drop guard run", False, repr(exc)))
    finally:
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
