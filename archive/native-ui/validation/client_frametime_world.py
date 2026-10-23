#!/usr/bin/env python3
"""Hall frame time in a real world: a real client joins the rig's server, stands in the world, opens the Hall over it, and
FrameSampler records draw and whole-frame times with nothing hovered and with a card / a button hovered.

    python validation/client_frametime_world.py --server-dir <rig>/testserver-181 --java <jdk21 java> --out <dir> [--gui-scale 3] [--sodium]

`--sodium` adds Sodium from the rig's disabled-mods folder to the client only. The report is <out>/frametime.txt. Opens a window for a
couple of minutes (sound muted). Needs a display and a GPU.
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
sys.path.insert(0, str(HERE / "smoke"))

import client_launch  # noqa: E402
from client_e2e import PLAYER, Remote  # noqa: E402
from rcon import Rcon  # noqa: E402
from run_durability_test import Server, install_jar, read_password, reset_tower_world, server_port  # noqa: E402

PHASE_MS = 4000


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("SMOKE_JAVA", "java")))
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--gui-scale", type=int, default=3)
    parser.add_argument("--sodium", action="store_true")
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    (out / "frametime.txt").unlink(missing_ok=True)
    tower_jar = args.jar or client_launch.first(client_launch.ROOT / "build" / "libs", "CobbleTowers-*[!s].jar")
    install_jar(server_dir, tower_jar.resolve())
    reset_tower_world(server_dir)

    server = Server(server_dir, args.java)
    password = read_password(server_dir)
    port = server_port(server_dir)
    client: subprocess.Popen | None = None
    ok = False
    try:
        print("Booting the server")
        server.start()
        server.wait_until_ready()

        game = out.parent / ("clientrig-sodium" if args.sodium else "clientrig-frametime")
        extra = []
        if args.sodium:
            extra.append(client_launch.first(server_dir.parent / "testserver-full" / "mods-disabled-for-testing", "sodium-fabric-*.jar"))
        client_launch.prepare(game, server_dir, tower_jar, args.gui_scale, extra)
        env = dict(os.environ, COBBLETOWERS_REMOTE=str(out))
        remote = Remote(out)
        print("Launching the client" + (" with Sodium" if args.sodium else ""))
        client = subprocess.Popen(
            client_launch.command(args.java, game, ["--quickPlayMultiplayer", f"127.0.0.1:{port}"], None), cwd=game, env=env,
            stdout=open(game / "client_output.log", "w", encoding="utf-8", errors="replace"), stderr=subprocess.STDOUT)
        joined = False
        for _ in range(240):
            with Rcon("127.0.0.1", 25575, password) as rcon:
                if PLAYER in rcon.command("list"):
                    joined = True
                    break
            time.sleep(1)
        if not joined:
            raise RuntimeError("the client never joined")
        time.sleep(8)   # chunks around the player load and settle, as in play

        remote.send("uncap")
        remote.send("wait 1500")
        remote.send("sample begin world_no_screen")
        remote.send(f"wait {PHASE_MS}")
        remote.send("sample end")
        remote.send("cmd tower menu")
        remote.send("wait 2500")
        remote.shot("world_hall_open")
        for phase, hover in (("hall_idle", "none"), ("hall_hover_card", "card"), ("hall_hover_button", "button"), ("hall_idle_again", "none")):
            remote.send(f"hover {hover}")
            remote.send("wait 800")
            remote.send(f"sample begin {phase}")
            remote.send(f"wait {PHASE_MS}")
            remote.send("sample end")
        remote.send("report")
        remote.send("close")
        remote.send("quit", wait=False)
        ok = (out / "frametime.txt").exists()
    finally:
        if client is not None:
            try:
                client.wait(timeout=30)
            except subprocess.TimeoutExpired:
                client.kill()
        server.stop()
    report = out / "frametime.txt"
    print(report.read_text(encoding="utf-8") if report.exists() else "No report written.")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
