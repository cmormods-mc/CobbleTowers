#!/usr/bin/env python3
"""Proves P32c's contracts against a real server.

The trial day is pinned (a test cannot wait for tomorrow) to a day whose three daily contracts include Floor Runner. A solo
player with six level-1 Magikarp clears floors 1-5 of the Neutral tower by operator event (floor 5 is a boss floor).

  * `/tower contracts` lists three daily and two weekly contracts, the same ones on the same day;
  * clearing floors moves the progress, completes Floor Runner at three floors, and pays its reward exactly once;
  * the boss floor counts for a boss contract, if the day has one;
  * one reroll per day changes only the chosen slot, and a second is refused;
  * a different day brings different contracts;
  * the event stream never raises an error.

    python validation/smoke/contract_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import datetime
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
from intermission_test import play, UUID_RE  # noqa: E402

TOWER = "cobbletowers:neutral"
STAMP = int(time.time()) % 100000
A = f"TCa{STAMP}"


def as_player(rcon: Rcon, name: str, command: str) -> str:
    return rcon.command(f"execute as {name} run cobbletowers play {command}")


def runs(rcon: Rcon) -> list[tuple[str, str]]:
    return re.findall(UUID_RE + r"\s+\S+\s+([A-Z_]+)", rcon.command("cobbletowers runs list"))


def wait_state(rcon: Rcon, run: str, state: str, seconds: int) -> bool:
    deadline = time.time() + seconds
    while time.time() < deadline:
        if any(rid == run and st == state for rid, st in runs(rcon)):
            return True
        time.sleep(1)
    return False


def contract_items(text: str) -> list[str]:
    """Each '<n>. Name - description [x/y] (DONE)' item. RCON joins the listing onto one line, so match across it."""
    return re.findall(r"\d\. .*?\[\d+/\d+\](?: DONE)?", text)


def daily_lines(text: str) -> list[str]:
    return contract_items(text.split("Weekly contracts")[0])


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
        handle = open(server_dir / "logs" / f"towers-contract-{A}.log", "w", encoding="utf-8", errors="replace")
        bots.append(subprocess.Popen(["node", str(HERE / "joinbot.js"), A, str(server_port(server_dir))],
                                     stdout=handle, stderr=subprocess.STDOUT, env=env))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for _ in range(90):
                if A in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError(f"{A} never joined")
            clear_tower(rcon)
            for _ in range(6):
                rcon.command(f"pokegiveother {A} magikarp level=1")
            rcon.command(f"cobbletowers trialadmin reset {A}")

            # ---- find a day with Floor Runner ---------------------------------------------------------------
            start = datetime.date(2026, 10, 5)
            chosen = ""
            listing = ""
            for offset in range(60):
                day = (start + datetime.timedelta(days=offset)).isoformat()
                rcon.command(f"cobbletowers trialadmin day {day}")
                listing = rcon.command(f"execute as {A} run cobbletowers play contracts")
                if "Floor Runner" in listing:
                    chosen = day
                    break
            if not chosen:
                raise RuntimeError("no day in sixty had Floor Runner: " + listing[:300])
            lines = daily_lines(listing)
            weekly = contract_items(listing.split("Weekly contracts")[1])
            results.append(Result("three daily and two weekly contracts are listed", len(lines) == 3 and len(weekly) == 2,
                                  listing.strip()[:400]))
            again = rcon.command(f"execute as {A} run cobbletowers play contracts")
            results.append(Result("the same day lists the same contracts", daily_lines(again) == lines, again.strip()[:300]))
            results.append(Result("every contract starts at zero", all("[0/" in l for l in lines), listing.strip()[:300]))

            # ---- play floors: progress, completion, one payment -----------------------------------------------
            as_player(rcon, A, f"tower {TOWER}")
            as_player(rcon, A, "confirm")
            as_player(rcon, A, "start")
            run = ""
            for _ in range(30):
                found = [rid for rid, st in runs(rcon) if st == "ENCOUNTER_ACTIVE"]
                if found:
                    run = found[0]
                    break
                time.sleep(2)
            if not run:
                raise RuntimeError("the run never opened: " + rcon.command("cobbletowers runs list"))
            for floor in range(1, 6):
                rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
                rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                if not wait_state(rcon, run, "INTERMISSION", 30):
                    raise RuntimeError(f"floor {floor} never reached its intermission")
                if floor == 1:
                    first = as_player(rcon, A, "contracts")
                    runner = next((l for l in daily_lines(first) if "Floor Runner" in l), "")
                    results.append(Result("a cleared floor moves a floor contract (1/3)", "[1/3]" in runner, runner))
                if floor == 3:
                    third = as_player(rcon, A, "contracts")
                    runner = next((l for l in daily_lines(third) if "Floor Runner" in l), "")
                    results.append(Result("Floor Runner is complete at three floors", "[3/3]" in runner and "DONE" in runner, runner))
                if floor < 5:
                    play(rcon, A, "pick 1")
                    # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
                    play(rcon, A, "pick 1")
                    play(rcon, A, "ready")
                    if not wait_state(rcon, run, "ENCOUNTER_ACTIVE", 60):
                        raise RuntimeError(f"floor {floor + 1} never opened")
            time.sleep(2)
            text = server.read_log()
            paid = re.findall(r"completed the contract cobbletowers:floor_runner and is paid 40 CobbleDollars", text)
            results.append(Result("and its reward was paid exactly once, not again by floors four and five", len(paid) == 1,
                                  f"{len(paid)} payments"))
            final = as_player(rcon, A, "contracts")
            final_lines = daily_lines(final)
            runner = next((l for l in final_lines if "Floor Runner" in l), "")
            results.append(Result("a completed contract stops counting (it stays at 3/3)", "[3/3]" in runner, runner))
            boss = next((l for l in final_lines if "Boss Breaker" in l or "Double Boss" in l), None)
            if boss:
                results.append(Result("the boss floor counted for a boss contract", "[1/" in boss or "DONE" in boss, boss))
            deep = next((l for l in final_lines if "Deep Dive" in l), None)
            if deep:
                results.append(Result("five floors complete Deep Dive", "DONE" in deep, deep))

            # ---- reroll --------------------------------------------------------------------------------------
            slots = [i for i, l in enumerate(final_lines) if "DONE" not in l]
            if slots:
                slot = slots[0] + 1
                before = final_lines
                reply = as_player(rcon, A, f"contracts reroll daily {slot}")
                after = daily_lines(as_player(rcon, A, "contracts"))
                changed = [i for i in range(3) if before[i].split(" - ")[0] != after[i].split(" - ")[0]]
                results.append(Result("a reroll changes only the chosen slot", "Rerolled" in reply and changed == [slot - 1],
                                      f"{reply.strip()[:150]} changed {changed}"))
                second = as_player(rcon, A, f"contracts reroll daily {slot}")
                results.append(Result("a second reroll the same day is refused", "already used" in second, second.strip()[:150]))
            done_slot = next((i for i, l in enumerate(final_lines) if "DONE" in l), None)
            if done_slot is not None and slots:
                refused = as_player(rcon, A, f"contracts reroll daily {done_slot + 1}")
                results.append(Result("(and a finished contract could not be rerolled anyway)",
                                      "already" in refused, refused.strip()[:150]))

            # ---- another day --------------------------------------------------------------------------------
            other = (datetime.date.fromisoformat(chosen) + datetime.timedelta(days=1)).isoformat()
            rcon.command(f"cobbletowers trialadmin day {other}")
            tomorrow = daily_lines(as_player(rcon, A, "contracts"))
            results.append(Result("the next day brings different contracts, all at zero",
                                  tomorrow != lines and all("[0/" in l for l in tomorrow), str(tomorrow)[:300]))

            # ---- the run report and the summary switch -------------------------------------------------------
            rcon.command(f"cobbletowers trialadmin day {chosen}")
            play(rcon, A, "cashout")
            time.sleep(4)
            text = server.read_log()
            report_line = re.search(rf"Run report for {run}: (.*)", text)
            results.append(Result("a finished run produces a report with its floors", report_line is not None
                                  and "5 floors" in report_line.group(1) and "Neutral Tower" in report_line.group(1),
                                  report_line.group(0) if report_line else "no 'Run report' line"))
            report = as_player(rcon, A, "report")
            results.append(Result("/tower report shows it again", "Run report: Neutral Tower" in report and "cashed out" in report,
                                  report.strip()[:300]))
            off = as_player(rcon, A, "summary off")
            on = as_player(rcon, A, "summary on")
            results.append(Result("the login summary can be switched off and on", "off" in off and "on" in on, off.strip()[:100] + on.strip()[:100]))

            bad = [l for l in server.read_log().splitlines() if "ERROR" in l and "cobbletowers" in l.lower()]
            results.append(Result("no CobbleTowers error was logged", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("contract run", False, repr(exc)))
    finally:
        try:
            with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
                rcon.command("cobbletowers trialadmin day off")
        except Exception:  # noqa: BLE001
            pass
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
