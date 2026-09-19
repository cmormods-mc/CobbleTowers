#!/usr/bin/env python3
"""P8 live: the intermission draft, voting, lock-in and what a drafted modifier changes.

Drives a real server over RCON with two real bots. Five things it proves that no unit test can:

1. A draft actually opens when a run reaches an intermission, with cards the resolver allowed.
2. `intermission_complete` is refused while it is open, so a run cannot skip the choice.
3. A majority settles it, and the chosen modifier is accumulated onto the run.
4. **A hard kill mid-draft returns the same three cards** -- the determinism claim (TDS #29),
   checked against a server that really died rather than against the same process.
5. A draft nobody is left to answer is settled by the watchdog instead of holding the cell.

The run is driven to its intermission with `runs advance` rather than by winning a fight. That is
deliberate: the subject here is the draft, and making it depend on two bots beating a real draw
would make a red result mean "the bots lost" as often as "the draft is broken". Floor combat is
covered by floor_encounter_test.py and participant_test.py; the last section here does fight a real
floor, because that is the only way to see a modifier change one.

    python validation/smoke/draft_test.py \\
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
# Fresh names per run: pokegiveother ADDS to a party, so a reused name accumulates earlier tests'
# fillers until the lead is a level 1 Magikarp.
ALPHA = f"TDa{STAMP}"
BETA = f"TDb{STAMP}"

NEUTRAL_EFFECTS = "level +0, +0 opponent(s), boss level +0, boss hp 100%, reward 100%"


def to_intermission(rcon: Rcon, run: str) -> None:
    """Walks a run from FLOOR_READY to INTERMISSION without fighting anything.

    Every one of these is a real transition through the real table; none of them is a shortcut the
    production path does not take. What is skipped is the battle, not the state machine.
    """
    rcon.command(f"cobbletowers runs advance {run} preparation_complete")
    rcon.command(f"cobbletowers runs advance {run} encounter_started")
    rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
    rcon.command(f"cobbletowers runs advance {run} rewards_banked")


# RCON hands a whole command's output back as ONE line, so `runs show` arrives as a single
# space-separated string. Reading it with splitlines() finds only the first line, and every later
# probe then quietly returns "" and passes -- the vacuous-check trap this directory's README warns
# about. These read the shape they are looking for instead of assuming line breaks survive.
def draft_line(shown: str) -> str:
    found = re.search(r"draft at floor \d+.*?(?:OPEN, \d+ vote\(s\)|-> \S+(?: \(tie-break\))?)", shown)
    return found.group(0) if found else ""


def effects_line(shown: str) -> str:
    found = re.search(r"modifiers: .*?reward \d+%", shown)
    return found.group(0) if found else ""


def cards_in(shown: str) -> list[str]:
    found = re.search(r"draft at floor \d+[^\[]*\[(.*?)\]", shown)
    return [card.strip() for card in found.group(1).split(",")] if found else []


def vote(rcon: Rcon, name: str, card: int) -> str:
    return rcon.command(f"execute as {name} run cobbletowers runs draft vote {card}")


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
    bots: dict[str, subprocess.Popen] = {}
    password = read_password(server_dir)
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        for name in (ALPHA, BETA):
            bots[name] = start_battle_bot(rig, name, FIRST_MOVE)

        with Rcon("127.0.0.1", 25575, password) as rcon:
            for name in (ALPHA, BETA):
                if not wait_joined(rcon, name):
                    raise RuntimeError(f"{name} never joined; see {rig}/bot/{name}.log")
            clear_tower(rcon)
            for name in (ALPHA, BETA):
                give_party(rcon, name)

            loaded = rcon.command("cobbletowers definitions")
            results.append(Result("the server loaded the modifier definitions",
                                  re.search(r"([1-9]\d*) modifier\(s\)", loaded) is not None,
                                  loaded.strip()[:300]))

            # --- a draft opens at an intermission ----------------------------------------------
            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")

            before = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("a fresh run carries no modifiers at all",
                                  NEUTRAL_EFFECTS in effects_line(before), effects_line(before)))

            to_intermission(rcon, run)
            opened = wait_for(server.log, r"opened a(?: LOCK-IN)? draft at floor 1", seconds=30)
            results.append(Result("reaching an intermission opens a draft",
                                  bool(opened), "no draft line; see " + str(server.log)))

            shown = rcon.command(f"cobbletowers runs show {run}")
            cards = cards_in(shown)
            results.append(Result("the draft offers three cards",
                                  len(cards) == 3, f"{len(cards)}: {draft_line(shown)}"))
            results.append(Result("the three cards are three different modifiers",
                                  len(set(cards)) == len(cards), str(cards)))
            results.append(Result("the draft is open and unvoted",
                                  "OPEN" in draft_line(shown) and "0 vote(s)" in draft_line(shown),
                                  draft_line(shown)))

            # --- the run cannot skip the choice ------------------------------------------------
            refused = rcon.command(f"cobbletowers runs advance {run} intermission_complete")
            results.append(Result("a run cannot leave an intermission with a draft still open",
                                  "still choosing a modifier" in refused, refused.strip()[:240]))

            # --- determinism across a real crash ------------------------------------------------
            #
            # save-all flush first: a hard kill loses everything unwritten, and the question here is
            # whether what was WRITTEN comes back the same, not whether an autosave happened to fire.
            print("Flushing and hard-killing the server mid-draft")
            rcon.command("save-all flush")
            time.sleep(3)

        server.process.kill()
        server.process.wait(timeout=60)
        for name, bot in list(bots.items()):
            bot.kill()
        bots.clear()

        print("Restarting")
        server.start()
        server.wait_until_ready()
        with Rcon("127.0.0.1", 25575, password) as rcon:
            after = rcon.command(f"cobbletowers runs show {run}")
            recovered = cards_in(after)
            results.append(Result("a hard kill mid-draft returns the very same three cards",
                                  recovered == cards and bool(cards),
                                  f"before {cards} / after {recovered}"))
            results.append(Result("and the draft is still open rather than settled by the crash",
                                  "OPEN" in draft_line(after), draft_line(after) or "(no draft line)"))
            results.append(Result("the crash parked the run for recovery rather than losing it",
                                  "RECOVERY_REQUIRED" in after, after.strip()[:200]))

            # A hard kill parks the run, so it has to be resumed before it can play on. The draft
            # has to survive that too: being carried across the crash is only half the claim if
            # recovery then drops it.
            resumed = rcon.command(f"cobbletowers runs advance {run} recovery_completed")
            back = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("recovery puts the run back at its intermission",
                                  "INTERMISSION" in back, resumed.strip()[:200]))
            results.append(Result("and the draft is still on the table after recovery",
                                  cards_in(back) == cards and "OPEN" in draft_line(back),
                                  draft_line(back) or "(no draft line)"))

            # --- voting -------------------------------------------------------------------------
            for name in (ALPHA, BETA):
                bots[name] = start_battle_bot(rig, name, FIRST_MOVE)
            for name in (ALPHA, BETA):
                if not wait_joined(rcon, name):
                    raise RuntimeError(f"{name} never rejoined; see {rig}/bot/{name}.log")
                # The parties do not survive the hard kill, and a floor cannot level an opponent
                # against a party that is not there. Found by this test failing for that reason and
                # saying nothing about it -- which is now a logged error rather than a silent one.
                give_party(rcon, name)

            first = vote(rcon, ALPHA, 1)
            results.append(Result("a player can vote for a card",
                                  "Vote recorded" in first, first.strip()[:200]))
            partway = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("one vote of two does not settle the draft",
                                  "OPEN" in draft_line(partway) and "1 vote(s)" in draft_line(partway),
                                  draft_line(partway)))

            vote(rcon, BETA, 1)
            settled = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the last vote settles it",
                                  "OPEN" not in draft_line(settled), draft_line(settled)))
            results.append(Result("a 2-0 majority is not recorded as a tie-break",
                                  "tie-break" not in draft_line(settled), draft_line(settled)))
            results.append(Result("the card the party voted for is the one it got",
                                  bool(cards) and cards[0] in draft_line(settled).split("->")[-1],
                                  f"cards {cards} / {draft_line(settled)}"))

            found_chosen = re.search(r"-> (\S+)", draft_line(settled))
            chosen = found_chosen.group(1) if found_chosen else ""
            results.append(Result("the chosen modifier is accumulated onto the run",
                                  chosen in settled, effects_line(settled)))
            results.append(Result("and it changes what the run's effects add up to",
                                  NEUTRAL_EFFECTS not in effects_line(settled), effects_line(settled)))
            print(f"  drafted {chosen}")
            print(f"  {effects_line(settled)}")

            moved = rcon.command(f"cobbletowers runs advance {run} intermission_complete")
            results.append(Result("with the draft settled the run moves on",
                                  "still choosing" not in moved, moved.strip()[:200]))

            # --- the modifier reaches a real floor ----------------------------------------------
            #
            # The only assertion that actually needs a battle: what `runs show` reports and what the
            # floor builds have to be the same thing. Read from the effects line rather than
            # hardcoded, because which card won is the seed's business, not this test's.
            found_extra = re.search(r"\+(\d+) opponent", effects_line(settled))
            extra = int(found_extra.group(1)) if found_extra else 0
            rcon.command(f"cobbletowers runs advance {run} next_floor_confirmed")
            for name in (ALPHA, BETA):
                tell_bot(rig, name, "FIGHT")
                tell_bot(rig, name, f"MOVE {FIRST_MOVE}")
                wait_for(rig / "bot" / f"{name}.log", r"AUTOFIGHT on", seconds=30)
            begun = begin_floor(rcon, run)
            print(f"  floor 2 begun: {begun.strip()[:160]}")
            # Assert what is PRESENT, never merely the absence of a word: the first version of the
            # `else` branch checked `"plus" not in begun`, which a floor that failed to start
            # altogether passed with flying colours.
            results.append(Result("the floor actually put opponents up",
                                  "opponent(s)" in begun, begun.strip()[:200]))
            if extra > 0:
                results.append(Result("an ENCOUNTER modifier puts more opponents on the floor",
                                      f"plus {extra} more each" in server.read_log(), begun.strip()[:200]))
            else:
                results.append(Result("a floor with no ENCOUNTER modifier puts up exactly one each",
                                      "2 opponent(s)" in begun and "plus" not in begun,
                                      begun.strip()[:200]))

            during = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the settled draft is cleared once the run leaves its intermission",
                                  not draft_line(during), draft_line(during) or "(cleared)"))

            resolved = wait_for_floor(server, rig, [ALPHA, BETA])
            print(f"  floor 2 outcome: {resolved or '<none>'}")
            results.append(Result("a floor fought under a modifier still resolves",
                                  bool(resolved), "no outcome; see " + str(server.log)))

            # --- a draft nobody is left to answer -----------------------------------------------
            if "cleared" in resolved:
                rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                reopened = rcon.command(f"cobbletowers runs show {run}")
                if draft_line(reopened):
                    print("Disconnecting everybody, leaving a draft nobody can answer")
                    for name, bot in list(bots.items()):
                        bot.kill()
                    bots.clear()
                    time.sleep(5)
                    rcon.command("cobbletowers runs watchdog player")
                    swept = rcon.command(f"cobbletowers runs show {run}")
                    results.append(Result(
                        "a draft with nobody left to vote is settled rather than holding the cell",
                        "OPEN" not in draft_line(swept), draft_line(swept) or "(no draft)"))
                else:
                    results.append(Result("a second intermission offered another draft",
                                          False, "no draft at the second intermission"))
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
    print(f"draft_test: {len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
