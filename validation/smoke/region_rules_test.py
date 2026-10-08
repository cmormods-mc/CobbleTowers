#!/usr/bin/env python3
"""Proves each regional tower's rule (P38) reaches a real battle: Tideforge suppresses the player's held items, Rootvale's enemy drains
10% on floor 1, Duskvale puts one status on the lead, and Neutral asks for none of them.

The server logs the operations it hands to Showdown ("Tower battle <id> carries N effect(s): [...]") and the extension logs that it
applied them ("Applied N of N tower effect(s)"); both are checked. The Showdown behaviour itself (the items really are ignored, the
heal really happens) is proved by validation/showdown/tower_fx_test.js.

    python validation/smoke/region_rules_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from, server_port,
)
from regional_content_test import give_party, start_join_bot, wait_online  # noqa: E402


def floor_battle_effects(rcon: Rcon, server: Server, tower: str) -> tuple[str, str]:
    """Starts a floor on the tower; returns the effects line the server logged for its battle and the extension's applied line."""
    before = len(server.read_log())
    run = run_id_from(rcon.command(f"cobbletowers runs create {tower} TowerRegionBot"))
    rcon.command(f"cobbletowers runs advance {run} party_submitted")
    rcon.command(f"cobbletowers runs advance {run} party_validated")
    rcon.command(f"cobbletowers runs allocate {run}")
    rcon.command(f"cobbletowers runs advance {run} preparation_complete")
    rcon.command(f"cobbletowers runs encounter {run}")
    time.sleep(4)
    text = server.read_log()[before:]
    rcon.command(f"cobbletowers runs advance {run} abandon_requested")
    clear_tower(rcon)
    carried = re.search(r"Tower battle \S+ carries (\d+) effect\(s\): (.*)", text)
    applied = re.search(r"Applied (\d+) of (\d+) tower effect", text)
    return (carried.group(0) if carried else ""), (applied.group(0) if applied else "")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
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
    bot = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bot = start_join_bot(server_port(server_dir), node_modules, server_dir / "logs" / "towers-region-bot.log")
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon):
                raise RuntimeError("the bot never joined")
            give_party(rcon)

            carried, applied = floor_battle_effects(rcon, server, "cobbletowers:tideforge")
            results.append(Result("tideforge: the player's items are suppressed", '"op":"suppress_items"' in carried, carried[:240]))
            results.append(Result("tideforge: it asks for no drain and no status", "drain" not in carried and "status" not in carried, carried[:240]))
            results.append(Result("tideforge: the extension applied it", bool(applied) and applied.split()[1] == applied.split()[3], applied))

            carried, applied = floor_battle_effects(rcon, server, "cobbletowers:rootvale")
            results.append(Result("rootvale: the enemy drains 10% on floor 1", '"op":"drain"' in carried and '"percent":10' in carried, carried[:240]))
            results.append(Result("rootvale: items are not suppressed", "suppress_items" not in carried, carried[:240]))
            results.append(Result("rootvale: the extension applied it", bool(applied) and applied.split()[1] == applied.split()[3], applied))

            carried, applied = floor_battle_effects(rcon, server, "cobbletowers:duskvale")
            results.append(Result("duskvale: the lead gets one of the listed statuses",
                                  re.search(r'"op":"status".*"status":"(psn|brn|par|slp|frz)"|"status":"(psn|brn|par|slp|frz)".*"op":"status"', carried) is not None,
                                  carried[:240]))
            results.append(Result("duskvale: no drain, items not suppressed", "drain" not in carried and "suppress_items" not in carried, carried[:240]))
            results.append(Result("duskvale: the extension applied it", bool(applied) and applied.split()[1] == applied.split()[3], applied))

            carried, applied = floor_battle_effects(rcon, server, "cobbletowers:neutral")
            results.append(Result("neutral: no region rule at all",
                                  not any(word in carried for word in ("suppress_items", "drain", '"op":"status"')), carried[:240]))

            log = server.read_log()
            results.append(Result("no CobbleTowers exception during any of it", "\tat com.cobbletowers" not in log,
                                  "a CobbleTowers error is in the log"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("region rules run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
