#!/usr/bin/env python3
"""Builds a floor in a real world and takes it apart again.

What cannot be reached without a server, and so is checked here:

  * the converted arena actually places -- the conversion from WorldEdit's format is checked
    block-for-block offline, but "Minecraft accepts this file" is a different claim;
  * the blocks land where the anchors say they do, so a player put at the entry has something under
    their feet and room above their head;
  * the cell's chunks are held by a ticket and NOT by a forceload, which is the difference between
    "loaded while a run needs it" and "loaded forever, written into the world" (TDS #27);
  * ending a run clears the arena and lets the chunks go.

It also prints the numbers the plan promised rather than estimating them: how long a paste takes
cold, how long a reset takes, and how many chunks the tower holds per cell.

    python validation/smoke/floor_build_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import gzip
import os
import re
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "validation"))

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    BOT, Result, Server, install_jar, read_password, run_id_from, server_port, start_bot, wait_online,
)
from schem_to_structure import Reader  # noqa: E402

TOWER = "cobbletowers:neutral"
ARENA = ROOT / "src" / "main" / "resources" / "data" / "cobbletowers" / "structure" / "arena_floor.nbt"


def arena_samples(count: int = 6):
    """A few (offset, block) pairs from the committed structure, spread through it."""
    data = gzip.decompress(ARENA.read_bytes())
    _, root = Reader(data).root()
    palette = [entry["Name"] for entry in root["palette"]]
    blocks = root["blocks"]
    size = tuple(root["size"])
    step = max(len(blocks) // (count + 1), 1)
    picked = [blocks[i * step] for i in range(1, count + 1)]
    return size, [(tuple(block["pos"]), palette[block["state"]]) for block in picked]


def number(text: str, pattern: str, default: int = -1) -> int:
    match = re.search(pattern, text)
    return int(match.group(1)) if match else default


class Probe:
    """Asks the server a yes/no question and gets an answer back.

    `execute ... run say ok` looks like it should work and does not: say produces no command feedback,
    so RCON returns an empty string whether the condition held or not -- every probe reads as a
    failure. Storing the result in a scoreboard and reading that back is the version that answers.
    """

    OBJECTIVE = "ct_probe"
    HOLDER = "probe"

    def __init__(self, rcon):
        self.rcon = rcon
        rcon.command(f"scoreboard objectives add {self.OBJECTIVE} dummy")

    def holds(self, condition: str) -> bool:
        self.rcon.command(f"execute store success score {self.HOLDER} {self.OBJECTIVE} run {condition}")
        answer = self.rcon.command(f"scoreboard players get {self.HOLDER} {self.OBJECTIVE}")
        return " 1 " in f" {answer.strip()} " or answer.strip().endswith("has 1 [" + self.OBJECTIVE + "]")

    def close(self) -> None:
        self.rcon.command(f"scoreboard objectives remove {self.OBJECTIVE}")


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
    for stale in ["cobbletowers_runs.dat", "cobbletowers_cells.dat"]:
        (server_dir / "world" / "data" / stale).unlink(missing_ok=True)

    size, samples = arena_samples()
    results: list[Result] = []
    server = Server(server_dir, java)
    bot = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bot = start_bot(server_port(server_dir), node_modules, server_dir / "logs" / "towers-floor-bot.log")

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon):
                raise RuntimeError("the bot never joined; run commands need a player selector")

            # Forceloads are written into the world and survive a restart -- which is the very
            # property this test asserts the tower does NOT rely on. An earlier session's probe left
            # one behind, so clear them before asking the question.
            rcon.command("execute in cobbletowers:tower run forceload remove all")
            probe = Probe(rcon)

            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            allocated = rcon.command(f"cobbletowers runs allocate {run}")
            cell = number(allocated, r"cell (\d+)")
            results.append(Result("a floor is built when the instance is allocated",
                                  "PREPARING" in allocated and cell >= 0, allocated.strip()[:200]))

            shown = rcon.command(f"cobbletowers cells show {cell}")
            centre = re.search(r"centre (-?\d+) (-?\d+) (-?\d+)", shown)
            cx, cy, cz = (int(v) for v in centre.groups())
            origin = (cx - size[0] // 2, cy, cz - size[2] // 2)
            results.append(Result("the cell reports its chunks are held", "chunks held" in shown,
                                  shown.strip()[:220]))

            # The arena is really there, at the offsets the structure says.
            wrong = []
            for (ox, oy, oz), block in samples:
                where = (origin[0] + ox, origin[1] + oy, origin[2] + oz)
                if not probe.holds(f"execute in cobbletowers:tower "
                                   f"if block {where[0]} {where[1]} {where[2]} {block}"):
                    wrong.append(f"{where} is not {block}")
            results.append(Result(f"the converted arena placed correctly ({len(samples)} samples)",
                                  not wrong, "; ".join(wrong)[:300]))

            # Held by a ticket, not written into the world as a forceload.
            forced = rcon.command("execute in cobbletowers:tower run forceload query")
            results.append(Result("the chunks are held by a ticket, not a forceload",
                                  "No force loaded chunks" in forced, forced.strip()[:200]))

            # The entry anchor is somewhere a player can be.
            entry_below = (origin[0] + 25, origin[1] + 1, origin[2] + 6)
            solid = probe.holds(f"execute in cobbletowers:tower unless block "
                                f"{entry_below[0]} {entry_below[1]} {entry_below[2]} minecraft:air")
            clear = probe.holds(f"execute in cobbletowers:tower if block "
                                f"{entry_below[0]} {entry_below[1] + 2} {entry_below[2]} minecraft:air")
            results.append(Result("the entry anchor has ground underfoot and room above", solid and clear,
                                  f"ground={solid} headroom={clear}"))

            listed = rcon.command("cobbletowers cells list")
            held = number(listed, r"(\d+) cell\(s\) held loaded")
            per_cell = number(listed, r"(\d+) chunk\(s\) each")
            total_chunks = number(listed, r"(\d+) tower chunk\(s\) in all")
            warm = number(listed, r"(\d+) warm")
            print(f"  cells held {held}, {per_cell} chunks each, {total_chunks} tower chunks, warm {warm}")
            results.append(Result("the tower reports what it is holding loaded",
                                  held >= 1 and per_cell == 49, listed.strip()[:200]))

            # The pool tops up after a run takes a cell, so the next party does not wait for a paste.
            time.sleep(1)
            results.append(Result("the warm pool built cells ahead of the next run", warm >= 1,
                                  f"warm={warm}; {listed.strip()[:160]}"))

            # Ending the run resets the cell and lets the chunks go.
            rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            (sx, sy, sz), _ = samples[0]
            emptied = probe.holds(f"execute in cobbletowers:tower if block "
                                  f"{origin[0] + sx} {origin[1] + sy} {origin[2] + sz} minecraft:air")
            results.append(Result("ending a run clears the arena out of the cell", emptied,
                                  "a sampled arena block is still there after the run ended"))
            probe.close()
            after = rcon.command(f"cobbletowers cells show {cell}")
            results.append(Result("and lets the cell's chunks go", "chunks not held" in after,
                                  after.strip()[:220]))
            results.append(Result("a cell released clean is not quarantined", "quarantined" not in after,
                                  after.strip()[:220]))

        log = server.read_log()
        paste = re.findall(r"prepared with .+? in (\d+) ms", log)
        reset = re.findall(r"reset, (\d+) block\(s\) cleared", log)
        print(f"  paste times (ms): {paste}   blocks cleared per reset: {reset}")
        results.append(Result("a cold paste is measured, not estimated", bool(paste),
                              "no paste timing appeared in the log"))
        results.append(Result("no CobbleTowers exception during any of it",
                              "com.cobbletowers" not in log.replace("com.cobbletowers.CobbleTowers", ""),
                              "see " + str(server.log)))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("floor build run", False, repr(exc)))
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
