#!/usr/bin/env python3
"""Proves P32a's Playlists against a real server, on the four-floor test tower.

Two players: A has a Glaceon (level 100, Ice, fully evolved) and five Magikarp (level 1, Water); B has only Magikarp.

  * the lobby refuses a party that breaks the clauses and names the Pokemon: Monotype (Glaceon is not a Water type),
    Level Cap 50 (Glaceon is level 100), Underdog (Glaceon is fully evolved);
  * a party that complies starts, and `runs show` names the mode;
  * the clauses keep holding: a Fire type that joins B's party mid-run holds the next floor, with the reason in the log;
  * Hardcore: the vendor is closed (the command, and no vendor villager stands), the Empty Pockets modifier is forced, and a
    cycle clear posts to the Hardcore boards, with its difficulty bonus, and not to the Standard ones;
  * Solo Gauntlet: a second player cannot be invited, and only three Pokemon register.

    python validation/smoke/playlist_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
from floor_encounter_test import give_party  # noqa: E402
from intermission_test import play, clear_floor, UUID_RE  # noqa: E402

TOWER = "cobbletowers:test"
STAMP = int(time.time()) % 100000
A = f"TPa{STAMP}"
B = f"TPb{STAMP}"


def as_player(rcon: Rcon, name: str, command: str) -> str:
    return rcon.command(f"execute as {name} run cobbletowers play {command}")


def runs(rcon: Rcon) -> list[tuple[str, str]]:
    """(run id, state) pairs; RCON joins the listing onto one line, so each id is paired with the state after it."""
    return re.findall(UUID_RE + r"\s+\S+\s+([A-Z_]+)", rcon.command("cobbletowers runs list"))


def wait_run(rcon: Rcon, state: str, seconds: int, known: set[str] | None = None) -> str:
    deadline = time.time() + seconds
    while time.time() < deadline:
        for run_id, run_state in runs(rcon):
            if (known is None or run_id not in known) and run_state == state:
                return run_id
        time.sleep(2)
    return ""


def start_refused(rcon: Rcon, server: Server, name: str, mode: str, expect: str, results: list[Result]) -> None:
    reply = as_player(rcon, name, f"playlist {mode}")
    before = len(server.read_log())
    as_player(rcon, name, "confirm")
    as_player(rcon, name, "start")
    time.sleep(10)
    text = server.read_log()[before:]
    refused = f"could not start {TOWER}" in text and expect in text
    results.append(Result(f"{mode}: a party that breaks it is refused, naming '{expect}'",
                          refused and not runs(rcon), reply.strip()[:80] + " | " + text[-300:]))


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
    node_modules = rig / "bot" / "node_modules"
    bots: list[subprocess.Popen] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        for name in (A, B):
            handle = open(server_dir / "logs" / f"towers-playlist-{name}.log", "w", encoding="utf-8", errors="replace")
            bots.append(subprocess.Popen(["node", str(HERE / "joinbot.js"), name, str(server_port(server_dir))],
                                         stdout=handle, stderr=subprocess.STDOUT, env=env))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (A, B):
                for _ in range(90):
                    if name in rcon.command("list"):
                        break
                    time.sleep(1)
                else:
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            rcon.command("cobbletowers masteryadmin clearboards")
            give_party(rcon, A)
            for _ in range(5):
                rcon.command(f"pokegiveother {B} magikarp level=1")

            # ---- the clauses refuse a bad party --------------------------------------------------------------
            play(rcon, A, f"tower {TOWER}")
            start_refused(rcon, server, A, "monotype", "Glaceon is not a Water type", results)
            start_refused(rcon, server, A, "level_cap_50", "Glaceon is level 100", results)
            start_refused(rcon, server, A, "underdog", "Glaceon is fully evolved", results)
            unknown = as_player(rcon, A, "playlist nonsense")
            results.append(Result("an unknown mode is refused with the list", "No mode called" in unknown, unknown.strip()[:200]))

            # ---- Solo Gauntlet: one player, three Pokemon -------------------------------------------------------
            reply = as_player(rcon, A, "playlist solo_gauntlet")
            invite = as_player(rcon, A, f"invite {B}")
            results.append(Result("Solo Gauntlet allows one player: inviting a second is refused",
                                  "allows 1 player" in invite, invite.strip()[:200]))
            as_player(rcon, A, "confirm")
            as_player(rcon, A, "start")
            solo = wait_run(rcon, "ENCOUNTER_ACTIVE", 60)
            if not solo:
                raise RuntimeError("the Solo Gauntlet run never opened: " + reply + invite + rcon.command("cobbletowers runs list"))
            shown = rcon.command(f"cobbletowers runs show {solo}")
            results.append(Result("and its run shows the mode", "mode: Solo Gauntlet" in shown, shown.strip()[:200]))
            results.append(Result("only three Pokemon register", len(re.findall(r"3 registered", shown)) >= 1, shown.strip()[-300:]))
            rcon.command(f"cobbletowers runs advance {solo} abandon_requested")
            time.sleep(8)

            # ---- a compliant Monotype run, and the clauses hold on every floor -------------------------------
            play(rcon, B, f"tower {TOWER}")
            as_player(rcon, B, "playlist monotype")
            as_player(rcon, B, "confirm")
            as_player(rcon, B, "start")
            mono = wait_run(rcon, "ENCOUNTER_ACTIVE", 60, {solo})
            results.append(Result("a party that complies starts under Monotype", bool(mono), rcon.command("cobbletowers runs list")[:200]))
            if mono:
                clear_floor(rcon, mono)
                intermission = ""
                for _ in range(40):
                    intermission = next((rid for rid, st in runs(rcon) if rid == mono and st == "INTERMISSION"), "")
                    if intermission:
                        break
                    time.sleep(1)
                rcon.command(f"pokegiveother {B} charizard level=10")
                play(rcon, B, "pick 1")
                # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
                play(rcon, B, "pick 1")
                play(rcon, B, "ready")
                time.sleep(14)
                held = next((st for rid, st in runs(rcon) if rid == mono), "")
                text = server.read_log()
                results.append(Result("a Fire type that joined mid-run holds the next floor, with the reason",
                                      held == "INTERMISSION" and "cannot open its next floor" in text
                                      and "Charizard is not a Water type" in text, f"state {held}; " + text[-300:]))
                play(rcon, B, "cashout")
                time.sleep(6)

            # ---- Hardcore ---------------------------------------------------------------------------------------
            known = {solo, mono} - {""}
            play(rcon, A, f"tower {TOWER}")
            as_player(rcon, A, "playlist hardcore")
            as_player(rcon, A, "confirm")
            as_player(rcon, A, "start")
            hard = wait_run(rcon, "ENCOUNTER_ACTIVE", 60, known)
            if not hard:
                raise RuntimeError("the Hardcore run never opened: " + rcon.command("cobbletowers runs list"))
            shown = rcon.command(f"cobbletowers runs show {hard}")
            results.append(Result("Hardcore forces Empty Pockets on the run", "cobbletowers:empty_pockets" in shown, shown.strip()[-400:]))

            clear_floor(rcon, hard)
            for _ in range(40):
                if any(rid == hard and st == "INTERMISSION" for rid, st in runs(rcon)):
                    break
                time.sleep(1)
            time.sleep(2)
            vendor = rcon.command(f"execute as {A} run cobbletowers runs vendor")
            results.append(Result("the vendor command refuses in Hardcore", "closed in this mode" in vendor, vendor.strip()[:200]))
            standing = rcon.command("execute in cobbletowers:tower if entity @e[type=minecraft:villager,tag=cobbletowers_vendor]")
            results.append(Result("and no vendor villager stands", "failed" in standing.lower(), standing.strip()[:200]))

            # finish the cycle: floors 2-4
            for floor in range(1, 4):
                play(rcon, A, "pick 1")
                # a second round: an event room may open behind the draft (P34b); an unneeded pick is refused
                play(rcon, A, "pick 1")
                play(rcon, A, "ready")
                for _ in range(60):
                    if any(rid == hard and st == "ENCOUNTER_ACTIVE" for rid, st in runs(rcon)):
                        break
                    time.sleep(1)
                clear_floor(rcon, hard)
                for _ in range(40):
                    if any(rid == hard and st == "INTERMISSION" for rid, st in runs(rcon)):
                        break
                    time.sleep(1)
            time.sleep(3)
            hard_board = rcon.command(f"execute as {A} run cobbletowers play leaderboard difficulty {TOWER} hardcore")
            standard_board = rcon.command(f"execute as {A} run cobbletowers play leaderboard difficulty {TOWER}")
            score = re.search(r"score (\d+)", hard_board)
            results.append(Result("the clear posts to the Hardcore board with its difficulty bonus (15 solo + 15 + Empty Pockets 3)",
                                  A in hard_board and score is not None and int(score.group(1)) >= 33, hard_board.strip()[:300]))
            results.append(Result("and not to the Standard board", "no entries yet" in standard_board, standard_board.strip()[:300]))

            bad = [l for l in server.read_log().splitlines() if "ERROR" in l and "cobbletowers" in l.lower()]
            results.append(Result("no CobbleTowers error was logged", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("playlist run", False, repr(exc)))
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
