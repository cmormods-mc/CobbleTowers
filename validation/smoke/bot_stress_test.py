#!/usr/bin/env python3
"""Checks that the smoke rig's battle bot can win against whatever a floor draws, many times in a row.

The bot used to cast one named move and stall on some opponent draws: every move came back "Invalid
action choice" and it never re-prompted, so a fought floor could sit forever (found in P17). It now sends
DEFAULT and lets the battle choose a legal move. One lucky pass proves nothing about a failure that depended
on the draw, so this runs the first opponent of floor 1 many times in one server session, each with a fresh
random seed, and records every species that was drawn and whether the bot beat it.

It deliberately stops at the first opponent's defeat rather than clearing the whole floor and its boss:
the opponent is the part that varies, and a boss fight per iteration would make a dozen draws take an hour.

    python validation/smoke/bot_stress_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>] [--rounds 12]
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
from floor_encounter_test import FIRST_MOVE, TOWER, give_party, start_battle_bot, tell_bot, wait_for  # noqa: E402
from participant_test import wait_joined  # noqa: E402

BOT = f"TBs{int(time.time()) % 100000}"
PER_ROUND_SECONDS = 150


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--rounds", type=int, default=12)
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
    outcomes: list[tuple[str, bool, int]] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bot = start_battle_bot(rig, BOT, FIRST_MOVE)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_joined(rcon, BOT):
                raise RuntimeError(f"{BOT} never joined; see {rig}/bot/{BOT}.log")
            clear_tower(rcon)
            give_party(rcon, BOT)
            tell_bot(rig, BOT, "FIGHT")
            if FIRST_MOVE:
                tell_bot(rig, BOT, f"MOVE {FIRST_MOVE}")
            if not wait_for(rig / "bot" / f"{BOT}.log", r"AUTOFIGHT on", seconds=30):
                raise RuntimeError("the bot never armed")

            for round_number in range(1, args.rounds + 1):
                log_offset = len(server.read_log())
                run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
                rcon.command(f"cobbletowers runs advance {run} party_submitted")
                rcon.command(f"cobbletowers runs advance {run} party_validated")
                rcon.command(f"cobbletowers runs allocate {run}")
                rcon.command(f"cobbletowers runs advance {run} preparation_complete")
                rcon.command(f"cobbletowers runs encounter {run}")

                started = time.time()
                beaten = False
                while time.time() - started < PER_ROUND_SECONDS:
                    shown = rcon.command(f"cobbletowers runs show {run}")
                    if "OPPONENT_DEFEATED" in shown:
                        beaten = True
                        break
                    if "wiped the party" in server.read_log()[log_offset:]:
                        break
                    time.sleep(2)
                seconds = int(time.time() - started)
                drew = re.search(r"battle \S+ started: \S+ vs (\S+) at level (\d+)", server.read_log()[log_offset:])
                species = drew.group(1) if drew else "<no battle started>"
                outcomes.append((species, beaten, seconds))
                print(f"  round {round_number}: {species} -> {'beaten' if beaten else 'NOT beaten'} in {seconds}s")

                rcon.command(f"cobbletowers runs advance {run} abandon_requested")
                clear_tower(rcon)
                time.sleep(2)
                # The bot answers one battle per FIGHT it has seen; DEFAULT mode needs no move re-sent.
                tell_bot(rig, BOT, "FIGHT")
    except Exception as exc:  # noqa: BLE001
        results.append(Result("bot stress run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        print("Stopping server")
        server.stop()

    beaten_count = sum(1 for _, ok, _ in outcomes if ok)
    species_seen = sorted({species for species, _, _ in outcomes})
    results.append(Result(f"the bot beat every opponent it was dealt ({beaten_count}/{len(outcomes)})",
                          bool(outcomes) and beaten_count == len(outcomes),
                          "; ".join(f"{s}:{'ok' if ok else 'STALLED'}" for s, ok, _ in outcomes)))
    results.append(Result(f"a spread of species was drawn ({len(species_seen)} distinct)", len(species_seen) >= 3,
                          ", ".join(species_seen)))
    slowest = max((seconds for _, _, seconds in outcomes), default=0)
    results.append(Result(f"no single opponent took longer than two minutes (slowest {slowest}s)", slowest < 120, str(slowest)))

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
