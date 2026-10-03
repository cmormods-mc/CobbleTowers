#!/usr/bin/env python3
"""Proves P18's party registration and, above all, that it puts a player's Pokemon back.

A bot owns eight Pokemon: six in the party and two in a box. It registers the two boxed ones and starts a
run through the lobby. What this proves live, against real Cobblemon storage:

  * the registered Pokemon are moved into the party (exactly those two), the six displaced ones go to the
    box, and **nobody is lost or duplicated** (the same eight ids before and after);
  * the run records two registered Pokemon;
  * ending the run puts every Pokemon **back in exactly the slot it started in**;
  * a hard kill mid-run, after Cobblemon's autosave wrote the swapped layout, leaves the player swapped across the
    restart and a recovery abandon then restores them exactly;
  * a hard kill mid-run *before* Cobblemon saved the swap (its disk copy is the original layout, the
    journal says so) also ends with every Pokemon exactly where it started.

Cobblemon writes a player's storage on disconnect and on a 30-second timer, never on save-all, so the test
persists the original layout by disconnecting the bot (as a real session's last logout would) and lets the
timer write the swapped one.

The chooser screen is not exercised: a headless bot cannot open one.

    python validation/smoke/party_registration_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

BOT = f"TRg{int(time.time()) % 100000}"
TOWER = "cobbletowers:neutral"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
LIVE_STATES = ("ENCOUNTER_ACTIVE", "RECOVERY_REQUIRED", "ALLOCATING_INSTANCE", "PREPARING", "FLOOR_READY")


def start_bot(port: int, node_modules: Path, log: Path) -> subprocess.Popen:
    env = dict(os.environ, NODE_PATH=str(node_modules))
    handle = open(log, "w", encoding="utf-8", errors="replace")
    return subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(port)],
                            stdout=handle, stderr=subprocess.STDOUT, env=env)


def wait_online(rcon: Rcon, seconds: int = 90) -> bool:
    for _ in range(seconds):
        if BOT in rcon.command("list"):
            return True
        time.sleep(1)
    return False


def layout(rcon: Rcon) -> list[tuple[str, str]]:
    """Every Pokemon the bot owns as (place, id), sorted: the whole collection, exactly."""
    out = rcon.command(f"cobbletowers play pokemon {BOT}")
    return sorted(re.findall(r"(party \d+|box \d+/\d+) " + UUID_RE, out))


def ids(entries: list[tuple[str, str]]) -> list[str]:
    return sorted(pokemon for _, pokemon in entries)


def party_ids(entries: list[tuple[str, str]]) -> list[str]:
    return [pokemon for place, pokemon in sorted(entries) if place.startswith("party")]


def play(rcon: Rcon, command: str) -> None:
    rcon.command(f"execute as {BOT} run cobbletowers play {command}")


def live_run(rcon: Rcon) -> str:
    """The id of the run that is not finished. RCON joins the run list onto one line, so each id is paired
    with the state that follows it rather than the output being split on newlines."""
    listing = rcon.command("cobbletowers runs list")
    for found in re.finditer(UUID_RE + r"\s+\S+\s+(\w+)\s+floor", listing):
        if found.group(2) in LIVE_STATES:
            return found.group(1)
    return ""


def wait_for(predicate, seconds: int = 30) -> bool:
    for _ in range(seconds * 2):
        if predicate():
            return True
        time.sleep(0.5)
    return False


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
    port = server_port(server_dir)
    bot_log = server_dir / "logs" / "towers-registration-bot.log"
    password = read_password(server_dir)
    bots: list[subprocess.Popen] = []

    def open_rcon() -> Rcon:
        return Rcon("127.0.0.1", 25575, password)

    def register_and_start(rcon: Rcon, boxed: list[str]) -> None:
        play(rcon, f"tower {TOWER}")
        for pokemon in boxed:
            play(rcon, f"register toggle {pokemon}")
        play(rcon, "start")
        wait_for(lambda: "ENCOUNTER_ACTIVE" in rcon.command("cobbletowers runs list"), 40)

    def join_bot() -> None:
        bots.append(start_bot(port, node_modules, bot_log))
        with open_rcon() as rcon:
            if not wait_online(rcon):
                raise RuntimeError("the bot never joined")
            time.sleep(3)

    def persist() -> None:
        """Disconnect and rejoin the bot: Cobblemon saves a player's storage on disconnect, so this is the
        last logout a real session would have had before a crash."""
        for bot in bots:
            bot.kill()
        bots.clear()
        with open_rcon() as rcon:
            for _ in range(60):
                if BOT not in rcon.command("list"):
                    break
                time.sleep(1)
        time.sleep(2)
        join_bot()

    def crash() -> None:
        """A hard kill, then a fresh boot and a fresh bot."""
        server.process.kill()
        server.process.wait(timeout=60)
        for bot in bots:
            bot.kill()
        bots.clear()
        server.start()
        server.wait_until_ready()
        join_bot()

    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        join_bot()

        with open_rcon() as rcon:
            clear_tower(rcon)
            rcon.command(f"pokegiveother {BOT} glaceon level=100")
            for _ in range(5):
                rcon.command(f"pokegiveother {BOT} magikarp level=1")
            rcon.command(f"pokegiveother {BOT} charizard level=50")
            rcon.command(f"pokegiveother {BOT} snorlax level=60")

        persist()   # Cobblemon now has the original layout on disk
        with open_rcon() as rcon:
            original = layout(rcon)
            boxed = [pokemon for place, pokemon in original if place.startswith("box")]
            results.append(Result("the bot owns eight Pokemon: six in the party, two in a box",
                                  len(original) == 8 and len(boxed) == 2, str(original)))
            if len(boxed) != 2:
                raise RuntimeError("could not set up two boxed Pokemon: " + str(original))

            # --- a normal run: swap in, then put back -----------------------------------------------
            register_and_start(rcon, boxed)
            run = live_run(rcon)
            results.append(Result("a lobby with two boxed Pokemon registered starts a run", bool(run),
                                  rcon.command("cobbletowers runs list")[:200]))
            during = layout(rcon)
            results.append(Result("the party is now exactly the two registered Pokemon",
                                  sorted(party_ids(during)) == sorted(boxed), str(during)))
            results.append(Result("nobody was lost or duplicated by the swap",
                                  ids(during) == ids(original) and len(during) == len(original), str(during)))
            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the run registered two Pokemon", "2 registered" in shown, shown[-200:]))

            rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            restored = wait_for(lambda: layout(rcon) == original, 15)
            results.append(Result("ending the run puts every Pokemon back in exactly its original slot",
                                  restored, str(layout(rcon))))
            clear_tower(rcon)

            # --- a crash after Cobblemon saved the swapped layout -------------------------------------
            register_and_start(rcon, boxed)
            swapped_before = layout(rcon)

        # Cobblemon autosaves every 30 seconds (pokemonSaveIntervalSeconds), so waiting a little longer puts
        # the swapped layout on its disk copy without a disconnect -- which would end the run as a wipe.
        time.sleep(36)
        crash()
        with open_rcon() as rcon:
            run = live_run(rcon)
            results.append(Result("after a crash the run is parked, not lost", bool(run),
                                  rcon.command("cobbletowers runs list")[:200]))
            now = layout(rcon)
            results.append(Result("the player is still swapped after the restart: the run still needs them so",
                                  now == swapped_before, f"{now} vs {swapped_before}"))
            reply = rcon.command(f"cobbletowers runs advance {run} recovery_abandoned") if run else "no run"
            restored = wait_for(lambda: layout(rcon) == original, 15)
            results.append(Result("abandoning the parked run restores every Pokemon exactly (crash after a save)",
                                  restored, f"advance said: {reply.strip()[:160]} | runs: "
                                  f"{rcon.command('cobbletowers runs list')[:300]} | layout: {layout(rcon)}"))
            clear_tower(rcon)

        # --- a crash before Cobblemon saved the swap: its disk copy is the original layout -----------
        persist()   # Cobblemon's disk copy is the original layout again
        with open_rcon() as rcon:
            register_and_start(rcon, boxed)
            swapped = layout(rcon)
            results.append(Result("the second run is swapped before the crash",
                                  sorted(party_ids(swapped)) == sorted(boxed), str(swapped)))

        crash()
        with open_rcon() as rcon:
            run = live_run(rcon)
            results.append(Result("after a crash with no save the run is parked, not lost", bool(run),
                                  rcon.command("cobbletowers runs list")[:200]))
            reply = rcon.command(f"cobbletowers runs advance {run} recovery_abandoned") if run else "no run"
            restored = wait_for(lambda: layout(rcon) == original, 15)
            results.append(Result("abandoning the parked run restores every Pokemon exactly (crash before a save)",
                                  restored, f"advance said: {reply.strip()[:160]} | runs: "
                                  f"{rcon.command('cobbletowers runs list')[:300]} | layout: {layout(rcon)}"))
            results.append(Result("and nothing was lost or duplicated whichever layout the crash left",
                                  ids(layout(rcon)) == ids(original) and len(layout(rcon)) == 8, str(layout(rcon))))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("party registration run", False, repr(exc)))
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
