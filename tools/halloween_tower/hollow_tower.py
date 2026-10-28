#!/usr/bin/env python3
"""Hollow's Eve: the walkable ten-floor Halloween tower (P55), generated, validated and exported.

    python tools/halloween_tower/hollow_tower.py            # build, validate, write .schem, .nbt and the layout JSON
    python tools/halloween_tower/hollow_tower.py --check    # build and validate only

Tower-local coordinates: T = (0,0,0) at the north-west corner of the foundation, +X east, +Z south, +Y up. The footprint is
97 x 97 and the envelope 97 x 121 x 97. Floor k (1-based) stands on feet level F = 6 + 10 (k - 1): eight blocks of clear space,
then a two-block slab that is the next floor's ground.

One floor (unmirrored, odd floors), from the arrival side to the stair:

    x:  0-1 wall | 2-17 ARRIVAL hall | 18-19 wall | 20-76 ARENA / INTER | 77-78 wall | 79-94 STAIR hall | 95-96 wall
    z:  0-1 wall | 2-20 INTER (x 20-76) | 21-22 wall (GATE A, x 45-51) | 23-72 ARENA | 73-74 wall | 75-94 GALLERY

    arrive by the stair of the floor below (north end of the ARRIVAL hall) -> walk south to the door at z 46-50 -> ARENA
    fight -> GATE A opens -> INTER (vendor and the three modifier pedestals) -> GATE B (x 77-78, z 9-13) -> STAIR hall -> climb

Even floors are the mirror image (x -> 96 - x), so the stair of one floor lands in the ARRIVAL hall of the next. Floor 10 has no
stair: after its champion the run completes or starts a new cycle by teleport, never on foot.

Nothing here touches Minecraft. The output is a Sponge .schem (mcschematic), converted to the vanilla .nbt by
validation/schem_to_structure.py, plus layout.json with every anchor and gate box for the floor definitions.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import deque
from pathlib import Path

import numpy as np

SIZE_X, SIZE_Y, SIZE_Z = 97, 121, 97
FLOORS = 10
BASE_FEET = 6
PITCH = 10
CLEAR = 8           # feet .. feet + 7 is open air; feet + 8 and feet + 9 are the slab
MID_X = 48

# ---- the zones (unmirrored x) ------------------------------------------------------------------------------------------------
ARRIVAL = (2, 17)
STAIR_HALL = (79, 94)
ARENA_X = (20, 76)
ARENA_Z = (23, 72)
INTER_Z = (2, 20)
GALLERY_Z = (75, 94)
DOOR_Z = (46, 50)           # arrival hall -> arena, in the wall x 18..19
GATE_A_X = (45, 51)         # inter | arena wall z 21..22
GATE_B_Z = (9, 13)          # inter -> stair hall, in the wall x 77..78
STAIR_X = (85, 87)
STAIR_Z0 = 6                # step i (1..10) is at z = 5 + i
HOLE_Z = (10, 15)

AIR = "minecraft:air"
SOLID_FACE = {"minecraft:cobweb"}

PLAIN_WALL = "minecraft:polished_blackstone_bricks"
OUTER = "minecraft:deepslate_bricks"
SLAB = "minecraft:polished_blackstone"
TRIM = "minecraft:crying_obsidian"
DARK = "minecraft:black_concrete"
PURPLE = "minecraft:purple_concrete"
TERRA = "minecraft:purple_terracotta"
ORANGE = "minecraft:orange_concrete"
GLASS = "minecraft:purple_stained_glass"
LANTERN = "minecraft:jack_o_lantern"
BARS = "minecraft:iron_bars"
WALL_CAP = "minecraft:polished_blackstone_brick_wall"
STAIR_BLOCK = "minecraft:polished_blackstone_brick_stairs[facing=south,half=bottom,shape=straight]"
CHISELED = "minecraft:chiseled_polished_blackstone"
AMETHYST = "minecraft:amethyst_block"
SHROOM = "minecraft:shroomlight"
COBWEB = "minecraft:cobweb"
PUMPKIN = "minecraft:carved_pumpkin[facing=south]"
SOUL = "minecraft:soul_lantern[hanging=true]"

# blocks a player can walk through; anything else blocks (iron bars, walls, glass, stairs and slabs included)
PASSABLE = {AIR, COBWEB, SOUL}
# collision boxes 1.5 high: nobody steps onto them
TALL = {WALL_CAP}


def feet(floor: int) -> int:
    return BASE_FEET + PITCH * (floor - 1)


def mirrored(floor: int) -> bool:
    return floor % 2 == 0


class Tower:
    """A numpy block grid with a palette. Writes are in floor-local coordinates and mirrored by the floor in progress."""

    def __init__(self) -> None:
        self.grid = np.zeros((SIZE_X, SIZE_Y, SIZE_Z), dtype=np.uint16)
        self.palette: list[str] = [AIR]
        self.index: dict[str, int] = {AIR: 0}
        self.mirror = False

    def block_id(self, name: str) -> int:
        found = self.index.get(name)
        if found is None:
            found = len(self.palette)
            self.palette.append(name)
            self.index[name] = found
        return found

    def _xs(self, x0: int, x1: int) -> tuple[int, int]:
        if x0 > x1:
            x0, x1 = x1, x0
        if self.mirror:
            x0, x1 = SIZE_X - 1 - x1, SIZE_X - 1 - x0
        return x0, x1

    def fill(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int, block: str) -> None:
        x0, x1 = self._xs(x0, x1)
        if y0 > y1 or z0 > z1:
            raise ValueError(f"empty box {x0},{x1} {y0},{y1} {z0},{z1}")
        self.grid[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = self.block_id(self._facing(block))

    def set(self, x: int, y: int, z: int, block: str) -> None:
        self.fill(x, x, y, y, z, z, block)

    def air(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int) -> None:
        self.fill(x0, x1, y0, y1, z0, z1, AIR)

    def _facing(self, block: str) -> str:
        if not self.mirror:
            return block
        return block.replace("facing=east", "facing=@").replace("facing=west", "facing=east").replace("facing=@", "facing=west")

    def at(self, x: int, y: int, z: int) -> str:
        return self.palette[int(self.grid[x, y, z])]


# ---- one floor ---------------------------------------------------------------------------------------------------------------

def build_floor(t: Tower, floor: int) -> None:
    f = feet(floor)
    t.mirror = mirrored(floor)
    top = f + CLEAR - 1                      # last clear layer
    # ceiling slab, which is the next floor's ground
    t.fill(0, 96, f + CLEAR, f + CLEAR + 1, 0, 96, SLAB)
    # ground of this floor: plinth for floor 1, the slab of the floor below otherwise (already there)
    if floor == 1:
        t.fill(0, 96, 0, f - 1, 0, 96, SLAB)
    # outer walls, full height
    t.fill(0, 1, f, top, 0, 96, OUTER)
    t.fill(95, 96, f, top, 0, 96, OUTER)
    t.fill(2, 94, f, top, 0, 1, OUTER)
    t.fill(2, 94, f, top, 95, 96, OUTER)
    # the open interior
    t.air(2, 94, f, top, 2, 94)
    # partitions
    t.fill(18, 19, f, top, 2, 72, PLAIN_WALL)                 # arrival hall | rooms
    t.fill(77, 78, f, top, 2, 72, PLAIN_WALL)                 # rooms | stair hall
    t.fill(20, 76, f, top, 21, 22, PLAIN_WALL)                # inter | arena
    t.fill(2, 94, f, top, 73, 74, PLAIN_WALL)                 # rooms | gallery
    # the gallery looks into the arena through a long window
    t.fill(24, 72, f + 2, f + 5, 73, 74, GLASS)
    # arrival door and the two gates
    t.air(18, 19, f, f + 5, DOOR_Z[0], DOOR_Z[1])
    t.air(77, 78, f, f + 4, GATE_B_Z[0], GATE_B_Z[1])
    t.air(GATE_A_X[0], GATE_A_X[1], f, f + 5, 21, 22)
    # lintels over the openings are the wall above them, already there; close the gates
    t.fill(GATE_A_X[0], GATE_A_X[1], f, f + 5, 21, 22, BARS)
    t.fill(77, 78, f, f + 4, GATE_B_Z[0], GATE_B_Z[1], BARS)
    # windows in the outer walls of both halls and the gallery
    for z in range(8, 70, 8):
        t.fill(0, 1, f + 2, f + 5, z, z + 2, GLASS)
        t.fill(95, 96, f + 2, f + 5, z, z + 2, GLASS)
    for x in range(8, 90, 12):
        t.fill(x, x + 2, f + 2, f + 5, 95, 96, GLASS)
        t.fill(x, x + 2, f + 2, f + 5, 0, 1, GLASS)
    decorate(t, floor, f, top)


def decorate(t: Tower, floor: int, f: int, top: int) -> None:
    ground = f - 1
    accent = (ORANGE, PURPLE, ORANGE)[(floor - 1) // 4 % 3]
    # arena floor: a chequer with a purple ring and an orange centre mark
    for x in range(ARENA_X[0], ARENA_X[1] + 1):
        for z in range(ARENA_Z[0], ARENA_Z[1] + 1):
            t.set(x, ground, z, DARK if (x + z) % 2 == 0 else SLAB)
    t.fill(ARENA_X[0], ARENA_X[1], ground, ground, ARENA_Z[0], ARENA_Z[0], TRIM)
    t.fill(ARENA_X[0], ARENA_X[1], ground, ground, ARENA_Z[1], ARENA_Z[1], TRIM)
    t.fill(ARENA_X[0], ARENA_X[0], ground, ground, ARENA_Z[0], ARENA_Z[1], TRIM)
    t.fill(ARENA_X[1], ARENA_X[1], ground, ground, ARENA_Z[0], ARENA_Z[1], TRIM)
    t.fill(43, 53, ground, ground, 43, 53, TERRA)
    t.fill(46, 50, ground, ground, 46, 50, accent)
    # pillars with lit caps
    for px in (26, 38, 58, 70):
        for pz in (29, 43, 53, 67):
            if 40 <= px <= 56 and 43 <= pz <= 53:
                continue
            t.fill(px, px + 1, f, top - 1, pz, pz + 1, PLAIN_WALL)
            t.fill(px, px + 1, top, top, pz, pz + 1, TRIM)
            t.set(px, f, pz, CHISELED)
    # ceiling lights, every 8 blocks, embedded in the slab so they never block a walk
    for x in range(24, 74, 8):
        for z in range(27, 72, 8):
            t.set(x, f + CLEAR, z, LANTERN)
    # inter room: a purple carpet-like floor and a lit dais where the vendor stands
    t.fill(ARENA_X[0], ARENA_X[1], ground, ground, INTER_Z[0], INTER_Z[1], PURPLE)
    t.fill(44, 52, ground, ground, 2, 8, TERRA)
    t.fill(46, 50, ground, ground, 3, 7, accent)
    for x in range(26, 72, 8):
        t.set(x, f + CLEAR, 12, LANTERN)
        t.set(x, f + CLEAR, 5, LANTERN)
    # halls: a dark runner and a lantern line
    for hall in (ARRIVAL, STAIR_HALL):
        t.fill(hall[0], hall[1], ground, ground, 2, 72, DARK)
        t.fill(hall[0] + 5, hall[0] + 10, ground, ground, 2, 72, PURPLE)
        for z in range(6, 70, 8):
            t.set(hall[0] + 7, f + CLEAR, z, LANTERN)
            t.set(hall[0] + 8, f + CLEAR, z, LANTERN)
    # gallery
    t.fill(2, 94, ground, ground, GALLERY_Z[0], GALLERY_Z[1], DARK)
    for x in range(8, 90, 10):
        t.set(x, f + CLEAR, 84, LANTERN)
    # webs above head height only (feet + 3 and up), in corners of the arena and the gallery
    for x, z in ((20, 23), (76, 23), (20, 72), (76, 72), (2, 75), (94, 75), (2, 94), (94, 94)):
        t.set(x, top, z, COBWEB)
        t.set(x, top - 1, z, COBWEB)
    # hanging soul lanterns above the pedestals' row
    for x in (44, 48, 52):
        t.set(x, top, 11, SOUL)


# ---- stairs ------------------------------------------------------------------------------------------------------------------

def build_stair(t: Tower, floor: int) -> None:
    """The stair that leaves {floor} for {floor + 1}: ten steps south, a hole in the slab above, rails around it up there."""
    f = feet(floor)
    t.mirror = mirrored(floor)
    slab_lo, slab_hi = f + CLEAR, f + CLEAR + 1
    x0, x1 = STAIR_X
    # side walls of the stair, beyond the first steps; the entrance is open from the hall side at z 5..9
    t.fill(x1 + 1, x1 + 1, f, slab_hi, 5, 16, PLAIN_WALL)
    t.fill(x0 - 1, x0 - 1, f, slab_hi, 10, 16, PLAIN_WALL)
    # the hole in the slab above
    t.air(x0, x1, slab_lo, slab_hi, HOLE_Z[0], HOLE_Z[1])
    for i in range(1, 11):
        z = STAIR_Z0 - 1 + i
        y = f + i - 1
        # solid support below the step, down to the ground
        if y - 1 >= f:
            t.fill(x0, x1, f, y - 1, z, z, PLAIN_WALL)
        t.fill(x0, x1, y, y, z, z, STAIR_BLOCK)
    # rails on the ground above (floor + 1 feet level), so nobody walks into the hole from the north or the sides
    up = f + PITCH
    t.fill(x0 - 1, x0 - 1, up, up, HOLE_Z[0], HOLE_Z[1], WALL_CAP)
    t.fill(x1 + 1, x1 + 1, up, up, HOLE_Z[0], HOLE_Z[1], WALL_CAP)
    t.fill(x0, x1, up, up, HOLE_Z[0] - 1, HOLE_Z[0] - 1, WALL_CAP)


# ---- the crown and the exterior ----------------------------------------------------------------------------------------------

def build_crown(t: Tower) -> None:
    t.mirror = False
    roof = feet(FLOORS) + CLEAR + 1          # top of the roof slab
    # parapet, two high
    t.fill(0, 96, roof + 1, roof + 2, 0, 1, OUTER)
    t.fill(0, 96, roof + 1, roof + 2, 95, 96, OUTER)
    t.fill(0, 1, roof + 1, roof + 2, 2, 94, OUTER)
    t.fill(95, 96, roof + 1, roof + 2, 2, 94, OUTER)
    # a stepped glass-and-pumpkin crown in the middle
    cx, cz = MID_X, MID_X
    for layer, half in enumerate((14, 12, 10, 8, 6, 4, 2)):
        y = roof + 1 + layer
        material = GLASS if layer % 2 == 0 else AMETHYST
        t.fill(cx - half, cx + half, y, y, cz - half, cz + half, material)
        if half > 2:
            t.air(cx - half + 1, cx + half - 1, y, y, cz - half + 1, cz + half - 1)
    t.fill(cx - 1, cx + 1, roof + 8, roof + 8, cz - 1, cz + 1, LANTERN)
    # corner towers with jack-o'-lantern tops
    for x, z in ((0, 0), (94, 0), (0, 94), (94, 94)):
        t.fill(x, x + 2, roof + 1, roof + 6, z, z + 2, OUTER)
        t.fill(x, x + 2, roof + 7, roof + 7, z, z + 2, LANTERN)
    # exterior trim: crying-obsidian band at every slab, orange accents in the corners
    for floor in range(1, FLOORS + 1):
        band = feet(floor) + CLEAR
        t.fill(0, 96, band, band, 0, 0, TRIM)
        t.fill(0, 96, band, band, 96, 96, TRIM)
        t.fill(0, 0, band, band, 1, 95, TRIM)
        t.fill(96, 96, band, band, 1, 95, TRIM)
        t.fill(0, 0, band - 6, band - 1, 0, 0, SHROOM)
        t.fill(96, 96, band - 6, band - 1, 0, 0, SHROOM)
        t.fill(0, 0, band - 6, band - 1, 96, 96, SHROOM)
        t.fill(96, 96, band - 6, band - 1, 96, 96, SHROOM)
    # a stepped plinth around the base is inside the footprint: carve a shallow lower course
    t.fill(0, 96, 0, 0, 0, 96, TRIM)


def build() -> Tower:
    t = Tower()
    for floor in range(1, FLOORS + 1):
        build_floor(t, floor)
    for floor in range(1, FLOORS):
        build_stair(t, floor)
    build_crown(t)
    t.mirror = False
    return t


# ---- the layout the floor definitions use -----------------------------------------------------------------------------------

def mx(floor: int, x: int) -> int:
    return SIZE_X - 1 - x if mirrored(floor) else x


def layout(floor: int) -> dict:
    f = feet(floor)

    def anchor(x: int, z: int, yaw: int) -> dict:
        return {"x": mx(floor, x), "y": f, "z": z, "yaw": yaw}

    east_facing = 270 if not mirrored(floor) else 90         # looking from the arrival side into the arena
    west_facing = 90 if not mirrored(floor) else 270
    out = {
        "entry": anchor(24, 48, east_facing),
        "presentation": anchor(46, 48, west_facing),
        "spectator": anchor(MID_X, 84, 180),
        "exit": anchor(MID_X, 5, 0),
        "gate_a": box(floor, GATE_A_X[0], GATE_A_X[1], f, f + 5, 21, 22),
    }
    if floor < FLOORS:
        out["gate_b"] = box(floor, 77, 78, f, f + 4, GATE_B_Z[0], GATE_B_Z[1])
    return out


def box(floor: int, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int) -> dict:
    a, b = sorted((mx(floor, x0), mx(floor, x1)))
    return {"min": [a, y0, z0], "max": [b, y1, z1]}


# ---- validation: a walk over the blocks themselves ----------------------------------------------------------------------------

def passable(t: Tower, x: int, y: int, z: int) -> bool:
    if not (0 <= x < SIZE_X and 0 <= y < SIZE_Y and 0 <= z < SIZE_Z):
        return False
    return t.at(x, y, z) in PASSABLE


def standable(t: Tower, x: int, y: int, z: int) -> bool:
    """Feet at y: two clear blocks and something solid under the feet."""
    return passable(t, x, y, z) and passable(t, x, y + 1, z) and not passable(t, x, y - 1, z) \
        and 0 <= y - 1 and t.at(x, y - 1, z) not in TALL


def reach(t: Tower, start: tuple[int, int, int], gates_open: set[int], gates: dict[int, list[dict]]) -> set[tuple[int, int, int]]:
    """Flood fill on foot: four-way steps of at most one block up or two down, with headroom. Open gates are carved first."""
    opened = []
    for floor in gates_open:
        for box_ in gates.get(floor, []):
            (ax, ay, az), (bx, by, bz) = box_["min"], box_["max"]
            for x in range(ax, bx + 1):
                for y in range(ay, by + 1):
                    for z in range(az, bz + 1):
                        opened.append((x, y, z, int(t.grid[x, y, z])))
                        t.grid[x, y, z] = 0
    seen = {start}
    queue = deque([start])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for ny in (y, y + 1, y - 1, y - 2):
                if (nx, ny, nz) in seen or not standable(t, nx, ny, nz):
                    continue
                if ny == y + 1 and not passable(t, x, y + 2, z):
                    continue
                if ny < y and not passable(t, nx, y + 1, nz):
                    continue
                seen.add((nx, ny, nz))
                queue.append((nx, ny, nz))
                break
    for x, y, z, old in opened:
        t.grid[x, y, z] = old
    return seen


def validate(t: Tower) -> list[str]:
    problems: list[str] = []
    gates = {k: [box_ for name, box_ in layout(k).items() if name.startswith("gate")] for k in range(1, FLOORS + 1)}
    cobweb_below = []
    for floor in range(1, FLOORS + 1):
        lay = layout(floor)
        f = feet(floor)
        for name in ("entry", "presentation", "exit"):
            a = lay[name]
            if not standable(t, a["x"], a["y"], a["z"]):
                problems.append(f"floor {floor}: the {name} anchor {a} is not standable")
        # nothing is a cobweb inside the head-height volume of the walk
        for x in range(SIZE_X):
            for z in range(SIZE_Z):
                for y in range(f, f + 3):
                    if t.at(x, y, z) == COBWEB:
                        cobweb_below.append((floor, x, y, z))
    if cobweb_below:
        problems.append(f"cobwebs inside the walking volume: {cobweb_below[:4]}")

    for floor in range(1, FLOORS + 1):
        lay = layout(floor)
        entry = (lay["entry"]["x"], lay["entry"]["y"], lay["entry"]["z"])
        f = feet(floor)
        closed = reach(t, entry, set(), gates)
        inter = (lay["exit"]["x"], lay["exit"]["y"], lay["exit"]["z"])
        pres = (lay["presentation"]["x"], lay["presentation"]["y"], lay["presentation"]["z"])
        if pres not in closed:
            problems.append(f"floor {floor}: the opponent's spot is not reachable from the entry on foot")
        if inter in closed:
            problems.append(f"floor {floor}: the vendor's room is reachable with gate A closed")
        opened_a = reach(t, entry, {floor}, {floor: [gates[floor][0]]}) if gates[floor] else closed
        if inter not in opened_a:
            problems.append(f"floor {floor}: the vendor's room is not reachable with gate A open")
        if floor < FLOORS:
            nxt = layout(floor + 1)
            landing = (nxt["entry"]["x"], nxt["entry"]["y"], nxt["entry"]["z"])
            if landing in opened_a:
                problems.append(f"floor {floor}: the next floor is reachable with gate B closed")
            both = reach(t, entry, {floor}, {floor: gates[floor]})
            if landing not in both:
                problems.append(f"floor {floor}: the next floor's entry is not reachable with both gates open")
            # every reachable cell of the open walk stays inside this floor's slab band or the next floor's
            ys = {c[1] for c in both}
            if min(ys) < f - PITCH or max(ys) > f + PITCH:
                problems.append(f"floor {floor}: the walk leaves its floors, y {min(ys)}..{max(ys)}")
            if floor > 1:
                below = layout(floor - 1)["entry"]
                if (below["x"], below["y"], below["z"]) in both:
                    problems.append(f"floor {floor}: the arena of the floor below is reachable on foot")
        if floor == FLOORS:
            both = reach(t, entry, {floor}, {floor: gates[floor]})
            if max(c[1] for c in both) > f:
                problems.append("floor 10 has a way up that was not designed")
    return problems


# ---- output ------------------------------------------------------------------------------------------------------------------

def palette_check(t: Tower, loot_tables: Path | None) -> list[str]:
    if loot_tables is None:
        return []
    real = {p.stem for p in loot_tables.glob("*.json")} | {"air", "barrier", "light", "iron_bars"}
    bad = []
    for name in t.palette:
        base = name.split("[")[0].removeprefix("minecraft:")
        if base not in real:
            bad.append(name)
    return bad


def write_schem(t: Tower, path: Path) -> int:
    import mcschematic
    schem = mcschematic.MCSchematic()
    count = 0
    xs, ys, zs = np.nonzero(t.grid)
    for x, y, z in zip(xs.tolist(), ys.tolist(), zs.tolist()):
        schem.setBlock((x, y, z), t.palette[int(t.grid[x, y, z])])
        count += 1
    path.parent.mkdir(parents=True, exist_ok=True)
    schem.save(str(path.parent), path.stem, mcschematic.Version.JE_1_21)
    return count


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="build and validate only")
    parser.add_argument("--out", type=Path, default=Path("validation/schematics"))
    parser.add_argument("--loot-tables", type=Path, help="data/minecraft/loot_table/blocks from a 1.21.1 jar, to check every block id")
    args = parser.parse_args()
    t = build()
    problems = validate(t)
    problems += [f"unknown block id {name}" for name in palette_check(t, args.loot_tables)]
    solid = int(np.count_nonzero(t.grid))
    print(f"hollow tower: {SIZE_X}x{SIZE_Y}x{SIZE_Z}, {solid} blocks, {len(t.palette)} block states")
    for p in problems:
        print("FAIL", p)
    if problems:
        return 1
    print("validation: PASS (every floor walkable entry -> arena -> gate A -> vendor -> gate B -> next floor, nothing earlier)")
    if args.check:
        return 0
    count = write_schem(t, args.out / "hollow_tower.schem")
    layout_json = {str(k): layout(k) for k in range(1, FLOORS + 1)}
    (args.out / "hollow_tower.layout.json").write_text(json.dumps(layout_json, indent=2) + "\n")
    print(f"wrote {args.out / 'hollow_tower.schem'} ({count} blocks) and hollow_tower.layout.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
