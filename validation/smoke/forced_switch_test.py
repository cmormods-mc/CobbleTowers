#!/usr/bin/env python3
"""A "no switching" boss fight must still let a player replace a Pokemon that has fainted (P27, found in play).

The No Retreat modifier turns voluntary switching off. CobbleRaids used to refuse EVERY switch under that rule, including
the forced switch Showdown asks for after a faint, so the player whose Pokemon fainted could not pick another and the
fight stalled for good. This plays a Neutral floor 1 with No Retreat granted and the boss made much tougher (Bulwark Boss,
Towering Presence, Champion's Vigil) against a level-30 lead, so
a Pokemon is certain to faint inside the boss battle, and checks the bot is never told "Switching is not allowed" and the
floor resolves.

The bot logs `SWITCH_REFUSED` whenever the server says that, `TRY_SWITCH` for a switch it chose, and `SWITCH` for its answer to
a faint. A refusal after a TRY_SWITCH is correct; after a SWITCH it is the bug. The test cannot make a Pokemon faint on demand, so
whether a replacement was asked for is reported, not required; the rule itself is unit-tested in CobbleRaids
(RaidSwitchRulesTest).

    python validation/smoke/forced_switch_test.py --server-dir <rig> --java <jdk21 java> [--jar <CobbleTowers build>]
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

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, read_password, reset_tower_world, run_id_from, wait_online,
)
from floor_encounter_test import (  # noqa: E402
    FIRST_MOVE, begin_floor, start_battle_bot, tell_bot, wait_for, wait_for_floor,
)

BOT = f"FS{int(time.time()) % 100000}"


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
        server.start()
        server.wait_until_ready()
        os.environ["BOT_TRY_SWITCH"] = "1"      # the bot also tries one switch by choice per battle
        os.environ["BOT_TRACE"] = "1"
        bot = start_battle_bot(rig, BOT, FIRST_MOVE)
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon, seconds=90) and BOT not in rcon.command("list"):
                raise RuntimeError(f"{BOT} never joined")
            clear_tower(rcon)
            # A strong lead (it must survive the opponents) with five level-1 backups, against a boss made much harder
            # below: the lead is certain to faint in the boss battle and the backups are what must be sent in.
            rcon.command(f"pokegiveother {BOT} glaceon level=30")
            for _ in range(5):
                rcon.command(f"pokegiveother {BOT} magikarp level=1")

            run = run_id_from(rcon.command(f"cobbletowers runs create cobbletowers:neutral {BOT}"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            for modifier in ("no_retreat", "bulwark_boss", "towering_presence", "champions_vigil"):
                rcon.command(f"cobbletowers runs grant {run} cobbletowers:{modifier}")
            tell_bot(rig, BOT, "FIGHT")
            wait_for(rig / "bot" / f"{BOT}.log", r"AUTOFIGHT on", seconds=30)
            begin_floor(rcon, run)

            started = wait_for(server.log, r"starts its boss under EncounterRules\[.*switchingAllowed=false", seconds=240)
            results.append(Result("the boss fight began with switching turned off", bool(started),
                                  "no 'switchingAllowed=false' boss line"))
            outcome = wait_for_floor(server, rig, [BOT], seconds=300,
                                     pattern=rf"Floor 1 of run {run} (cleared(?! its)|wiped the party)")
            print(f"  outcome: {outcome or '<none>'}")
            results.append(Result("the floor resolved (the fight did not stall)", bool(outcome), "no outcome within 300 s"))

            try:
                text = (rig / "bot" / f"{BOT}.log").read_text(encoding="utf-8", errors="replace")
            except FileNotFoundError:
                text = ""
            lines = text.splitlines()
            tried = [line for line in lines if "TRY_SWITCH" in line]
            refused = [line for line in lines if "SWITCH_REFUSED" in line]
            results.append(Result("a switch chosen by the player under No Retreat is still refused (the rule bites)",
                                  bool(tried) and bool(refused), f"{len(tried)} tried, {len(refused)} refused"))
            # A refusal is right only for a switch the bot CHOSE. One that follows a forced switch ("SWITCH <name>", the
            # bot's answer to a faint) is the bug this test exists for.
            wrong = []
            last = ""
            for line in lines:
                if "TRY_SWITCH" in line:
                    last = "tried"
                elif " SWITCH " in line:
                    last = "forced"
                elif "SWITCH_REFUSED" in line and last == "forced":
                    wrong.append(line)
            forced = [line for line in lines if " SWITCH " in line]
            print(f"  voluntary switches tried: {len(tried)}, refused: {len(refused)}; forced replacements answered: {len(forced)}")
            results.append(Result("a replacement for a fainted Pokemon was never refused", not wrong,
                                  f"{len(wrong)} refused: {wrong[0][:120] if wrong else ''}"))
            bad = [line for line in server.read_log().splitlines() if "com.cobbletowers" in line and "ERROR" in line]
            results.append(Result("no CobbleTowers error", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("forced switch run", False, repr(exc)))
    finally:
        if bot:
            bot.kill()
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
