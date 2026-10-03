#!/usr/bin/env python3
"""Proves P23 live: a tower battle carries its queued effects into the real Showdown, and no other battle does.

  * CobbleRaids installs CobbleTowers' module as showdown/ext-cobbletowers-fx.js on boot;
  * effects queued for one player ride that player's floor battle (and boss), once, resolved to the right sides;
  * the teammate with nothing queued fights an ordinary battle: no towerFx anywhere for them;
  * the extension never errors in the server log, and the floor still resolves.

What the effects DO inside Showdown is proved by validation/showdown/tower_fx_test.js against the real simulator;
this proves the wiring around it.

    python validation/smoke/showdown_fx_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

FX = ('[{"op":"boost","side":"self","stat":"atk","stages":1},'
      '{"op":"damage","side":"self","type":"any","percent":120},'
      '{"op":"resist","side":"self","percent":90},{"op":"eval","code":"1"}]')


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
            results.append(Result("the seam queues the valid operations and drops the unknown one",
                                  "Queued 3 of 4" in queued, queued.strip()[:200]))

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
            carried = re.findall(r"Tower battle \S+ carries (\d+) effect\(s\): (.*)", log)
            results.append(Result("exactly one floor battle carried effects (the queued player's)",
                                  len(carried) == 1 and carried[0][0] == "3", str(carried)))
            if carried:
                results.append(Result("the effects were resolved to p1 (self) and p2 (foe's side untouched)",
                                      '"sides":["p1"]' in carried[0][1] and '"p2"' not in carried[0][1], carried[0][1][:200]))

            wait_for_floor(server, rig, BOTS, seconds=180, pattern=r"Floor \d+ boss \S+ started at level \d+")
            time.sleep(2)
            carried = re.findall(r"Tower battle \S+ carries (\d+) effect\(s\)", server.read_log())
            results.append(Result("the boss battle carried the queued effects too, and only that player's",
                                  len(carried) == 2, str(carried)))

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
