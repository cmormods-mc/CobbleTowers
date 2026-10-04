#!/usr/bin/env python3
"""Spike for P30 (over-the-cap EVs): the evs operation reaches a real floor battle and a real CobbleRaids boss battle.

An operator queues `evs` for one player (+300 EVs in every stat on the foe, +100 HP EVs on their own side). Then:

  * the floor battle's Showdown reports raising the foe (p2) and the player (p1) Pokemon, with the new max HP;
  * the boss battle's Showdown raises the boss side and the queued player's side too;
  * the floor still resolves with the scaled enemies in play;
  * the extension never errors in the server log.

    python validation/smoke/ev_scaling_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from, wait_online,
)
from floor_encounter_test import (  # noqa: E402
    BOTS, FIRST_MOVE, TOWER, begin_floor, give_party, start_battle_bot, tell_bot, wait_for, wait_for_floor,
)

FX = '[{"op":"evs","side":"foe","amount":300},{"op":"evs","side":"self","amount":100,"stat":"hp"}]'


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
        ext = server_dir / "showdown" / "ext-cobbletowers-fx.js"
        results.append(Result("CobbleRaids installed the module on boot", ext.exists(), f"missing {ext}"))
        manifest = server_dir / "showdown" / "extensions.js"
        results.append(Result("and listed it in the manifest the loader reads",
                              manifest.exists() and "cobbletowers-fx" in manifest.read_text(encoding="utf-8"), f"bad {manifest}"))
        # The Java side only ever logs what it HANDED to Showdown. These two lines come from inside the simulator's
        # own JavaScript runtime, and are the only proof the extension was actually loaded there.
        boot_log = server.read_log()
        results.append(Result("Showdown's own runtime loaded the extension (no 'Cannot load module')",
                              "Showdown extension cobbletowers-fx loaded" in boot_log and "Cannot load module" not in boot_log,
                              "; ".join(l for l in boot_log.splitlines() if "Showdown extension" in l or "Cannot load module" in l)[:300]))
        for name in BOTS:
            bots.append(start_battle_bot(rig, name, FIRST_MOVE))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in BOTS:
                if not wait_online(rcon, seconds=90) and name not in rcon.command("list"):
                    raise RuntimeError(f"{name} never joined")
            rcon.command("execute in cobbletowers:tower run forceload remove all")
            clear_tower(rcon)
            for name in BOTS:
                give_party(rcon, name)

            queued = rcon.command(f"cobbletowers runs fx {BOTS[0]} {FX}")
            results.append(Result("the seam queues both evs operations",
                                  "Queued 2 of 2" in queued, queued.strip()[:200]))

            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            for name in BOTS:
                tell_bot(rig, name, "FIGHT")
                tell_bot(rig, name, f"MOVE {FIRST_MOVE}")
            for name in BOTS:
                if not wait_for(rig / "bot" / f"{name}.log", r"AUTOFIGHT on", seconds=30):
                    raise RuntimeError(f"{name} never armed")

            begin_floor(rcon, run)
            time.sleep(3)
            log = server.read_log()
            raised = re.findall(r"\[CobbleTowers\] EVs raised: (.*)", log)
            results.append(Result("the floor battle raised the foe's and the player's Pokemon", len(raised) == 1
                                  and "p2:" in raised[0] and "p1:" in raised[0], str(raised)))
            applied = re.findall(r"\[CobbleTowers\] Applied (\d+) of (\d+) tower effect", log)
            results.append(Result("the simulator applied both operations", applied == [("2", "2")], str(applied)))
            print("  floor battle:", raised)

            wait_for_floor(server, rig, BOTS, seconds=180, pattern=r"Floor \d+ boss \S+ started at level \d+")
            time.sleep(2)
            raised = re.findall(r"\[CobbleTowers\] EVs raised: (.*)", server.read_log())
            results.append(Result("the boss battle raised the boss and the queued player's side too",
                                  len(raised) == 2 and "p3:" in raised[1] and re.search(r"p[12]:", raised[1]) is not None, str(raised)))
            print("  boss battle:", raised[1:] if raised else raised)

            resolved = wait_for_floor(server, rig, BOTS, seconds=360)
            results.append(Result("the floor still resolved with effects in play", bool(resolved),
                                  "no cleared/wiped line; see " + str(server.log)))
            final = server.read_log()
            bad = [line for line in final.splitlines()
                   if re.search(r"tower-fx|cobbletowers-fx|towerFx", line, re.I) and re.search(r"error|exception|fail", line, re.I)]
            results.append(Result("the extension never errored in the server log", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("showdown fx run", False, repr(exc)))
    finally:
        for bot in bots:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(r.name) for r in results)
    failed = sum(1 for r in results if not r.passed)
    for r in results:
        print(f"  [{'PASS' if r.passed else 'FAIL'}] {r.name:<{width}}  {r.detail if not r.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
