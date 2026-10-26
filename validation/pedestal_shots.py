#!/usr/bin/env python3
"""Pictures of the P55 modifier pedestals as a REAL client sees them (server-only mod, so the client has no CobbleTowers jar).

Boots the rig server, starts a vanilla-form Fabric client as the only participant of a Halloween run, clears floor 1 and
photographs the intermission: an overview, a gaze on the first card (action bar prompt), and the settled hologram after a vote.

    python validation/pedestal_shots.py --server-dir <rig>/testserver-181 --java <jdk21 java> --out <dir> [--jar <build>]

Needs a display (the window opens on the desktop for a few minutes, sound muted) and a GPU. The server must be stopped.
"""

from __future__ import annotations

import argparse
import importlib.util
import math
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE / "smoke"))

from rcon import Rcon  # noqa: E402
from run_durability_test import Server, install_jar, reset_tower_world, read_password, server_port  # noqa: E402
from floor_encounter_test import give_party  # noqa: E402
from intermission_test import play, run_line, wait_state, clear_floor, UUID_RE  # noqa: E402

VIEWER = "ScreenShots"   # the name client_launch gives the client
TOWER = "cobbletowers:halloween"
IN_TOWER = "execute in cobbletowers:tower run "


def load_launcher():
    """The archived launcher, pointed at this repo's main source set (the client source set is gone)."""
    spec = importlib.util.spec_from_file_location("client_launch", ROOT / "archive" / "native-ui" / "validation" / "client_launch.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.ROOT = ROOT

    def libraries() -> list[str]:
        gradle = ROOT / "validation" / "print_main_classpath.gradle"
        result = subprocess.run([str(ROOT / "gradlew.bat"), "-q", "-I", str(gradle), "printClientLibs", "--console=plain"],
                                cwd=ROOT, check=True, capture_output=True, text=True, encoding="utf-8", errors="replace")
        line = next((l for l in result.stdout.splitlines() if l.startswith("CLIENTLIBS=")), None)
        if line is None:
            raise RuntimeError("Gradle did not print the classpath: " + result.stdout[-500:] + result.stderr[-500:])
        keep = []
        for entry in line[len("CLIENTLIBS="):].split(";"):
            name = Path(entry).name
            if not entry or "loom-cache" in entry or f"{os.sep}build{os.sep}" in entry or name.startswith(module.SKIP_PREFIXES):
                continue
            keep.append(entry)
        return keep

    module.libraries = libraries
    return module


def wait_for_pack(pack: Path) -> None:
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


def prepare_client(launch, game: Path, rig: Path, pack: Path) -> None:
    wait_for_pack(pack)
    shutil.rmtree(game / "mods", ignore_errors=True)
    (game / "mods").mkdir(parents=True, exist_ok=True)
    for pattern in ("fabric-api-*.jar", "Cobblemon-*.jar", "cobblemon-cards-fabric-*.jar", "accessories-fabric-*.jar", "owo-lib-*.jar"):
        jar = launch.first(rig / "mods", pattern)
        shutil.copy2(jar, game / "mods" / jar.name)
    (game / "resourcepacks").mkdir(exist_ok=True)
    shutil.copy2(pack, game / "resourcepacks" / "polymer.zip")
    (game / "options.txt").write_text(
        "guiScale:2\nonboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nfullscreen:false\n"
        'soundCategory_master:0.0\nlang:en_us\ntutorialStep:none\nrenderDistance:10\n'
        'resourcePacks:["vanilla","file/polymer.zip"]\nincompatibleResourcePacks:[]\n', encoding="utf-8")


def nums(text: str) -> list[float]:
    return [float(x) for x in re.findall(r"(-?\d+\.?\d*)d", text)]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("SMOKE_JAVA", "java")))
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()

    rig = args.server_dir.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    os.environ["SMOKE_JAVA_OPTS"] = (os.environ.get("SMOKE_JAVA_OPTS", "") + " -Dcobbletowers.ignoreWindows=true").strip()
    if args.jar:
        install_jar(rig, args.jar.resolve())
    reset_tower_world(rig)

    launch = load_launcher()
    game = rig.parent / "clientrig-pedestal"
    game.mkdir(exist_ok=True)
    server = Server(rig, args.java)
    client = None
    shots: list[Path] = []

    def photo(name: str) -> None:
        shot = out / f"{name}.png"
        done = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(HERE / "capture_window.ps1"),
                               "-Out", str(shot), "-ProcessId", str(client.pid)], check=False, capture_output=True, text=True)
        print("shot", shot.name, shot.exists(), (done.stderr or done.stdout).strip()[:200])
        if shot.exists():
            shots.append(shot)

    try:
        print("Booting server")
        server.start()
        server.wait_until_ready()
        pack = rig / "polymer" / "resource_pack.zip"
        prepare_client(launch, game, rig, pack)
        port = server_port(rig)
        client = subprocess.Popen(launch.command(args.java, game, ["--quickPlayMultiplayer", f"127.0.0.1:{port}"], rig),
                                  cwd=game, stdout=open(game / "client_output.log", "w", encoding="utf-8", errors="replace"),
                                  stderr=subprocess.STDOUT)
        with Rcon("127.0.0.1", 25575, read_password(rig)) as rcon:
            clicked = False
            for tick in range(150):
                if VIEWER in rcon.command("list"):
                    break
                # The server's required resource pack asks the client first; "Proceed" sits left of centre under the text.
                latest = game / "logs" / "latest.log"
                if not clicked and latest.exists() and "Connecting to" in latest.read_text(encoding="utf-8", errors="replace"):
                    time.sleep(6)
                    before = len(shots)
                    photo("0_resource_pack_prompt")
                    if len(shots) > before:   # the window is up and photographed at the top-left, so the button is where it was seen
                        subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(HERE / "click_window.ps1"),
                                        "-X", "488", "-Y", "387"], check=False)
                        clicked = True
                time.sleep(1)
            else:
                photo("0_stuck_client")
                info = subprocess.run(["powershell", "-NoProfile", "-Command",
                                       f"Get-Process -Id {client.pid} | Select-Object Id,Responding,MainWindowTitle,CPU | Format-List | Out-String"],
                                      capture_output=True, text=True).stdout
                sys.exit("the client never joined (alive: %s)\n%s\nsee %s" % (client.poll() is None, info, game / "logs" / "latest.log"))
            time.sleep(8)
            rcon.command("time set noon")
            rcon.command(f"gamemode survival {VIEWER}")
            give_party(rcon, VIEWER)
            rcon.command(f"give {VIEWER} cobbletowers:tower_key 3")   # the fixed name has used up its free first run
            play(rcon, VIEWER, f"tower {TOWER}")
            play(rcon, VIEWER, "confirm")
            play(rcon, VIEWER, "start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=60, floor=1):
                sys.exit("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)
            clear_floor(rcon, run)
            if not wait_state(rcon, "INTERMISSION", seconds=30, floor=1):
                sys.exit("never reached the intermission: " + run_line(rcon))
            time.sleep(4)

            def pos(selector: str) -> list[float]:
                return nums(rcon.command(f"{IN_TOWER}data get entity {selector} Pos"))

            vendor = pos("@e[type=minecraft:villager,tag=cobbletowers_vendor,limit=1]")
            boxes = [pos(f"@e[tag=cobbletowers_pedestal_hitbox,tag=cobbletowers_pedestal_card_{i},limit=1]") for i in range(3)]
            if len(vendor) != 3 or any(len(b) != 3 for b in boxes):
                sys.exit(f"could not find the vendor/pedestals: {vendor} {boxes}")
            print("vendor", vendor, "pedestals", boxes)
            cx, cy, cz = boxes[1]
            dx, dz = cx - vendor[0], cz - vendor[2]
            norm = math.hypot(dx, dz) or 1.0
            dx, dz = dx / norm, dz / norm

            def yaw_toward(fx: float, fz: float, tx: float, tz: float) -> float:
                return math.degrees(math.atan2(-(tx - fx), tz - fz))

            def stand(x: float, z: float, tx: float, tz: float, pitch: float) -> None:
                rcon.command(f"{IN_TOWER}tp {VIEWER} {x:.2f} {cy:.2f} {z:.2f} {yaw_toward(x, z, tx, tz):.1f} {pitch}")

            time.sleep(6)  # the client interpolates the first teleport slowly
            # 1. overview: back from the row, looking at the middle pedestal
            stand(cx + dx * 7.0, cz + dz * 7.0, cx, cz, 4)
            time.sleep(3)
            photo("1_overview")
            # 2. gaze: close to the first card, crosshair on its click box
            gx, gy, gz = boxes[0]
            stand(gx + dx * 3.4, gz + dz * 3.4, gx, gz, 0)
            time.sleep(3)
            photo("2_gaze_first_card")
            # 3. a vote: the hologram settles (a solo party settles at once)
            play(rcon, VIEWER, "pick 1")
            time.sleep(3)
            stand(cx + dx * 7.0, cz + dz * 7.0, cx, cz, 4)
            time.sleep(3)
            photo("3_after_vote")
            # 4. the vendor beside them
            vx, vz = vendor[0], vendor[2]
            stand(vx + dx * 8.5 + (-dz) * 4.0, vz + dz * 8.5 + dx * 4.0, vx, vz, 8)
            time.sleep(3)
            photo("4_vendor_side")
            log = (game / "logs" / "latest.log").read_text(encoding="utf-8", errors="replace") if (game / "logs" / "latest.log").exists() else ""
            bad = [l for l in log.splitlines() if re.search(r"error|exception", l, re.I)]
            print("client log error lines:", len(bad))
            for line in bad[:8]:
                print("  ", line[:200])
    finally:
        if client:
            client.kill()
        server.stop()
    print("\n".join(str(s) for s in shots))


if __name__ == "__main__":
    main()
