#!/usr/bin/env python3
"""Proves the beta tools (P48) on a live server (dev jar; Polymer and WebGUI in mods/).

  * /tower help prints the guide;
  * /tower report writes a block to logs/cobbletowers-reports.log with the player, version and text, and a second report inside the cooldown is refused;
  * a report sent inside a run carries the run's id, tower, floor and its recent transitions;
  * reportadmin list prints it;
  * run starts, floors entered and run ends reach the tuning counters (masteryadmin counters);
  * `runs abandon` ends a run an operator found stuck (state ABANDONED, counted as run_end.abandoned and run_lost_at.f01).

    python validation/smoke/report_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TRp{int(time.time()) % 100000}"
TOWER = "cobbletowers:neutral"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"


def run_line(rcon: Rcon) -> str:
    for line in rcon.command("cobbletowers runs list").splitlines():
        if re.search(UUID_RE, line):
            return line.strip()
    return ""


def wait_state(rcon: Rcon, state: str, floor: int, seconds: int = 60) -> bool:
    deadline = time.time() + seconds
    while time.time() < deadline:
        line = run_line(rcon)
        if state in line and f"floor {floor}" in line:
            return True
        time.sleep(1)
    return False


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    reports = server_dir / "logs" / "cobbletowers-reports.log"
    if reports.exists():
        reports.unlink()
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bot = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(server_dir.parent / "bot" / "node_modules"))
        handle = open(server_dir / "logs" / f"towers-report-{BOT}.log", "w", encoding="utf-8", errors="replace")
        bot = subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(server_port(server_dir))],
                               stdout=handle, stderr=subprocess.STDOUT, env=env)
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for _ in range(90):
                if BOT in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("the bot never joined")
            clear_tower(rcon)
            rcon.command(f"pokegiveother {BOT} glaceon level=100")

            def play(command: str) -> str:
                return rcon.command(f"execute as {BOT} run cobbletowers play {command}")

            helptext = play("help")
            results.append(Result("/tower help prints the guide", "How CobbleTowers works" in helptext and "The tower" in helptext, helptext.strip()[:200]))

            first = play("report the lobby looked odd")
            results.append(Result("/tower report is accepted", "logged as R" in first, first.strip()))
            second = play("report again straight away")
            results.append(Result("a second report inside the cooldown is refused", "Please wait" in second, second.strip()))
            empty = play("report")
            results.append(Result("a report with no text asks for some", "Say what happened" in empty, empty.strip()))
            text = reports.read_text(encoding="utf-8") if reports.exists() else ""
            results.append(Result("the report is in the log file with the player, version and text, and no run",
                                  BOT in text and "the lobby looked odd" in text and re.search(r" v\d+\.\d+", text) is not None and "run: none" in text,
                                  text[:300]))

            play(f"tower {TOWER}")
            play("confirm")
            play("start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", 1, seconds=40):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)
            counters = rcon.command("cobbletowers masteryadmin counters run_")
            floors = rcon.command("cobbletowers masteryadmin counters floor_")
            results.append(Result("a run start and floor 1 are counted", "run_start" in counters and "floor_entered" in floors and "f01" in floors,
                                  counters.strip()[:200] + " | " + floors.strip()[:200]))

            # the cooldown is 30 s per player: wait it out, then report from inside the run
            time.sleep(31)
            inside = play("report floor one froze")
            text = reports.read_text(encoding="utf-8")
            block = text.split("floor one froze")[-1]
            results.append(Result("a report inside a run carries the run and its recent transitions",
                                  "logged as R" in inside and f"run: {run}" in text and "tower cobbletowers:neutral floor 1" in text
                                  and "ENCOUNTER_STARTED" in block, (inside + block)[:400]))
            listed = rcon.command("cobbletowers reportadmin list 3")
            results.append(Result("reportadmin list shows the reports", "floor one froze" in listed and "the lobby looked odd" in listed, listed.strip()[:300]))

            out = rcon.command(f"cobbletowers runs abandon {run}")
            results.append(Result("runs abandon ends the stuck run", "abandoned" in out.lower(), out.strip()))
            time.sleep(4)
            state = run_line(rcon)
            results.append(Result("the run is no longer live", "ENCOUNTER_ACTIVE" not in state, state))
            counters = rcon.command("cobbletowers masteryadmin counters run_")
            results.append(Result("the end is counted as abandoned at floor 1", "abandoned" in counters and "f01" in counters, counters.strip()[:400]))
            log = server.read_log()
            results.append(Result("no CobbleTowers exception", "	at com.cobbletowers" not in log, "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("report run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
