#!/usr/bin/env python3
"""Proves P46's rotating trials against a real server (dev jar; Polymer and WebGUI in mods/).

The mineflayer player stops handling packets soon after joining (Polymer's payloads) and times out 30 s later, so the whole
bot part is short: join, start the trial, reach the first intermission. The restart half needs no player.

  * `cobbletowers trialadmin roll <date>` prints the day's daily and weekly (route, house rule, mutators, weekly rule);
  * `/tower trial daily` carries the route line;
  * the trial run plays on `cobbletowers:rotating/daily-<day>` with the rolled enemy level and mutators;
  * the rotating tower stays out of `/cobbletowers definitions`;
  * after a restart mid-run the tower is rebuilt from the run (no build error, the run is still listed);
  * after the restart `/tower trial daily` still shows the same trial.

    python validation/smoke/rotating_trial_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
from trial_test import as_player, runs, wait_new  # noqa: E402

STAMP = int(time.time()) % 100000
A = f"RTa{STAMP}"


def pick_day(rcon: Rcon) -> tuple[str, str]:
    """A day whose daily is a composed one with a mixed route; returns its date and the roll text."""
    import datetime
    day = datetime.date(2026, 10, 5)
    for _ in range(40):
        text = rcon.command(f"cobbletowers trialadmin roll {day}")
        if "(featured)" not in text.split("weekly")[0] and "Route:" in text and ">" in text.split("Route:")[1].split("\n")[0][:80]:
            return str(day), text
        day += datetime.timedelta(days=1)
    raise RuntimeError("no mixed composed day in 40 days")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
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
        env = dict(os.environ, NODE_PATH=str(rig / "bot" / "node_modules"))
        handle = open(server_dir / "logs" / f"towers-rotating-{A}.log", "w", encoding="utf-8", errors="replace")
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            day, roll_text = pick_day(rcon)
            results.append(Result("trialadmin roll prints both kinds with a route",
                                  "DAILY " + day in roll_text and "WEEKLY" in roll_text and roll_text.count("Route:") >= 1, roll_text.strip()[:400]))
            level = re.search(r"level (\d+)", roll_text)
            locked = int(level.group(1)) if level else 0
            rcon.command("cobbletowers trialadmin day " + day)
            defs = rcon.command("cobbletowers definitions")
            results.append(Result("the rotating tower is not in the definitions listing", "rotating" not in defs, defs.strip()[:300]))

            bots.append(subprocess.Popen(["node", str(HERE / "joinbot.js"), A, str(server_port(server_dir))],
                                         stdout=handle, stderr=subprocess.STDOUT, env=env))
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

            info = as_player(rcon, A, "trial daily")
            results.append(Result("/tower trial daily shows the route", "Route: " in info and f"{locked}" in info, info.strip()[:400]))
            before = len(server.read_log())
            as_player(rcon, A, "trial play daily")
            as_player(rcon, A, "confirm")
            as_player(rcon, A, "start")
            run = wait_new(rcon, "ENCOUNTER_ACTIVE", 60, set())
            if not run:
                raise RuntimeError("the trial run never opened: " + rcon.command("cobbletowers runs list"))
            time.sleep(2)
            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the run plays on the rotating tower", f"rotating/daily-{day}" in shown, shown.strip()[:300]))
            fought = re.findall(r"Floor 1 battle \S+ started: \S+ vs (\S+) at level (\d+)", server.read_log()[before:])
            results.append(Result("its first opponent is the locked level", bool(fought) and all(int(l) == locked for _, l in fought),
                                  f"locked {locked}, saw {fought[:2]}"))
            rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
            rcon.command(f"cobbletowers runs advance {run} rewards_banked")
            for _ in range(20):
                if any(rid == run and st == "INTERMISSION" for rid, st in runs(rcon)):
                    break
                time.sleep(1)
            results.append(Result("floor one clears into an intermission",
                                  any(rid == run and st == "INTERMISSION" for rid, st in runs(rcon)), str(runs(rcon))))
        for bot in bots:
            bot.kill()
        bots.clear()

        # ---- restart mid-run: the rotating tower is built again from the run ------------------------------------------
        print("Restarting the server")
        server.stop()
        server = Server(server_dir, java)
        server.start()
        server.wait_until_ready()
        log = server.read_log()
        results.append(Result("after a restart no rotating tower failed to build", "Cannot build rotating tower" not in log,
                              "; ".join(l for l in log.splitlines() if "rotating" in l.lower())[:300]))
        results.append(Result("the tower was built again at boot, before anyone asked for the trial",
                              f"Built rotating tower cobbletowers:rotating/daily-{day} (5 floors)" in log,
                              "; ".join(l for l in log.splitlines() if "rotating" in l.lower())[:300]))
        results.append(Result("the run was loaded from disk", re.search(r"Loaded [1-9]\d* tower run\(s\) from disk", log) is not None,
                              "; ".join(l for l in log.splitlines() if "tower run" in l)[:300]))
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            rcon.command("cobbletowers trialadmin day " + day)
            listing = rcon.command("cobbletowers runs list")
            results.append(Result("the run is still listed", run in listing, listing.strip()[:300]))
            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("and still names the rotating tower", f"rotating/daily-{day}" in shown, shown.strip()[:300]))
            again = rcon.command(f"cobbletowers trialadmin roll {day}")
            results.append(Result("the same day rolls the same trial after the restart", again.strip()[:200] == roll_text.strip()[:200],
                                  again.strip()[:200]))
            bad = [l for l in server.read_log().splitlines() if "ERROR" in l and "cobbletowers" in l.lower()]
            results.append(Result("no CobbleTowers error was logged after the restart", not bad, "; ".join(bad)[:300]))
            rcon.command("cobbletowers trialadmin day off")
    except Exception as exc:  # noqa: BLE001
        results.append(Result("rotating trial", False, repr(exc)))
    finally:
        for bot in bots:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name.ljust(width)}  {'' if result.passed else result.detail}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
