#!/usr/bin/env python3
"""Proves P14's three regional towers actually resolve in a real game, not just offline.

`validate_definitions.py` and `ci_local.sh` already prove the content parses and every id in this
repo's own schema resolves. What they cannot prove is the one thing that actually matters for new
content: that the real Cobblemon jar accepts every jersey/supporting species id and aspect this phase
authored, and that the real CobbleRaids jar accepts every milestone/boss-pool raid definition id --
both were checked offline against the bundled jars' own data (see P14-regional-content.md), but an
offline check is not the same as Cobblemon/CobbleRaids actually being asked to spawn one.

For each of Tideforge, Rootvale and Duskvale: create a solo run, submit and validate an oversized-lead
party (the same "one strong lead, five level-1 fillers" trick every other floor test uses so the level
policy draws something beatable), allocate, and begin the floor twice. Each draw's "battle ... started:
X vs SPECIES at level N" log line is checked against that region's own encounter pool -- proving the
draw actually reached Cobblemon and came back with a species from the right region, not Neutral's.

This deliberately does NOT play a floor to completion or reach a milestone boss: doing that for all
ten floors of all three towers would mean playing thirty real floors end to end, which is a full soak
run, not a smoke test of new content. The F5/F10 milestone raid definitions are checked offline only
(see the design doc); this test's job is the regular-floor draw path, which is where a bad species or
aspect id would actually surface.

    python validation/smoke/regional_content_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from, server_port,
)

# run_durability_test.start_bot hardcodes its own module's BOT="TowerDuraBot" into the joinbot.js
# invocation regardless of what a caller names its own constant -- so this test spawns joinbot.js
# directly with its own name instead of importing that function, the same way floor_encounter_test.py
# defines its own start_battle_bot rather than reusing it.
BOT = "TowerRegionBot"
LEAD = "glaceon"
FILLERS = 5

# The exact fifteen species (five jerseys + ten supporting) each region's encounter pool authors, per
# P14-regional-content.md. A draw outside this set for a given tower means the wrong pool was read.
POOLS = {
    "cobbletowers:tideforge": {
        "kyogre", "vaporeon", "blastoise", "golisopod", "empoleon",
        "floatzel", "barraskewda", "toxapex", "lanturn", "corviknight",
        "perrserker", "greninja", "drednaw", "toxtricity", "basculin",
    },
    "cobbletowers:rootvale": {
        "celebi", "hydrapple", "leafeon", "snivy", "breloom",
        "rillaboom", "tsareena", "ferrothorn", "amoonguss", "trevenant",
        "torterra", "tangrowth", "roserade", "toedscruel", "decidueye",
    },
    "cobbletowers:duskvale": {
        "darkrai", "umbreon", "obstagoon", "marshadow", "dusknoir",
        "kingambit", "weavile", "chandelure", "zoroark", "mismagius",
        "grimmsnarl", "absol", "sableye", "houndoom", "mimikyu",
    },
}
DRAWS_PER_TOWER = 2


def wait_online(rcon: Rcon, seconds: int = 90) -> bool:
    for _ in range(seconds):
        if BOT in rcon.command("list"):
            return True
        time.sleep(1)
    return False


def start_join_bot(port: int, node_modules: Path, log: Path):
    """joinbot.js under this test's own name -- see the note above `BOT`."""
    import subprocess
    env = dict(os.environ, NODE_PATH=str(node_modules))
    handle = open(log, "w", encoding="utf-8", errors="replace")
    return subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(port)],
                            stdout=handle, stderr=subprocess.STDOUT, env=env)


def give_party(rcon: Rcon) -> None:
    rcon.command(f"pokegiveother {BOT} {LEAD} level=100")
    for _ in range(FILLERS):
        rcon.command(f"pokegiveother {BOT} magikarp level=1")


def draw_once(rcon: Rcon, server: Server, tower: str, log_offset: int) -> tuple[str, int]:
    """Creates a solo run on `tower`, begins the floor, and returns the species drawn.

    The party is given once, before any run exists (see `main`), and reused for every draw: Cobblemon
    parties are not tied to a tower run, and `pokegiveother` only adds -- calling it again per draw
    would overflow the already-full six-slot party into PC boxes for nothing.
    """
    run = run_id_from(rcon.command(f"cobbletowers runs create {tower} {BOT}"))
    rcon.command(f"cobbletowers runs advance {run} party_submitted")
    rcon.command(f"cobbletowers runs advance {run} party_validated")
    rcon.command(f"cobbletowers runs allocate {run}")
    rcon.command(f"cobbletowers runs advance {run} preparation_complete")
    rcon.command(f"cobbletowers runs encounter {run}")
    time.sleep(3)  # the battle-start line is logged from Cobblemon's own thread
    log = server.read_log()
    new_text = log[log_offset:]
    match = re.search(r"battle (\S+) started: (\S+) vs (\S+) at level (\d+)", new_text)
    species = match.group(3).split(":")[-1] if match else ""
    rcon.command(f"cobbletowers runs advance {run} abandon_requested")
    clear_tower(rcon)
    return species, len(log)


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
        bot_log = server_dir / "logs" / "towers-regional-bot.log"
        bot = start_join_bot(server_port(server_dir), node_modules, bot_log)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon):
                said = bot_log.read_text(encoding="utf-8", errors="replace").strip() if bot_log.exists() else ""
                raise RuntimeError(f"the bot never joined. node said: {said[-400:] or '<nothing>'}")

            loaded = rcon.command("cobbletowers definitions")
            for tower in POOLS:
                name = tower.split(":")[1]
                results.append(Result(f"{name} tower loaded", name in loaded, loaded.strip()[:300]))

            give_party(rcon)
            log_offset = len(server.read_log())
            for tower, expected in POOLS.items():
                name = tower.split(":")[1]
                for attempt in range(1, DRAWS_PER_TOWER + 1):
                    species, log_offset = draw_once(rcon, server, tower, log_offset)
                    results.append(Result(
                        f"{name} draw {attempt}: opponent came from its own pool",
                        species in expected,
                        f"drew '{species}', expected one of {name}'s 15-species pool" if species not in expected
                        else f"drew {species}"))
                    if species:
                        print(f"  {name} draw {attempt}: {species}")
    except Exception as exc:  # noqa: BLE001
        results.append(Result("regional content run", False, repr(exc)))
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
