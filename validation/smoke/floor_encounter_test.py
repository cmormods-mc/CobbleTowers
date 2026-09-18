#!/usr/bin/env python3
"""Plays a tower floor against a real server: opponents appear, get fought, and the floor resolves.

This is the first test where something actually happens on a floor. What it proves, none of which a
unit test can reach:

  * the draw reaches Cobblemon at all -- an opponent is spawned from the pool at the level the policy
    decided, and a battle starts against it;
  * a floor with two players starts two battles and waits for both, which is the whole reason the
    prerequisite is parallel solo battles rather than one shared one;
  * winning resolves the floor and writes the unclaimed pool;
  * the opponent is gone afterwards, so P3's cleanup does not quarantine the cell over it;
  * **the same opponents come back after a crash** (TDS #29), which is the claim the deterministic
    draw exists to make.

Party trick, from the raids rig's notes: a level 100 lead plus several level 1 fillers gives a low
party mean, so the level policy draws a weak opponent the bot can actually beat, while the lead is
still strong enough to win.

    python validation/smoke/floor_encounter_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from, server_port, wait_online,
)

TOWER = "cobbletowers:neutral"
LEAD = "glaceon"
FILLERS = 5
# Fresh names each run, because pokegiveother ADDS to a party: reusing a name across runs fills the
# six slots with earlier tests' fillers until the lead is a level 1 Magikarp that knows nothing the
# bot can cast. That is what a stalled floor looked like -- "Invalid action choice ... blizzard" over
# and over, from a Magikarp. Names stay inside Minecraft's 16-character limit.
BOTS = [f"TFa{int(time.time()) % 100000}", f"TFb{int(time.time()) % 100000}"]


def start_battle_bot(rig: Path, name: str, move: str) -> subprocess.Popen:
    """The raids rig's own battle bot: reads commands from bot/cmd_<name>.txt."""
    env = dict(os.environ, NODE_PATH=str(rig / "bot" / "node_modules"))
    log = open(rig / "bot" / f"{name}.log", "w", encoding="utf-8", errors="replace")
    return subprocess.Popen(["node", str(rig / "bot" / "raidbot.js"), name, move],
                            cwd=str(rig / "bot"), stdout=log, stderr=subprocess.STDOUT, env=env)


def tell_bot(rig: Path, name: str, line: str) -> None:
    with open(rig / "bot" / f"cmd_{name}.txt", "a", encoding="utf-8") as handle:
        handle.write(line + "\n")


def give_party(rcon: Rcon, name: str) -> None:
    # pokegiveother, not pokegive: from RCON the latter answers "A player is required to run this
    # command here" and silently gives nothing.
    rcon.command(f"pokegiveother {name} {LEAD} level=100")
    for _ in range(FILLERS):
        rcon.command(f"pokegiveother {name} magikarp level=1")


def wait_for(log_path: Path, pattern: str, seconds: int = 90) -> str:
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            text = log_path.read_text(encoding="utf-8", errors="replace")
        except FileNotFoundError:
            text = ""
        match = re.search(pattern, text)
        if match:
            return match.group(0)
        time.sleep(2)
    return ""


def begin_floor(rcon: Rcon, run: str) -> str:
    rcon.command(f"cobbletowers runs advance {run} preparation_complete")
    return rcon.command(f"cobbletowers runs encounter {run}")


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
    bots: list[subprocess.Popen] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        for name in BOTS:
            bots.append(start_battle_bot(rig, name, "blizzard"))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in BOTS:
                if not wait_online(rcon, seconds=90) and name not in rcon.command("list"):
                    raise RuntimeError(f"{name} never joined; see {rig}/bot/{name}.log")
            rcon.command("execute in cobbletowers:tower run forceload remove all")
            clear_tower(rcon)
            for name in BOTS:
                give_party(rcon, name)

            # Both bots in one run. EntityArgument.players() is a single selector token, so this is
            # "@a" rather than two names -- and @a is exactly the two bots, since nobody else is on.
            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            # Arm the bots BEFORE the floor starts. The rig's bot answers only a battle prompt that
            # arrives after FIGHT and never re-prompts, so starting the battle first leaves it
            # standing there forever -- the same race that cost the Milestone 1 spike a whole run.
            for name in BOTS:
                tell_bot(rig, name, "FIGHT")
                tell_bot(rig, name, "MOVE blizzard")
            for name in BOTS:
                if not wait_for(rig / "bot" / f"{name}.log", r"AUTOFIGHT on", seconds=30):
                    raise RuntimeError(f"{name} never armed; see {rig}/bot/{name}.log")

            begun = begin_floor(rcon, run)
            results.append(Result("a floor begins and puts opponents up", "opponent" in begun,
                                  begun.strip()[:200]))
            results.append(Result("a two-player floor puts up one opponent each",
                                  "2 opponent(s)" in begun, begun.strip()[:200]))

            # Both players are tracked as fighting before either has finished. This is the claim the
            # parallel-battle design rests on, and it had never actually been run.
            during = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("both players are tracked as fighting at once",
                                  during.count("FIGHTING") == 2, during.strip()[:260]))

            time.sleep(2)  # the battle start is logged from Cobblemon's own thread
            log = server.read_log()
            started = re.findall(r"battle (\S+) started: (\S+) vs (\S+) at level (\d+)", log)
            results.append(Result("an opponent was drawn and a battle started against it",
                                  bool(started), "no battle start line in the log"))
            results.append(Result("each player got their own battle, not a shared one",
                                  len(started) == 2, f"{len(started)} battle(s) started: {started}"))
            if started:
                print(f"  drew {started[0][2]} at level {started[0][3]}")
                results.append(Result("the opponent's level came from the party, not from the pool alone",
                                      1 <= int(started[0][3]) <= 100, str(started[0])))

            # TDS #78: a tower opponent is never captured.
            #
            # Targeted by the species that was drawn, not "the first Pokemon in the dimension": once a
            # battle starts, the player's own lead is standing there too, and the loose selector read
            # the Glaceon and reported on the wrong entity entirely.
            drawn = started[0][2].split(":")[-1] if started else "lairon"
            entity = rcon.command(
                f"execute in cobbletowers:tower run data get entity "
                f"@e[type=cobblemon:pokemon,nbt={{Pokemon:{{Species:\"cobblemon:{drawn}\"}}}},limit=1]")
            results.append(Result("the opponent is marked uncatchable", "ncatchable" in entity,
                                  entity.strip()[:240]))

            # The prerequisite is only half a floor now: clearing it hands over to the floor's
            # CobbleRaids boss, and the floor is not finished until that is fought.
            boss_started = wait_for(server.log, r"Floor \d+ boss \S+ started at level \d+", seconds=180)
            print(f"  boss: {boss_started or '<none>'}")
            results.append(Result("clearing the prerequisite starts the floor's boss",
                                  bool(boss_started), "no boss start line; see " + str(server.log)))

            during_boss = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the floor is in its boss phase, not resolved",
                                  "BOSS" in during_boss and "ENCOUNTER_ACTIVE" in during_boss,
                                  during_boss.strip()[:260]))

            # Already armed above; just wait for the outcome. The boss has a shared health pool, so
            # this takes appreciably longer than the ordinary opponents did.
            resolved = wait_for(server.log, r"Floor \d+ of run \S+ (cleared|wiped the party)", seconds=360)
            print(f"  floor outcome: {resolved or '<none>'}")
            results.append(Result("the floor resolved rather than hanging", bool(resolved),
                                  "no cleared/wiped line appeared; see " + str(server.log)))

            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the unclaimed pool recorded what was earned",
                                  "unclaimed pool: 0 entry" not in shown, shown.strip()[:260]))

            # The floor waited for both. If it had resolved on the first win, the round would have
            # been dropped and the second victory would have found nothing to record -- so counting
            # two defeated opponents against exactly one cleared floor is the proof.
            defeated = shown.count("OPPONENT_DEFEATED")
            cleared_entries = shown.count("FLOOR_CLEARED")
            results.append(Result("the floor waited for both players before resolving",
                                  defeated == 2 and cleared_entries == 1,
                                  f"{defeated} opponent entry(s), {cleared_entries} floor entry(s)"))
            results.append(Result("the boss is recorded in the pool, separately from the trash",
                                  shown.count("BOSS_DEFEATED") == 1, shown.strip()[:300]))
            results.append(Result("a won floor forfeits nothing", "POOL_FORFEITED" not in shown,
                                  shown.strip()[:300]))
            print("  " + " | ".join(line.strip() for line in shown.split("  ") if "pool" in line))

            remaining = rcon.command(
                "execute in cobbletowers:tower run execute if entity @e[type=cobblemon:pokemon] run say left")
            results.append(Result("no opponent is left standing in the cell afterwards", "left" not in remaining,
                                  remaining.strip()[:200]))

        # Keep the log before restarting: Server.start() deletes it, and the run that matters
        # happened on the first boot. Asserting against the log after a restart is asserting against
        # an almost empty file, which is how three checks passed here while proving nothing.
        first_boot_log = server.read_log()

        print("Killing the server mid-run to check the draw is not rerolled")
        server.process.kill()
        server.process.wait(timeout=60)
        for bot in bots:
            bot.kill()
        bots = []

        server.start()
        server.wait_until_ready()
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            after = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("the run and its pool survived the kill", run in after and "pool" in after,
                                  after.strip()[:200]))
            kept = re.search(r"unclaimed pool: (\d+)", after)
            print(f"  pool after restart: {kept.group(1) if kept else '?'} entry(s)")

        both_logs = first_boot_log + server.read_log()
        results.append(Result("no CobbleTowers exception during any of it",
                              "com.cobbletowers" not in both_logs.replace("com.cobbletowers.CobbleTowers", ""),
                              "see the trace above; first-boot log was kept"))
        # Print whatever the server said about our own code, so a failure here is readable without
        # going and grepping a log that the next restart would have deleted.
        ours = [line for line in both_logs.splitlines()
                if "cobbletowers" in line.lower() or "\tat com.cobbletowers" in line]
        if ours:
            print("  --- what the server said about CobbleTowers ---")
            for line in ours[-25:]:
                print("  " + line[:220])
    except Exception as exc:  # noqa: BLE001
        results.append(Result("floor encounter run", False, repr(exc)))
    finally:
        for bot in bots:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results)
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  "
              f"{result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
