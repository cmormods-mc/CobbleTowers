#!/usr/bin/env python3
"""Proves P32b's Seeded Trials against a real server.

The trial day is pinned with an operator command (a test cannot wait for tomorrow). Two players each have six level-1 Magikarp,
which satisfy every playlist a trial may use. Floors are cleared by operator event; everything else is the real player path.

  * `/tower trial` says what today's trial is, with the attempt and streak state;
  * the trial's run is limited to its floors (it ends COMPLETED on the last one), has the trial's enemy level locked, and its
    scored attempt posts to that day's board, once;
  * a second run of the same trial is practice: the same opponent (same seed), nothing posted, and the attempt cannot be retried;
  * a trial is not a cycle of the tower: mastery's cycle count and the Clears board do not move;
  * three consecutive days build the streak and pay the 3-day milestone once; a missed day lapses it;
  * the weekly trial runs ten floors and posts to its own board.

    python validation/smoke/trial_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
from intermission_test import play, UUID_RE  # noqa: E402

STAMP = int(time.time()) % 100000
A = f"TTa{STAMP}"
B = f"TTb{STAMP}"
DAY1, DAY2, DAY3, DAY_LAPSED = "2026-10-05", "2026-10-06", "2026-10-07", "2026-10-10"


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


def wait_new(rcon: Rcon, state: str, seconds: int, known: set[str]) -> str:
    deadline = time.time() + seconds
    while time.time() < deadline:
        for rid, st in runs(rcon):
            if rid not in known and st == state:
                return rid
        time.sleep(2)
    return ""


def play_trial(rcon: Rcon, name: str, kind: str, floors: int, known: set[str]) -> str:
    """Selects the trial, starts it, and clears every floor by operator event; returns the run id."""
    as_player(rcon, name, f"trial play {kind}")
    as_player(rcon, name, "start")
    run = wait_new(rcon, "ENCOUNTER_ACTIVE", 60, known)
    if not run:
        raise RuntimeError("the trial run never opened: " + rcon.command("cobbletowers runs list"))
    clear_floors(rcon, name, run, floors)
    return run


def clear_floors(rcon: Rcon, name: str, run: str, floors: int) -> None:
    for floor in range(1, floors + 1):
        rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
        if floor == floors:
            rcon.command(f"cobbletowers runs advance {run} final_floor_cleared")
            return
        rcon.command(f"cobbletowers runs advance {run} rewards_banked")
        if not wait_state(rcon, run, "INTERMISSION", 30):
            raise RuntimeError(f"floor {floor} never reached its intermission")
        play(rcon, name, "pick 1")
        # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
        play(rcon, name, "pick 1")
        play(rcon, name, "ready")
        if not wait_state(rcon, run, "ENCOUNTER_ACTIVE", 60):
            raise RuntimeError(f"floor {floor + 1} never opened")


def opponents(text: str, run: str) -> list[str]:
    """Species fought on floor 1, in log order, for a run (matched through the battle line's player name)."""
    return re.findall(r"Floor 1 battle \S+ started: \S+ vs (\S+) at level (\d+)", text)


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
            handle = open(server_dir / "logs" / f"towers-trial-{name}.log", "w", encoding="utf-8", errors="replace")
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
            rcon.command("cobbletowers masteryadmin clearboards")
            for name in (A, B):
                for _ in range(6):
                    rcon.command(f"pokegiveother {name} magikarp level=1")
                rcon.command(f"cobbletowers trialadmin reset {name}")
            rcon.command(f"cobbletowers trialadmin day {DAY1}")

            # ---- the day's trial ----------------------------------------------------------------------------
            info = as_player(rcon, A, "trial daily")
            results.append(Result("/tower trial describes today's daily trial", "Daily Trial: " in info and "(5 floors)" in info,
                                  info.strip()[:300]))
            results.append(Result("with the scored attempt unused and no streak",
                                  "not used yet" in info and "Streak: 0 day(s)" in info, info.strip()[:300]))
            level = re.search(r"enemies level (\d+)", info)
            locked = int(level.group(1)) if level else 0
            tower = re.search(r"\n\s+(\w+),", info)
            results.append(Result("and says the enemy level is locked", locked > 0, info.strip()[:300]))

            # ---- the scored attempt -----------------------------------------------------------------------------
            before = len(server.read_log())
            run1 = play_trial(rcon, A, "daily", 5, set())
            time.sleep(3)
            text = server.read_log()
            shown = rcon.command(f"cobbletowers runs show {run1}")
            results.append(Result("the run is a scored trial attempt limited to five floors",
                                  f"trial: daily:{DAY1} (scored, 5 floors" in shown, shown.strip()[:300]))
            fought = opponents(text[before:], run1)
            results.append(Result("its enemies are the locked level", bool(fought) and all(int(l) == locked for _, l in fought),
                                  f"locked {locked}, saw {fought[:3]}"))
            results.append(Result("clearing floor five completes the run (it does not continue to floor six)",
                                  wait_state(rcon, run1, "COMPLETED", 20), str(runs(rcon))))
            time.sleep(2)
            text = server.read_log()
            done = re.search(rf"Run {run1} finished trial daily:{DAY1}: 5/5 floors, score (\d+), rank 1 of 1", text)
            results.append(Result("it was judged: 5/5 floors, ranked first on today's board", done is not None,
                                  "no 'finished trial' line"))
            board = rcon.command(f"execute as {A} run cobbletowers play trial board daily")
            results.append(Result("the daily board lists the attempt with its score, in Solo",
                                  A in board.split("Team")[0] and "score " in board, board.strip()[:300]))
            mastery = rcon.command(f"execute as {A} run cobbletowers play mastery cobbletowers:{tower.group(1) if tower else 'neutral'}")
            clears = rcon.command(f"execute as {A} run cobbletowers play leaderboard clears cobbletowers:{tower.group(1) if tower else 'neutral'}")
            results.append(Result("a trial is not a cycle: no cycle counted and nothing on the Clears board",
                                  "level 0 (Unranked)" in mastery and "no entries yet" in clears, mastery.strip()[:200] + clears.strip()[:100]))
            info = as_player(rcon, A, "trial daily")
            results.append(Result("the attempt shows as used with its score, and the streak is 1",
                                  "More runs are practice" in info and "Streak: 1 day(s)" in info, info.strip()[:400]))

            # ---- practice: same seed, nothing posts ------------------------------------------------------------
            before = len(server.read_log())
            as_player(rcon, A, "trial play daily")
            as_player(rcon, A, "start")
            run2 = wait_new(rcon, "ENCOUNTER_ACTIVE", 60, {run1})
            if not run2:
                raise RuntimeError("the practice run never opened")
            time.sleep(3)
            practice_fought = opponents(server.read_log()[before:], run2)
            shown = rcon.command(f"cobbletowers runs show {run2}")
            results.append(Result("a second run is practice", "practice" in shown and "scored" not in shown.split("trial:")[1].split(")")[0],
                                  shown.strip()[:300]))
            results.append(Result("it faces the same first opponent (the seed is shared)",
                                  bool(practice_fought) and bool(fought) and practice_fought[0][0] == fought[0][0],
                                  f"{fought[:1]} vs {practice_fought[:1]}"))
            rcon.command(f"cobbletowers runs advance {run2} abandon_requested")
            time.sleep(8)
            board = rcon.command(f"execute as {A} run cobbletowers play trial board daily")
            results.append(Result("and posted nothing", len(re.findall(r"#\d", board)) == 1, board.strip()[:300]))

            # ---- three days: the streak and its milestone -------------------------------------------------------
            known = {run1, run2}
            for day in (DAY2, DAY3):
                rcon.command(f"cobbletowers trialadmin day {day}")
                trial_run = play_trial(rcon, A, "daily", 5, known)
                known.add(trial_run)
                wait_state(rcon, trial_run, "COMPLETED", 20)
                time.sleep(2)
            info = as_player(rcon, A, "trial daily")
            results.append(Result("three consecutive days make a 3-day streak", "Streak: 3 day(s)" in info, info.strip()[:400]))
            text = server.read_log()
            results.append(Result("the 3-day milestone was paid, once",
                                  len(re.findall(r"reached a 3-day trial streak and is paid 50 CobbleDollars", text)) == 1,
                                  "; ".join(l for l in text.splitlines() if "trial streak" in l)[:300]))
            rcon.command(f"cobbletowers trialadmin day {DAY_LAPSED}")
            info = as_player(rcon, A, "trial daily")
            results.append(Result("after days without a trial the streak lapses (best remembered)",
                                  "Streak: 0 day(s), best 3" in info and "lapsed" in info, info.strip()[:400]))

            # ---- the weekly trial --------------------------------------------------------------------------------
            weekly_info = as_player(rcon, B, "trial weekly")
            results.append(Result("the weekly trial is ten floors", "Weekly Trial: " in weekly_info and "(10 floors)" in weekly_info,
                                  weekly_info.strip()[:300]))
            weekly = play_trial(rcon, B, "weekly", 10, known)
            wait_state(rcon, weekly, "COMPLETED", 30)
            time.sleep(2)
            text = server.read_log()
            results.append(Result("a ten-floor clear posts to the weekly board",
                                  re.search(rf"Run {weekly} finished trial weekly:\S+: 10/10 floors", text) is not None,
                                  "no weekly 'finished trial' line"))
            board = rcon.command(f"execute as {B} run cobbletowers play trial board weekly")
            results.append(Result("and the weekly board shows it", B in board and "score " in board, board.strip()[:300]))

            bad = [l for l in server.read_log().splitlines() if "ERROR" in l and "cobbletowers" in l.lower()]
            results.append(Result("no CobbleTowers error was logged", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("trial run", False, repr(exc)))
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
