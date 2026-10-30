#!/usr/bin/env python3
"""Real-client pictures of the World Boss graveyard on the scratch rig (flat world, datapack structure gy:graveyard).

    python validation/graveyard_look.py --rig L:/claude-cobbleraids-work/gy-rig --java <jdk21 java> --out <dir> [--origin 2560,0]

Pastes the build at (0, 40, 0), joins a vanilla client in spectator mode and photographs the plaza, avenue, church and hill.
The rig server (port 25570, RCON 25576) must be stopped.
"""

from __future__ import annotations

import argparse
import math
import os
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "smoke"))

import pedestal_shots as ps  # noqa: E402
from rcon import Rcon  # noqa: E402

BASE_Y = 40
VIEWER = "ScreenShots"

# name, x, y (schematic), z, look-at x, y, z (schematic)
VIEWS = [
    ("1_gate_avenue", 64, 15, 119, 64, 17, 60),
    ("2_plaza_north", 64, 15, 90, 64, 22, 30),
    ("3_plaza_east", 64, 15, 90, 120, 15, 90),
    ("4_plaza_west", 64, 15, 90, 8, 15, 90),
    ("5_plaza_south", 64, 15, 90, 64, 15, 126),
    ("6_church_far", 64, 34, 126, 64, 24, 30),
    ("7_church_porch", 64, 20, 50, 64, 22, 30),
    ("8_hill_aerial", 20, 60, 120, 64, 15, 50),
    ("9_graves_close", 40, 16, 80, 52, 14, 70),
    ("10_flame_pillar", 52, 16, 96, 60, 17, 90),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rig", required=True, type=Path)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--origin", default="2560,0")
    args = parser.parse_args()
    ox, oy = (int(v) for v in args.origin.split(","))
    rig = args.rig.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)

    launch = ps.load_launcher()
    game = rig.parent / "gy-client"
    (game / "mods").mkdir(parents=True, exist_ok=True)
    for old in (game / "mods").glob("*.jar"):
        old.unlink()
    api = launch.first(rig.parent / "testserver-181" / "mods", "fabric-api-*.jar")
    (game / "mods" / api.name).write_bytes(api.read_bytes())
    (game / "options.txt").write_text(
        "guiScale:2\nonboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nfullscreen:false\n"
        "soundCategory_master:0.0\nlang:en_us\ntutorialStep:none\nrenderDistance:16\nsimulationDistance:8\nhideGui:true\n", encoding="utf-8")

    server = subprocess.Popen([str(args.java), "-Xmx3G", "-jar", "fabric-server-launch.jar", "nogui"], cwd=rig,
                              stdout=open(rig / "boot.log", "w", encoding="utf-8", errors="replace"), stderr=subprocess.STDOUT)
    client = None
    shots: list[Path] = []

    def ps1(script: str, *extra: str) -> subprocess.CompletedProcess:
        return subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(HERE / script),
                               "-OriginX", str(ox), "-OriginY", str(oy), *extra], check=False, capture_output=True, text=True)

    def photo(name: str) -> None:
        shot = out / f"{name}.png"
        done = ps1("capture_window.ps1", "-Out", str(shot), "-ProcessId", str(client.pid))
        print("shot", shot.name, shot.exists(), (done.stderr or done.stdout).strip()[:100])
        if shot.exists():
            shots.append(shot)

    try:
        import socket
        for _ in range(150):
            try:
                socket.create_connection(("127.0.0.1", 25576), timeout=2).close()
                break
            except OSError:
                time.sleep(2)
        else:
            sys.exit("the rig server never answered on RCON 25576; see " + str(rig / "boot.log"))
        time.sleep(3)
        rcon = Rcon("127.0.0.1", 25576, "gyrig")
        with rcon:
            client = subprocess.Popen(launch.command(args.java, game, ["--quickPlayMultiplayer", "127.0.0.1:25570", "--username", VIEWER], rig),
                                      cwd=game, stdout=open(game / "client_output.log", "w", encoding="utf-8", errors="replace"), stderr=subprocess.STDOUT)
            for _ in range(150):
                if VIEWER in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                sys.exit("the client never joined; see " + str(game / "logs" / "latest.log"))
            rcon.command("gamerule doDaylightCycle false")
            rcon.command("gamerule doWeatherCycle false")
            rcon.command("weather clear")
            rcon.command("forceload add 0 0 127 127")
            rcon.command(f"gamemode spectator {VIEWER}")
            rcon.command(f"tp {VIEWER} 64 {BASE_Y + 20} 126")
            time.sleep(4)
            print("place", rcon.command(f"place template gy:graveyard 0 {BASE_Y} 0"))
            time.sleep(10)

            def go(name: str, x, y, z, tx, ty, tz) -> None:
                dx, dy, dz = tx - x, ty - y, tz - z
                yaw = math.degrees(math.atan2(-dx, dz))
                pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
                rcon.command(f"tp {VIEWER} {x + 0.5:.1f} {y + BASE_Y:.1f} {z + 0.5:.1f} {yaw:.1f} {pitch:.1f}")
                time.sleep(3.5)
                photo(name)

            for label, tod in (("night", 18000), ("dusk", 13000)):
                rcon.command(f"time set {tod}")
                time.sleep(2)
                for view in VIEWS:
                    go(f"{label}_{view[0]}", *view[1:])
            log = (game / "logs" / "latest.log").read_text(encoding="utf-8", errors="replace") if (game / "logs" / "latest.log").exists() else ""
            bad = [l for l in log.splitlines() if "ERROR" in l and "data fixer" not in l]
            print("client log error lines:", len(bad))
            for line in bad[:6]:
                print("  ", line[:200])
            rcon.command("stop")
    finally:
        if client:
            client.kill()
        try:
            server.wait(timeout=40)
        except subprocess.TimeoutExpired:
            server.kill()
    print("\n".join(str(s) for s in shots))


if __name__ == "__main__":
    main()
