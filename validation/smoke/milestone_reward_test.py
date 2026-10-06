#!/usr/bin/env python3
"""Proves P21's milestone rewards against a real server: both players of a team receive what is guaranteed.

Two players start a run through the lobby (P16), play to floor 5 through the real pick/ready/countdown path
(P17) with the floors cleared by operator event, and at floor 5 a boss milestone is earned with
`runs earn ... milestone` (a real boss fight would earn it; a test cannot fight one cheaply). Banking at the
intermission must then:

  * give **each** of the two players the guaranteed XL candy, in full, not one candy split between them;
  * credit each player's Raid Points in CobbleRaids' own balance, through its new public API;
  * skip -- not crash on -- the CobbleCards booster pack, because that mod is not installed in this rig, and say
    so once at startup as a note rather than an error;
  * never report a typo in a reward table.

    python validation/smoke/milestone_reward_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)
from floor_encounter_test import TOWER, give_party  # noqa: E402

STAMP = int(time.time()) % 100000
A = f"TMa{STAMP}"
B = f"TMb{STAMP}"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"


def start_bot(name: str, port: int, node_modules: Path, log: Path) -> subprocess.Popen:
    env = dict(os.environ, NODE_PATH=str(node_modules))
    handle = open(log, "w", encoding="utf-8", errors="replace")
    return subprocess.Popen(["node", str(HERE / "joinbot.js"), name, str(port)],
                            stdout=handle, stderr=subprocess.STDOUT, env=env)


def wait_online(rcon: Rcon, name: str, seconds: int = 90) -> bool:
    for _ in range(seconds):
        if name in rcon.command("list"):
            return True
        time.sleep(1)
    return False


def state_of(rcon: Rcon, floor: int) -> str:
    listing = rcon.command("cobbletowers runs list")
    for found in re.finditer(UUID_RE + r"\s+\S+\s+(\w+)\s+floor\s+(\d+)", listing):
        if int(found.group(3)) == floor:
            return found.group(2)
    return ""


def run_id(rcon: Rcon) -> str:
    found = re.search(UUID_RE, rcon.command("cobbletowers runs list"))
    return found.group(1) if found else ""


def wait_state(rcon: Rcon, state: str, floor: int, seconds: int = 40) -> bool:
    for _ in range(seconds * 2):
        if state_of(rcon, floor) == state:
            return True
        time.sleep(0.5)
    return False


def count_of(rcon: Rcon, player: str, item: str) -> int:
    """How many of `item` the player is carrying, summed over every stack."""
    inventory = rcon.command(f"data get entity {player} Inventory")
    total = 0
    for stack in re.finditer(r'\{[^{}]*?id: "' + re.escape(item) + r'"[^{}]*?\}', inventory):
        found = re.search(r"count: (\d+)", stack.group(0)) or re.search(r"Count: (\d+)", stack.group(0))
        total += int(found.group(1)) if found else 1
    return total


def raid_points(rcon: Rcon, player: str) -> int:
    out = rcon.command(f"execute as {player} run cobbleraids points")
    found = re.search(r"(\d+)", out)
    return int(found.group(1)) if found else -1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--node-modules", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    node_modules = args.node_modules or (server_dir.parent / "bot" / "node_modules")
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bots: list[subprocess.Popen] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        port = server_port(server_dir)
        for name in (A, B):
            bots.append(start_bot(name, port, node_modules, server_dir / "logs" / f"towers-mreward-{name}.log"))

        log = server.read_log()
        results.append(Result("startup reports no typo in any reward table",
                              "which is not a registered item" not in log, "an ERROR line from RewardCatalogCheck"))
        results.append(Result("startup notes the optional CobbleCards mod is not installed, rather than failing",
                              "which is not installed; they will be skipped" in log or "cobblemon-cards" not in log,
                              "no note about the missing optional mod"))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (A, B):
                if not wait_online(rcon, name):
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            give_party(rcon, A)
            give_party(rcon, B)
            before = {A: raid_points(rcon, A), B: raid_points(rcon, B)}

            rcon.command(f"execute as {A} run cobbletowers play tower {TOWER}")
            rcon.command(f"execute as {A} run cobbletowers play invite {B}")
            rcon.command(f"execute as {B} run cobbletowers play accept {A}")
            rcon.command(f"execute as {A} run cobbletowers play confirm")
            rcon.command(f"execute as {A} run cobbletowers play start")
            if not wait_state(rcon, "ENCOUNTER_ACTIVE", 1):
                raise RuntimeError("floor 1 never opened: " + rcon.command("cobbletowers runs list")[:200])
            run = run_id(rcon)

            # --- play to floor 5 by the real intermission path, floors cleared by operator event -------
            for floor in range(1, 5):
                rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
                rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                if not wait_state(rcon, "INTERMISSION", floor):
                    raise RuntimeError(f"floor {floor} never reached its intermission")
                # Both vote first: a ready click is refused while the draft is still open.
                for _ in range(2):   # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
                    for name in (A, B):
                        rcon.command(f"execute as {name} run cobbletowers play pick 1")
                for name in (A, B):
                    rcon.command(f"execute as {name} run cobbletowers play ready")
                if not wait_state(rcon, "ENCOUNTER_ACTIVE", floor + 1):
                    raise RuntimeError(f"floor {floor + 1} never opened")

            # --- floor 5 is a milestone: earn it, then bank --------------------------------------------
            earned_before = {n: count_of(rcon, n, "cobblemon:exp_candy_xl") for n in (A, B)}
            for kind in ("opponent", "boss", "floor", "milestone"):
                rcon.command(f"cobbletowers runs earn {run} {kind}")
            rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
            rcon.command(f"cobbletowers runs advance {run} rewards_banked")
            reached = wait_state(rcon, "INTERMISSION", 5)
            results.append(Result("the milestone floor reaches its intermission and banks", reached,
                                  rcon.command("cobbletowers runs list")[:200]))
            time.sleep(2)

            for name in (A, B):
                gained = count_of(rcon, name, "cobblemon:exp_candy_xl") - earned_before[name]
                results.append(Result(f"{name} received the guaranteed XL candy in full", gained >= 1, f"gained {gained}"))
            for name in (A, B):
                gained = raid_points(rcon, name) - before[name]
                results.append(Result(f"{name}'s Raid Points rose by at least the guaranteed 25 (CobbleRaids balance)",
                                      gained >= 25, f"{before[name]} -> {raid_points(rcon, name)}"))

            log = server.read_log()
            banked = re.search(r"banked its rewards through floor 5: (\[.*\])", log)
            results.append(Result("the banking line shows the guaranteed grants flagged per player",
                                  bool(banked) and "perPlayer=true" in banked.group(1),
                                  banked.group(1)[:300] if banked else "no banking line"))
            results.append(Result("a CobbleCards pack in the pool was skipped, not crashed on, with the mod absent",
                                  "which is not registered; skipping it" in log or "cobblemon-cards" not in (banked.group(1) if banked else ""),
                                  "expected a skip line"))
            results.append(Result("no CobbleTowers exception during any of it", "\tat com.cobbletowers" not in log,
                                  "a CobbleTowers stack frame is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("milestone reward run", False, repr(exc)))
    finally:
        for bot in bots:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  "
              f"{result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
