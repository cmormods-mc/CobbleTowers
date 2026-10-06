#!/usr/bin/env python3
"""Proves season finalisation survives a crash between its steps (P36a/P36c).

Finalisation is three steps with progress saved after each: (1) write the Hall season, (2) prune the old seasonal boards, (3) mark it done
and announce. This test plays a cycle in season 1, moves the calendar into the off-season, and lets finalisation start with a test-only
seam (`-Dcobbletowers.testOnlyCrashAfterSeasonStep=N`) that HALTS the JVM right after step N is saved: no shutdown hooks, no further saves,
exactly what a real crash leaves on disk. It then boots the server again with no seam and checks that finalisation resumes after step N,
completes once, does not write the Hall season twice, and announces once. Run for a crash after step 1 and after step 2.

    python validation/smoke/season_crash_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>] [--steps 1 2]
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

TOWER = "cobbletowers:test"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
OFF_SEASON = "2026-11-16"
IN_SEASON = "2026-10-12"


def run_line(rcon: Rcon) -> str:
    """The newest run in `runs list` (RCON joins the list onto one line)."""
    text = " ".join(rcon.command("cobbletowers runs list").split())
    starts = [match.start() for match in re.finditer(UUID_RE, text)]
    return text[starts[-1]:].strip() if starts else ""


def wait_state(rcon: Rcon, state: str, floor: int, seconds: int = 60) -> bool:
    deadline = time.time() + seconds
    while time.time() < deadline:
        line = run_line(rcon)
        if state in line and f"floor {floor}" in line:
            return True
        time.sleep(1)
    return False


def run_case(step: int, server_dir: Path, java: Path, node_modules: Path, results: list[Result]) -> None:
    label = f"crash after step {step}"
    bot_name = f"TCr{step}{int(time.time()) % 10000}"
    password = read_password(server_dir)
    server = Server(server_dir, java)
    bot = None
    try:
        # ---- first boot: play a cycle, end the season, crash during finalisation --------------------------------------
        os.environ["SMOKE_JAVA_OPTS"] = f"-Dcobbletowers.testOnlyCrashAfterSeasonStep={step}"
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-seasoncrash-bot.log", "w", encoding="utf-8", errors="replace")
        bot = subprocess.Popen(["node", str(HERE / "joinbot.js"), bot_name, str(server_port(server_dir))],
                               stdout=handle, stderr=subprocess.STDOUT, env=env)
        with Rcon("127.0.0.1", 25575, password) as rcon:
            for _ in range(90):
                if bot_name in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("the bot never joined")
            clear_tower(rcon)
            rcon.command(f"pokegiveother {bot_name} glaceon level=100")
            rcon.command("cobbletowers masteryadmin clearboards")
            rcon.command("cobbletowers seasonadmin clear")
            rcon.command(f"cobbletowers trialadmin day {IN_SEASON}")

            def play(command: str) -> None:
                rcon.command(f"execute as {bot_name} run cobbletowers play {command}")

            play(f"tower {TOWER}")
            play("confirm")
            play("start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", 1, seconds=40):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)
            for floor in range(1, 5):
                rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
                rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                if not wait_state(rcon, "INTERMISSION", floor, seconds=20):
                    raise RuntimeError(f"floor {floor} never reached its intermission: {run_line(rcon)}")
                time.sleep(2)
                if floor == 4:
                    break
                for card in (1, 1, 2):
                    play(f"pick {card}")
                play("ready")
                if not wait_state(rcon, "ENCOUNTER_ACTIVE", floor + 1, seconds=40):
                    raise RuntimeError(f"floor {floor + 1} never opened: {run_line(rcon)}")
            time.sleep(3)
            rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            time.sleep(8)
            before = rcon.command("cobbletowers play hall")
            results.append(Result(f"{label}: nothing is in the Hall before the season ends", "empty" in before, before.strip()[:160]))

            rcon.command(f"cobbletowers trialadmin day {OFF_SEASON}")
            try:
                rcon.command("cobbletowers seasonadmin check")     # finalisation starts here and the JVM halts inside it
            except Exception:  # noqa: BLE001  -- the connection dies with the server
                pass
        try:
            server.process.wait(timeout=60)
        except subprocess.TimeoutExpired:
            pass
        crashed = server.process.poll() is not None
        first_log = server.read_log()
        results.append(Result(f"{label}: the server really died mid-finalisation", crashed and server.process.returncode == 137,
                              f"exit code {server.process.poll()}"))
        results.append(Result(f"{label}: and it had saved exactly the steps up to {step}",
                              f"step {step} done" in first_log and f"step {step + 1} done" not in first_log
                              and "finalised" not in first_log, "the first boot's log does not show that"))
        if bot is not None:
            bot.kill()
            bot = None

        # ---- second boot: no seam; the calendar is pinned again (a restart forgets a pin), finalisation resumes ------------
        os.environ["SMOKE_JAVA_OPTS"] = ""
        server.start()
        server.wait_until_ready()
        with Rcon("127.0.0.1", 25575, password) as rcon:
            rcon.command(f"cobbletowers trialadmin day {OFF_SEASON}")
            dry = rcon.command("cobbletowers seasonadmin finalize dry")
            results.append(Result(f"{label}: a dry run after the crash still sees the unfinished season",
                                  "Season 1" in dry and "Nothing to finalise" not in dry, dry.strip()[:200]))
            rcon.command("cobbletowers seasonadmin check")
            time.sleep(2)
            second_log = server.read_log()
            results.append(Result(f"{label}: finalisation resumes after step {step}",
                                  f"Season 1 finalisation resumes after step {step}" in second_log,
                                  "no 'resumes after step' line"))
            results.append(Result(f"{label}: the remaining steps run and the season finishes once",
                                  second_log.count("Season 1 (The Rising Tide) finalised") == 1
                                  and (step >= 2 or "step 2 done" in second_log) and (step >= 1 or "step 1 done" in second_log),
                                  "the second boot did not finish the season exactly once"))
            results.append(Result(f"{label}: a step that was already saved is not run again",
                                  f"finalisation: step {step} done" not in second_log, f"step {step} ran twice"))
            hall = rcon.command("cobbletowers play hall 1")
            results.append(Result(f"{label}: the Hall holds season 1 once, with both boards of its winner",
                                  hall.count("Hall of Fame - Season 1") == 1 and "Highest difficulty" in hall
                                  and "Fastest cycle" in hall and hall.count("Highest difficulty") == 1, hall.strip()[:300]))
            again = rcon.command("cobbletowers seasonadmin finalize dry")
            results.append(Result(f"{label}: nothing is left to finalise afterwards", "Nothing to finalise" in again, again.strip()[:160]))
            results.append(Result(f"{label}: the end of the season is announced once, after the crash",
                                  second_log.count("has ended. Its winners are in the Hall of Fame") <= 1
                                  and "has ended. Its winners" not in first_log, "an announcement was repeated"))
            results.append(Result(f"{label}: no CobbleTowers exception on the second boot",
                                  "\tat com.cobbletowers" not in second_log, "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result(f"{label}: run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        server.stop()
        os.environ["SMOKE_JAVA_OPTS"] = ""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--node-modules", type=Path, default=None)
    parser.add_argument("--steps", type=int, nargs="+", default=[1, 2])
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    node_modules = args.node_modules or (server_dir.parent / "bot" / "node_modules")
    if args.jar:
        install_jar(server_dir, args.jar.resolve())

    results: list[Result] = []
    for step in args.steps:
        reset_tower_world(server_dir)
        run_case(step, server_dir, java, node_modules, results)

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
