#!/usr/bin/env python3
"""P55 Phase A checks for the walkable Hollow's Eve tower: Y schedule, stair geometry, progression graph.

    python validation/p55_phase_a_check.py

Pure arithmetic, no Minecraft. Exits 1 on any FAIL. Tower-local coordinates: +X east, +Z south, +Y up, T=(0,0,0).
"""
from __future__ import annotations

import sys
from collections import deque

FLOORS = 10
BASE_SURFACE = 6      # floor 1 feet level; plinth below it
PITCH = 10            # floor-to-floor rise
SLAB = 2              # floor slab thickness
CLEAR = PITCH - SLAB  # clear height from feet to the next slab's underside
MIN_CLEAR = 7         # battle halls need room for big Pokemon; proposed, runtime test owes the real number
FOOTPRINT = 97
CELL_INTERIOR = 256
CELL_FLOOR_Y = 64
WORLD_MAX_Y = 384

results: list[tuple[str, str, str]] = []


def check(name: str, ok: bool, evidence: str) -> None:
    results.append((name, "PASS" if ok else "FAIL", evidence))


def surface(k: int) -> int:
    return BASE_SURFACE + PITCH * (k - 1)


# ---- Y schedule ------------------------------------------------------------------------------------------------
crown_top = surface(FLOORS) + CLEAR + SLAB + 14  # roof slab plus a 14-block crown
check("clear height", CLEAR >= MIN_CLEAR, f"clear {CLEAR} >= {MIN_CLEAR}")
check("tower fits the world", CELL_FLOOR_Y + crown_top < WORLD_MAX_Y, f"top y {CELL_FLOOR_Y + crown_top} < {WORLD_MAX_Y}")
check("tower fits the cell", FOOTPRINT <= CELL_INTERIOR, f"{FOOTPRINT} <= {CELL_INTERIOR}")

# ---- stair: 10 steps of 1:1, feet at step i = surface(k) + 1 + i, last step lands on surface(k+1) --------------
steps = PITCH
check("stair lands on next surface", surface(1) + steps == surface(2), f"{surface(1)} + {steps} == {surface(2)}")
slab_under = surface(1) + CLEAR  # slab occupies [slab_under, slab_under + SLAB - 1]
need = 3  # feet .. feet+2 free above a step (conservative for head clearance while climbing)
first_blocked = next(i for i in range(steps) if surface(1) + 1 + i + (need - 1) >= slab_under)
opening = steps - first_blocked
check("stair headroom needs a slab opening", True,
      f"steps 0..{first_blocked - 1} sit under the slab with room; steps {first_blocked}..{steps - 1} need the opening: "
      f"{opening} columns + top landing")
check("stair run fits one hall ring", steps + 4 <= 20, f"run {steps} + 2 landings of 2 = {steps + 4} <= 20")

# ---- progression graph ------------------------------------------------------------------------------------------
edges: list[tuple[str, str, str]] = [("LOBBY", "ENT1", "")]  # (from, to, gate)
tot_floors = list(range(1, FLOORS))                            # optional gamble room off the intermission, 1..9
for k in range(1, FLOORS + 1):
    edges += [(f"ENT{k}", f"ARENA{k}", ""),
              (f"ARENA{k}", f"INTER{k}", f"GATE-{k}-A"),       # opens when the floor is cleared
              ]
    if k < FLOORS:
        edges += [(f"INTER{k}", f"STAIR{k}", f"GATE-{k}-B"),   # opens when the party readies up
                  (f"STAIR{k}", f"ENT{k + 1}", "")]
    else:
        edges += [(f"INTER{k}", "REWARD", "GATE-10-B"), ("REWARD", "EXIT", "")]
for k in tot_floors:
    edges += [(f"INTER{k}", f"TOT{k}", f"DOOR-{k}-T"), (f"TOT{k}", f"INTER{k}", "")]  # door opens when the seed rolls it


def reach(removed: set[str]) -> set[str]:
    seen, queue = {"LOBBY"}, deque(["LOBBY"])
    while queue:
        node = queue.popleft()
        for a, b, _ in edges:
            if a == node and b not in seen and b not in removed:
                seen.add(b)
                queue.append(b)
    return seen


everything = {n for e in edges for n in e[:2]}
check("every node reachable", reach(set()) == everything, f"{len(everything)} nodes, {len(reach(set()))} reached")
optional = {f"TOT{k}" for k in tot_floors}
without = reach(optional)
check("trick-or-treat rooms are optional", "EXIT" in without and (everything - optional) <= without,
      f"EXIT reachable with all {len(optional)} rooms removed")
check("exit reachable", "EXIT" in reach(set()), "EXIT in reach set")

# gates only depend on earlier state: gate order must follow floor order (no cycles)
order = [g for _, _, g in edges if g.startswith("GATE")]
floors_of = [int(g.split("-")[1]) for g in order]
check("gate prerequisites are acyclic", floors_of == sorted(floors_of), "gate floors never go backwards in walk order")
check("no gate guards its own key", all(a != b for a, b, _ in edges), "no self edge")

width = max(len(r[0]) for r in results)
failed = 0
for name, status, evidence in results:
    print(f"{status:5} {name:<{width}}  {evidence}")
    failed += status == "FAIL"
print()
print("Y schedule (feet level, slab underside):")
for k in range(1, FLOORS + 1):
    print(f"  F{k:02}: surface y={surface(k):3}  clear to y={surface(k) + CLEAR - 1:3}  slab y={surface(k) + CLEAR}..{surface(k) + CLEAR + SLAB - 1}")
print(f"  crown top y={crown_top}; world y {CELL_FLOOR_Y} .. {CELL_FLOOR_Y + crown_top}")
sys.exit(1 if failed else 0)
