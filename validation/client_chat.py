#!/usr/bin/env python3
"""A real client seeing the cosmetics (P36d), with pictures.

Boots the rig's server with this build and starts a real Fabric client (see client_launch.py). The server pins the calendar inside season 1, the
player founds a club, crosses the season track with the operator's points command and so earns the three season banners and the Challenger
title, then wears the Champion title; a chat line is sent as the player, and the client's chat and inventory are photographed.

  * the client joins and the cosmetics are earned on the real server;
  * a picture of the chat line, with the title and the club tag in front of the player's name;
  * a picture of the inventory with the three season banners, as real items with their patterns;
  * the client log has no complaint about a banner or a name.

    python validation/client_chat.py --server-dir <rig>/testserver-181 --java <jdk21 java> --out <dir> [--jar <build>] [--gui-scale 3]

The window opens on the desktop for a few minutes (sound is muted). Needs a display and a GPU.
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
sys.path.insert(0, str(HERE / "smoke"))

import client_launch  # noqa: E402
from client_e2e import PLAYER, Remote  # noqa: E402
from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, install_jar, reset_tower_world, read_password, server_port,
)

ERROR_WORDS = ("error", "exception", "unable", "failed")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("SMOKE_JAVA", "java")))
    parser.add_argument("--jar", type=Path, default=None, help="the CobbleTowers jar (default: the one in build/libs)")
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--gui-scale", type=int, default=3)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.png"):
        old.unlink()
    tower_jar = args.jar or client_launch.first(client_launch.ROOT / "build" / "libs", "CobbleTowers-*[!s].jar")
    install_jar(server_dir, tower_jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, args.java)
    client: subprocess.Popen | None = None
    password = read_password(server_dir)
    port = server_port(server_dir)

    def rcon_run(command: str) -> str:
        with Rcon("127.0.0.1", 25575, password) as rcon:
            return rcon.command(command)

    game = out.parent / "clientrig"
    log_path = game / "client_output.log"
    try:
        print("Booting the server")
        server.start()
        server.wait_until_ready()
        client_launch.prepare(game, server_dir, tower_jar, args.gui_scale, [])
        env = dict(os.environ, COBBLETOWERS_REMOTE=str(out))
        remote = Remote(out)
        print("Launching the client")
        client = subprocess.Popen(client_launch.command(args.java, game, ["--quickPlayMultiplayer", f"127.0.0.1:{port}"], None),
                                  cwd=game, env=env, stdout=open(log_path, "w", encoding="utf-8", errors="replace"),
                                  stderr=subprocess.STDOUT)
        joined = False
        for _ in range(240):
            if PLAYER in rcon_run("list"):
                joined = True
                break
            time.sleep(1)
        results.append(Result("the real client joins the real server", joined, "never joined"))
        if not joined:
            raise RuntimeError("the client never joined")
        time.sleep(6)

        rcon_run(f"gamemode survival {PLAYER}")
        rcon_run("cobbletowers seasonadmin clear")
        rcon_run("cobbletowers clubs clear")
        rcon_run("cobbletowers trialadmin day 2026-10-12")
        rcon_run(f"execute as {PLAYER} run cobbletowers play club create Tidal_Crew tc")
        rcon_run(f"cobbletowers seasonadmin points {PLAYER} 2250")
        time.sleep(3)
        inventory = rcon_run(f"data get entity {PLAYER} Inventory")
        results.append(Result("the season track gave the player the three banners", inventory.count("minecraft:blue_banner") >= 3,
                              f"{inventory.count('minecraft:blue_banner')} blue banner item(s)"))
        # The first title earned is worn at once; wear the Champion one (listed first) for the picture.
        rcon_run(f"execute as {PLAYER} run cobbletowers play title 1")
        names = rcon_run(f"cobbletowers cosmeticsadmin names {PLAYER}")
        results.append(Result("the server decorates the player's name", f"Champion S1 [TC] {PLAYER}" in names, names.strip()[:200]))

        # ---- the chat line as the client shows it ------------------------------------------------------------------------
        rcon_run(f"execute as {PLAYER} run say hello from the tower")
        remote.send("wait 1500")
        results.append(Result("a picture of the decorated chat line is saved", remote.shot("chat_1_tag_in_chat"), ""))

        # ---- the inventory with the banners -----------------------------------------------------------------------------
        remote.send("inventory")
        remote.send("wait 2500")
        results.append(Result("a picture of the inventory with the season banners is saved", remote.shot("chat_2_banners_in_inventory"), ""))
        remote.send("close")
        remote.send("wait 400")

        remote.send("quit", wait=False)
        time.sleep(3)
    except Exception as exc:  # noqa: BLE001
        results.append(Result("client chat run", False, repr(exc)))
    finally:
        if client is not None and client.poll() is None:
            client.kill()
        print("Stopping the server")
        try:
            rcon_run("cobbletowers trialadmin day off")
        except Exception:  # noqa: BLE001
            pass
        server.stop()

    problems: list[str] = []
    if log_path.exists():
        for line in log_path.read_text(encoding="utf-8", errors="replace").splitlines():
            lower = line.lower()
            if any(word in lower for word in ERROR_WORDS) and re.search(r"banner|cobbletowers|custom_name|component", lower):
                problems.append(line.strip()[:240])
    results.append(Result("the client log has no banner, name or component complaint", not problems, "; ".join(problems[:4])))

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed; pictures in {out}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
