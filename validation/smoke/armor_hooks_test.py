#!/usr/bin/env python3
"""Proves the armor-set bonuses that live OUTSIDE tower battles reach the game, on the installed Cobblemon (P24).

armor_set_test.py proves attributes and battle effects. This proves the rest, each through the real code path:

  * experience, catch rate and shiny odds: `/cobbletowers armor probe` builds Cobblemon's own events, posts them on the
    real CobblemonEvents bus, and reports what CobbleTowers' subscribed handlers did to the numbers;
  * Raid Points: `/cobbletowers armor points` queues a reward and delivers it through RewardDelivery, and the result
    is read from CobbleRaids' own balance;
  * the vendor discount: the catalog price a wearer sees, and exactly what a purchase debits.

    python validation/smoke/armor_hooks_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from,
)
from floor_encounter_test import FIRST_MOVE, TOWER, give_party, start_battle_bot  # noqa: E402
from participant_test import wait_joined  # noqa: E402
from vendor_test import balance_of, bought_result, to_intermission_no_battle  # noqa: E402

BOT = f"TAh{int(time.time()) % 100000}"
PIECES = {"head": "helmet", "chest": "chestplate", "legs": "leggings", "feet": "boots"}


def clear_armor(rcon: Rcon) -> None:
    for slot in PIECES:
        rcon.command(f"item replace entity {BOT} armor.{slot} with minecraft:air")


def wear(rcon: Rcon, set_id: str, slots: list[str]) -> None:
    for slot in slots:
        rcon.command(f"item replace entity {BOT} armor.{slot} with cobbletowers:{set_id}_{PIECES[slot]}")


def probe(rcon: Rcon) -> tuple[float, float, float] | None:
    out = rcon.command(f"cobbletowers armor probe {BOT}")
    match = re.search(r"xp 1000 -> (\d+) \| catch 100\.0 -> ([0-9.]+) \| shiny 1000\.0 -> ([0-9.]+)", out)
    return (float(match.group(1)), float(match.group(2)), float(match.group(3))) if match else None


def raid_points(rcon: Rcon) -> int:
    out = rcon.command(f"execute as {BOT} run cobbleraids points")
    found = re.search(r"(\d+)", out)
    return int(found.group(1)) if found else -1


def near(actual, expected, tolerance=0.6) -> bool:
    return actual is not None and abs(actual - expected) <= tolerance


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
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
    bot: subprocess.Popen | None = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bot = start_battle_bot(rig, BOT, FIRST_MOVE)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_joined(rcon, BOT):
                raise RuntimeError(f"{BOT} never joined")
            clear_tower(rcon)
            give_party(rcon, BOT)
            clear_armor(rcon)

            # === Cobblemon: experience, catch rate, shiny odds ================================================
            base = probe(rcon)
            results.append(Result("the probe runs against the installed Cobblemon with nothing worn",
                                  base is not None, rcon.command(f"cobbletowers armor probe {BOT}")[:300]))
            results.append(Result("with nothing worn, every number comes back unchanged",
                                  base == (1000.0, 100.0, 1000.0), str(base)))

            wear(rcon, "challenger", ["head", "chest"])
            got = probe(rcon)
            results.append(Result("Challenger x2: +5% experience (1000 -> 1050), catch and shiny untouched",
                                  got is not None and got[0] == 1050 and near(got[1], 100.0) and near(got[2], 1000.0), str(got)))

            clear_armor(rcon)
            wear(rcon, "rootvale", ["head", "chest"])
            got = probe(rcon)
            results.append(Result("Rootvale x2: +10% experience (1000 -> 1100)", got is not None and got[0] == 1100, str(got)))

            clear_armor(rcon)
            wear(rcon, "tideforge", ["head", "chest"])
            got = probe(rcon)
            results.append(Result("Tideforge x2: +10% catch rate (100 -> 110), experience untouched",
                                  got is not None and near(got[1], 110.0) and got[0] == 1000, str(got)))

            clear_armor(rcon)
            wear(rcon, "duskvale", ["head", "chest"])
            got = probe(rcon)
            results.append(Result("Duskvale x2: +10% shiny odds (1000 -> 1100), which is MORE shinies, not fewer",
                                  got is not None and near(got[2], 1100.0, 1.0) and got[2] > 1000.0, str(got)))

            clear_armor(rcon)
            wear(rcon, "tideforge", ["head", "chest"])
            wear(rcon, "rootvale", ["legs", "feet"])
            got = probe(rcon)
            results.append(Result("a mixed outfit stacks both two-piece bonuses (xp +10%, catch +10%)",
                                  got is not None and got[0] == 1100 and near(got[1], 110.0), str(got)))

            clear_armor(rcon)
            again = probe(rcon)
            results.append(Result("taking everything off puts every number back", again == (1000.0, 100.0, 1000.0), str(again)))

            # === Raid Points: the real delivery path, measured in CobbleRaids' own balance ======================
            before = raid_points(rcon)
            rcon.command(f"cobbletowers armor points {BOT} 100")
            plain = raid_points(rcon) - before
            results.append(Result("100 Raid Points delivered with nothing worn arrive as exactly 100",
                                  plain == 100, f"{before} -> {before + plain}"))

            wear(rcon, "tideforge", list(PIECES))
            before = raid_points(rcon)
            rcon.command(f"cobbletowers armor points {BOT} 100")
            boosted = raid_points(rcon) - before
            results.append(Result("a full Tideforge set (+10% Raid Points) turns 100 into 110", boosted == 110, f"+{boosted}"))

            # === the vendor discount ========================================================================
            clear_armor(rcon)
            wear(rcon, "challenger", list(PIECES))
            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            to_intermission_no_battle(rcon, run)

            catalog = rcon.command(f"execute as {BOT} run cobbletowers runs vendor")
            results.append(Result("a full Challenger set (10% off) shows Full Heal at 23, not 25",
                                  "Full Heal -- 23 CobbleDollars" in catalog, catalog.strip()[:300]))

            rcon.command(f"cobbletowers runs vendor credit {BOT} 30")
            bought = rcon.command(f"cobbletowers runs vendor buy {run} cobbletowers:full_heal {BOT} {BOT}")
            after = rcon.command(f"execute as {BOT} run cobbletowers runs vendor")
            results.append(Result("the purchase succeeds and debits the discounted 23 (30 -> 7)",
                                  bought_result(bought) == "SUCCESS" and balance_of(after) == 7,
                                  f"{bought.strip()[:100]} / {after.strip()[:100]}"))

            clear_armor(rcon)
            catalog = rcon.command(f"execute as {BOT} run cobbletowers runs vendor")
            results.append(Result("without the set the price is back to 25", "Full Heal -- 25 CobbleDollars" in catalog,
                                  catalog.strip()[:300]))

            log = server.read_log()
            problems = [line for line in log.splitlines()
                        if re.search(r"armor|ArmorCommand|CobbleTowers", line) and re.search(r"ERROR|Exception", line)]
            results.append(Result("no armor errors in the server log", not problems, "; ".join(problems)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("armor hooks run", False, repr(exc)))
    finally:
        if bot:
            bot.terminate()
        server.stop()

    print()
    width = max(len(r.name) for r in results)
    failed = sum(1 for r in results if not r.passed)
    for r in results:
        print(f"  [{'PASS' if r.passed else 'FAIL'}] {r.name:<{width}}  {r.detail if not r.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
