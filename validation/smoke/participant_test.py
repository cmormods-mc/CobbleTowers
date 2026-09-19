#!/usr/bin/env python3
"""Drives P7 against a real server: leaving, coming back, being dropped, and cleaning up after a crash.

Everything here is a path no unit test can reach, because every one of them is about a real
connection or a real cell:

  * a player who disconnects mid-floor goes DISCONNECTED and **keeps their combat state**, their
    battle ends, and the floor carries on for everybody else;
  * they come back inside the window as a **spectator**, in the tower, not fighting;
  * the floor clears, they are owed the intermission, and the intermission gives them back;
  * `/cobbletowers runs leave` is terminal, and the last player leaving ends the run;
  * **the watchdog really drops a stalled player** -- the same sweep the tick runs, with the clock
    wound forward by the command, so the ten-minute cap does not have to be waited out;
  * a hard kill mid-floor leaves entities in the cell, and **recovery sweeps them** rather than
    leaving the next release to quarantine the cell over them.

    python validation/smoke/participant_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
from floor_encounter_test import (  # noqa: E402
    FIRST_MOVE, TOWER, begin_floor, give_party, start_battle_bot, tell_bot, wait_for, wait_for_floor,
)

STAMP = int(time.time()) % 100000
# Fresh names per run: pokegiveother ADDS to a party, so a reused name accumulates earlier tests'
# fillers until the lead is a level 1 Magikarp. Sixteen characters is Minecraft's limit.
LEAVER = f"TPa{STAMP}"
STAYER = f"TPb{STAMP}"
SOLO = f"TPc{STAMP}"


def wait_joined(rcon: Rcon, name: str, seconds: int = 90) -> bool:
    """Waits for one named bot. The durability test's wait_online only ever watches its own bot."""
    for _ in range(seconds):
        if name in rcon.command("list"):
            return True
        time.sleep(1)
    return False


def arm(rig: Path, name: str) -> None:
    """The rig's bot answers only a prompt that arrives after FIGHT, and never re-prompts."""
    tell_bot(rig, name, "FIGHT")
    tell_bot(rig, name, f"MOVE {FIRST_MOVE}")


def state_of(shown: str, player_id: str) -> str:
    for line in shown.splitlines():
        if player_id in line and "/" in line:
            return line.strip()
    return ""


def uuid_of(rcon: Rcon, name: str) -> str:
    # The run prints participants by UUID, and offline-mode UUIDs are derived from the name, so the
    # same bot name reconnects as the same participant -- which is the whole point of the test.
    answer = rcon.command(f"data get entity {name} UUID")
    numbers = [int(part) for part in re.findall(r"-?\d+", answer.split(":", 1)[-1])]
    if len(numbers) < 4:
        return ""
    raw = "".join(f"{number & 0xFFFFFFFF:08x}" for number in numbers[:4])
    return f"{raw[0:8]}-{raw[8:12]}-{raw[12:16]}-{raw[16:20]}-{raw[20:32]}"


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
    bots: dict[str, subprocess.Popen] = {}
    first_boot_log = ""
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        for name in (LEAVER, STAYER):
            bots[name] = start_battle_bot(rig, name, FIRST_MOVE)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (LEAVER, STAYER):
                if not wait_joined(rcon, name):
                    raise RuntimeError(f"{name} never joined; see {rig}/bot/{name}.log")
            clear_tower(rcon)
            for name in (LEAVER, STAYER):
                give_party(rcon, name)
            leaver_id = uuid_of(rcon, LEAVER)
            stayer_id = uuid_of(rcon, STAYER)

            # --- a floor with two players, one of whom walks out of it -------------------------
            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            for name in (LEAVER, STAYER):
                arm(rig, name)
                if not wait_for(rig / "bot" / f"{name}.log", r"AUTOFIGHT on", seconds=30):
                    raise RuntimeError(f"{name} never armed; see {rig}/bot/{name}.log")

            begun = begin_floor(rcon, run)
            results.append(Result("a two-player floor begins", "2 opponent(s)" in begun, begun.strip()[:200]))

            print(f"Killing {LEAVER}'s connection mid-floor")
            bots.pop(LEAVER).kill()
            dropped = wait_for(server.log, r"Player \S+ left run \S+ mid-floor", seconds=60)
            results.append(Result("a disconnect mid-floor is noticed", bool(dropped), "no disconnect line"))

            time.sleep(2)
            gone = rcon.command(f"cobbletowers runs show {run}")
            leaver_line = state_of(gone, leaver_id)
            results.append(Result("the player who left is DISCONNECTED, not ejected from the run",
                                  "DISCONNECTED" in leaver_line and "MEMBER" in leaver_line
                                  and "VOLUNTARILY_LEFT" not in leaver_line, leaver_line or gone[:240]))
            results.append(Result("their battle ended and the floor recorded them as out",
                                  f"{leaver_id}=OUT" in gone.replace(" ", ""), gone.strip()[:300]))
            results.append(Result("the floor carried on for the player who stayed",
                                  f"{stayer_id}=FIGHTING" in gone.replace(" ", ""), gone.strip()[:300]))

            print(f"Reconnecting {LEAVER} inside the window")
            bots[LEAVER] = start_battle_bot(rig, LEAVER, FIRST_MOVE)
            back = wait_for(server.log, r"Player \S+ rejoined run \S+ as a spectator", seconds=90)
            results.append(Result("coming back inside the window is a reconnect, not a new run",
                                  bool(back), "no rejoin line; see " + str(server.log)))
            results.append(Result("they did not come back past their window",
                                  "after their window closed" not in back, back))

            time.sleep(2)
            rejoined = rcon.command(f"cobbletowers runs show {run}")
            rejoined_line = state_of(rejoined, leaver_id)
            results.append(Result("they are back online and spectating, not fighting",
                                  "ONLINE" in rejoined_line and "SPECTATING_TEAM" in rejoined_line,
                                  rejoined_line or rejoined[:240]))
            where = rcon.command(f"data get entity {LEAVER} Dimension")
            results.append(Result("a spectator is put in the tower, not left where they logged out",
                                  "cobbletowers:tower" in where, where.strip()[:200]))

            # --- the floor finishes, one way or the other --------------------------------------
            #
            # Which way is up to a bot with a real party against a real draw, so this does not
            # pretend to know: both outcomes are P7 paths worth proving, and the branch says which
            # one ran. What is asserted unconditionally is that the floor RESOLVED -- a floor one
            # player walked out of must not hang holding the run, the cell and its tickets.
            resolved = wait_for_floor(server, rig, [STAYER, LEAVER])
            print(f"  floor outcome: {resolved or '<none>'}")
            for drew in re.findall(r"battle \S+ started: (\S+) vs (\S+) at level (\d+)", server.read_log()):
                print(f"  drew {drew[1]} at level {drew[2]} for {drew[0]}")
            results.append(Result("a floor one player walked out of still resolves rather than hanging",
                                  bool(resolved), "no outcome; see " + str(server.log)))
            cleared = "cleared" in resolved

            if cleared:
                owed = rcon.command(f"cobbletowers runs show {run}")
                results.append(Result("a spectator is owed the intermission once the floor is done",
                                      "REVIVE_PENDING" in state_of(owed, leaver_id), state_of(owed, leaver_id)))

                rcon.command(f"cobbletowers runs advance {run} rewards_banked")
                revived = rcon.command(f"cobbletowers runs show {run}")
                results.append(Result("the intermission puts them back in the fight",
                                      "INTERMISSION" in revived and "ACTIVE" in state_of(revived, leaver_id),
                                      state_of(revived, leaver_id) or revived[:240]))
            else:
                wiped = rcon.command(f"cobbletowers runs show {run}")
                results.append(Result("a wiped floor forfeits the pool rather than banking it",
                                      "POOL_FORFEITED" in wiped, wiped.strip()[:300]))
                results.append(Result("a player who was only spectating is still in the run afterwards",
                                      "MEMBER" in state_of(wiped, leaver_id), state_of(wiped, leaver_id)))

            # --- leaving on purpose ------------------------------------------------------------
            #
            # Only meaningful while the run is still live; a wiped run has already ended.
            if cleared:
                rcon.command(f"execute as {LEAVER} run cobbletowers runs leave")
                left = rcon.command(f"cobbletowers runs show {run}")
                results.append(Result("leaving on purpose is recorded as a choice, not a disconnect",
                                      "VOLUNTARILY_LEFT" in state_of(left, leaver_id), state_of(left, leaver_id)))
                results.append(Result("one player leaving does not end the run",
                                      not any(word in left.splitlines()[0] for word in ("ABANDONED", "FAILED")),
                                      left.splitlines()[0][:200]))

                rcon.command(f"execute as {STAYER} run cobbletowers runs leave")
                empty = rcon.command(f"cobbletowers runs show {run}")
                results.append(Result("the last player leaving ends the run",
                                      "ABANDONED" in empty.splitlines()[0], empty.splitlines()[0][:200]))

            # --- the watchdog, on a real floor, with the clock wound forward -------------------
            print("Starting a solo floor for the watchdog")
            bots[SOLO] = start_battle_bot(rig, SOLO, FIRST_MOVE)
            if not wait_joined(rcon, SOLO):
                raise RuntimeError(f"{SOLO} never joined; see {rig}/bot/{SOLO}.log")
            give_party(rcon, SOLO)
            watched = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {SOLO}"))
            rcon.command(f"cobbletowers runs advance {watched} party_submitted")
            rcon.command(f"cobbletowers runs advance {watched} party_validated")
            rcon.command(f"cobbletowers runs allocate {watched}")
            # Deliberately NOT armed: this bot never answers its prompt, which is exactly the floor
            # the watchdog exists for -- the one P5 hit when a lead had no move it could cast.
            begin_floor(rcon, watched)
            time.sleep(3)
            open_floor = rcon.command(f"cobbletowers runs show {watched}")
            results.append(Result("the watchdog's floor is genuinely open before it is swept",
                                  "FIGHTING" in open_floor, open_floor.strip()[:240]))

            swept = rcon.command("cobbletowers runs watchdog player")
            print(f"  {swept.strip()}")
            time.sleep(1)
            log_now = server.read_log()
            results.append(Result("the watchdog drops a player who has stopped answering",
                                  bool(re.search(r"has not moved floor \d+ of run \S+", log_now)),
                                  "no watchdog drop line; see " + str(server.log)))
            after_sweep = rcon.command(f"cobbletowers runs show {watched}")
            results.append(Result("a floor everybody was dropped from settles rather than hanging",
                                  "FIGHTING" not in after_sweep, after_sweep.strip()[:240]))
            results.append(Result("a dropped floor is a wipe, not a silent stall",
                                  "FAILED" in after_sweep.splitlines()[0] or "POOL_FORFEITED" in after_sweep,
                                  after_sweep.splitlines()[0][:200]))
            results.append(Result("the watchdog did not touch the floors that were being played",
                                  log_now.count("stopped answering") == 1,
                                  f"{log_now.count('stopped answering')} drops"))

        # --- a crash mid-floor, and the cell it leaves behind ---------------------------------
        print("Starting one more floor, then killing the server on top of it")
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            crashed = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {SOLO}"))
            rcon.command(f"cobbletowers runs advance {crashed} party_submitted")
            rcon.command(f"cobbletowers runs advance {crashed} party_validated")
            rcon.command(f"cobbletowers runs allocate {crashed}")
            begin_floor(rcon, crashed)
            time.sleep(4)
            # `data get entity`, not `execute ... run say`: a say inside an execute prints to chat and
            # returns NOTHING over RCON, so the obvious probe reads as "nothing there" whatever the
            # truth is. This one answers in the reply.
            standing = rcon.command(
                "execute in cobbletowers:tower run data get entity @e[type=cobblemon:pokemon,limit=1] UUID")
            results.append(Result("something really is standing in the cell when the server dies",
                                  "entity data" in standing, standing.strip()[:200]))
            # A kill -9 loses everything not yet written, entities included, so without this the
            # restart finds an empty cell and the sweep has nothing to find -- the test would prove
            # only that the harness can delete a Pokemon. A real crash follows an autosave; this is
            # that autosave.
            rcon.command("save-all flush")
            time.sleep(3)

        first_boot_log = server.read_log()
        server.process.kill()
        server.process.wait(timeout=60)
        for bot in bots.values():
            bot.kill()
        bots = {}

        server.start()
        server.wait_until_ready()
        # The count is the proof, and it is the only honest one available. `cells verify` after the
        # fact would report clean whatever the truth is: by then the sweep has dropped the tickets,
        # and an unloaded chunk has no entities to report -- P4's known limit, and exactly the shape
        # of "reports clean and is believed" that this whole phase exists to stop.
        swept_line = wait_for(server.log, r"Cell \d+ of interrupted run \S+ had \d+ entity", seconds=120)
        print(f"  {swept_line or '<no sweep line>'}")
        found = int(re.search(r"had (\d+) entity", swept_line).group(1)) if swept_line else 0
        results.append(Result("recovery sweeps the cell of the run it parks, and finds what was left in it",
                              found > 0, "no sweep line; see " + str(server.log)))
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            parked = rcon.command(f"cobbletowers runs show {crashed}")
            results.append(Result("the interrupted run is parked, not lost",
                                  "RECOVERY_REQUIRED" in parked, parked.splitlines()[0][:200]))
            results.append(Result("the parked run kept the cell it will resume into",
                                  "cell none" not in parked, parked.strip()[:240]))
            results.append(Result("the tickets taken to sweep the cell were given back",
                                  "chunks released" in server.read_log(), "no release line"))

        both_logs = first_boot_log + server.read_log()
        results.append(Result("no CobbleTowers exception during any of it",
                              "com.cobbletowers" not in both_logs.replace("com.cobbletowers.CobbleTowers", ""),
                              "see the trace above; the first boot's log was kept"))
        ours = [line for line in both_logs.splitlines()
                if "cobbletowers" in line.lower() or "\tat com.cobbletowers" in line]
        if ours:
            print("  --- what the server said about CobbleTowers ---")
            for line in ours[-25:]:
                print("  " + line[:220])
    except Exception as exc:  # noqa: BLE001
        results.append(Result("participant run", False, repr(exc)))
    finally:
        for bot in bots.values():
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
