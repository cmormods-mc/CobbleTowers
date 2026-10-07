#!/usr/bin/env python3
"""Proves P19: a player can pay for a vendor service that is applied to a teammate (TDS #18).

The wire protocol and `VendorPurchaseService` always took a separate payer and target, but nothing aimed
at anyone but the buyer, so the only live check (`vendor_test.py`) bought for the same player. Two bots
start a run through the lobby (P16); the floor is cleared by operator event so the intermission, which is
where the vendor opens, is reached without fighting. Then:

  * A pays for B: A is debited, B is not, and the purchase is counted against the run;
  * a player who is not in the run cannot be bought for, and nothing is charged;
  * a teammate who has gone offline cannot be bought for, and nothing is charged.

What the service does to the target's party is `vendor_test.py`'s ground; the vendor screen's teammate
picker is not live-testable (a headless bot cannot open a screen).

    python validation/smoke/teammate_vendor_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
from vendor_test import balance_of, bought_result  # noqa: E402

STAMP = int(time.time()) % 100000
A = f"TVa{STAMP}"
B = f"TVb{STAMP}"
C = f"TVc{STAMP}"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
LIVE = ("ENCOUNTER_ACTIVE", "INTERMISSION", "RECOVERY_REQUIRED")
SERVICE = "cobbletowers:full_heal"
PRICE = 25  # full_heal.json


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


def run_id(rcon: Rcon, state: str) -> str:
    """The run in `state`. RCON joins the list onto one line, so each id is paired with the state after it."""
    listing = rcon.command("cobbletowers runs list")
    for found in re.finditer(UUID_RE + r"\s+\S+\s+(\w+)\s+floor", listing):
        if found.group(2) == state:
            return found.group(1)
    return ""


def wait_state(rcon: Rcon, state: str, seconds: int = 40) -> str:
    for _ in range(seconds * 2):
        found = run_id(rcon, state)
        if found:
            return found
        time.sleep(0.5)
    return ""


def wallet(rcon: Rcon, name: str) -> int:
    return balance_of(rcon.command(f"execute as {name} run cobbletowers runs vendor"))


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
    bots: dict[str, subprocess.Popen] = {}
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        port = server_port(server_dir)
        for name in (A, B, C):
            bots[name] = start_bot(name, port, node_modules, server_dir / "logs" / f"towers-teammate-{name}.log")

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (A, B, C):
                if not wait_online(rcon, name):
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            give_party(rcon, A)
            give_party(rcon, B)   # C stays out of the run on purpose

            rcon.command(f"execute as {A} run cobbletowers play tower {TOWER}")
            rcon.command(f"execute as {A} run cobbletowers play invite {B}")
            rcon.command(f"execute as {B} run cobbletowers play accept {A}")
            rcon.command(f"execute as {A} run cobbletowers play confirm")
            rcon.command(f"execute as {A} run cobbletowers play start")
            run = wait_state(rcon, "ENCOUNTER_ACTIVE")
            if not run:
                raise RuntimeError("the run never started: " + rcon.command("cobbletowers runs list")[:200])
            rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
            rcon.command(f"cobbletowers runs advance {run} rewards_banked")
            if not wait_state(rcon, "INTERMISSION", 20):
                raise RuntimeError("never reached the intermission: " + rcon.command("cobbletowers runs list")[:200])

            rcon.command(f"cobbletowers runs vendor credit {A} 100")
            before_a, before_b = wallet(rcon, A), wallet(rcon, B)
            results.append(Result("the payer is funded and the teammate has nothing",
                                  before_a == 100 and before_b == 0, f"A={before_a} B={before_b}"))

            # --- A pays, B receives -------------------------------------------------------------
            bought = rcon.command(f"cobbletowers runs vendor buy {run} {SERVICE} {A} {B}")
            results.append(Result("buying for a teammate succeeds", bought_result(bought) == "SUCCESS", bought.strip()[:200]))
            after_a, after_b = wallet(rcon, A), wallet(rcon, B)
            # Buying can complete the daily "shopper" contract, which pays the payer a bonus.
            bonus = sum(int(n) for n in re.findall(r"completed the contract \S+ and is paid (\d+) CobbleDollars",
                                                   server.read_log()))
            results.append(Result("the payer was debited and the teammate was not",
                                  after_a == before_a - PRICE + bonus and after_b == before_b,
                                  f"A={after_a} (expected {before_a - PRICE + bonus}) B={after_b}"))
            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the purchase is counted against the run", "full_heal" in shown and "1" in shown.split("vendor purchases:")[-1][:60],
                                  shown[-300:]))
            results.append(Result("the log names who paid for whom",
                                  "bought cobbletowers:full_heal for" in server.read_log(), "no purchase log line"))

            # --- someone outside the run -------------------------------------------------------
            funds = wallet(rcon, A)
            outsider = rcon.command(f"cobbletowers runs vendor buy {run} {SERVICE} {A} {C}")
            results.append(Result("a player who is not in the run cannot be bought for",
                                  bought_result(outsider) == "TARGET_NOT_IN_RUN", outsider.strip()[:200]))
            results.append(Result("and nothing was charged", wallet(rcon, A) == funds, f"{wallet(rcon, A)} vs {funds}"))

            # --- a teammate who has gone offline -----------------------------------------------
            bots.pop(B).kill()
            for _ in range(40):
                if B not in rcon.command("list"):
                    break
                time.sleep(0.5)
            time.sleep(2)
            # An offline player cannot be named in a command selector, so this goes through the same service
            # with the offline player's id resolved server-side: the operator command needs an online target.
            gone = rcon.command(f"cobbletowers runs vendor buy {run} {SERVICE} {A} {B}")
            results.append(Result("an offline teammate cannot be targeted (the command refuses an offline selector; the service-level TARGET_OFFLINE guard is for a modified client)",
                                  "No player was found" in gone or "TARGET_OFFLINE" in gone or "Could not" in gone, gone.strip()[:200]))
            results.append(Result("and nothing was charged for it", wallet(rcon, A) == funds, f"{wallet(rcon, A)} vs {funds}"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("teammate vendor run", False, repr(exc)))
    finally:
        for bot in bots.values():
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
