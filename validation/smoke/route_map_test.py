#!/usr/bin/env python3
"""P56 live: the branching route map on a tower with route_map (Hollow's Eve). The draft after each floor offers only the lanes the
map links from where the party stands; a milestone pays a relic and the floor after it has no choice; a hard kill returns the same
stops.

Floors are cleared by operator command (no battles); the drafts and votes are the real player path as one bot. The page's map is
not checked here (a bot cannot render it; see web/tools/desk-check.mjs and the payload test). Needs the Halloween tower open outside
its window: SMOKE_JAVA_OPTS gets -Dcobbletowers.ignoreWindows=true.

    python validation/smoke/route_map_test.py --server-dir <rig>/testserver-181 --java <jdk21 java> [--jar <dev jar>]
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
from route_test import (  # noqa: E402
    cards_text, draft_open, route_of, show, steps_of, vote,
)
import route_test  # noqa: E402

BOT = f"TRm{int(time.time()) % 100000}"
route_test.BOT = BOT
TOWER = "cobbletowers:halloween"
PLAYABLE = {"BATTLE", "REST", "SHRINE", "FORGE", "LIBRARY", "VAULT", "POOL"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--node-modules", type=Path, default=None)
    parser.add_argument("--no-kill", action="store_true", help="skip the hard kill and restart; finish the walk through the milestone instead")
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    node_modules = args.node_modules or (server_dir.parent / "bot" / "node_modules")
    if "ignoreWindows" not in os.environ.get("SMOKE_JAVA_OPTS", ""):
        os.environ["SMOKE_JAVA_OPTS"] = (os.environ.get("SMOKE_JAVA_OPTS", "") + " -Dcobbletowers.ignoreWindows=true").strip()
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bot = None

    def start_bot() -> subprocess.Popen:
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-routemap-bot.log", "w", encoding="utf-8", errors="replace")
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
        taken: list[int] = []          # the lane taken at each step, in order
        offered: list[list[int]] = []  # the lanes each step offered
        kinds_seen: set[str] = set()

        def step(rcon: Rcon, run: str, floor: int, label: str) -> list[tuple[int, str]]:
            """Opens the intermission after `floor`, checks the stops against the walk so far, votes the LAST card."""
            to_intermission(rcon, run)
            time.sleep(1.0)
            stops = route_of(show(rcon, run))
            lanes = [lane for lane, _ in stops]
            kinds_seen.update(kind for _, kind in stops)
            results.append(Result(f"{label}: the draft is a route step with at least one stop", bool(stops), show(rcon, run).strip()[-200:]))
            results.append(Result(f"{label}: every stop is a kind that has a room today", all(kind in PLAYABLE for _, kind in stops), str(stops)))
            results.append(Result(f"{label}: lanes are distinct and in order", lanes == sorted(set(lanes)), str(lanes)))
            if taken:
                last = taken[-1]
                results.append(Result(f"{label}: after lane {last} only that lane and its neighbours can be offered",
                                      all(abs(lane - last) <= 1 for lane in lanes), f"{lanes} after {last}"))
            offered.append(lanes)
            return stops

        def take(rcon: Rcon, run: str, floor: int, stops: list[tuple[int, str]]) -> None:
            card = len(stops)   # the last card: the lane farthest from the middle is the one a plain walk would skip
            lane, kind = stops[card - 1]
            reply = vote(rcon, card)
            time.sleep(1.0)
            after = show(rcon, run)
            taken.append(lane)
            results.append(Result(f"floor {floor}: voting a card takes its lane", steps_of(after).split(" ")[-1] == f"{floor}:{lane}", f"vote said: {reply.strip()[:160]} / {after.strip()[-200:]}"))
            if kind != "BATTLE" and draft_open(after):
                vote(rcon, 1)
                time.sleep(1.0)
            rcon.command(f"cobbletowers runs advance {run} intermission_complete")
            rcon.command(f"cobbletowers runs advance {run} next_floor_confirmed")

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            wait_bot(rcon)
            clear_tower(rcon)
            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")

            for floor in (1, 2, 3):
                take(rcon, run, floor, step(rcon, run, floor, f"floor {floor}"))
            stops4 = step(rcon, run, 4, "floor 4")

            if args.no_kill:
                take(rcon, run, 4, stops4)
                to_intermission(rcon, run)
                time.sleep(1.0)
                shown = show(rcon, run)
                results.append(Result("floor 5, a milestone, is not a step of the route (it pays a relic)", not route_of(shown) and draft_open(shown), shown.strip()[-260:]))
                # past the boss the run needs the milestone reward flow, which milestone_floor_test drives; the floor after a milestone
                # (a lone stop, no choice) is covered by the RouteMap unit tests
            else:
                rcon.command("save-all flush")
                time.sleep(3)
                print("Hard-killing the server mid-step")

        if not args.no_kill:
            server.process.kill()
            server.process.wait(timeout=60)
            bot.kill()
            server.start()
            server.wait_until_ready()
            bot = start_bot()
            with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
                wait_bot(rcon)
                recovered = show(rcon, run)
                results.append(Result("a hard kill mid-step returns the very same stops", route_of(recovered) == stops4 and bool(stops4), f"before {stops4} / after {route_of(recovered)}"))
                want = " ".join(f"{i + 1}:{lane}" for i, lane in enumerate(taken))
                results.append(Result("and the route taken before it survives", steps_of(recovered) == want, f"{steps_of(recovered)!r} wanted {want!r}"))
                # a run a restart interrupted ends when its player rejoins (commit a55c8c5): nothing is left to vote in
                results.append(Result("the interrupted run is ended when its player rejoins, by design", "ABANDONED" in recovered, recovered.strip()[-220:]))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            results.append(Result("across the steps the party saw several kinds of stop", len(kinds_seen) >= 2, str(sorted(kinds_seen))))
            text = server.log.read_text(encoding="utf-8", errors="replace") if hasattr(server.log, "read_text") else ""
            results.append(Result("no route fault was logged", "route" not in "".join(l for l in text.splitlines() if "ERROR" in l).lower(),
                                  "; ".join(l for l in text.splitlines() if "ERROR" in l)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("route map run", False, repr(exc)))
    finally:
        if bot:
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
