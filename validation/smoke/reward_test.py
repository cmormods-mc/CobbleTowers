#!/usr/bin/env python3
"""P9 live: a real floor clear pays out, a cash-out grants what remains, and a milestone bank works.

Before this phase, `REWARDS_BANKED`, `FINAL_FLOOR_CLEARED` and `CASH_OUT_CHOSEN` existed in the
transition table from P1's first commit, but nothing in production code ever fired any of them -- a
real floor clear stalled in `FLOOR_RESOLVING` forever, and the only way a live run ever reached
`INTERMISSION` was the `runs advance` debug command. This is the test that would have caught that:
it wins a real floor and checks the run actually moves on by itself.

What it proves, none of which a unit test can reach:

  1. Winning a real floor's prerequisite and boss reaches INTERMISSION on its own -- the literal gap
     this phase closes.
  2. An ordinary (non-milestone) floor's earnings show as still at risk, not banked yet.
  3. Cashing out is not blocked by an open draft (the confirmed design decision), and it grants
     everything the run has earned so far, delivered straight to an online player's inventory.
  4. A milestone floor's own intermission is a real, un-forfeitable payout point, distinct from an
     ordinary floor's -- proven on a second run walked forward with `runs advance` (the state machine
     under test, not a second real fight; floor_encounter_test.py already proves a real floor plays).
  5. The final floor reaches COMPLETED and banks whatever remains.

    python validation/smoke/reward_test.py \\
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
from floor_encounter_test import (  # noqa: E402
    FIRST_MOVE, TOWER, begin_floor, give_party, start_battle_bot, tell_bot, wait_for, wait_for_floor,
)
from participant_test import wait_joined  # noqa: E402

STAMP = int(time.time()) % 100000
BOT = f"TRw{STAMP}"


def advance_to_floor_resolving(rcon: Rcon, run: str) -> None:
    """FLOOR_READY to FLOOR_RESOLVING by hand, no battle. Assumes the run is already at FLOOR_READY.

    The same shortcut draft_test.py's to_intermission takes: every move is the real transition, only
    the battle itself is skipped. No LedgerEntry is written along this path (TowerEncounters is what
    writes those, and it is never called), which is exactly what makes it safe to use for proving the
    STATE MACHINE reaches a milestone or the final floor without needing four more real fights.
    """
    rcon.command(f"cobbletowers runs advance {run} encounter_started")
    rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")


def advance_to_next_floor(rcon: Rcon, run: str) -> None:
    """INTERMISSION -> the next floor's FLOOR_READY. Assumes INTERMISSION already.

    INTERMISSION_COMPLETE is refused while a draft is open (TDS #2), and reaching an intermission
    always opens one here -- ten modifiers are loaded and nobody is voting. `draft force` settles it
    without needing a real vote, the same shortcut draft_test.py's own operator path uses.
    """
    rcon.command(f"cobbletowers runs draft force {run}")
    rcon.command(f"cobbletowers runs advance {run} intermission_complete")
    rcon.command(f"cobbletowers runs advance {run} next_floor_confirmed")


def banked_through(shown: str) -> int:
    found = re.search(r"Banked through floor (\d+)", shown)
    return int(found.group(1)) if found else -1


def queued_count(shown: str) -> int:
    found = re.search(r"Queued for you: (\d+) grant", shown)
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
    bot: subprocess.Popen | None = None
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

            # === Run A: a real floor, won for real =========================================
            run_a = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            rcon.command(f"cobbletowers runs advance {run_a} party_submitted")
            rcon.command(f"cobbletowers runs advance {run_a} party_validated")
            rcon.command(f"cobbletowers runs allocate {run_a}")

            tell_bot(rig, BOT, "FIGHT")
            tell_bot(rig, BOT, f"MOVE {FIRST_MOVE}")
            if not wait_for(rig / "bot" / f"{BOT}.log", r"AUTOFIGHT on", seconds=30):
                raise RuntimeError(f"{BOT} never armed; see {rig}/bot/{BOT}.log")

            begun = begin_floor(rcon, run_a)
            results.append(Result("floor 1 begins and puts an opponent up", "opponent" in begun,
                                  begun.strip()[:200]))

            boss_started = wait_for_floor(server, rig, [BOT], seconds=180,
                                          pattern=r"Floor \d+ boss \S+ started at level \d+")
            results.append(Result("clearing the prerequisite starts the floor's boss",
                                  bool(boss_started), "no boss start line; see " + str(server.log)))

            resolved = wait_for_floor(server, rig, [BOT], seconds=360)
            print(f"  floor outcome: {resolved or '<none>'}")
            results.append(Result("the floor resolved rather than hanging", bool(resolved),
                                  "no cleared/wiped line appeared; see " + str(server.log)))

            # --- the actual regression: does a real win reach an intermission by itself? -------
            after_win = rcon.command(f"cobbletowers runs show {run_a}")
            results.append(Result(
                "winning floor 1 reaches INTERMISSION on its own, not stuck in FLOOR_RESOLVING",
                f"{run_a}  INTERMISSION" in after_win, after_win.strip()[:200]))
            results.append(Result("a REWARDS_BANKED transition line was logged for the win",
                                  "REWARDS_BANKED" in server.read_log(), "see " + str(server.log)))

            # --- an ordinary floor's earnings are recorded, but still at risk ------------------
            shown = rcon.command(f"execute as {BOT} run cobbletowers runs reward show")
            results.append(Result("an ordinary floor's clear has not been banked yet",
                                  banked_through(shown) == 0, shown.strip()[:300]))
            results.append(Result("what it earned shows as at risk, not banked",
                                  "at risk" in shown and "[banked]" not in shown, shown.strip()[:300]))
            results.append(Result("nothing has been queued to the player yet",
                                  queued_count(shown) == 0, shown.strip()[:200]))

            # === cashing out, with the draft still open =========================================
            #
            # Confirmed design decision: cashing out is NOT blocked by an open draft, because a party
            # leaving for good has no reason to be forced through a vote it will never use. Reaching
            # INTERMISSION opened one (ten modifiers are loaded); it is left unanswered on purpose.
            # `runs draft show` (the dedicated command) never prints "OPEN" for an unresolved draft --
            # only the general `runs show`'s draft line does, the way draft_test.py already reads it.
            open_draft = " OPEN, " in rcon.command(f"cobbletowers runs show {run_a}")
            results.append(Result("an intermission draft is open and unanswered",
                                  open_draft, "no open draft; the cash-out test below proves less"))

            cashed = rcon.command(f"execute as {BOT} run cobbletowers runs cashout")
            results.append(Result("cashing out is not blocked by the open draft",
                                  "cashed out" in cashed.lower(), cashed.strip()[:200]))

            after_cashout = rcon.command(f"cobbletowers runs show {run_a}")
            results.append(Result("the run is CASHED_OUT", "CASHED_OUT" in after_cashout,
                                  after_cashout.strip()[:200]))
            results.append(Result("cashing out committed the grant's own key, distinct from the transition's",
                                  f"run:{run_a}:floor:1:granted" in after_cashout, after_cashout.strip()[:300]))

            log = server.read_log()
            banked_line = re.search(rf"Run {re.escape(run_a)} banked its rewards through floor 1: \[(.*?)\]", log)
            results.append(Result("cashing out actually banked a non-empty grant",
                                  bool(banked_line) and banked_line.group(1).strip() != "",
                                  banked_line.group(0) if banked_line else "no banking line; see " + str(server.log)))
            if banked_line:
                print(f"  granted: {banked_line.group(1)}")

            inventory = rcon.command(f"data get entity {BOT} Inventory")
            granted_something = any(item in inventory for item in
                                    ("minecraft:cobbled_deepslate", "minecraft:diamond", "minecraft:emerald"))
            results.append(Result("the grant actually reached the player's inventory",
                                  granted_something, inventory.strip()[:300]))

            # === Run B: the state machine at a milestone and at the final floor =================
            #
            # No second real fight: floor_encounter_test.py already proves a floor plays for real,
            # and P9's own claim here is about the transition wiring, not the battle. Walked forward
            # by hand, the way draft_test.py's to_intermission is. Floors 5 and 10 are milestones in
            # the shipped tower (neutral/boss_05.json, neutral/champion_10.json); every other floor
            # is ordinary.
            run_b = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            rcon.command(f"cobbletowers runs advance {run_b} party_submitted")
            rcon.command(f"cobbletowers runs advance {run_b} party_validated")
            rcon.command(f"cobbletowers runs allocate {run_b}")
            rcon.command(f"cobbletowers runs advance {run_b} preparation_complete")  # floor 1, FLOOR_READY

            for floor in range(1, 5):  # floors 1-4: ordinary
                advance_to_floor_resolving(rcon, run_b)
                rcon.command(f"cobbletowers runs advance {run_b} rewards_banked")
                if floor < 4:
                    advance_to_next_floor(rcon, run_b)

            at_floor_4 = rcon.command(f"cobbletowers runs show {run_b}")
            results.append(Result("run B reached its floor-4 intermission by hand",
                                  f"{run_b}  INTERMISSION" in at_floor_4 and "floor 4" in at_floor_4,
                                  at_floor_4.strip()[:200]))
            results.append(Result("an ordinary floor's own intermission is not a bank point",
                                  f"run:{run_b}:floor:4:granted" not in at_floor_4, at_floor_4.strip()[:300]))

            advance_to_next_floor(rcon, run_b)      # floor 5, FLOOR_READY -- the milestone
            advance_to_floor_resolving(rcon, run_b)
            rcon.command(f"cobbletowers runs advance {run_b} rewards_banked")

            at_milestone = rcon.command(f"cobbletowers runs show {run_b}")
            results.append(Result("floor 5's milestone intermission is a real bank point",
                                  f"run:{run_b}:floor:5:granted" in at_milestone, at_milestone.strip()[:300]))

            for floor in range(6, 10):  # floors 6-9: ordinary again
                advance_to_next_floor(rcon, run_b)
                advance_to_floor_resolving(rcon, run_b)
                rcon.command(f"cobbletowers runs advance {run_b} rewards_banked")

            advance_to_next_floor(rcon, run_b)      # floor 10, FLOOR_READY -- the final floor
            advance_to_floor_resolving(rcon, run_b)
            completed = rcon.command(f"cobbletowers runs advance {run_b} final_floor_cleared")
            results.append(Result("the final floor's clear reaches COMPLETED",
                                  "COMPLETED" in completed, completed.strip()[:200]))

            after_complete = rcon.command(f"cobbletowers runs show {run_b}")
            results.append(Result("COMPLETED banks whatever remained, milestone or not",
                                  f"run:{run_b}:floor:10:granted" in after_complete,
                                  after_complete.strip()[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("reward run", False, repr(exc)))
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
    print(f"\nreward_test: {len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
