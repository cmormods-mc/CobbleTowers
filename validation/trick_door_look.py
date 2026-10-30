#!/usr/bin/env python3
"""Real-client pictures of the Trick or Treat door (P57): standing open in the event room's doorway, shut, the swing, and from the chamber side.

    python validation/trick_door_look.py --server-dir <rig>/testserver-181 --java <jdk21 java> --out <dir> --jar <dev jar> [--origin 2560,0]

Needs the dev jar (forced rooms, /cobbletowers trickdoor), a display and a GPU. The rig server must be stopped.
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
            time.sleep(3)
            rcon.command("cobbletowers runs draft room TRICK_OR_TREAT")
            play(rcon, ps.VIEWER, "pick 1")   # the ordinary draft settles and the Trick or Treat room opens behind it
            time.sleep(5)
            listing = rcon.command("cobbletowers trickdoor list")
            print("trickdoor list:", listing[:200])
            found = re.search(r"(-?\d+), (-?\d+), (-?\d+) run", listing)
            if not found:
                sys.exit("no trick door was installed: " + listing)
            mx, my, mz = (int(found.group(i)) for i in (1, 2, 3))
            gx, gz = mx + 2.5, mz + 1.0   # the middle of the gap, between its two wall layers

            def stand(x: float, y: float, z: float, tx: float, ty: float, tz: float, name: str, wait: float = 2.5) -> None:
                yaw = math.degrees(math.atan2(-(tx - x), tz - z))
                pitch = -math.degrees(math.atan2(ty - (y + 1.62), math.hypot(tx - x, tz - z)))   # from the eyes, not the feet
                rcon.command(f"{ps.IN_TOWER}tp {ps.VIEWER} {x:.2f} {y:.2f} {z:.2f} {yaw:.1f} {pitch:.1f}")
                time.sleep(wait)
                photo(name)

            def burst(prefix: str, frames: int, delay: int) -> subprocess.Popen:
                return subprocess.Popen(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(HERE / "capture_burst.ps1"),
                                         "-OriginX", str(ox), "-OriginY", str(oy), "-OutDir", str(out / prefix), "-Frames", str(frames),
                                         "-DelayMs", str(delay), "-Prefix", prefix, "-ProcessId", str(client.pid)],
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

            # the pedestal gem and its hologram stand right in front of the door, so the hall views are taken off to one side
            look = (gx, my + 1.2, gz)
            stand(gx + 2.5, my, mz + 7, *look, "1_open_from_hall")
            stand(gx, my, mz + 2.7, *look, "2_open_close")
            stand(gx, my, mz - 5, *look, "3_open_from_chamber")
            print("close:", rcon.command("cobbletowers trickdoor close"))
            time.sleep(2)
            stand(gx + 2.5, my, mz + 7, *look, "4_shut_from_hall")
            stand(gx, my, mz + 2.7, *look, "5_shut_close", 3)
            stand(gx - 3, my, mz + 5, *look, "6_shut_angle")
            stand(gx, my, mz + 4.0, *look, "7_before_swing", 3)
            swing = burst("swing", 30, 40)
            time.sleep(1.2)
            print("open:", rcon.command("cobbletowers trickdoor open"))
            swing.wait(timeout=120)
            time.sleep(1.5)
            stand(gx - 2.5, my, mz + 4, *look, "8_open_angle")
            stand(gx, my, mz - 4, gx, my + 1.2, gz - 6, "9_chamber_looking_in")
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
