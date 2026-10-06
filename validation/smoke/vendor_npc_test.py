#!/usr/bin/env python3
"""Proves P28's physical vendor against a real server: a villager stands in the cell for an intermission,
a run participant's right-click is served, an outsider's is refused, and it is gone when the floor opens.

Two players run a solo-team through the lobby (P16); floor 1 is cleared by operator event so the test does not
depend on fighting. A third player (creative, so the exit sweep leaves them alone) is not in any run. Right-clicks
are real (`vendorbot.js` calls activateEntity); what the server did with each is read from its log, because a
headless bot cannot open the screen the click leads to.

  * no vendor exists before the intermission, exactly one after arriving at it, and it is a no-AI invulnerable
    villager tagged with the run;
  * a participant's click is allowed, an outsider's is refused;
  * the vendor is gone once the next floor opens, and once the run cashes out;
  * no cell was left unfit for reuse (a leftover mob would quarantine it).

    python validation/smoke/vendor_npc_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
from intermission_test import play, run_line, wait_state, clear_floor, UUID_RE  # noqa: E402

STAMP = int(time.time()) % 100000
A = f"TVa{STAMP}"
B = f"TVb{STAMP}"
C = f"TVc{STAMP}"
VENDOR = "@e[type=minecraft:villager,tag=cobbletowers_vendor]"
IN_TOWER = "execute in cobbletowers:tower"


def vendor_count(rcon: Rcon) -> int:
    reply = rcon.command(f"{IN_TOWER} if entity {VENDOR}")
    found = re.search(r"count:\s*(\d+)", reply)
    return int(found.group(1)) if found else 0


def wait_vendors(rcon: Rcon, want: int, seconds: int = 15) -> int:
    """The vendor count once it settles at `want`, or whatever it is when the time runs out (a loaded server lags)."""
    deadline = time.time() + seconds
    count = vendor_count(rcon)
    while count != want and time.time() < deadline:
        time.sleep(1)
        count = vendor_count(rcon)
    return count


def vendor_data(rcon: Rcon, path: str) -> str:
    return rcon.command(f"{IN_TOWER} run data get entity @e[type=minecraft:villager,tag=cobbletowers_vendor,limit=1] {path}")


def stand_next_to_vendor(rcon: Rcon, name: str) -> None:
    rcon.command(f"{IN_TOWER} as @e[type=minecraft:villager,tag=cobbletowers_vendor,limit=1] at @s run tp {name} ~ ~ ~1.5")


def click(bot: subprocess.Popen) -> None:
    bot.stdin.write("use\n")
    bot.stdin.flush()


def log_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    rig = server_dir.parent
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    node_modules = rig / "bot" / "node_modules"
    bots: dict[str, subprocess.Popen] = {}
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        for name in (A, B, C):
            handle = open(server_dir / "logs" / f"towers-vendor-{name}.log", "w", encoding="utf-8", errors="replace")
            bots[name] = subprocess.Popen(["node", str(HERE / "vendorbot.js"), name, str(server_port(server_dir))],
                                          stdin=subprocess.PIPE, stdout=handle, stderr=subprocess.STDOUT,
                                          env=env, text=True)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (A, B, C):
                for _ in range(90):
                    if name in rcon.command("list"):
                        break
                    time.sleep(1)
                else:
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            rcon.command(f"op {C}")
            rcon.command(f"gamemode creative {C}")
            for name in (A, B):
                give_party(rcon, name)

            play(rcon, A, f"tower {TOWER}")
            play(rcon, A, f"invite {B}")
            play(rcon, B, f"accept {A}")
            play(rcon, A, "confirm")
            play(rcon, A, "start")

            if not wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=40, floor=1):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)
            results.append(Result("no vendor stands during a floor", vendor_count(rcon) == 0,
                                  str(vendor_count(rcon))))

            clear_floor(rcon, run)
            if not wait_state(rcon, "INTERMISSION", seconds=20, floor=1):
                raise RuntimeError("never reached the intermission: " + run_line(rcon))
            count = wait_vendors(rcon, 1)
            results.append(Result("exactly one vendor stands at the intermission", count == 1, str(count)))
            results.append(Result("the vendor has no AI", "1b" in vendor_data(rcon, "NoAI"), vendor_data(rcon, "NoAI")))
            results.append(Result("the vendor is invulnerable", "1b" in vendor_data(rcon, "Invulnerable"),
                                  vendor_data(rcon, "Invulnerable")))
            results.append(Result("the vendor is tagged with its run", run in vendor_data(rcon, "Tags"),
                                  vendor_data(rcon, "Tags")))

            # --- clicks ------------------------------------------------------------------------
            stand_next_to_vendor(rcon, A)
            time.sleep(1)
            click(bots[A])
            time.sleep(3)
            text = log_text(server.log)
            results.append(Result("a participant's click is allowed",
                                  re.search(rf"Vendor of run {run}: {A} used it \(ALLOWED\)", text) is not None,
                                  text[-300:]))

            stand_next_to_vendor(rcon, C)
            time.sleep(1)
            click(bots[C])
            time.sleep(3)
            text = log_text(server.log)
            results.append(Result("an outsider's click is refused",
                                  re.search(rf"Vendor of run {run}: {C} used it \(NOT_IN_THIS_RUN\)", text) is not None,
                                  text[-300:]))

            # --- the vendor leaves with the intermission -----------------------------------------
            play(rcon, A, "pick 1")
            play(rcon, B, "pick 1")
            # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
            play(rcon, A, "pick 1")
            play(rcon, B, "pick 1")
            play(rcon, A, "ready")
            play(rcon, B, "ready")
            opened = wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=60, floor=2)
            results.append(Result("floor 2 opens", opened, run_line(rcon)))
            results.append(Result("the vendor is gone once the next floor opens", vendor_count(rcon) == 0,
                                  str(vendor_count(rcon))))

            # --- and a second intermission gets a fresh one, which a cash-out removes -------------
            if opened:
                clear_floor(rcon, run)
                if wait_state(rcon, "INTERMISSION", seconds=20, floor=2):
                    count = wait_vendors(rcon, 1)
                    results.append(Result("the next intermission has its own single vendor", count == 1, str(count)))
                    play(rcon, A, "cashout")
                    play(rcon, B, "cashout")
                    time.sleep(3)
                    results.append(Result("a cash-out removes the vendor", vendor_count(rcon) == 0,
                                          str(vendor_count(rcon))))
                else:
                    results.append(Result("floor 2 reaches an intermission", False, run_line(rcon)))
            text = log_text(server.log)
            results.append(Result("no cell was left unfit for reuse", "unfit for reuse" not in text,
                                  "; ".join(l for l in text.splitlines() if "unfit for reuse" in l)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("vendor npc run", False, repr(exc)))
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
