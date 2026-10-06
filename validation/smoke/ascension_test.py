#!/usr/bin/env python3
"""Proves P30's Ascension against a real server, on the four-floor test tower (a cycle is cheap there).

Two players run through the lobby. Floors are cleared by operator event so the test does not depend on fighting. Then:

  * before anyone has ascended, a start at Ascension 1 is refused;
  * clearing the last floor of a cycle reaches an INTERMISSION (not COMPLETED) and says the cycle is complete;
  * readying up ascends: the run is on floor 5, in Ascension 1, on tower floor 1, with one modifier forced on it;
  * floor 5's battle carries Ascension's over-the-cap EVs for the enemy and the matching boon for the player;
  * after the run ends, both players have the record, so a new lobby may start at Ascension 1, and the run it creates
    begins on floor 5 with a forced modifier already in force.

    python validation/smoke/ascension_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)
from floor_encounter_test import give_party  # noqa: E402
from intermission_test import play, run_line, wait_state, clear_floor, show, UUID_RE  # noqa: E402

TOWER = "cobbletowers:test"
STAMP = int(time.time()) % 100000
A = f"TAa{STAMP}"
B = f"TAb{STAMP}"


def say(rcon: Rcon, name: str, command: str) -> str:
    return rcon.command(f"execute as {name} run cobbletowers play {command}")


def wait_new_run(rcon: Rcon, old: str, state: str, seconds: int) -> str:
    """The id of a run other than `old` that reaches `state`, or empty."""
    deadline = time.time() + seconds
    while time.time() < deadline:
        # RCON joins the listing onto one line, so pair each id with the state that follows it.
        listing = rcon.command("cobbletowers runs list")
        for run_id, run_state in re.findall(UUID_RE + r"\s+\S+\s+([A-Z_]+)", listing):
            if run_id != old and run_state == state:
                return run_id
        time.sleep(2)
    return ""


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
    node_modules = rig / "bot" / "node_modules"
    bots: list[subprocess.Popen] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        for name in (A, B):
            handle = open(server_dir / "logs" / f"towers-ascension-{name}.log", "w", encoding="utf-8", errors="replace")
            bots.append(subprocess.Popen(["node", str(HERE / "joinbot.js"), name, str(server_port(server_dir))],
                                         stdout=handle, stderr=subprocess.STDOUT, env=env))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (A, B):
                for _ in range(90):
                    if name in rcon.command("list"):
                        break
                    time.sleep(1)
                else:
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            for name in (A, B):
                give_party(rcon, name)

            play(rcon, A, f"tower {TOWER}")
            refused = say(rcon, A, "ascension 1")
            results.append(Result("a start at Ascension 1 is refused before anyone has ascended",
                                  "Ascension 0 at most" in refused, refused.strip()[:200]))
            play(rcon, A, f"invite {B}")
            play(rcon, B, f"accept {A}")
            play(rcon, A, "confirm")
            play(rcon, A, "start")

            if not wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=40, floor=1):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)

            # --- a whole cycle: floors 1-4, each cleared by operator event ------------------------------
            for floor in range(1, 5):
                clear_floor(rcon, run)
                if not wait_state(rcon, "INTERMISSION", seconds=30, floor=floor):
                    raise RuntimeError(f"floor {floor} never reached its intermission: " + run_line(rcon))
                if floor < 4:
                    play(rcon, A, "pick 1")
                    play(rcon, B, "pick 1")
                    # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
                    play(rcon, A, "pick 1")
                    play(rcon, B, "pick 1")
                    play(rcon, A, "ready")
                    play(rcon, B, "ready")
                    if not wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=60, floor=floor + 1):
                        raise RuntimeError(f"floor {floor + 1} never opened: " + run_line(rcon))

            line = run_line(rcon)
            results.append(Result("clearing the last floor of a cycle reaches an intermission, not COMPLETED",
                                  "INTERMISSION" in line and "COMPLETED" not in line, line))
            text = server.read_log()
            results.append(Result("the cycle's end banked rewards (it is where the team may cash out)",
                                  re.search(rf"Run {run} banked its rewards through floor 4", text) is not None,
                                  "no 'banked through floor 4' line"))
            shown = show(rcon)
            results.append(Result("before ascending the run is still in the base cycle",
                                  "ascension 0 (tower floor 4 of 4)" in shown,
                                  next((l for l in shown.splitlines() if "ascension" in l), "no ascension line")))
            drafted_before = int(re.search(r"modifiers: (\d+) drafted", shown).group(1))

            # --- ascend --------------------------------------------------------------------------------
            play(rcon, A, "pick 1")
            play(rcon, B, "pick 1")
            # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
            play(rcon, A, "pick 1")
            play(rcon, B, "pick 1")
            play(rcon, A, "ready")
            play(rcon, B, "ready")
            opened = wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=60, floor=5)
            results.append(Result("readying up at the cycle's end ascends: floor 5 opens", opened, run_line(rcon)))
            time.sleep(2)
            shown = show(rcon)
            results.append(Result("the run reports Ascension 1, tower floor 1",
                                  "ascension 1 (tower floor 1 of 4)" in shown,
                                  next((l for l in shown.splitlines() if "ascension" in l), "no ascension line")))
            drafted_after = int(re.search(r"modifiers: (\d+) drafted", shown).group(1))
            text = server.read_log()
            forced = re.search(rf"Run {run} ascends to Ascension 1, forced (\S+)", text)
            results.append(Result("one modifier was forced on the run, and it is one that hardens",
                                  forced is not None and drafted_after >= drafted_before + 2,
                                  f"{forced.group(1) if forced else 'nothing forced'}; {drafted_before} -> {drafted_after}"))

            # --- end this run; both players now hold the record -----------------------------------------
            rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            time.sleep(8)
            offered = say(rcon, A, f"tower {TOWER}")
            say(rcon, A, f"invite {B}")
            play(rcon, B, f"accept {A}")
            accepted = say(rcon, A, "ascension 1")
            results.append(Result("with both records at 1 the host may choose Ascension 1",
                                  "Starting at Ascension 1" in accepted, accepted.strip()[:200]))
            too_deep = say(rcon, A, "ascension 2")
            results.append(Result("but not Ascension 2", "Ascension 1 at most" in too_deep, too_deep.strip()[:200]))
            play(rcon, A, "confirm")
            play(rcon, A, "start")
            direct = wait_new_run(rcon, run, "ENCOUNTER_ACTIVE", seconds=60)
            if not direct:
                raise RuntimeError("the direct start never opened a floor: " + rcon.command("cobbletowers runs list"))
            shown = rcon.command(f"cobbletowers runs show {direct}")
            results.append(Result("a direct start begins on floor 5 in Ascension 1",
                                  " floor 5 " in shown and "ascension 1 (tower floor 1 of 4)" in shown,
                                  next((l for l in shown.splitlines() if "ascension" in l), "no ascension line")))
            drafted = int(re.search(r"modifiers: (\d+) drafted", shown).group(1))
            results.append(Result("and starts with its forced modifier already in force", drafted == 1, f"{drafted} drafted"))

            # --- the battle carries the EVs (a fresh run: nobody is stuck in an earlier floor's battle) ------
            deadline = time.time() + 60
            raised = []
            while time.time() < deadline and not raised:
                raised = re.findall(r"\[CobbleTowers\] EVs raised: (.*)", server.read_log())
                time.sleep(2)
            results.append(Result("floor 5's battle raised the enemy by 50 EVs and the player by 25",
                                  bool(raised) and "p2:" in raised[-1] and "+50 " in raised[-1] and "p1:" in raised[-1]
                                  and "+25 " in raised[-1], str(raised[-1:])))
            print("  floor 5 battle:", raised[-1:] if raised else raised)

            bad = [l for l in server.read_log().splitlines() if "ERROR" in l and "cobbletowers" in l.lower()]
            results.append(Result("no CobbleTowers error was logged", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("ascension run", False, repr(exc)))
    finally:
        for bot in bots:
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
