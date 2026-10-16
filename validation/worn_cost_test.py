#!/usr/bin/env python3
"""Server cost of the 3D worn parts (P49): N bots in one area wear a set and move, parts on versus off, mean tick time compared.

    python validation/worn_cost_test.py --server-dir <rig> [--bots 30] [--set tideforge]

The server must already be running this build (it is not started or stopped here). Every bot is also a viewer of every other, so
each wearer's displays go to N-1 clients. The parts are switched with `cobbletowers armor worn reload` after rewriting the config.
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
sys.path.insert(0, str(HERE / "smoke"))

from rcon import Rcon  # noqa: E402
from run_durability_test import read_password, server_port  # noqa: E402

SLOTS = ("head", "chest", "legs", "feet")
PIECES = {"head": "helmet", "chest": "chestplate", "legs": "leggings", "feet": "boots"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--bots", type=int, default=30)
    parser.add_argument("--set", default="tideforge")
    parser.add_argument("--samples", type=int, default=20)
    args = parser.parse_args()
    rig = args.server_dir.resolve()
    password, port = read_password(rig), server_port(rig)
    env = dict(os.environ, NODE_PATH=str(rig.parent / "bot" / "node_modules"))
    config_file = rig / "config" / "cobbletowers-armor.json"

    def rcon(command: str) -> str:
        with Rcon("127.0.0.1", 25575, password) as r:
            return r.command(command)

    def mean_tick_ms() -> float:
        values = []
        for _ in range(args.samples):
            found = re.search(r"Average time per tick: ([\d.]+)ms", rcon("tick query"))
            if found:
                values.append(float(found.group(1)))
            time.sleep(2)
        return sum(values) / max(1, len(values))

    ow = "execute in minecraft:overworld run "
    names = [f"Cost{i}" for i in range(args.bots)]
    procs = []
    try:
        for i, name in enumerate(names):
            procs.append(subprocess.Popen(["node", str(HERE / "smoke" / "posebot.js"), name, str(port), ("spin", "walk", "sneak")[i % 3]],
                                          env=env, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT))
            time.sleep(0.4)
        for _ in range(120):
            if all(name in rcon("list") for name in names):
                break
            time.sleep(1)
        else:
            sys.exit("the bots never joined")
        rcon(ow + "fill -8 200 -8 8 210 8 minecraft:air")
        rcon(ow + "fill -8 199 -8 8 199 8 minecraft:stone")
        for i, name in enumerate(names):
            rcon(ow + f"tp {name} {-6 + (i % 6) * 2.4:.1f} 200 {-6 + (i // 6) * 2.4:.1f}")
            for slot in SLOTS:
                rcon(f"item replace entity {name} armor.{slot} with cobbletowers:{args.set}_{PIECES[slot]}")
        results = {}
        for label, enabled in (("on", "true"), ("off", "false"), ("on again", "true")):
            config_file.write_text('{"enabled": %s, "hide_from_owner": true, "require_pack": false, "update_ticks": 1, "smoothing_ticks": 2}' % enabled,
                                   encoding="utf-8")
            rcon("cobbletowers armor worn reload")
            time.sleep(8)
            rcon("cobbletowers armor worn reset")
            ms = mean_tick_ms()
            results[label] = ms
            print(f"parts {label}: mean tick {ms:.2f} ms;", rcon("cobbletowers armor worn"))
        delta = (results["on"] + results["on again"]) / 2 - results["off"]
        print(f"{args.bots} wearers: parts cost about {delta:.2f} ms per tick ({delta / args.bots * 1000:.0f} us per wearer)")
    finally:
        for proc in procs:
            proc.kill()
        config_file.write_text('{"enabled": true, "hide_from_owner": true, "require_pack": false, "update_ticks": 1, "smoothing_ticks": 2}', encoding="utf-8")


if __name__ == "__main__":
    main()
