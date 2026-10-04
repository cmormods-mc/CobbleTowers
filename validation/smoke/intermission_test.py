#!/usr/bin/env python3
"""Proves P17's intermission against a real server: a team plays on from floor 1 to floor 2 and cashes out
by vote, with no operator command after the lobby starts.

Two real (headless) players start a run through the lobby (P16). Floors are cleared by operator event
(`runs advance ... encounter_resolved_cleared`, `rewards_banked`) rather than fought: the bot rig's battle
bots stall on some random opponent draws (a refused move, then no re-prompt), which has nothing to do with
what is under test here. Everything from the intermission on is the real player path:

  * a ready click is refused while the modifier draft is still open;
  * once both vote, the draft settles, and readying up one at a time counts up -- only the last ready
    starts the countdown;
  * floor 2 opens by itself after the countdown (the run is on floor 2 and in an encounter);
  * at the next intermission one of two voting to cash out does **not** end the run (a tie), and the
    second vote does (CASHED_OUT).

The screen is not exercised: a headless bot cannot open one. The cell rebuild at a milestone floor is
covered by `milestone_floor_test.py`, because getting to floor 5 by playing would take far too long here.

    python validation/smoke/intermission_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
from floor_encounter_test import TOWER, give_party  # noqa: E402

STAMP = int(time.time()) % 100000
A = f"TIa{STAMP}"
B = f"TIb{STAMP}"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"


def play(rcon: Rcon, name: str, command: str) -> None:
    rcon.command(f"execute as {name} run cobbletowers play {command}")


def run_line(rcon: Rcon) -> str:
    for line in rcon.command("cobbletowers runs list").splitlines():
        if re.search(UUID_RE, line):
            return line.strip()
    return ""


def wait_state(rcon: Rcon, state: str, seconds: int = 200, floor: int | None = None) -> bool:
    deadline = time.time() + seconds
    while time.time() < deadline:
        line = run_line(rcon)
        if state in line and (floor is None or f"floor {floor}" in line):
            return True
        if any(end in line for end in ("FAILED", "ABANDONED", "RECOVERY_REQUIRED")):
            return False
        time.sleep(2)
    return False


def clear_floor(rcon: Rcon, run: str) -> None:
    rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
    rcon.command(f"cobbletowers runs advance {run} rewards_banked")


def show(rcon: Rcon) -> str:
    found = re.search(UUID_RE, run_line(rcon))
    return rcon.command(f"cobbletowers runs show {found.group(1)}") if found else ""


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
            handle = open(server_dir / "logs" / f"towers-intermission-{name}.log", "w", encoding="utf-8", errors="replace")
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
            play(rcon, A, f"invite {B}")
            play(rcon, B, f"accept {A}")
            play(rcon, A, "start")

            # --- floor 1 is cleared by operator event, then the real intermission ----------------
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=40, floor=1):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)
            clear_floor(rcon, run)
            reached = wait_state(rcon, "INTERMISSION", seconds=20, floor=1)
            results.append(Result("a cleared floor 1 reaches its intermission", reached, run_line(rcon)))
            if not reached:
                raise RuntimeError("never reached the first intermission: " + run_line(rcon))

            opened = show(rcon)
            results.append(Result("the modifier draft is open at the intermission",
                                  "draft at floor 1" in opened and "OPEN" in opened, opened[-300:]))

            play(rcon, A, "ready")
            blocked = show(rcon)
            results.append(Result("a ready click is refused while the draft is open",
                                  "0/2 ready" in blocked, [l for l in blocked.splitlines() if "intermission" in l][-1:] and
                                  [l for l in blocked.splitlines() if "intermission" in l][-1]))

            play(rcon, A, "pick 1")
            play(rcon, B, "pick 1")
            # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
            play(rcon, A, "pick 1")
            play(rcon, B, "pick 1")
            settled = show(rcon)
            draft_lines = [l for l in settled.splitlines() if "draft at floor" in l]
            results.append(Result("both votes settle the draft", bool(draft_lines) and "OPEN" not in draft_lines[0],
                                  "; ".join(draft_lines)))

            play(rcon, A, "ready")
            one = show(rcon)
            results.append(Result("one ready of two does not start the countdown",
                                  "1/2 ready" in one and "counting down" not in one,
                                  [l for l in one.splitlines() if "intermission" in l][-1:] and
                                  [l for l in one.splitlines() if "intermission" in l][-1]))
            play(rcon, B, "ready")
            both = show(rcon)
            results.append(Result("everyone ready starts the countdown", "counting down" in both,
                                  [l for l in both.splitlines() if "intermission" in l][-1:] and
                                  [l for l in both.splitlines() if "intermission" in l][-1]))

            # --- floor 2 opens by itself --------------------------------------------------------
            second = wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=60, floor=2)
            results.append(Result("floor 2 opens after the countdown with no operator command", second, run_line(rcon)))

            # --- a cash-out vote needs a majority ------------------------------------------------
            if second:
                clear_floor(rcon, run)
            if second and wait_state(rcon, "INTERMISSION", seconds=20, floor=2):
                play(rcon, A, "cashout")
                tie = run_line(rcon)
                results.append(Result("one of two voting to cash out does not end the run",
                                      "INTERMISSION" in tie, tie))
                play(rcon, B, "cashout")
                time.sleep(2)
                done = run_line(rcon)
                results.append(Result("the second vote cashes out", "CASHED_OUT" in done, done))
            else:
                results.append(Result("floor 2 is cleared and reaches an intermission", False, run_line(rcon)))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("intermission run", False, repr(exc)))
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
