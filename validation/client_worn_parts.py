#!/usr/bin/env python3
"""Pictures of the 3D worn parts (P49 spike) in a REAL client: a bot wears a set, turns and looks about, the client photographs it.

Same rig as client_worn_armor.py (stock-style client with the Polymer pack, no CobbleTowers jar); the server must already run this
build with the parts enabled and `require_pack` false (the pack is installed by hand, so Polymer never sees it as delivered).

    python validation/client_worn_parts.py --server-dir <rig> --java <jdk21 java> --out <dir> [--set tideforge]
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
from client_worn_armor import BOT, SLOTS, VIEWER, prepare  # noqa: E402
from rcon import Rcon  # noqa: E402
from run_durability_test import read_password, server_port  # noqa: E402

# (label, bot yaw, bot pitch, [(view name, dx, dz, viewer yaw)]); the bot starts facing south (+z)
SHOTS = (
    ("rest", 0, 0, [("front", 0, 3.6, 180), ("side", 3.6, 0, 90), ("back", 0, -3.6, 0)]),
    ("west", 90, 0, [("front", -3.6, 0, -90), ("back", 3.6, 0, 90)]),
    ("down", 0, 40, [("side", 3.6, 0, 90)]),
    ("up", 0, -40, [("side", 3.6, 0, 90)]),
)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("SMOKE_JAVA", "java")))
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--set", default="tideforge")
    parser.add_argument("--lifecycle", action="store_true", help="also change dimension and die, checking the parts come back")
    parser.add_argument("--gui-scale", type=int, default=2)
    args = parser.parse_args()

    rig = args.server_dir.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    pack = rig / "polymer" / "resource_pack.zip"
    if not pack.exists():
        sys.exit("no polymer/resource_pack.zip: run `polymer generate-pack` on the server first")
    password, port = read_password(rig), server_port(rig)
    game = rig.parent / "clientrig-worn"
    prepare(game, rig, pack, args.gui_scale)

    def rcon(command: str) -> str:
        with Rcon("127.0.0.1", 25575, password) as r:
            return r.command(command)

    client = bot = None
    try:
        env = dict(os.environ, NODE_PATH=str(rig.parent / "bot" / "node_modules"))
        bot = subprocess.Popen(["node", str(HERE / "smoke" / "joinbot.js"), BOT, str(port)], env=env,
                               stdout=open(game / "bot_output.log", "w"), stderr=subprocess.STDOUT)
        client = subprocess.Popen(client_launch.command(args.java, game, ["--quickPlayMultiplayer", f"127.0.0.1:{port}"], rig),
                                  cwd=game, stdout=open(game / "client_output.log", "w", encoding="utf-8", errors="replace"),
                                  stderr=subprocess.STDOUT)
        for _ in range(240):
            online = rcon("list")
            if VIEWER in online and BOT in online:
                break
            time.sleep(1)
        else:
            sys.exit("the client or the bot never joined")
        time.sleep(8)
        ow = "execute in minecraft:overworld run "
        rcon("time set noon")
        rcon("weather clear")
        rcon(f"gamemode creative {VIEWER}")
        rcon(ow + f"tp {BOT} 0 201 0")
        time.sleep(3)
        rcon(ow + "fill -8 200 -8 8 210 8 minecraft:air")
        rcon(ow + "fill -8 199 -8 8 199 8 minecraft:stone")
        rcon(ow + f"tp {BOT} 0 200 0 0 0")
        rcon(f"effect give {VIEWER} minecraft:invisibility 999 0 true")
        found = re.findall(r"-?\d+\.\d+", rcon(f"data get entity {BOT} Pos"))
        bx, bz = (float(found[0]), float(found[2])) if len(found) >= 3 else (0.0, 0.0)
        rcon(ow + f"tp {VIEWER} {bx:.2f} 200 {bz + 3.6:.2f} 180 8")
        time.sleep(6)
        for slot, piece in SLOTS:
            rcon(f"item replace entity {BOT} armor.{slot} with cobbletowers:{args.set}_{piece}")
        time.sleep(2)  # the service scans every half second
        print(rcon("cobbletowers armor worn"))
        for label, yaw, pitch, views in SHOTS:
            rcon(ow + f"tp {BOT} {bx:.2f} 200 {bz:.2f} {yaw} {pitch}")
            time.sleep(2)
            for name, dx, dz, vyaw in views:
                rcon(ow + f"tp {VIEWER} {bx + dx:.2f} 200 {bz + dz:.2f} {vyaw} 8")
                time.sleep(1.6)
                shot = out / f"{args.set}_{label}_{name}.png"
                done = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(HERE / "capture_window.ps1"), "-Out", str(shot)],
                                      check=False, capture_output=True, text=True)
                print("shot", shot.name, shot.exists(), (done.stderr or done.stdout).strip()[:200])
        print(rcon("cobbletowers armor worn"))
        if args.lifecycle:
            def snap(label: str) -> None:
                rcon(ow + f"tp {VIEWER} {bx:.2f} 200 {bz + 3.6:.2f} 180 8")
                time.sleep(2)
                shot = out / f"{args.set}_{label}.png"
                subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(HERE / "capture_window.ps1"), "-Out", str(shot)],
                               check=False, capture_output=True, text=True)
                print("shot", shot.name, shot.exists(), "|", rcon("cobbletowers armor worn"))

            rcon(f"execute in minecraft:the_nether run tp {BOT} 0 100 0")
            time.sleep(3)
            print("in the nether:", rcon("cobbletowers armor worn"))
            rcon(ow + f"tp {BOT} {bx:.2f} 200 {bz:.2f} 0 0")
            time.sleep(3)
            snap("after_dimension")
            rcon(f"kill {BOT}")
            time.sleep(3)
            print("dead:", rcon("cobbletowers armor worn"), "|", rcon("list"))
            time.sleep(3)
            rcon(ow + f"tp {BOT} {bx:.2f} 200 {bz:.2f} 0 0")
            for slot, piece in SLOTS:
                rcon(f"item replace entity {BOT} armor.{slot} with cobbletowers:{args.set}_{piece}")
            time.sleep(2)
            snap("after_death")
        log = (game / "logs" / "latest.log").read_text(encoding="utf-8", errors="replace")
        bad = [l for l in log.splitlines() if re.search(r"error|exception|warn", l, re.I) and re.search(r"model|texture|display|worn", l, re.I)]
        print("client log lines about model/texture problems:", len(bad))
        for line in bad[:10]:
            print("  ", line[:220])
    finally:
        for proc in (client, bot):
            if proc:
                proc.kill()


if __name__ == "__main__":
    main()
