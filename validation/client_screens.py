#!/usr/bin/env python3
"""Pictures of the mod's client screens, from a real Minecraft client, with no person at it.

Launches a real Fabric client in production form (see client_launch.py) offline, with COBBLETOWERS_SCREENSHOTS set. The client's own
ScreenshotHarness then opens each screen with sample data, presses what a player would press, saves a PNG of the real frame buffer
and quits. The pictures are what a person would have seen: layout, colours, text fit, animation frames.

    python validation/client_screens.py --out <dir> --rig <testserver dir with the release mods> [--java <jdk21 java>]
                                        [--gui-scale 3] [--timeout 600]

The window opens on the desktop for a minute or so (sound is muted). It needs a display and a GPU, like any client. GUI scale 3 on its
1280x720 window is a 426x240 screen, the smallest a player can have, so it is the case that finds layouts that do not fit; scale 2
is 640x360. For the same screens fed by a real server (real payloads, real clicks, the world), see client_e2e.py.

What it cannot show: how a screen feels to use (timing at a real frame rate, mouse feel), or anything only a keyboard-and-mouse
session reveals.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import client_launch  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--rig", required=True, type=Path, help="a server rig whose mods folder holds the release jars of Fabric API, "
                                                               "Cobblemon and CobbleRaids")
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("SMOKE_JAVA", "java")))
    parser.add_argument("--jar", type=Path, default=None, help="the CobbleTowers jar (default: the one in build/libs)")
    parser.add_argument("--gui-scale", type=int, default=3)
    parser.add_argument("--timeout", type=int, default=600, help="seconds to wait for the client to finish")
    args = parser.parse_args()

    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.png"):
        old.unlink()
    game = out.parent / "clientrig"
    client_launch.prepare(game, args.rig.resolve(), args.jar, args.gui_scale)
    command = client_launch.command(args.java, game, [])
    env = dict(os.environ, COBBLETOWERS_SCREENSHOTS=str(out))
    print(f"Launching the client, pictures to {out}")
    process = subprocess.Popen(command, cwd=game, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                               encoding="utf-8", errors="replace")
    deadline = time.time() + args.timeout
    finished = False
    log = game / "client_output.log"
    try:
        assert process.stdout is not None
        with log.open("w", encoding="utf-8") as sink:
            for line in process.stdout:
                sink.write(line)
                if "Screenshot harness" in line or "Saved " in line or "Screenshot step" in line or "Exception" in line:
                    print(line.rstrip())
                if "Screenshot harness finished" in line:
                    finished = True
                if time.time() > deadline:
                    print("timed out waiting for the client")
                    break
        process.wait(timeout=60)
    except subprocess.TimeoutExpired:
        process.kill()
    finally:
        if process.poll() is None:
            process.kill()
    pictures = sorted(out.glob("*.png"))
    print(f"{len(pictures)} picture(s) in {out}; the client's output is in {log}")
    sys.exit(0 if finished and pictures else 1)


if __name__ == "__main__":
    main()
