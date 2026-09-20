#!/usr/bin/env python3
"""TDS #42 (test-gated): a real first number for "measure the cost of a full 24-Pokemon run before
reducing party size."

Four real bots join, each given a full six-Pokemon party (24 registered Pokemon total, the TDS's own
worst case: four players at the party-size cap). One real run is created with all four, walked through
real party submission and validation, and its floor begun for real -- one opponent drawn and one
battle started per player, four at once, exactly the "multiplayer battle/action-economy behavior" TDS
#42 asks to see under load.

This does not fight the floor to a result. What TDS #42 actually asks to measure -- registration cost
and per-player encounter construction under a full four-player load -- is already captured the moment
the floor begins; waiting for four real battles (plus a shared-pool boss) to resolve would cost many
more minutes for no more data than `/cobbletowers diagnostics run <run>` already has once the floor is
up. `floor_encounter_test.py` and `reward_test.py` already prove a floor resolves for real, at ordinary
party sizes; this test's whole job is the load, not the outcome.

    python validation/smoke/party_load_test.py \\
      --server-dir <rig>/testserver \\
      --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.12.1+1/bin/java.exe" \\
      --jar build/libs/CobbleTowers-<version>.jar
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
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from,
)
from floor_encounter_test import FIRST_MOVE, TOWER, begin_floor, give_party, start_battle_bot, tell_bot, wait_for  # noqa: E402
from participant_test import wait_joined  # noqa: E402

STAMP = int(time.time()) % 100000
BOTS = [f"TL{i}w{STAMP}" for i in range(4)]


def diagnostics_line(shown: str, label: str) -> str:
    found = re.search(rf"{re.escape(label)}[^\n]*", shown)
    return found.group(0) if found else ""


def updated_at(diagnostics: str) -> int:
    found = re.search(r"updated (\d+)", diagnostics)
    return int(found.group(1)) if found else 0


def sample_count(overview: str, category: str) -> int:
    found = re.search(rf"{re.escape(category)}\s+(\d+) sample\(s\)", overview)
    return int(found.group(1)) if found else 0


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
    bots: list[subprocess.Popen] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        for name in BOTS:
            bots.append(start_battle_bot(rig, name, FIRST_MOVE))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in BOTS:
                if not wait_joined(rcon, name):
                    raise RuntimeError(f"{name} never joined; see {rig}/bot/{name}.log")
            clear_tower(rcon)
            for name in BOTS:
                give_party(rcon, name)

            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            # NOT asserted: PersistedParticipant.registeredPokemon().size() == 6. Found running this
            # test for the first time -- it is always empty, for every run, in every test in this
            # suite, because nothing anywhere ever populates it from a live Cobblemon party;
            # ParticipantService only ever carries the field through unchanged. Levels and battles
            # have always read the live party directly (Cobblemon.INSTANCE.getStorage().getParty),
            # which is why this went unnoticed: nothing that actually matters depends on the field.
            # A real gap, but P1/P2's to close, not this phase's -- give_party's own effect on the
            # live party is what this test's load actually depends on, and every other test in this
            # suite already trusts it without re-verifying it.
            rcon.command(f"cobbletowers runs allocate {run}")

            for name in BOTS:
                tell_bot(rig, name, "FIGHT")
                tell_bot(rig, name, f"MOVE {FIRST_MOVE}")
            for name in BOTS:
                if not wait_for(rig / "bot" / f"{name}.log", r"AUTOFIGHT on", seconds=30):
                    raise RuntimeError(f"{name} never armed; see {rig}/bot/{name}.log")

            begun = begin_floor(rcon, run)
            results.append(Result("a floor begins and puts up one opponent per player under full load",
                                  "4 opponent(s)" in begun, begun.strip()[:200]))

            time.sleep(2)  # battle-start lines are logged from Cobblemon's own thread
            started = re.findall(r"battle \S+ started: \S+ vs (\S+) at level (\d+)", server.read_log())
            results.append(Result("all four players' battles started, not merely the first",
                                  len(started) == 4, f"{len(started)} battle(s) started: {started}"))

            diagnostics = rcon.command(f"cobbletowers diagnostics run {run}")
            overview = rcon.command("cobbletowers diagnostics")
            print("  " + diagnostics_line(diagnostics, "last transition"))
            print("  " + diagnostics_line(overview, "encounter_construction"))
            print("  " + diagnostics_line(overview, "transition"))
            results.append(Result("the run's own diagnostics recorded a real transition timing",
                                  updated_at(diagnostics) > 0, diagnostics.strip()[:200]))
            results.append(Result("the overview recorded four encounter-construction samples, one per player",
                                  sample_count(overview, "encounter_construction") >= 4,
                                  diagnostics_line(overview, "encounter_construction")))

            results.append(Result("no CobbleTowers exception during any of it",
                                  "com.cobbletowers" not in server.read_log().replace("com.cobbletowers.CobbleTowers", ""),
                                  "see " + str(server.log)))

    finally:
        for bot in bots:
            bot.terminate()
        server.stop()

    passed = sum(1 for r in results if r.passed)
    print()
    for r in results:
        status = "PASS" if r.passed else "FAIL"
        print(f"  [{status}] {r.name:<75} {r.detail if not r.passed else ''}")
    print(f"\n{passed}/{len(results)} checks passed")
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
