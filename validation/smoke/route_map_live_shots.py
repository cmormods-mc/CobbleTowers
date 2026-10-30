#!/usr/bin/env python3
"""Walks a real Hollow's Eve run on a live server and saves, at each floor, the exact JSON the intermission page would receive
(dev command `cobbletowers web dump intermission`). `web/tools/shoot-live.mjs` then draws those files with the real page, so a
screenshot shows what the server actually sends for a generated map. Needs the DEV jar and the event tower open:
SMOKE_JAVA_OPTS gets -Dcobbletowers.ignoreWindows=true.

    python validation/smoke/route_map_live_shots.py --server-dir <rig>/testserver-181 --java <jdk21 java> --jar <dev jar> --out <dir>
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    RCON_PORT, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from, server_port,
)
from draft_test import to_intermission  # noqa: E402
import route_test  # noqa: E402
from route_test import draft_open, route_of, show, steps_of, vote  # noqa: E402

BOT = f"TRl{int(time.time()) % 100000}"
route_test.BOT = BOT
TOWER = "cobbletowers:halloween"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--seed-runs", type=int, default=1, help="how many separate runs to capture (different seeds, different maps)")
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    node_modules = server_dir.parent / "bot" / "node_modules"
    if "ignoreWindows" not in os.environ.get("SMOKE_JAVA_OPTS", ""):
        os.environ["SMOKE_JAVA_OPTS"] = (os.environ.get("SMOKE_JAVA_OPTS", "") + " -Dcobbletowers.ignoreWindows=true").strip()
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)
    args.out.mkdir(parents=True, exist_ok=True)
    shutil.rmtree(server_dir / "payload-dumps", ignore_errors=True)

    server = Server(server_dir, java)
    bot = None
    saved: list[str] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-liveshots-bot.log", "w", encoding="utf-8", errors="replace")
        bot = subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(server_port(server_dir))], stdout=handle, stderr=subprocess.STDOUT, env=env)

        with Rcon("127.0.0.1", RCON_PORT, read_password(server_dir)) as rcon:
            for _ in range(90):
                if BOT in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("bot never joined")

            def dump(label: str) -> None:
                reply = rcon.command(f"cobbletowers web dump intermission {BOT}")
                found = sorted((server_dir / "payload-dumps").glob("intermission-*.json"), key=lambda f: f.stat().st_mtime)
                if not found:
                    raise RuntimeError("no dump written: " + reply)
                target = args.out / f"{label}.json"
                shutil.copyfile(found[-1], target)
                saved.append(target.name)
                print("saved", target.name, "<-", reply.strip()[:80])

            for attempt in range(args.seed_runs):
                clear_tower(rcon)
                run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
                rcon.command(f"cobbletowers runs advance {run} party_submitted")
                rcon.command(f"cobbletowers runs advance {run} party_validated")
                rcon.command(f"cobbletowers runs allocate {run}")
                for floor in (1, 2, 3, 4):
                    to_intermission(rcon, run)
                    time.sleep(1.2)
                    stops = route_of(show(rcon, run))
                    dump(f"run{attempt + 1}_floor{floor}_open")
                    card = (floor % len(stops)) + 1 if stops else 1
                    vote(rcon, card)
                    time.sleep(1.2)
                    if floor == 3:
                        dump(f"run{attempt + 1}_floor{floor}_settled")
                    after = show(rcon, run)
                    print(f"run {attempt + 1} floor {floor}: offered {stops}, took card {card}, route {steps_of(after)!r}")
                    if draft_open(after) and stops and stops[card - 1][1] != "BATTLE":
                        vote(rcon, 1)
                        time.sleep(1.0)
                    rcon.command(f"cobbletowers runs advance {run} intermission_complete")
                    rcon.command(f"cobbletowers runs advance {run} next_floor_confirmed")
    finally:
        if bot:
            bot.kill()
        print("Stopping server")
        server.stop()
    print(f"{len(saved)} payload(s) saved in {args.out}")
    sys.exit(0 if saved else 1)


if __name__ == "__main__":
    main()
