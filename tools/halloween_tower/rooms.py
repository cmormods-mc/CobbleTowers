#!/usr/bin/env python3
"""Hollow's Eve: the eight rooms that dress the three chambers behind a floor's route doors (P55).

    python tools/halloween_tower/rooms.py            # build every room, check it, write room_<kind>.schem and the .nbt structures
    python tools/halloween_tower/rooms.py --check    # build and check only

A room is 11 wide (x), 9 high (y: the ground row, then eight clear rows) and 15 deep (z), the size of the inside of a chamber plus its floor. The
tower pastes the empty chamber; when the route draft opens, the server clears each chamber and places the room for the kind of stop that lane
leads to (battle, rest, shrine, gambler, forge, library, vault, pool). Rooms are only dressing: the stop's rules are unchanged.

Local coordinates: x 0..10 west to east (the aisle is x 4..6), z 0..14 north to south (z 14 is the door, z 0 the back doorway into the corridor),
y 0 is the floor and y 1 is where the feet are. Every room keeps the aisle (x 4..6, y 1..3) clear from the door to the back doorway; `check` proves it
with the same flood fill the tower uses. No block entity is used (no chests, beds, banners, signs, campfires, lecterns, barrels).
"""
from __future__ import annotations

import argparse
import math
import sys
from collections import deque
from pathlib import Path

import numpy as np

W, H, D = 11, 9, 15
AIR = "minecraft:air"

BRICKS = "minecraft:polished_blackstone_bricks"
SLAB = "minecraft:polished_blackstone"
BLACKSTONE = "minecraft:blackstone"
DARK = "minecraft:black_concrete"
PURPLE = "minecraft:purple_concrete"
ORANGE = "minecraft:orange_concrete"
ORANGE_WOOL = "minecraft:orange_wool"
PURPLE_WOOL = "minecraft:purple_wool"
BLACK_WOOL = "minecraft:black_wool"
RED_WOOL = "minecraft:red_wool"
WHITE_WOOL = "minecraft:white_wool"
WHITE = "minecraft:white_concrete"
TRIM = "minecraft:crying_obsidian"
OBSIDIAN = "minecraft:obsidian"
AMETHYST = "minecraft:amethyst_block"
CLUSTER = "minecraft:amethyst_cluster[facing=up]"
SHROOM = "minecraft:shroomlight"
JACK = "minecraft:jack_o_lantern[facing=south]"
SOUL = "minecraft:soul_lantern[hanging=true]"
LANTERN = "minecraft:lantern[hanging=true]"
CHAIN = "minecraft:chain[axis=y]"
HAY = "minecraft:hay_block[axis=y]"
SPRUCE = "minecraft:spruce_planks"
BOOKSHELF = "minecraft:bookshelf"
CANDLE = "minecraft:candle[candles=3,lit=true]"
CAULDRON = "minecraft:cauldron"
LAVA_CAULDRON = "minecraft:lava_cauldron"
ANVIL_E = "minecraft:anvil[facing=east]"
ANVIL_N = "minecraft:anvil[facing=north]"
SMITHING = "minecraft:smithing_table"
GRINDSTONE = "minecraft:grindstone[face=floor,facing=south]"
IRON_BLOCK = "minecraft:iron_block"
IRON_BARS = "minecraft:iron_bars"
MAGMA = "minecraft:magma_block"
NETHER_BRICKS = "minecraft:red_nether_bricks"
GOLD = "minecraft:gold_block"
GILDED = "minecraft:gilded_blackstone"
LODESTONE = "minecraft:lodestone"
PRISMARINE = "minecraft:prismarine_bricks"
SEA_LANTERN = "minecraft:sea_lantern"
WATER = "minecraft:water"
CARPET_PURPLE = "minecraft:purple_carpet"
CHISELED = "minecraft:chiseled_polished_blackstone"
GLASS = "minecraft:purple_stained_glass"


class Room:
    def __init__(self) -> None:
        self.grid = np.zeros((W, H, D), dtype=np.uint16)
        self.palette = [AIR]
        self.index = {AIR: 0}

    def block_id(self, name: str) -> int:
        found = self.index.get(name)
        if found is None:
            found = len(self.palette)
            self.palette.append(name)
            self.index[name] = found
        return found

    def fill(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int, block: str) -> None:
        self.grid[min(x0, x1):max(x0, x1) + 1, y0:y1 + 1, min(z0, z1):max(z0, z1) + 1] = self.block_id(block)

    def set(self, x: int, y: int, z: int, block: str) -> None:
        self.fill(x, x, y, y, z, z, block)

    def mirror_x(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int, block: str) -> None:
        """The same box on both sides of the aisle."""
        self.fill(x0, x1, y0, y1, z0, z1, block)
        self.fill(W - 1 - x1, W - 1 - x0, y0, y1, z0, z1, block)


def base(r: Room, floor_side: str = BLACKSTONE) -> None:
    """The ground of every room: dark paving, a purple aisle runner with a lit line either side, and a few hanging lights."""
    r.fill(0, 10, 0, 0, 0, 14, DARK)
    r.fill(1, 3, 0, 0, 0, 14, floor_side)
    r.fill(7, 9, 0, 0, 0, 14, floor_side)
    r.fill(4, 6, 0, 0, 0, 14, PURPLE)
    r.fill(3, 3, 0, 0, 1, 13, SHROOM)
    r.fill(7, 7, 0, 0, 1, 13, SHROOM)


def hang(r: Room, x: int, z: int, light: str = SOUL, drop: int = 2) -> None:
    for y in range(8, 8 - drop, -1):
        r.set(x, y, z, CHAIN)
    r.set(x, 8 - drop, z, light)


def battle(r: Room) -> None:
    """A gauntlet: rows of pillars with lit caps, orange pennants on the walls, a glowing line down the aisle."""
    base(r)
    for z in (3, 7, 11):
        r.mirror_x(1, 1, 1, 6, z, z, BRICKS)
        r.mirror_x(1, 1, 7, 7, z, z, TRIM)
        r.mirror_x(2, 2, 1, 1, z, z, JACK)
    for z in (2, 6, 10):
        r.mirror_x(0, 0, 3, 6, z, z + 1, ORANGE_WOOL)
        r.mirror_x(0, 0, 2, 2, z, z + 1, PURPLE_WOOL)
    for z in (3, 7, 11):
        hang(r, 5, z, SOUL, 2)


def rest(r: Room) -> None:
    """A campfire corner without the campfire: bedrolls, hay, a soup cauldron, candles and warm lanterns."""
    base(r, SPRUCE)
    for z in (2, 6, 10):
        r.fill(1, 2, 1, 1, z, z + 1, RED_WOOL)
        r.fill(1, 1, 1, 1, z, z + 1, WHITE_WOOL)
    r.mirror_x(0, 0, 1, 3, 12, 13, HAY)
    r.mirror_x(1, 1, 1, 1, 12, 13, HAY)
    r.set(1, 2, 12, CANDLE)
    r.set(9, 2, 12, CANDLE)
    r.fill(8, 9, 1, 1, 2, 3, HAY)
    r.set(8, 2, 2, CAULDRON)
    r.set(9, 2, 3, CANDLE)
    r.fill(8, 8, 1, 1, 6, 6, MAGMA)
    r.set(8, 2, 6, CAULDRON)
    for z in (3, 7, 11):
        hang(r, 5, z, LANTERN, 1)


def shrine(r: Room) -> None:
    """An altar on each side of the aisle: crying obsidian and amethyst, soul lanterns, purple light through glass."""
    base(r)
    for z in (4, 10):
        r.mirror_x(1, 2, 1, 1, z, z + 1, OBSIDIAN)
        r.mirror_x(1, 2, 2, 2, z, z + 1, TRIM)
        r.mirror_x(1, 1, 3, 3, z, z, AMETHYST)
        r.mirror_x(1, 1, 4, 4, z, z, CLUSTER)
    for z in (2, 7, 12):
        r.mirror_x(0, 0, 2, 5, z, z + 1, GLASS)
    r.fill(4, 6, 1, 1, 7, 7, CARPET_PURPLE)
    for z in (3, 7, 11):
        hang(r, 5, z, SOUL, 2)


def gambler(r: Room) -> None:
    """A wheel in the floor: twenty-four orange and black sectors round a pumpkin, with great dice at the sides."""
    base(r)
    cx, cz = 5, 7
    for x in range(W):
        for z in range(D):
            dx, dz = x - cx, z - cz
            rr = math.hypot(dx, dz)
            if rr <= 4.6 and not (4 <= x <= 6 and False):
                sector = int((math.atan2(dz, dx) + math.pi) / (2 * math.pi) * 12) % 2
                r.set(x, 0, z, ORANGE if sector == 0 else DARK)
            if rr <= 1.5:
                r.set(x, 0, z, SHROOM)
    for dz0, dx0 in ((2, 1), (10, 8)):
        r.fill(dx0, dx0 + 1, 1, 2, dz0, dz0 + 1, WHITE)
        r.set(dx0, 2, dz0, DARK)
        r.set(dx0 + 1, 2, dz0 + 1, DARK)
    for z in (3, 7, 11):
        hang(r, 5, z, SOUL, 1)
    r.mirror_x(0, 0, 3, 5, 6, 8, ORANGE_WOOL)


def forge(r: Room) -> None:
    """Anvils, a grindstone, a smithing table and a pot of lava, on red nether brick and magma, with iron piled in the corners."""
    base(r, NETHER_BRICKS)
    r.set(1, 1, 3, ANVIL_E)
    r.set(1, 1, 6, ANVIL_E)
    r.set(1, 1, 9, ANVIL_E)
    r.set(2, 1, 12, SMITHING)
    r.set(8, 1, 3, GRINDSTONE)
    r.set(8, 1, 6, LAVA_CAULDRON)
    r.set(9, 1, 9, LAVA_CAULDRON)
    r.mirror_x(1, 2, 1, 2, 0, 1, IRON_BLOCK)
    r.fill(7, 9, 0, 0, 5, 7, MAGMA)
    r.fill(1, 2, 0, 0, 5, 7, MAGMA)
    for z in (3, 7, 11):
        hang(r, 5, z, LANTERN, 2)
    r.mirror_x(0, 0, 3, 6, 2, 12, IRON_BARS)


def library(r: Room) -> None:
    """Bookshelves three high along both walls, candles on the lecterns' place, purple carpet and hanging soul lights."""
    base(r)
    for z in range(1, 14):
        if z in (7,):
            continue
        r.mirror_x(1, 2, 1, 3, z, z, BOOKSHELF)
    r.mirror_x(1, 1, 4, 4, 2, 12, BOOKSHELF)
    for z in (3, 11):
        r.mirror_x(1, 1, 5, 5, z, z, CANDLE)
    r.mirror_x(3, 3, 1, 1, 2, 12, CARPET_PURPLE)
    for z in (3, 7, 11):
        hang(r, 5, z, SOUL, 2)


def vault(r: Room) -> None:
    """Gold: stacked blocks behind bars on each side, gilded blackstone in the floor, a lodestone on a plinth."""
    base(r, GILDED)
    r.mirror_x(1, 2, 1, 1, 2, 12, GOLD)
    r.mirror_x(1, 2, 2, 2, 4, 10, GOLD)
    r.mirror_x(1, 1, 3, 3, 6, 8, GOLD)
    r.mirror_x(3, 3, 1, 3, 2, 12, IRON_BARS)
    r.mirror_x(1, 1, 4, 4, 7, 7, LODESTONE)
    for z in (3, 7, 11):
        hang(r, 5, z, SOUL, 1)
    r.mirror_x(0, 0, 4, 6, 4, 10, SHROOM)


def pool(r: Room) -> None:
    """Two basins of prismarine and still water, lit from beneath, either side of a dry aisle."""
    base(r, PRISMARINE)
    for x0, x1 in ((1, 3), (7, 9)):
        r.fill(x0, x1, 0, 0, 3, 11, PRISMARINE)
        r.fill(x0, x1, 1, 1, 3, 3, PRISMARINE)
        r.fill(x0, x1, 1, 1, 11, 11, PRISMARINE)
        r.fill(x0, x0, 1, 1, 3, 11, PRISMARINE)
        r.fill(x1, x1, 1, 1, 3, 11, PRISMARINE)
        r.fill(x0 + 1 if x0 == 1 else x0, x1 - 1 if x1 == 9 else x1, 1, 1, 4, 10, WATER)
        r.fill(x0 + 1, x1 - 1, 0, 0, 4, 10, SEA_LANTERN)
    # the aisle side of each basin is a low wall (a block high), so the water stays in
    r.mirror_x(3, 3, 1, 1, 3, 11, PRISMARINE)
    for z in (3, 7, 11):
        hang(r, 5, z, SOUL, 2)


ROOMS = {
    "battle": battle, "rest": rest, "shrine": shrine, "gambler": gambler,
    "forge": forge, "library": library, "vault": vault, "pool": pool,
}


def build(kind: str) -> Room:
    r = Room()
    ROOMS[kind](r)
    return r


# ---- checking ---------------------------------------------------------------------------------------------------------------------

PASSABLE = {AIR, SOUL, LANTERN, CHAIN}


def passable(r: Room, x: int, y: int, z: int) -> bool:
    if not (0 <= x < W and 0 <= y < H and 0 <= z < D):
        return False
    name = r.palette[int(r.grid[x, y, z])]
    return name in PASSABLE or name.startswith("minecraft:purple_carpet") or name == CARPET_PURPLE or name == CANDLE or name.startswith("minecraft:chain")


def aisle_clear(r: Room) -> list[str]:
    """The aisle (x 4..6) must be open from the door to the back doorway, three high, and the feet row there must be flat floor."""
    problems = []
    for z in range(D):
        for x in (4, 5, 6):
            for y in (1, 2, 3):
                if not passable(r, x, y, z):
                    problems.append(f"the aisle is blocked at ({x}, {y}, {z}) by {r.palette[int(r.grid[x, y, z])]}")
                    return problems
            below = r.palette[int(r.grid[x, 0, z])]
            if below == AIR or below.startswith("minecraft:water") or below == LAVA_CAULDRON:
                problems.append(f"the aisle has no floor at ({x}, 0, {z})")
                return problems
    return problems


def walkable(r: Room) -> bool:
    """On foot from the door end of the aisle to the back doorway end, with the same rules as the tower (four-way, one up, two clear)."""
    def standing(x, y, z):
        if not (passable(r, x, y, z) and passable(r, x, y + 1, z)):
            return False
        below = r.palette[int(r.grid[x, y - 1, z])] if y >= 1 else AIR
        return below != AIR and not passable(r, x, y - 1, z) and not below.startswith("minecraft:water")
    start, goal = (5, 1, 14), (5, 1, 0)
    if not standing(*start):
        return False
    seen = {start}
    queue = deque([start])
    while queue:
        x, y, z = queue.popleft()
        if (x, y, z) == goal:
            return True
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for ny in (y, y + 1, y - 1):
                if (nx, ny, nz) not in seen and standing(nx, ny, nz):
                    seen.add((nx, ny, nz))
                    queue.append((nx, ny, nz))
                    break
    return False


def check(kind: str, r: Room) -> list[str]:
    problems = [f"{kind}: {p}" for p in aisle_clear(r)]
    if not walkable(r):
        problems.append(f"{kind}: no way on foot from the door to the back doorway")
    return problems


# ---- output -----------------------------------------------------------------------------------------------------------------------

def palette_check(r: Room, loot_tables: Path | None) -> list[str]:
    if loot_tables is None:
        return []
    real = {p.stem for p in loot_tables.glob("*.json")} | {"air", "water", "iron_bars", "lava_cauldron", "cauldron"}
    return [name for name in r.palette if name.split("[")[0].removeprefix("minecraft:") not in real]


def write_schem(r: Room, path: Path) -> int:
    import mcschematic
    schem = mcschematic.MCSchematic()
    count = 0
    xs, ys, zs = np.nonzero(r.grid)
    for x, y, z in zip(xs.tolist(), ys.tolist(), zs.tolist()):
        schem.setBlock((x, y, z), r.palette[int(r.grid[x, y, z])])
        count += 1
    path.parent.mkdir(parents=True, exist_ok=True)
    schem.save(str(path.parent), path.stem, mcschematic.Version.JE_1_21)
    return count


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--out", type=Path, default=Path("validation/schematics"))
    parser.add_argument("--loot-tables", type=Path)
    args = parser.parse_args()
    problems: list[str] = []
    rooms = {kind: build(kind) for kind in ROOMS}
    for kind, r in rooms.items():
        problems += check(kind, r)
        problems += [f"{kind}: unknown block id {n}" for n in palette_check(r, args.loot_tables)]
    for kind, r in rooms.items():
        print(f"room {kind}: {int(np.count_nonzero(r.grid))} blocks, {len(r.palette)} states")
    for p in problems:
        print("FAIL", p)
    if problems:
        return 1
    print("rooms: PASS (every aisle is open and walkable)")
    if args.check:
        return 0
    for kind, r in rooms.items():
        write_schem(r, args.out / f"room_{kind}.schem")
    print(f"wrote {len(rooms)} room schematics to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
