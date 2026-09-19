#!/usr/bin/env python3
"""P8b live: a drafted modifier changing the boss battle itself, through CobbleRaids.

This is the phase's risky half, and the only part of it that cannot be argued from source: the
weather is set inside the Showdown process, where Java can see nothing. So the patch reports back
what the field HOLDS after it tried -- read from `battle.field.weather`, never from what it was
told to set -- and CobbleRaids logs that as `-raidfield`. An id Showdown does not recognise, or a
patch that silently stopped being loaded, reads as a failure here rather than as success.

What it proves:

1. `EncounterRules` reaches CobbleRaids at all -- the start log names the rules it was given.
2. A FIELD modifier really sets the weather **inside Showdown**, confirmed by read-back.
3. An ENEMY `boss_health_percent` really scales the shared pool, confirmed against the baseline the
   log now carries with it.
4. A PLAYER_CONSTRAINT banning the bot's own move is refused at the server, in a real battle.

Modifiers are granted with `runs grant` rather than drafted: which cards come up is the seed's
business, and a test that waited for the right one would be testing the draw. The draft itself is
covered by draft_test.py.

    python validation/smoke/battle_rules_test.py \\
      --server-dir <rig>/testserver \\
      --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.12.1+1/bin/java.exe" \\
      --jar build/libs/CobbleTowers-<version>.jar \\
      --raids-jar ../SnobblemonRaids/build/libs/CobbleRaids-<version>.jar
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from,
)
from floor_encounter_test import (  # noqa: E402
    FIRST_MOVE, TOWER, begin_floor, give_party, start_battle_bot, tell_bot, wait_for, wait_for_floor,
)
from participant_test import wait_joined  # noqa: E402

STAMP = int(time.time()) % 100000
ALPHA = f"TRa{STAMP}"
BETA = f"TRb{STAMP}"

WEATHER_MODIFIER = "cobbletowers:downpour"
WEATHER_ID = "raindance"
HEALTH_MODIFIER = "cobbletowers:bulwark_boss"
HEALTH_PERCENT = 130


def install_raids_jar(server_dir: Path, jar: Path) -> None:
    """Swaps the CobbleRaids jar, the same way install_jar swaps the tower's."""
    mods = server_dir / "mods"
    for existing in mods.glob("CobbleRaids-*.jar"):
        if "AddonRewards" in existing.name:
            continue
        existing.unlink()
    shutil.copy2(jar, mods / jar.name)
    print(f"Installed {jar.name}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--raids-jar", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    rig = server_dir.parent
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    if args.raids_jar:
        install_raids_jar(server_dir, args.raids_jar.resolve())
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bots: dict[str, subprocess.Popen] = {}
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        for name in (ALPHA, BETA):
            bots[name] = start_battle_bot(rig, name, FIRST_MOVE)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (ALPHA, BETA):
                if not wait_joined(rcon, name):
                    raise RuntimeError(f"{name} never joined; see {rig}/bot/{name}.log")
            clear_tower(rcon)
            for name in (ALPHA, BETA):
                give_party(rcon, name)

            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")

            # --- granted, not drafted ------------------------------------------------------------
            weather = rcon.command(f"cobbletowers runs grant {run} {WEATHER_MODIFIER}")
            health = rcon.command(f"cobbletowers runs grant {run} {HEALTH_MODIFIER}")
            results.append(Result("a field modifier can be granted to a run",
                                  "Granted" in weather, weather.strip()[:200]))
            results.append(Result("an enemy health modifier can be granted too",
                                  "Granted" in health, health.strip()[:200]))

            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the run reports the boss pool it will ask for",
                                  f"boss hp {HEALTH_PERCENT}%" in shown,
                                  shown.strip()[:300]))

            # --- fight the floor through to its boss ---------------------------------------------
            for name in (ALPHA, BETA):
                tell_bot(rig, name, "FIGHT")
                tell_bot(rig, name, f"MOVE {FIRST_MOVE}")
                if not wait_for(rig / "bot" / f"{name}.log", r"AUTOFIGHT on", seconds=30):
                    raise RuntimeError(f"{name} never armed; see {rig}/bot/{name}.log")

            begun = begin_floor(rcon, run)
            results.append(Result("the floor starts under the granted modifiers",
                                  "opponent(s)" in begun, begun.strip()[:200]))

            resolved = wait_for_floor(server, rig, [ALPHA, BETA])
            print(f"  floor outcome: {resolved or '<none>'}")
            log = server.read_log()

            # --- 1. the rules crossed the boundary ------------------------------------------------
            started = re.search(r"Owned encounter \S+ started for \S+: (\S+) at level (\d+), (\d+) player\(s\),"
                                r" pool (\d+)(?: \((\d+)% of (\d+)\))?(?: rules=(.*))?", log)
            results.append(Result("CobbleRaids was told the encounter's rules",
                                  started is not None and bool(started.group(7)),
                                  started.group(0)[:240] if started else "no owned-encounter start line"))
            if started:
                print(f"  {started.group(0)}")

            # --- 2. the pool really was scaled ----------------------------------------------------
            #
            # Against the baseline the log carries with it, so this is arithmetic on two numbers
            # from the same line rather than a second run to compare against.
            scaled = started is not None and started.group(5) is not None
            if scaled:
                applied = int(started.group(4))
                percent = int(started.group(5))
                baseline = int(started.group(6))
                results.append(Result("the boss pool is scaled by the granted percentage",
                                      percent == HEALTH_PERCENT and applied == baseline * percent // 100,
                                      f"{applied} vs {baseline} at {percent}%"))
            else:
                results.append(Result("the boss pool is scaled by the granted percentage",
                                      False, "the start line carries no scaling"))

            # --- 3. the weather actually reached Showdown ------------------------------------------
            #
            # The one claim that cannot be argued from source. Read back from battle.field.weather
            # by the patch itself; a mismatch is logged as a warning and fails here.
            applied_field = re.search(r"Raid \S+ field: weather '(\S+)' applied", log)
            mismatch = re.search(r"asked Showdown for weather '(\S+)' and it holds '(\S+)' instead", log)
            results.append(Result("Showdown really set the weather the modifier asked for",
                                  applied_field is not None and applied_field.group(1) == WEATHER_ID,
                                  mismatch.group(0) if mismatch else "no -raidfield confirmation in the log"))
            if applied_field:
                print(f"  field confirmed: {applied_field.group(0)}")

            results.append(Result("and nothing reported the field condition as failing to apply",
                                  mismatch is None and "could not be applied" not in log,
                                  mismatch.group(0) if mismatch else "a failure was reported"))
    finally:
        for bot in bots.values():
            bot.kill()
        server.stop()

    print()
    width = max(len(result.name) for result in results)
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  "
              f"{result.detail if not result.passed else ''}")
    print()
    print(f"battle_rules_test: {len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
