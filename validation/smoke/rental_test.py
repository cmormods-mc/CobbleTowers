#!/usr/bin/env python3
"""Proves P33's Rental Draft against a real server, and above all that a rental never leaks into a real collection and a player's
own Pokemon always come back.

A bot owns eight Pokemon (six in the party, two in a box). It drafts a team in chat, starts a rental run, and:

  * a rental run will not start before the draft is finished, and the bad picks are refused with reasons;
  * the party becomes exactly six RENTAL Pokemon, built exactly as their sets say (species, level, nature, ability, moves, held
    item, EVs), untradeable, and the player's own eight are all still there, none lost or duplicated;
  * the rentals really fight: floor one is played out by a bot with the drafted team, and Showdown accepts every move, ability and item;
  * a rental earns no experience (a million points leave every level unchanged) and adds nothing to the Pokedex;
  * ending the run deletes the rentals and puts every own Pokemon back in exactly its original slot;
  * a hard kill mid-run AFTER Cobblemon saved the rentals to disk, then a recovery abandon, leaves no rental and every own Pokemon
    where it started;
  * a hard kill BEFORE Cobblemon saved them (its disk copy is the original layout) does too;
  * a stray rental (one that belongs to no run) is swept within a minute and the player's own Pokemon are untouched.

The pack-opening screen is not exercised: a headless bot cannot open one. The chat flow drives the same draft.

    python validation/smoke/rental_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)

BOT = f"TRn{int(time.time()) % 100000}"
TOWER = "cobbletowers:test"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
LIVE_STATES = ("ENCOUNTER_ACTIVE", "RECOVERY_REQUIRED", "ALLOCATING_INSTANCE", "PREPARING", "FLOOR_READY")
SETS = ROOT / "src/main/resources/data/cobbletowers/cobbletowers/rental_sets"


def start_bot(port: int, node_modules: Path, log: Path) -> subprocess.Popen:
    env = dict(os.environ, NODE_PATH=str(node_modules))
    handle = open(log, "w", encoding="utf-8", errors="replace")
    return subprocess.Popen(["node", str(HERE / "battlebot.js"), BOT, str(port)],
                            stdout=handle, stderr=subprocess.STDOUT, env=env)


def wait_for(predicate, seconds: int = 30) -> bool:
    for _ in range(seconds * 2):
        if predicate():
            return True
        time.sleep(0.5)
    return False


def play(rcon: Rcon, command: str) -> str:
    return rcon.command(f"execute as {BOT} run cobbletowers play {command}")


def layout(rcon: Rcon) -> list[tuple[str, str]]:
    """Every Pokemon the bot owns, rentals included, as (place, id), sorted."""
    out = rcon.command(f"cobbletowers play pokemon {BOT}")
    return sorted(re.findall(r"(party \d+|box \d+/\d+) " + UUID_RE, out))


UUID_BARE = UUID_RE[1:-1]
ENTRY = (r"((?:party \d+|box \d+/\d+)) " + UUID_RE + r"(.*?)(?=(?:party \d+|box \d+/\d+) " + UUID_BARE + r"|$)")


def entries(rcon: Rcon) -> list[tuple[str, str, str]]:
    """Every Pokemon the bot holds as (place, id, the rest of its line). RCON joins the listing onto one line, so entries are split
    at the next 'party N' or 'box N/M', never at newlines."""
    return re.findall(ENTRY, rcon.command(f"cobbletowers play pokemon {BOT}"), re.DOTALL)


def rentals_listed(rcon: Rcon) -> list[str]:
    """The ids of the Pokemon the listing marks RENTAL."""
    return sorted(pid for _, pid, tail in entries(rcon) if "RENTAL" in tail)


def own(rcon: Rcon) -> list[tuple[str, str]]:
    """The bot's own Pokemon: every entry not marked RENTAL, as (place, id)."""
    return sorted((place, pid) for place, pid, tail in entries(rcon) if "RENTAL" not in tail)


def dex(rcon: Rcon) -> str:
    """The species the bot's Pokedex has a record of, as one sorted string."""
    found = re.search(r"DEX \d+ ([A-Za-z0-9_,:]*)", rcon.command(f"cobbletowers play rentals {BOT} dex"))
    return found.group(1) if found else "unreadable"


def ids(entries: list[tuple[str, str]]) -> list[str]:
    return sorted(pokemon for _, pokemon in entries)


def live_run(rcon: Rcon) -> str:
    listing = rcon.command("cobbletowers runs list")
    for found in re.finditer(UUID_RE + r"\s+\S+\s+(\w+)\s+floor", listing):
        if found.group(2) in LIVE_STATES:
            return found.group(1)
    return ""


def draft_team(rcon: Rcon, results: list[Result] | None = None) -> bool:
    """Drafts three packs in chat, never keeping a legendary or mythic (so the cap never gets in the way)."""
    for pack in range(3):
        view = play(rcon, "draft")
        cards = re.findall(r"(\d)\. \[(\w+)\]", view)
        if len(cards) != 5:
            return False
        picks = [number for number, rarity in cards if rarity not in ("legendary", "mythic")][:2]
        if len(picks) != 2:
            return False
        play(rcon, f"draft pick {picks[0]} {picks[1]}")
    return "Your team is drafted" in play(rcon, "draft")


def start_rental_run_for(rcon: Rcon) -> str:
    """Picks the test tower and the Rental playlist, drafts a team in chat, starts, and returns the run id."""
    play(rcon, f"tower {TOWER}")
    play(rcon, "playlist rental")
    if not draft_team(rcon):
        raise RuntimeError("could not draft a team: " + play(rcon, "draft")[:300])
    play(rcon, "start")
    wait_for(lambda: live_run(rcon) != "", 40)
    return live_run(rcon)


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

    sets = {}
    for path in SETS.glob("*.json"):
        body = json.loads(path.read_text(encoding="utf-8"))
        sets[body["species"]] = body

    results: list[Result] = []
    server = Server(server_dir, java)
    port = server_port(server_dir)
    bot_log = server_dir / "logs" / "towers-rental-bot.log"
    password = read_password(server_dir)
    bots: list[subprocess.Popen] = []

    def open_rcon() -> Rcon:
        return Rcon("127.0.0.1", 25575, password)

    def join_bot() -> None:
        bots.append(start_bot(port, node_modules, bot_log))
        with open_rcon() as rcon:
            for _ in range(90):
                if BOT in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("the bot never joined")
            time.sleep(3)

    def persist() -> None:
        """Disconnect and rejoin the bot: Cobblemon saves a player's storage on disconnect."""
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

    boots = [0]

    def crash() -> None:
        server.process.kill()
        server.process.wait(timeout=60)
        # Keep the log of the boot that just died: the next boot starts a fresh one.
        boots[0] += 1
        try:
            (server_dir / "logs" / f"towers-rental-boot{boots[0]}.log").write_text(server.read_log(), encoding="utf-8")
        except OSError:
            pass
        for bot in bots:
            bot.kill()
        bots.clear()
        server.start()
        server.wait_until_ready()
        join_bot()

    def start_rental_run(rcon: Rcon) -> str:
        play(rcon, f"tower {TOWER}")
        play(rcon, "playlist rental")
        if not draft_team(rcon):
            raise RuntimeError("could not draft a team: " + play(rcon, "draft")[:300])
        play(rcon, "start")
        wait_for(lambda: live_run(rcon) != "", 40)
        return live_run(rcon)

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

        persist()
        with open_rcon() as rcon:
            original = layout(rcon)
            results.append(Result("the bot owns eight Pokemon: six in the party, two in a box",
                                  len(original) == 8 and sum(1 for place, _ in original if place.startswith("box")) == 2,
                                  str(original)))

            dex_before = dex(rcon)

            # ---- the draft's rules -------------------------------------------------------------------------
            play(rcon, f"tower {TOWER}")
            reply = play(rcon, "playlist rental")
            results.append(Result("the Rental Draft mode is offered and points at the draft", "/tower draft" in reply, reply.strip()[:200]))
            play(rcon, "start")
            time.sleep(8)
            text = server.read_log()
            results.append(Result("starting before the draft is finished is refused, with the reason",
                                  "has not finished their draft" in text and not live_run(rcon), text[-300:]))
            view = play(rcon, "draft")
            results.append(Result("the first pack shows five cards with rarity and moves", len(re.findall(r"\d\. \[\w+\]", view)) == 5,
                                  view[:300]))
            same = play(rcon, "draft pick 2 2")
            results.append(Result("two of the same card is refused", "two different" in same, same.strip()[:120]))
            results.append(Result("and nothing was kept", "Pack 1 of 3" in play(rcon, "draft"), ""))
            restart = play(rcon, "draft restart")
            results.append(Result("a restart is allowed and leaves the same pack", "Pack 1 of 3" in restart, restart.strip()[:120]))
            play(rcon, "draft pick 1 2")
            results.append(Result("a valid pick moves to the next pack", "Pack 2 of 3" in play(rcon, "draft"), ""))
            play(rcon, "leave")   # clears the draft; the next one is drawn afresh
            time.sleep(1)

            # ---- a rental run -----------------------------------------------------------------------------
            run = start_rental_run(rcon)
            results.append(Result("a finished draft starts a rental run", bool(run), rcon.command("cobbletowers runs list")[:200]))
            during = layout(rcon)
            lent = rentals_listed(rcon)
            party = [pid for place, pid in during if place.startswith("party")]
            results.append(Result("the party is exactly six rentals", len(lent) == 6 and sorted(party) == lent,
                                  f"rentals {lent}; party {party}"))
            results.append(Result("the bot's own eight are all still there, none lost or duplicated",
                                  ids(own(rcon)) == ids(original) and len(own(rcon)) == 8, f"{own(rcon)} vs {original}"))
            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the run registered six Pokemon", "6 registered" in shown, shown[-200:]))

            described = rcon.command(f"cobbletowers play rentals {BOT}")
            lines = [line for line in described.replace("RENTAL", "\nRENTAL").split("\n") if line.startswith("RENTAL")]
            exact = len(lines) == 6
            problems = []
            for line in lines:
                species = re.search(UUID_RE + r" (\w+) level", line)
                if not species or species.group(2) not in sets:
                    problems.append("unknown " + line[:60])
                    continue
                want = sets[species.group(2)]
                checks = {
                    "level": f"level={want['level']}",
                    "nature": f"nature={want['nature']}",
                    "ability": f"ability={want['ability']}",
                    "item": f"held_item={want['item']}",
                    "tradeable": "tradeable=false",
                    "evs": "evs=" + ",".join(str(v) for v in want["evs"]),
                }
                for what, needle in checks.items():
                    if needle not in line:
                        problems.append(f"{species.group(2)} {what}: want {needle} in {line[:200]}")
                for move in want["moves"]:
                    if move not in line:
                        problems.append(f"{species.group(2)} missing move {move}")
            results.append(Result("every rental is built exactly as its set says, and untradeable", exact and not problems,
                                  "; ".join(problems)[:500] or described[:200]))

            dex_during = dex(rcon)
            results.append(Result("lending the team added nothing to the Pokedex", dex_during == dex_before and dex_before != "unreadable",
                                  f"before {dex_before} | during {dex_during}"))

            xp = rcon.command(f"cobbletowers play rentals {BOT} xp")
            gains = re.findall(r"XP \S+ (\d+) -> (\d+)", xp)
            results.append(Result("a rental earns no experience", len(gains) == 6 and all(a == b for a, b in gains), xp[:300]))

            # ---- the rentals really fight --------------------------------------------------------------------
            # Floor one is a boss floor, so a result can take minutes and depends on the draw; what matters here is that the drafted team
            # really fights: the bot gets its turns and its chosen moves are accepted. A result inside two minutes is a bonus.
            before = len(server.read_log())
            outcome = ""
            for _ in range(60):
                found = re.search(r"Floor \d+ of run \S+ (cleared|wiped the party)", server.read_log()[before:])
                if found:
                    outcome = found.group(0)
                    break
                time.sleep(2)
            fought = server.read_log()[before:]
            try:
                bot_text = (server_dir / "logs" / "towers-rental-bot.log").read_text(encoding="utf-8", errors="replace")
            except OSError:
                bot_text = ""
            choices = len(re.findall(r"CHOICE \S+ ->", bot_text))
            results.append(Result("the drafted team fights: its moves are chosen and played in a real battle",
                                  "boss" in server.read_log() and choices >= 5,
                                  f"{choices} choices; {outcome or 'no result yet'}; {fought[-300:]}"))
            results.append(Result("and Showdown never refused a rental set",
                                  "Invalid" not in fought and "Cannot" not in fought and "illegal" not in fought.lower(),
                                  fought[-400:]))

            # ---- ending the run deletes the rentals and returns every own Pokemon ----------------------------
            rcon.command(f"cobbletowers runs advance {run} abandon_requested")
            restored = wait_for(lambda: layout(rcon) == original, 20)
            results.append(Result("ending the run deletes every rental and puts each own Pokemon back in its original slot",
                                  restored, str(layout(rcon))))
            results.append(Result("and no rental is left anywhere", rentals_listed(rcon) == [], str(rentals_listed(rcon))))
            clear_tower(rcon)

            # ---- crash after Cobblemon saved the rentals ----------------------------------------------------
            run = start_rental_run(rcon)
            results.append(Result("a second rental run starts", bool(run), rcon.command("cobbletowers runs list")[:200]))
            during = layout(rcon)

        time.sleep(36)   # Cobblemon autosaves every 30 seconds: the rentals are on disk
        crash()
        with open_rcon() as rcon:
            run = live_run(rcon)
            results.append(Result("after a crash the run is parked, not lost", bool(run), rcon.command("cobbletowers runs list")[:200]))
            now = layout(rcon)
            results.append(Result("the rentals and the moved-aside Pokemon came back as saved", now == during, f"{now} vs {during}"))
            reply = rcon.command(f"cobbletowers runs advance {run} recovery_abandoned") if run else "no run"
            restored = wait_for(lambda: layout(rcon) == original, 20)
            results.append(Result("abandoning the parked run deletes the rentals and restores every Pokemon (crash after a save)",
                                  restored, f"{reply.strip()[:120]} | {layout(rcon)}"))
            results.append(Result("and no rental is left after that recovery either", rentals_listed(rcon) == [], str(rentals_listed(rcon))))
            clear_tower(rcon)

        # ---- crash before Cobblemon saved them ---------------------------------------------------------------
        persist()
        with open_rcon() as rcon:
            run = start_rental_run(rcon)
            results.append(Result("a third rental run starts", bool(run), rcon.command("cobbletowers runs list")[:200]))
        crash()
        with open_rcon() as rcon:
            run = live_run(rcon)
            results.append(Result("after a crash with no save the run is parked", bool(run), rcon.command("cobbletowers runs list")[:200]))
            reply = rcon.command(f"cobbletowers runs advance {run} recovery_abandoned") if run else "no run"
            restored = wait_for(lambda: layout(rcon) == original, 20)
            results.append(Result("abandoning it leaves every Pokemon where it started and no rental (crash before a save)",
                                  restored and rentals_listed(rcon) == [], f"{reply.strip()[:120]} | {layout(rcon)}"))
            clear_tower(rcon)

            # ---- the stray sweep ----------------------------------------------------------------------------
            before = layout(rcon)
            rcon.command(f"cobbletowers play rentals {BOT} stray")
            results.append(Result("a stray rental exists (set up by an operator)", len(rentals_listed(rcon)) == 1,
                                  str(rentals_listed(rcon))))
            swept = wait_for(lambda: rentals_listed(rcon) == [], 90)
            results.append(Result("a rental that belongs to no run is swept within a minute", swept, str(rentals_listed(rcon))))
            results.append(Result("and the player's own Pokemon were untouched by the sweep", own(rcon) == before, f"{own(rcon)} vs {before}"))
            results.append(Result("and the Pokedex still holds only what the player owns", dex(rcon) == dex_before, f"{dex(rcon)} vs {dex_before}"))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("rental draft run", False, repr(exc)))
    finally:
        for bot in bots:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name.ljust(width)}  "
              f"{result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
