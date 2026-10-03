#!/usr/bin/env python3
"""Proves P15's real party validation (TDS #41, #46) against a real server and a real Cobblemon party.

Before P15 nothing ever emitted PARTY_SUBMITTED/VALIDATED/REJECTED outside an operator's `runs advance`,
and the ruleset's party fields were enforced nowhere. `runs validate` is the real path, so this drives:

  * a player with **no Pokemon** is rejected: the run is ABANDONED and registered nothing;
  * the same player with a real six-Pokemon party is **validated**: the run moves to
    ALLOCATING_INSTANCE and `runs show` reports six registered, which is the snapshot being taken at
    validation time rather than at `runs create`.

Fainted-party rejection and the size cap are covered by PartyValidationTest; fainting a real Pokemon
from RCON is not something this rig can do deterministically.

    python validation/smoke/party_validation_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from, server_port,
)

BOT = "TowerPartyBot"
LEAD = "glaceon"
FILLERS = 5
TOWER = "cobbletowers:neutral"


def wait_online(rcon: Rcon, seconds: int = 90) -> bool:
    for _ in range(seconds):
        if BOT in rcon.command("list"):
            return True
        time.sleep(1)
    return False


def start_join_bot(port: int, node_modules: Path, log: Path):
    """joinbot.js under this test's own name -- see the note above `BOT`."""
    import subprocess
    env = dict(os.environ, NODE_PATH=str(node_modules))
    handle = open(log, "w", encoding="utf-8", errors="replace")
    return subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(port)],
                            stdout=handle, stderr=subprocess.STDOUT, env=env)


def give_party(rcon: Rcon) -> None:
    rcon.command(f"pokegiveother {BOT} {LEAD} level=100")
    for _ in range(FILLERS):
        rcon.command(f"pokegiveother {BOT} magikarp level=1")


def give_party(rcon: Rcon) -> None:
    rcon.command(f"pokegiveother {BOT} {LEAD} level=100")
    for _ in range(FILLERS):
        rcon.command(f"pokegiveother {BOT} magikarp level=1")


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
    bot = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bot_log = server_dir / "logs" / "towers-party-bot.log"
        bot = start_join_bot(server_port(server_dir), node_modules, bot_log)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon):
                said = bot_log.read_text(encoding="utf-8", errors="replace").strip() if bot_log.exists() else ""
                raise RuntimeError(f"the bot never joined. node said: {said[-400:] or '<nothing>'}")
            clear_tower(rcon)

            # --- no party: rejected -----------------------------------------------------------
            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            verdict = rcon.command(f"cobbletowers runs validate {run}")
            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("a player with no Pokemon is rejected",
                                  "rejected" in verdict and "ABANDONED" in shown.splitlines()[0],
                                  verdict.strip()[:200] + " | " + shown.splitlines()[0][:160]))
            clear_tower(rcon)

            # --- a real party: validated, registered ------------------------------------------
            give_party(rcon)
            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            created = rcon.command(f"cobbletowers runs show {run}")
            verdict = rcon.command(f"cobbletowers runs validate {run}")
            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("a full party is validated and the run moves on",
                                  "validated" in verdict and "ALLOCATING_INSTANCE" in shown.splitlines()[0],
                                  verdict.strip()[:200] + " | " + shown.splitlines()[0][:160]))
            results.append(Result("six Pokemon are registered after validation",
                                  "6 registered" in shown, shown[-200:]))
            results.append(Result("validating twice is refused, not a second registration",
                                  "validated" not in rcon.command(f"cobbletowers runs validate {run}"),
                                  "second validate was accepted"))
            allocated = rcon.command(f"cobbletowers runs allocate {run}")
            results.append(Result("a validated run goes on to take a cell",
                                  "cell" in allocated and "none" not in allocated.split("cell")[-1][:8],
                                  allocated.strip()[:200]))
            rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            clear_tower(rcon)
    except Exception as exc:  # noqa: BLE001
        results.append(Result("party validation run", False, repr(exc)))
    finally:
        if bot is not None:
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
