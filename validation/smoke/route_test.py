#!/usr/bin/env python3
"""P50 live: the route between floors. A draft is a step of the route; a stop's room opens behind it.

Floors are cleared by operator command (no battles); the drafts and votes are the real player path as one bot. What it proves that no
unit test can:

1. An intermission opens a ROUTE draft: each card rides on a lane and has a stop kind.
2. Voting a card takes that lane, and the next step can only reach the lanes next to it.
3. The stop that was taken decides the room: a plain battle opens none, a room opens its own two options.
4. A milestone floor is not a step of the route (it pays a relic).
5. A hard kill mid-step returns the same stops, and the route taken survives it.
6. `-Dcobbletowers.route=off` gives the plain three-card draft again.

    python validation/smoke/route_test.py --server-dir <rig>/testserver-181 --java <jdk21 java> [--jar build/libs/CobbleTowers-<version>.jar]
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
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from, server_port,
)
from draft_test import to_intermission  # noqa: E402

BOT = f"TRt{int(time.time()) % 100000}"
TOWER = "cobbletowers:neutral"
ROOM_CARD = {"REST": "rest_heal", "SHRINE": "shrine_curse", "GAMBLER": "gambler_stake", "FORGE": "forge_pay", "LIBRARY": "library_study",
             "VAULT": "vault_crack", "POOL": "pool_dive"}


def show(rcon: Rcon, run: str) -> str:
    return rcon.command(f"cobbletowers runs show {run}")


def route_of(shown: str) -> list[tuple[int, str]]:
    """The (lane, kind) of each card of an open route draft, in card order."""
    found = re.search(r"\(ROUTE ([^)]*)\)", shown)
    if not found:
        return []
    return [(int(lane), kind) for lane, kind in re.findall(r"(\d)=([A-Z]+)", found.group(1))]


def draft_open(shown: str) -> bool:
    return bool(re.search(r"draft at floor \d+.*OPEN", shown))


def cards_text(shown: str) -> str:
    found = re.search(r"draft at floor \d+[^\[]*\[(.*?)\]", shown)
    return found.group(1) if found else ""


def vote(rcon: Rcon, card: int) -> str:
    return rcon.command(f"execute as {BOT} run cobbletowers runs draft vote {card}")


def steps_of(shown: str) -> str:
    found = re.search(r"route: lane (-?\d) \(steps floor:lane ([^)]*)\)", shown)
    return found.group(2) if found else ""


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

    def start_bot() -> subprocess.Popen:
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-route-bot.log", "w", encoding="utf-8", errors="replace")
        return subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(server_port(server_dir))],
                                stdout=handle, stderr=subprocess.STDOUT, env=env)

    def wait_bot(rcon: Rcon) -> None:
        for _ in range(90):
            if BOT in rcon.command("list"):
                return
            time.sleep(1)
        raise RuntimeError("bot never joined")

    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bot = start_bot()
        lane_history: list[int] = []
        kinds_seen: set[str] = set()

        def take_step(rcon: Rcon, run: str, floor: int, stops: list[tuple[int, str]]) -> None:
            """Votes the first stop, checks the room behind it, answers the room, and leaves the intermission."""
            card = 1
            taken_lane, taken_kind = stops[card - 1]
            reply = vote(rcon, card)
            time.sleep(1.0)
            after = show(rcon, run)
            lane_history.append(taken_lane)
            results.append(Result(f"floor {floor}: voting a card takes its lane", steps_of(after).split(" ")[-1] == f"{floor}:{taken_lane}",
                                  reply.strip()[:120] + " / " + after.strip()[-200:]))
            if taken_kind == "BATTLE":
                results.append(Result(f"floor {floor}: a plain battle stop opens no room", not draft_open(after), after.strip()[-200:]))
            else:
                opened = draft_open(after) and ROOM_CARD[taken_kind] in cards_text(after)
                results.append(Result(f"floor {floor}: a {taken_kind} stop opens its own room", opened, cards_text(after)))
                if draft_open(after):
                    vote(rcon, 1)
                    time.sleep(1.0)
            rcon.command(f"cobbletowers runs advance {run} intermission_complete")
            rcon.command(f"cobbletowers runs advance {run} next_floor_confirmed")

        def check_stops(floor: int, stops: list[tuple[int, str]]) -> None:
            lanes = [lane for lane, _ in stops]
            kinds_seen.update(kind for _, kind in stops)
            if not lane_history:
                results.append(Result(f"floor {floor}: the first step has a stop in each of the three lanes", lanes == [0, 1, 2], str(stops)))
            else:
                last = lane_history[-1]
                expected = [lane for lane in (0, 1, 2) if abs(lane - last) <= 1]
                results.append(Result(f"floor {floor}: after lane {last} only the lanes next to it can be reached", lanes == expected, f"{stops} wanted {expected}"))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            wait_bot(rcon)
            clear_tower(rcon)
            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")

            to_intermission(rcon, run)
            time.sleep(1.0)
            stops = route_of(show(rcon, run))
            check_stops(1, stops)
            take_step(rcon, run, 1, stops)

            to_intermission(rcon, run)
            time.sleep(1.0)
            killed_stops = route_of(show(rcon, run))
            check_stops(2, killed_stops)
            rcon.command("save-all flush")
            time.sleep(3)
            print("Hard-killing the server mid-step")

        server.process.kill()
        server.process.wait(timeout=60)
        bot.kill()
        server.start()
        server.wait_until_ready()
        bot = start_bot()
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            wait_bot(rcon)
            recovered = show(rcon, run)
            results.append(Result("a hard kill mid-step returns the very same stops", route_of(recovered) == killed_stops and bool(killed_stops),
                                  f"before {killed_stops} / after {route_of(recovered)}"))
            results.append(Result("and the route taken before it survives", steps_of(recovered) == f"1:{lane_history[0]}", steps_of(recovered) or recovered.strip()[-200:]))
            rcon.command(f"cobbletowers runs advance {run} recovery_completed")
            take_step(rcon, run, 2, killed_stops)

            for floor in (3, 4):
                to_intermission(rcon, run)
                time.sleep(1.0)
                stops = route_of(show(rcon, run))
                check_stops(floor, stops)
                take_step(rcon, run, floor, stops)

            to_intermission(rcon, run)
            time.sleep(1.0)
            shown = show(rcon, run)
            results.append(Result("floor 5, a milestone, is not a step of the route (it pays a relic)", not route_of(shown) and draft_open(shown), shown.strip()[-260:]))
            results.append(Result("across the steps the party saw several kinds of stop", len(kinds_seen) >= 2, str(sorted(kinds_seen))))
    finally:
        if bot:
            bot.kill()
        server.stop()

    # ---- the switch: the plain draft again
    os.environ["SMOKE_JAVA_OPTS"] = "-Dcobbletowers.route=off"
    server = Server(server_dir, java)
    bot = None
    try:
        reset_tower_world(server_dir)
        server.start()
        server.wait_until_ready()
        bot = start_bot()
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            wait_bot(rcon)
            clear_tower(rcon)
            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            to_intermission(rcon, run)
            time.sleep(1.0)
            shown = show(rcon, run)
            results.append(Result("with the route off the draft is the plain three cards", not route_of(shown) and draft_open(shown) and len(cards_text(shown).split(",")) == 3,
                                  shown.strip()[-240:]))
    finally:
        if bot:
            bot.kill()
        server.stop()

    print()
    width = max(len(result.name) for result in results)
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  {result.detail if not result.passed else ''}")
    print()
    print(f"route_test: {len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
