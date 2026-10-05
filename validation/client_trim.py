#!/usr/bin/env python3
"""A real client wearing the season armor trims (P36b), with pictures.

Boots the rig's server with this build, starts a real Fabric client (see client_launch.py), and equips the tower armor with each
shipped season's trim in a different material. The inventory screen draws the player in that armor, so a picture of it shows whether the
trim really renders: the pattern texture, the atlas that bakes it per material, the palette recolouring. The client's own log is then
scanned for texture and atlas errors, which a missing or mis-sized trim texture would raise.

  * the client joins and the armor is equipped with each season's trim (the server accepts every pattern);
  * a picture of the inventory is saved for each season and material;
  * the client log has no trim, atlas or missing-texture error.

    python validation/client_trim.py --server-dir <rig>/testserver-181 --java <jdk21 java> --out <dir> [--jar <build>] [--gui-scale 3]

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

# (season, trim material, armor set): different sets and materials so the pictures show the colour palette really recolours it.
LOOKS = (
    (1, "minecraft:gold", "challenger"),
    (2, "minecraft:emerald", "rootvale"),
    (3, "minecraft:amethyst", "duskvale"),
    (1, "minecraft:diamond", "tideforge"),
)
SLOTS = (("head", "helmet"), ("chest", "chestplate"), ("legs", "leggings"), ("feet", "boots"))
ERROR_WORDS = ("error", "missing", "unable", "failed", "exception")


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
        # In creative the inventory key opens the item browser; survival opens the screen with the player in their armor.
        rcon_run(f"gamemode survival {PLAYER}")
        time.sleep(3)

        for number, (season, material, armor) in enumerate(LOOKS, start=1):
            for slot, piece in SLOTS:
                reply = rcon_run(f'item replace entity {PLAYER} armor.{slot} with cobbletowers:{armor}_{piece}'
                                 f'[trim={{pattern:"cobbletowers:season_{season}",material:"{material}"}}]')
                if "Replaced" not in reply:
                    results.append(Result(f"season {season} {material} {armor} {piece} is accepted", False, reply.strip()[:200]))
            time.sleep(2)
            remote.send("inventory")
            remote.send("wait 2500")
            taken = remote.shot(f"trim_{number}_season{season}_{material.split(':')[1]}_{armor}")
            results.append(Result(f"a picture of season {season}'s trim in {material.split(':')[1]} on {armor} armor is saved", taken, ""))
            remote.send("close")
            remote.send("wait 400")
        results.append(Result("the server accepted every season and material", all(r.passed for r in results), "see above"))

        remote.send("quit", wait=False)
        time.sleep(3)
    except Exception as exc:  # noqa: BLE001
        results.append(Result("client trim run", False, repr(exc)))
    finally:
        if client is not None and client.poll() is None:
            client.kill()
        print("Stopping the server")
        server.stop()

    # The client's own complaints about a trim texture or the atlas.
    problems: list[str] = []
    if log_path.exists():
        for line in log_path.read_text(encoding="utf-8", errors="replace").splitlines():
            lower = line.lower()
            if any(word in lower for word in ERROR_WORDS) and re.search(r"trim|armor_trims|season_", lower):
                problems.append(line.strip()[:240])
    results.append(Result("the client log has no trim, atlas or missing-texture complaint", not problems, "; ".join(problems[:4])))

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed; pictures in {out}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
