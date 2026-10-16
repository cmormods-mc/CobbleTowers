#!/usr/bin/env python3
"""Pictures of the armor sets worn in a REAL client, the way a stock client sees them (server-only mod).

The client has no CobbleTowers jar: Fabric API and Cobblemon, plus the resource pack Polymer generated on the server
(`polymer/resource_pack.zip`, put in the client's resourcepacks folder). A mineflayer bot wears each set (RCON `item replace`);
the client stands in front of it and the window is photographed from four sides.

    python validation/client_worn_armor.py --server-dir <rig> --java <jdk21 java> --out <dir> [--sets a,b] [--gui-scale 2]

The server must already be running with this build (it is not started or stopped here). Run `polymer generate-pack` first if the
armor art changed. The window opens on the desktop for a few minutes (sound muted); needs a display and a GPU.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "smoke"))

import client_launch  # noqa: E402
from rcon import Rcon  # noqa: E402
from run_durability_test import read_password, server_port  # noqa: E402

VIEWER = "ScreenShots"
BOT = "Mannequin"
SETS = ["recruit", "challenger", "paragon", "tidewalker", "tideforge", "leviathan",
        "sprout", "rootvale", "heartwood", "duskwanderer", "duskvale", "nightfall"]
SLOTS = (("head", "helmet"), ("chest", "chestplate"), ("legs", "leggings"), ("feet", "boots"))
# (dx, dz, yaw) of the viewer from the bot, which faces south (+z): front, side, back, other side
VIEWS = (("front", 0, 3.6, 180), ("side", 3.6, 0, 90), ("back", 0, -3.6, 0), ("side2", -3.6, 0, -90))


def wait_for_pack(pack: Path) -> None:
    """The server may still be writing the pack after a restart; a half-written zip makes the client drop it."""
    import zipfile
    for _ in range(120):
        try:
            if time.time() - pack.stat().st_mtime > 6 and zipfile.is_zipfile(pack):
                with zipfile.ZipFile(pack) as z:
                    if z.testzip() is None:
                        return
        except OSError:
            pass
        time.sleep(2)
    sys.exit("the resource pack never settled")


def prepare(game: Path, rig: Path, pack: Path, gui_scale: int) -> None:
    wait_for_pack(pack)
    shutil.rmtree(game / "mods", ignore_errors=True)
    (game / "mods").mkdir(parents=True)
    for pattern in ("fabric-api-*.jar", "Cobblemon-*.jar"):
        jar = client_launch.first(rig / "mods", pattern)
        shutil.copy2(jar, game / "mods" / jar.name)
    # the rig also runs CobblemonCards (with Accessories and owo-lib), which are not server-only
    for jar in client_launch.card_mods(rig):
        shutil.copy2(jar, game / "mods" / jar.name)
    (game / "resourcepacks").mkdir(exist_ok=True)
    shutil.copy2(pack, game / "resourcepacks" / "polymer.zip")
    (game / "options.txt").write_text(
        f"guiScale:{gui_scale}\nonboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nfullscreen:false\n"
        'soundCategory_master:0.0\nlang:en_us\ntutorialStep:none\nhideGui:true\nrenderDistance:8\n'
        'resourcePacks:["vanilla","file/polymer.zip"]\nincompatibleResourcePacks:[]\n', encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("SMOKE_JAVA", "java")))
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--sets", default=",".join(SETS))
    parser.add_argument("--gui-scale", type=int, default=2)
    args = parser.parse_args()

    rig = args.server_dir.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    pack = rig / "polymer" / "resource_pack.zip"
    if not pack.exists():
        sys.exit("no polymer/resource_pack.zip: run `polymer generate-pack` on the server first")
    password, port = read_password(rig), server_port(rig)
    node_modules = rig.parent / "bot" / "node_modules"
    game = rig.parent / "clientrig-worn"
    prepare(game, rig, pack, args.gui_scale)

    def rcon(command: str) -> str:
        with Rcon("127.0.0.1", 25575, password) as r:
            return r.command(command)

    client = bot = None
    try:
        env = dict(os.environ, NODE_PATH=str(node_modules))
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
        rcon("time set noon")
        rcon("weather clear")
        rcon(f"gamemode creative {VIEWER}")
        OW = "execute in minecraft:overworld run "
        rcon(OW + f"tp {BOT} 0 201 0")
        time.sleep(3)
        rcon(OW + "fill -8 200 -8 8 210 8 minecraft:air")
        rcon(OW + "fill -8 199 -8 8 199 8 minecraft:stone")
        rcon(OW + f"tp {BOT} 0 200 0 0 0")
        rcon(f"effect give {VIEWER} minecraft:invisibility 999 0 true")
        for who in (VIEWER, BOT):
            print(who, rcon(f"data get entity {who} Dimension")[:120], rcon(f"data get entity {who} Pos")[:120])
        # place the viewer around where the bot really stands (physics can nudge it off the teleport spot)
        found = re.findall(r"-?\d+\.\d+", rcon(f"data get entity {BOT} Pos"))
        bx, bz = (float(found[0]), float(found[2])) if len(found) >= 3 else (0.0, 0.0)
        rcon(OW + f"tp {VIEWER} {bx:.2f} 200 {bz + 3.6:.2f} 180 8")
        time.sleep(6)  # the first teleport after joining is interpolated slowly on the client
        for set_id in args.sets.split(","):
            for slot, piece in SLOTS:
                rcon(f"item replace entity {BOT} armor.{slot} with cobbletowers:{set_id}_{piece}")
            time.sleep(1)
            for name, dx, dz, yaw in VIEWS:
                rcon(OW + f"tp {VIEWER} {bx + dx:.2f} 200 {bz + dz:.2f} {yaw} 8")
                time.sleep(1.6)
                if os.environ.get("CAPTURE_DEBUG"):
                    for who in (VIEWER, BOT):
                        print("  ", who, rcon(f"data get entity {who} Pos")[-60:], rcon(f"data get entity {who} Rotation")[-40:])
                shot = out / f"{set_id}_{name}.png"
                done = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(HERE / "capture_window.ps1"), "-Out", str(shot)],
                                      check=False, capture_output=True, text=True)
                print("shot", shot.name, shot.exists(), (done.stderr or done.stdout).strip()[:300])
        log = (game / "logs" / "latest.log").read_text(encoding="utf-8", errors="replace")
        bad = [l for l in log.splitlines() if re.search(r"armor|shader|texture", l, re.I) and re.search(r"error|fail|invalid|exception", l, re.I)]
        print("client log lines about armor/shader/texture errors:", len(bad))
        for line in bad[:10]:
            print("  ", line[:200])
    finally:
        for proc in (client, bot):
            if proc:
                proc.kill()


if __name__ == "__main__":
    main()
