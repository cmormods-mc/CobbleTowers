#!/usr/bin/env python3
"""Real-client pictures of Grizelda, the Halloween tower's goblin merchant (vendor): the name tag, front, three-quarter views, a close-up and the back.

    python validation/goblin_look.py --server-dir <rig>/testserver-181 --java <jdk21 java> --out <dir> --jar <dev jar> [--origin 2560,0]

Needs the dev jar, a display and a GPU. The rig server must be stopped. Floor 1's intermission is where the vendor stands.
"""

from __future__ import annotations

import argparse
import math
import os
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "smoke"))

import pedestal_shots as ps  # noqa: E402
from rcon import Rcon  # noqa: E402
from run_durability_test import Server, install_jar, reset_tower_world, read_password, server_port  # noqa: E402
from floor_encounter_test import give_party  # noqa: E402
from intermission_test import play, run_line, wait_state, clear_floor, UUID_RE  # noqa: E402

SEL = "@e[type=minecraft:witch,tag=cobbletowers_vendor,limit=1]"


def nums(text: str) -> list[float]:
    return [float(v.rstrip("dDfF")) for v in re.findall(r"-?\d+(?:\.\d+)?[dDfF]?(?=[,\]])", text)]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("SMOKE_JAVA", "java")))
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--origin", default="2560,0")
    args = parser.parse_args()
    ox, oy = (int(v) for v in args.origin.split(","))

    rig = args.server_dir.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    os.environ["SMOKE_JAVA_OPTS"] = (os.environ.get("SMOKE_JAVA_OPTS", "") + " -Dcobbletowers.ignoreWindows=true").strip()
    if args.jar:
        install_jar(rig, args.jar.resolve())
    reset_tower_world(rig)

    launch = ps.load_launcher()
    game = rig.parent / "clientrig-pedestal"
    game.mkdir(exist_ok=True)
    server = Server(rig, args.java)
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
        print("Booting server")
        server.start()
        server.wait_until_ready()
        ps.prepare_client(launch, game, rig, rig / "polymer" / "resource_pack.zip")
        client = subprocess.Popen(launch.command(args.java, game, ["--quickPlayMultiplayer", f"127.0.0.1:{server_port(rig)}"], rig),
                                  cwd=game, stdout=open(game / "client_output.log", "w", encoding="utf-8", errors="replace"), stderr=subprocess.STDOUT)
        with Rcon("127.0.0.1", 25575, read_password(rig)) as rcon:
            clicked = False
            for _ in range(150):
                if ps.VIEWER in rcon.command("list"):
                    break
                latest = game / "logs" / "latest.log"
                if not clicked and latest.exists() and "Connecting to" in latest.read_text(encoding="utf-8", errors="replace"):
                    time.sleep(6)
                    before = len(shots)
                    photo("0_resource_pack_prompt")
                    if len(shots) > before:
                        ps1("click_window.ps1", "-X", str(ox + 488), "-Y", str(oy + 387))
                        clicked = True
                time.sleep(1)
            else:
                sys.exit("the client never joined; see " + str(game / "logs" / "latest.log"))
            time.sleep(8)
            rcon.command("time set noon")
            rcon.command(f"gamemode survival {ps.VIEWER}")
            give_party(rcon, ps.VIEWER)
            rcon.command(f"give {ps.VIEWER} cobbletowers:tower_key 3")
            play(rcon, ps.VIEWER, f"tower {ps.TOWER}")
            play(rcon, ps.VIEWER, "confirm")
            play(rcon, ps.VIEWER, "start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=60, floor=1):
                sys.exit("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)
            clear_floor(rcon, run)
            if not wait_state(rcon, "INTERMISSION", seconds=40, floor=1):
                sys.exit("never reached the intermission: " + run_line(rcon))
            time.sleep(6)
            pos = nums(rcon.command(f"{ps.IN_TOWER}data get entity {SEL} Pos"))
            rot = nums(rcon.command(f"{ps.IN_TOWER}data get entity {SEL} Rotation"))
            print("vendor at", pos, "rotation", rot)
            if len(pos) < 3:
                sys.exit("no goblin merchant stands in the cell")
            gx, gy, gz = pos[:3]
            yaw = rot[0] if rot else 0.0
            fx, fz = -math.sin(math.radians(yaw)), math.cos(math.radians(yaw))   # the way she faces (0 = south)
            rx, rz = -fz, fx                                                       # her right-hand side, seen from the front

            def stand(dist: float, side: float, name: str, height: float = 1.1, wait: float = 3.0, aim_y: float = 1.1) -> None:
                x, z = gx + fx * dist + rx * side, gz + fz * dist + rz * side
                ty = gy + aim_y
                yaw_to = math.degrees(math.atan2(-(gx - x), gz - z))
                pitch = -math.degrees(math.atan2(ty - (gy + 1.62), math.hypot(gx - x, gz - z)))
                rcon.command(f"{ps.IN_TOWER}tp {ps.VIEWER} {x:.2f} {gy:.2f} {z:.2f} {yaw_to:.1f} {pitch:.1f}")
                time.sleep(wait)
                photo(name)

            stand(4.5, 0, "1_front_4_5", aim_y=1.5)
            stand(3.0, 0, "2_front_3", aim_y=1.6)
            stand(2.6, -1.2, "3_three_quarter_left", aim_y=1.4)
            stand(2.6, 1.2, "4_three_quarter_right", aim_y=1.4)
            stand(1.8, 0, "5_close_nametag", aim_y=2.0)
            stand(-2.5, 0, "6_from_behind", aim_y=1.4)
            stand(6.0, 0, "7_far_front", aim_y=1.5)
            log = (game / "logs" / "latest.log").read_text(encoding="utf-8", errors="replace") if (game / "logs" / "latest.log").exists() else ""
            bad = [l for l in log.splitlines() if re.search(r"error|exception", l, re.I) and "data fixer" not in l]
            print("client log error lines:", len(bad))
            for line in bad[:6]:
                print("  ", line[:200])
    finally:
        if client:
            client.kill()
        server.stop()
    print("\n".join(str(s) for s in shots))


if __name__ == "__main__":
    main()
