#!/usr/bin/env python3
"""Moving wearers of the 3D parts (P49 spike), photographed in quick bursts from a REAL client, then a cost run with many wearers.

The bot runs posebot.js (`spin`, `sneak` or `walk`) wearing a set; the client bursts frames from the side. Afterwards `--extra N`
more wearers join, move for a while, and the server's per-tick cost for the parts is read back.

    python validation/client_worn_motion.py --server-dir <rig> --java <jdk21 java> --out <dir> [--set tideforge] [--extra 10]

Same prerequisites as client_worn_parts.py (server running this build, `require_pack` false, pack generated).
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


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("SMOKE_JAVA", "java")))
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--set", default="tideforge")
    parser.add_argument("--modes", default="spin,sneak,walk")
    parser.add_argument("--extra", type=int, default=0)
    parser.add_argument("--frames", type=int, default=10)
    parser.add_argument("--smoothing", default="", help="for mode fast: comma list of smoothing_ticks values to compare")
    args = parser.parse_args()

    rig = args.server_dir.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    pack = rig / "polymer" / "resource_pack.zip"
    password, port = read_password(rig), server_port(rig)
    game = rig.parent / "clientrig-worn"
    prepare(game, rig, pack, 2)
    env = dict(os.environ, NODE_PATH=str(rig.parent / "bot" / "node_modules"))

    def rcon(command: str) -> str:
        with Rcon("127.0.0.1", 25575, password) as r:
            return r.command(command)

    def start_bot(name: str, mode: str):
        return subprocess.Popen(["node", str(HERE / "smoke" / "posebot.js"), name, str(port), mode], env=env,
                                stdout=open(game / f"bot_{name}.log", "w"), stderr=subprocess.STDOUT)

    procs: list = []
    client = None
    try:
        modes = args.modes.split(",")
        bot = start_bot(BOT, modes[0])
        procs.append(bot)
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
        rcon(ow + f"tp {VIEWER} 4.1 200 0.5 90 8")
        time.sleep(6)

        def wear(name: str) -> None:
            for slot, piece in SLOTS:
                rcon(f"item replace entity {name} armor.{slot} with cobbletowers:{args.set}_{piece}")

        wear(BOT)
        time.sleep(2)
        for index, mode in enumerate(modes):
            if index > 0:
                bot.kill()
                time.sleep(2)
                bot = start_bot(BOT, mode)
                procs.append(bot)
                for _ in range(40):
                    if BOT in rcon("list"):
                        break
                    time.sleep(1)
                time.sleep(2)
                rcon(ow + f"tp {BOT} 0.5 200 0.5 0 0")
                wear(BOT)
                time.sleep(2)
            rcon(ow + f"tp {VIEWER} 4.1 200 0.5 90 8")
            time.sleep(1.5)
            if mode == "fast" and args.smoothing:
                config_file = rig / "config" / "cobbletowers-armor.json"
                for value in args.smoothing.split(","):
                    config_file.write_text('{"enabled": true, "hide_from_owner": true, "require_pack": false, "update_ticks": 1, "smoothing_ticks": %s}' % value, encoding="utf-8")
                    rcon("cobbletowers armor worn reload")
                    time.sleep(2.5)
                    done = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(HERE / "capture_burst.ps1"),
                                           "-OutDir", str(out), "-Frames", str(args.frames), "-DelayMs", "40", "-Prefix", f"{args.set}_fast_s{value}"],
                                          check=False, capture_output=True, text=True)
                    print("fast smoothing", value, (done.stderr or done.stdout).strip()[:100])
                continue
            done = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(HERE / "capture_burst.ps1"),
                                   "-OutDir", str(out), "-Frames", str(args.frames), "-DelayMs", "40", "-Prefix", f"{args.set}_{mode}"],
                                  check=False, capture_output=True, text=True)
            print(mode, (done.stderr or done.stdout).strip()[:200])
        print(rcon("cobbletowers armor worn"))

        if args.extra:
            rcon("cobbletowers armor worn reset")
            names = [f"Wearer{i}" for i in range(args.extra)]
            for i, name in enumerate(names):
                procs.append(start_bot(name, ("spin", "walk", "sneak")[i % 3]))
            for _ in range(90):
                if all(name in rcon("list") for name in names):
                    break
                time.sleep(1)
            for name in names:
                rcon(ow + f"tp {name} {(hash(name) % 9) - 4}.5 200 {(hash(name[::-1]) % 9) - 4}.5")
                wear(name)
            time.sleep(15)
            rcon("cobbletowers armor worn reset")
            time.sleep(30)
            print("with", args.extra + 1, "wearers:", rcon("cobbletowers armor worn"))
            print(rcon("tick query")[:300])
        log = (game / "logs" / "latest.log").read_text(encoding="utf-8", errors="replace")
        bad = [l for l in log.splitlines() if re.search(r"error|exception", l, re.I) and re.search(r"model|texture|display|worn", l, re.I)]
        print("client log lines about model/texture errors:", len(bad))
    finally:
        for proc in [client, *procs]:
            if proc:
                proc.kill()


if __name__ == "__main__":
    main()
