"""Parks a run in front of an opened Trick or Treat vault so it can be photographed. Needs RIG (the rig folder) and the rig server running.

Starts runs with fresh bots until the first floor's route offers a Trick or Treat stop, votes for it, opens the vault, and prints
"CHAMBER x y z" (the world position of the middle of the chamber's aisle, feet level, and its door end z). The run is then held; kill the script to release it.
"""
import os
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "smoke"))
import walk_tower_test as w  # noqa: E402
from rcon import Rcon  # noqa: E402

SP = Path(os.environ["RIG"])
node, srv = SP / "bots" / "node_modules", SP / "srv"


def last_run(r, state=None):
    """The newest listed run (in {state} when given): abandoned runs of earlier attempts stay on the list."""
    ls = [l for l in r.command("cobbletowers runs list").splitlines() if re.search(w.UUID_RE, l) and (state is None or state in l)]
    return ls[-1] if ls else ""


with Rcon("127.0.0.1", 25575, "walktest") as r:
    for attempt in range(1, 40):
        a, b = f"TKa{w.STAMP % 1000}{attempt}", f"TKb{w.STAMP % 1000}{attempt}"
        ws = [w.Walker(n, 25565, node, srv / "logs" / f"trick-{n}.log") for n in (a, b)]
        for n in (a, b):
            w.wait_for(lambda n=n: n in r.command("list"), 90)
            w.give_party(r, n)
        w.play(r, a, f"tower {w.TOWER}"); w.play(r, a, f"invite {b}"); w.play(r, b, f"accept {a}"); w.play(r, a, "confirm"); w.play(r, a, "start")
        w.wait_for(lambda: last_run(r, "ENCOUNTER_ACTIVE") != "", 60)
        time.sleep(3)
        for _ in range(8):
            r.command(f"cobbletowers runs winfloor {a}")
            if w.wait_for(lambda: last_run(r, "INTERMISSION") != "", 6):
                break
        run = re.search(w.UUID_RE, last_run(r, "INTERMISSION")).group(1)
        # the route draft opens a moment after the intermission begins
        w.log_has(srv, rf"Run {run} opened a draft at floor 1: .*? stops \[", 20)
        card = w.trick_card(srv, run, 1)
        print("attempt", attempt, "card", card, flush=True)
        if card:
            for rnd in range(2):
                for n in (a, b):
                    w.play(r, n, f"pick {card if rnd == 0 else 1}")
                time.sleep(2)
            outcome = w.log_has(srv, rf"Run {run} opened the Jack-o'-Lantern Vault of floor 1: (TREAT|TRICK)", 15)
            e = w.layout_of(1)["entry"]
            p = min((w.position(r, n) for n in (a, b)), key=lambda q: q[0])
            origin = (round(p[0] - e["x"] - .5), round(p[1] - e["y"]), round(p[2] - e["z"] - .5))
            doors = w.layout_of(1)["doors"]
            lane = next(i for i, d in enumerate(doors) if w.gate_state(r, origin, d["open"], "minecraft:air"))
            d = doors[lane]
            centre = (d["open"]["min"][0] + d["open"]["max"][0]) // 2
            print("OUTCOME", outcome, "LANE", lane, flush=True)
            print("CHAMBER", origin[0] + centre, origin[1] + d["pedestal"]["y"], origin[2] + d["open"]["min"][2], flush=True)
            time.sleep(6000)
        r.command(f"cobbletowers runs abandon {run}")
        for x in ws:
            x.process.kill()
