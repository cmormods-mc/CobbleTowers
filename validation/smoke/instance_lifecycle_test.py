#!/usr/bin/env python3
"""Drives a cell through its whole life against a real server: leased, dirtied, quarantined, resumed.

Three things here cannot be reached by a unit test, because each one needs a world:

  * a cell is verified against the actual entities standing in it, so leaving something behind is
    what quarantines it -- and the reason has to name what was found;
  * a quarantined cell must not be handed to the next run, which is only true if the allocator and
    the store agree across a restart;
  * a parked run resumes to the state its checkpoint recorded, and only while its cell is still
    usable.

The kill is a hard one, as in run_durability_test.py: a clean stop saves everything and would let a
build with no checkpointing at all pass.

    python validation/smoke/instance_lifecycle_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    BOT, Result, Server, clear_tower, install_jar, reset_tower_world, read_password, recovered_from, run_id_from,
    server_port, start_bot, wait_online,
)

TOWER = "cobbletowers:neutral"


def cell_from(text: str) -> int:
    match = re.search(r"cell (\d+)", text)
    if not match:
        raise RuntimeError("no cell in: " + text.strip()[:200])
    return int(match.group(1))


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

    # A previous run's parked runs and quarantines would make cell numbers unpredictable, and this
    # test is about which cell comes next.
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bot = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bot = start_bot(server_port(server_dir), node_modules, server_dir / "logs" / "towers-instance-bot.log")

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon):
                raise RuntimeError("the bot never joined; run commands need a player selector")
            clear_tower(rcon)

            listed = rcon.command("cobbletowers cells list")
            results.append(Result("the tower dimension is loaded", "dimension loaded" in listed,
                                  listed.strip()[:200]))

            # A: leased, checkpointed, and left for the kill to find.
            run_a = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            rcon.command(f"cobbletowers runs advance {run_a} party_submitted")
            rcon.command(f"cobbletowers runs advance {run_a} party_validated")
            allocated_a = rcon.command(f"cobbletowers runs allocate {run_a}")
            cell_a = cell_from(allocated_a)
            results.append(Result("allocating hands the run a cell", "PREPARING" in allocated_a,
                                  allocated_a.strip()[:200]))
            shown = rcon.command(f"cobbletowers runs show {run_a}")
            results.append(Result("the run records which cell it holds", f"cell {cell_a}" in shown,
                                  shown.strip()[:200]))
            rcon.command(f"cobbletowers runs advance {run_a} preparation_complete")

            # B: leased, dirtied, then ended, so releasing it has to quarantine the cell.
            run_b = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            rcon.command(f"cobbletowers runs advance {run_b} party_submitted")
            rcon.command(f"cobbletowers runs advance {run_b} party_validated")
            cell_b = cell_from(rcon.command(f"cobbletowers runs allocate {run_b}"))
            results.append(Result("two runs never get the same cell", cell_a != cell_b,
                                  f"both were given cell {cell_a}"))

            shown_b = rcon.command(f"cobbletowers runs show {run_b}")
            centre = re.search(r"centre (-?\d+) (-?\d+) (-?\d+)",
                               rcon.command(f"cobbletowers cells show {cell_b}"))
            x, y, z = (int(value) for value in centre.groups())
            # Chunk tickets are P4's, so the chunk is force-loaded by hand to put something in it.
            rcon.command(f"execute in cobbletowers:tower run forceload add {x} {z}")
            summoned = rcon.command(f"execute in cobbletowers:tower run summon minecraft:pig {x} {y} {z}")
            results.append(Result("something can be left behind in a cell", "Summoned" in summoned,
                                  summoned.strip()[:200] + " | " + shown_b.strip()[:80]))

            # A freshly summoned entity is not visible to any query until its tick ends -- vanilla's
            # own @e selector could not see it either, which is worth knowing before blaming a sweep.
            time.sleep(1)
            verified = rcon.command(f"cobbletowers cells verify {cell_b}")
            results.append(Result("verification sees what was left in the cell", "entity" in verified,
                                  verified.strip()[:200]))

            rcon.command(f"cobbletowers runs advance {run_b} abandon_requested")
            after = rcon.command(f"cobbletowers cells show {cell_b}")
            results.append(Result("ending a run with something still in its cell quarantines it",
                                  "quarantined" in after, after.strip()[:200]))

            # C: must skip the quarantined cell.
            run_c = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            rcon.command(f"cobbletowers runs advance {run_c} party_submitted")
            rcon.command(f"cobbletowers runs advance {run_c} party_validated")
            cell_c = cell_from(rcon.command(f"cobbletowers runs allocate {run_c}"))
            results.append(Result("a quarantined cell is not handed to the next run", cell_c != cell_b,
                                  f"run C was given the quarantined cell {cell_b}"))
            # A forceload is written into the world, so it outlives this test -- and the one left
            # here went on to fail an unrelated check in floor_build_test, which asserts the tower
            # holds chunks by ticket and not by forceload. Clean up what we forced.
            rcon.command("execute in cobbletowers:tower run forceload remove all")
            print(f"  A={run_a} cell {cell_a}, B cell {cell_b} quarantined, C cell {cell_c}")

        print("Killing the server with no stop and no save")
        server.process.kill()
        server.process.wait(timeout=60)
        if bot is not None:
            bot.kill()
            bot = None

        print("Restarting")
        server.start()
        server.wait_until_ready()
        restart_log = server.read_log()
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            shown = rcon.command(f"cobbletowers runs show {run_a}")
            results.append(Result("the lease survived the kill", f"cell {cell_a}" in shown,
                                  shown.strip()[:200]))
            results.append(Result("the interrupted run was parked", "RECOVERY_REQUIRED" in shown,
                                  shown.strip()[:200]))
            results.append(Result("the quarantine survived the kill too",
                                  "quarantined" in rcon.command(f"cobbletowers cells show {cell_b}"),
                                  "cell " + str(cell_b) + " came back in service after a crash"))

            resumed = rcon.command(f"cobbletowers runs advance {run_a} recovery_completed")
            results.append(Result("a parked run resumes to the state its checkpoint recorded",
                                  "FLOOR_READY" in resumed, resumed.strip()[:200]))

            # And a run whose cell is quarantined must not resume into it. B is terminal, so C is
            # parked by hand first, then its cell taken away underneath it.
            rcon.command(f"cobbletowers runs advance {run_c} technical_failure")
            rcon.command(f"cobbletowers cells quarantine {cell_c} deliberately taken out for the test")
            refused = rcon.command(f"cobbletowers runs advance {run_c} recovery_completed")
            results.append(Result("a run whose cell is quarantined is refused, not put back into it",
                                  "CELL_UNAVAILABLE" in refused, refused.strip()[:200]))

        results.append(Result("no CobbleTowers exception during any of it",
                              "com.cobbletowers" not in restart_log.replace("com.cobbletowers.CobbleTowers", ""),
                              "see " + str(server.log)))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("instance lifecycle run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results)
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  "
              f"{result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
