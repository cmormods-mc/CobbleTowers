#!/usr/bin/env python3
"""Proves P33b's real-card rewards against a real server, with and without CobblemonCards installed.

A bot drafts a rental team and an operator clears every floor of the four-floor test tower, so the run reaches COMPLETED. Then:

  phase A, the mod is NOT installed (the server's normal state): the run completes, nothing is granted, nothing fails, and the log says
  why;
  phase B, the mod IS installed (this test copies the jar in and out): the player receives one real card for each Pokemon they ran
  with, each carrying that Pokemon's species, a rarity no higher than the playlist's cap and a `<type>_spawn` stat; a God Pack's cards
  are shiny; the daily allowance (three runs) stops the fourth run's cards; and a run that is only abandoned grants none.

The jars (CobblemonCards and the Accessories and owo-lib mods it needs) are taken from --cards-jar's folder (default: the rig's
mods-disabled-for-testing) and are removed again when the test ends, so the rest of the live tests still see a server without them.

    python validation/smoke/card_reward_test.py --server-dir <rig>/testserver-181 --java <jdk21 java> [--jar <build>] [--cards-jar <jar>]
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import rental_test as rt  # noqa: E402
from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)

SETS = {path.stem: path for path in rt.SETS.glob("*.json")}
CAP = ["common", "uncommon", "rare", "epic"]


def clear_floors(rcon: Rcon, run: str, floors: int = 4) -> None:
    """Clears every floor by operator event, so the run reaches COMPLETED."""
    for floor in range(1, floors + 1):
        rcon.command(f"cobbletowers runs advance {run} encounter_resolved_cleared")
        if floor == floors:
            rcon.command(f"cobbletowers runs advance {run} final_floor_cleared")
            return
        rcon.command(f"cobbletowers runs advance {run} rewards_banked")
        for _ in range(60):
            if re.search(re.escape(run) + r"\s+\S+\s+INTERMISSION", rcon.command("cobbletowers runs list")):
                break
            time.sleep(1)
        rt.play(rcon, "pick 1")
        # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
        rt.play(rcon, "pick 1")
        rt.play(rcon, "ready")
        for _ in range(90):
            if re.search(re.escape(run) + r"\s+\S+\s+ENCOUNTER_ACTIVE", rcon.command("cobbletowers runs list")):
                break
            time.sleep(1)


def inventory(rcon: Rcon) -> str:
    return rcon.command(f"data get entity {rt.BOT} Inventory")


def cards_in(text: str) -> list[dict]:
    """The card items in an inventory dump: the species, rarity, stat and shiny flag of each. The game prints each card's data as
    `"cobblemon-cards:card_data": {stat: "dragon_spawn", is_shiny: 0b, ..., pokemon_id: "garchomp", stat_value: 0.1f, rarity: "epic"}`."""
    cards = []
    for body in re.findall(r'"cobblemon-cards:card_data": \{([^}]*)\}', text):
        pokemon = re.search(r'pokemon_id: "([^"]+)"', body)
        if not pokemon:
            continue
        cards.append({
            "species": pokemon.group(1),
            "rarity": (re.search(r'rarity: "([^"]+)"', body) or [None, ""])[1],
            "stat": (re.search(r'stat: "([^"]+)"', body) or [None, ""])[1],
            "shiny": "is_shiny: 1b" in body,
            "value": float((re.search(r"stat_value: ([0-9.]+)f", body) or [None, "0"])[1]),
        })
    return cards


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--cards-jar", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    rig = server_dir.parent
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    cards_jar = args.cards_jar or next(iter(sorted((rig / "testserver-full" / "mods-disabled-for-testing").glob("cobblemon-cards-fabric-*.jar"))), None)
    if cards_jar is None or not cards_jar.exists():
        sys.exit("cannot find the CobblemonCards jar: pass --cards-jar")
    # CobblemonCards needs the Accessories mod at runtime (which needs owo-lib), though its metadata does not say so.
    companions = [cards_jar] + [next(iter(sorted(cards_jar.parent.glob(pattern))), None) for pattern in ("accessories-fabric-*.jar", "owo-lib-*.jar")]
    if any(jar is None for jar in companions):
        sys.exit("cannot find accessories-fabric-*.jar and owo-lib-*.jar next to the CobblemonCards jar")
    installed_jars = [server_dir / "mods" / jar.name for jar in companions]
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)
    for jar in installed_jars:
        jar.unlink(missing_ok=True)

    results: list[Result] = []
    server = Server(server_dir, java)
    port = server_port(server_dir)
    password = read_password(server_dir)
    node_modules = rig / "bot" / "node_modules"
    bot: subprocess.Popen | None = None

    def open_rcon() -> Rcon:
        return Rcon("127.0.0.1", 25575, password)

    def join() -> None:
        nonlocal bot
        bot = rt.start_bot(port, node_modules, server_dir / "logs" / "towers-card-bot.log")
        with open_rcon() as rcon:
            for _ in range(90):
                if rt.BOT in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("the bot never joined")
        time.sleep(3)

    def leave() -> None:
        nonlocal bot
        if bot is not None:
            bot.kill()
            bot = None

    def complete_a_run(rcon: Rcon) -> tuple[str, list[str]]:
        """Drafts, starts and completes a rental run; returns the run id and the species the player ran with."""
        run = rt.start_rental_run_for(rcon)
        text = rcon.command(f"cobbletowers play rentals {rt.BOT}")
        team = sorted(re.findall(UUID_AND_SPECIES, text))
        clear_floors(rcon, run)
        for _ in range(30):
            if re.search(re.escape(run) + r"\s+\S+\s+COMPLETED", rcon.command("cobbletowers runs list")):
                break
            time.sleep(1)
        time.sleep(2)
        return run, team

    try:
        # ---- phase A: no card mod -----------------------------------------------------------------------------
        print("Phase A: the card mod is not installed")
        server.start()
        server.wait_until_ready()
        join()
        with open_rcon() as rcon:
            clear_tower(rcon)
            rcon.command(f"pokegiveother {rt.BOT} glaceon level=100")
            time.sleep(1)
            run, team = complete_a_run(rcon)
            state = rcon.command("cobbletowers runs list")
            results.append(Result("without the mod a rental run still completes", bool(re.search(re.escape(run) + r"\s+\S+\s+COMPLETED", state)),
                                  state[:200]))
            results.append(Result("and the player gets no card", cards_in(inventory(rcon)) == [], inventory(rcon)[:200]))
            log = server.read_log()
            results.append(Result("and the log says why", "is not installed on this server, so none are granted" in log, log[-300:]))
        leave()
        server.stop()

        # ---- phase B: with the mod ------------------------------------------------------------------------------
        print("Phase B: the card mod is installed")
        for jar in companions:
            shutil.copy2(jar, server_dir / "mods" / jar.name)
        server.start()
        server.wait_until_ready()
        join()
        with open_rcon() as rcon:
            clear_tower(rcon)
            expected_runs = 3
            all_cards: list[list[dict]] = []
            teams: list[list[str]] = []
            for n in range(expected_runs):
                rcon.command(f"clear {rt.BOT} cobblemon-cards:card")
                run, team = complete_a_run(rcon)
                teams.append(team)
                all_cards.append(cards_in(inventory(rcon)))
                clear_tower(rcon)
            first = all_cards[0]
            species = sorted(card["species"] for card in first)
            ran_with = teams[0]
            results.append(Result("a completed rental run grants one real card for each of the six Pokemon it ran with",
                                  len(first) == 6 and species == ran_with, f"cards {species} vs team {ran_with}"))
            results.append(Result("each card is capped at epic and carries that Pokemon type's spawn stat",
                                  bool(first) and all(card["rarity"] in CAP and card["stat"].endswith("_spawn") for card in first),
                                  str(first)))
            results.append(Result("a card's stat value is the mod's own middle for its rarity",
                                  bool(first) and all(abs(card["value"] - {"common": 0.0075, "uncommon": 0.0225, "rare": 0.055, "epic": 0.10}[card["rarity"]]
                                          - (0.03 if card["shiny"] else 0)) < 0.002 for card in first), str(first)))
            results.append(Result("the second and third runs earn cards too", len(all_cards[1]) == 6 and len(all_cards[2]) == 6,
                                  f"{len(all_cards[1])}, {len(all_cards[2])}"))

            # the fourth completed run on the same day is over the allowance
            rcon.command(f"clear {rt.BOT} cobblemon-cards:card")
            run, team = complete_a_run(rcon)
            after = len(cards_in(inventory(rcon)))
            results.append(Result("a fourth completed run in a day earns none (the allowance is three runs)", after == 0, f"{after} cards"))
            log = server.read_log()
            results.append(Result("and the log names the daily limit", "DAILY_LIMIT" in log, log[-300:]))
            clear_tower(rcon)

            # an abandoned run earns nothing, whatever the allowance
            rcon.command(f"clear {rt.BOT} cobblemon-cards:card")
            run = rt.start_rental_run_for(rcon)
            rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            time.sleep(6)
            results.append(Result("an abandoned run earns no cards", cards_in(inventory(rcon)) == [], inventory(rcon)[:200]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("card reward run", False, repr(exc)))
    finally:
        leave()
        server.stop()
        for jar in installed_jars:
            jar.unlink(missing_ok=True)

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name.ljust(width)}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


UUID_AND_SPECIES = r"RENTAL [0-9a-f-]{36} (\w+) level"

if __name__ == "__main__":
    main()
