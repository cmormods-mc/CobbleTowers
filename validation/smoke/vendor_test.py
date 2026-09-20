#!/usr/bin/env python3
"""P12 live: a real vendor purchase debits a wallet, heals nothing it should not, and is refused when
it should be.

No client speaks CobbleTowers' networking channel in this rig -- the bots are headless mineflayer,
so `VendorCatalogPayload`/`VendorPurchasePayload` are never exchanged over the wire here (the same gap
P11 left for the spectator panel and reward reveal screen). What this DOES prove, none of which a unit
test can reach because they need a real server, a real wallet file and a real run:

  1. `/cobbletowers runs vendor` reports a fresh wallet as empty and lists the shipped catalog.
  2. A purchase attempted before INTERMISSION is refused (NOT_INTERMISSION), charging nothing.
  3. A purchase with no CobbleDollars is refused (INSUFFICIENT_FUNDS), charging nothing.
  4. `runs vendor credit` (the operator tool standing in for a real earn, since the reward table's own
     CobbleDollar entry is a probabilistic roll draft_test.py's battle-skipping shortcut never reaches)
     funds a wallet, and a purchase then succeeds and debits exactly its price -- no more, no less.
  5. The run's own vendor purchase count increments, surviving in `PersistedRun` (TDS #19).

The actual heal/cure effect on a live Cobblemon party is not asserted here: that is Cobblemon's own
API behaving as documented (`Pokemon.setCurrentHealth`/`setStatus`), not a CobbleTowers integration
claim this test needs to make -- the same boundary `recallParties`' own live-party reads are treated
with everywhere else in this suite.

    python validation/smoke/vendor_test.py \\
      --server-dir <rig>/testserver \\
      --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.12.1+1/bin/java.exe" \\
      --jar build/libs/CobbleTowers-<version>.jar
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

STAMP = int(time.time()) % 100000
BOT = f"TVw{STAMP}"


def to_intermission_no_battle(rcon: Rcon, run: str) -> None:
    """FLOOR_READY to INTERMISSION, no battle fought -- the vendor is the subject, not the fight.

    The same shortcut reward_test.py's advance_to_floor_resolving and draft_test.py's to_intermission
    both take: every move here is the real transition table, only the battle itself is skipped.
    """
    rcon.command(f"cobbletowers runs advance {run} preparation_complete")
    rcon.command(f"cobbletowers runs advance {run} encounter_started")
    rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
    rcon.command(f"cobbletowers runs advance {run} rewards_banked")


def balance_of(shown: str) -> int:
    found = re.search(r"CobbleDollars: (\d+)", shown)
    return int(found.group(1)) if found else -1


def bought_result(reply: str) -> str:
    if "Bought" in reply:
        return "SUCCESS"
    found = re.search(r"Could not buy \S+: (\w+)", reply)
    return found.group(1) if found else "<no result in reply>"


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
    bot: subprocess.Popen | None = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bot = start_battle_bot(rig, BOT, FIRST_MOVE)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_joined(rcon, BOT):
                raise RuntimeError(f"{BOT} never joined; see {rig}/bot/{BOT}.log")
            clear_tower(rcon)
            give_party(rcon, BOT)

            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")

            # === the catalog, before anything has been earned or spent =========================
            fresh = rcon.command(f"execute as {BOT} run cobbletowers runs vendor")
            results.append(Result("a fresh wallet shows as empty", balance_of(fresh) == 0,
                                  fresh.strip()[:200]))
            results.append(Result("the shipped catalog lists both services",
                                  "Full Heal" in fresh and "Cure Status" in fresh, fresh.strip()[:300]))

            # === buying before INTERMISSION is refused, and charges nothing =====================
            too_early = rcon.command(
                f"cobbletowers runs vendor buy {run} cobbletowers:full_heal {BOT} {BOT}")
            results.append(Result("a purchase before INTERMISSION is refused",
                                  bought_result(too_early) == "NOT_INTERMISSION", too_early.strip()[:200]))

            to_intermission_no_battle(rcon, run)
            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the run actually reached INTERMISSION",
                                  "INTERMISSION" in shown, shown.strip()[:200]))

            # === buying with nothing in the wallet is refused, and charges nothing ==============
            broke = rcon.command(
                f"cobbletowers runs vendor buy {run} cobbletowers:full_heal {BOT} {BOT}")
            results.append(Result("a purchase with no CobbleDollars is refused",
                                  bought_result(broke) == "INSUFFICIENT_FUNDS", broke.strip()[:200]))
            after_broke = rcon.command(f"execute as {BOT} run cobbletowers runs vendor")
            results.append(Result("a refused purchase charged nothing",
                                  balance_of(after_broke) == 0, after_broke.strip()[:200]))

            # === crediting, then a real purchase that debits exactly its price ==================
            rcon.command(f"cobbletowers runs vendor credit {BOT} 30")
            funded = rcon.command(f"execute as {BOT} run cobbletowers runs vendor")
            results.append(Result("crediting raises the balance", balance_of(funded) == 30,
                                  funded.strip()[:200]))

            bought = rcon.command(
                f"cobbletowers runs vendor buy {run} cobbletowers:full_heal {BOT} {BOT}")
            results.append(Result("a funded purchase during INTERMISSION succeeds",
                                  bought_result(bought) == "SUCCESS", bought.strip()[:200]))

            after_buy = rcon.command(f"execute as {BOT} run cobbletowers runs vendor")
            # Full Heal is priced at 25 in the shipped content; 30 - 25 = 5.
            results.append(Result("the purchase debited exactly its price, no more and no less",
                                  balance_of(after_buy) == 5, after_buy.strip()[:200]))

            # === the run remembers how many times it has bought this (TDS #19) ==================
            purchase_line = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the run's own state records the purchase",
                                  "vendor purchases: {cobbletowers:full_heal=1}" in purchase_line,
                                  purchase_line.strip()[:300]))

            results.append(Result("no CobbleTowers exception during any of it",
                                  "com.cobbletowers" not in server.read_log().replace("com.cobbletowers.CobbleTowers", ""),
                                  "see " + str(server.log)))

    finally:
        if bot:
            bot.terminate()
        server.stop()

    passed = sum(1 for r in results if r.passed)
    print()
    for r in results:
        status = "PASS" if r.passed else "FAIL"
        print(f"  [{status}] {r.name:<70} {r.detail if not r.passed else ''}")
    print(f"\n{passed}/{len(results)} checks passed")
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
