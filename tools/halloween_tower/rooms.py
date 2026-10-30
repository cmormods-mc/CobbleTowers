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

sys.path.insert(0, str(Path(__file__).resolve().parent))
import interiors as I  # noqa: E402

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


def base(r: Room, a: str = I.POLISHED, b: str = I.TILES, salt: int = 1) -> None:
    """The ground of every room: a checker of two stones, a purple runner on the aisle with obsidian edges and a gilded stud every fourth block."""
    for x in range(W):
        for z in range(D):
            block = a if ((x // 3) + (z // 3)) % 2 == 0 else b
            if I.noise(x, z, salt) < 60:
                block = I.CRACKED
            r.set(x, 0, z, block)
    for z in range(D):
        r.fill(4, 6, 0, 0, z, z, PURPLE)
        for x in (3, 7):
            r.set(x, 0, z, TRIM if z % 4 else GILDED)


def hang(r: Room, x: int, z: int, light: str = SOUL, drop: int = 2) -> None:
    for y in range(8, 8 - drop, -1):
        r.set(x, y, z, CHAIN)
    r.set(x, 8 - drop, z, light)


def vault_over(r: Room, zs=(3, 7, 11), lamps=(1, 5, 9, 13), lamp: str = SOUL, drop: int = 2) -> None:
    """Ribs across the room with a pilaster and a lit pumpkin at each end, lamps hanging in the bays between them, webs high in the corners."""
    for z in zs:
        I.nave_rib(r, z, 0, W - 1, 1, 8)
        r.set(0, 3, z, I.jack("east"))
        r.set(W - 1, 3, z, I.jack("west"))
    for z in lamps:
        hang(r, 5, z, lamp, drop)
    for x, z in ((0, 0), (W - 1, 0), (0, D - 1), (W - 1, D - 1)):
        r.set(x, 8, z, "minecraft:cobweb")
        r.set(x, 7, z, "minecraft:cobweb")


def knight(r: Room, x: int, z: int, facing: str) -> None:
    """A guard of stone and iron with a lit pumpkin for a head, looking at the aisle."""
    r.set(x, 1, z, I.BRICKS)
    r.set(x, 2, z, IRON_BARS)
    r.set(x, 3, z, IRON_BARS)
    r.set(x, 4, z, I.jack(facing))


def battle(r: Room) -> None:
    """A guard of honour: pumpkin-headed knights down both walls between ribs, orange pennants above them, an orange line in the floor."""
    base(r, BLACKSTONE, I.TILES, 2)
    for z in range(D):
        for x in (3, 7):
            r.set(x, 0, z, ORANGE if z % 2 == 0 else TRIM)
    vault_over(r)
    for z in (1, 5, 9, 13):
        knight(r, 1, z, "east")
        knight(r, 9, z, "west")
    for z in (5, 9):
        for x in (0, 10):
            r.fill(x, x, 5, 7, z - 1, z + 1, ORANGE_WOOL)
            r.set(x, 5, z, PURPLE_WOOL)
            r.set(x, 4, z, PURPLE_WOOL)


def rest(r: Room) -> None:
    """A haunted inn's corner: bedrolls with a candle by each pillow on one side, a hearth of black stone with a glowing fire and a soup pot on the other."""
    base(r, SPRUCE, "minecraft:dark_oak_planks", 3)
    vault_over(r, lamp=LANTERN, drop=1)
    for z in (1, 5, 9):
        r.fill(1, 2, 1, 1, z, z + 1, RED_WOOL)
        r.fill(1, 1, 1, 1, z, z + 1, WHITE_WOOL)
        r.set(3, 1, z, SPRUCE)
        r.set(3, 2, z, CANDLE)
    r.fill(9, 10, 1, 6, 5, 9, BLACKSTONE)                   # the hearth: a stone mass with a mouth, a mantel and a chimney
    r.fill(9, 9, 1, 3, 6, 8, AIR)
    r.fill(9, 9, 1, 1, 6, 8, MAGMA)
    r.fill(9, 9, 2, 3, 6, 8, "minecraft:orange_stained_glass")
    r.set(9, 2, 7, SHROOM)
    r.fill(9, 9, 4, 4, 5, 9, I.CHISELED)
    r.fill(8, 8, 4, 4, 5, 9, I.BRICKS)
    r.set(8, 5, 7, I.jack("west"))
    r.set(8, 1, 5, CAULDRON)
    r.set(8, 1, 9, CAULDRON)
    r.mirror_x(0, 0, 1, 2, 12, 13, HAY)
    r.set(1, 1, 13, HAY)
    r.set(8, 1, 12, HAY)
    r.set(8, 2, 12, CANDLE)


def shrine(r: Room) -> None:
    """An altar of obsidian and amethyst on each side under a tall lit window of violet and amber glass, soul lanterns in the bays."""
    base(r, I.POLISHED, I.TILES, 4)
    vault_over(r, lamp=SOUL, drop=2)
    for z in (5, 9):
        for x0, x1, inner in ((1, 2, 2), (8, 9, 8)):
            r.fill(x0, x1, 1, 1, z - 1, z + 1, OBSIDIAN)
            r.fill(x0, x1, 2, 2, z - 1, z + 1, TRIM)
            r.set(x1 if x0 == 1 else x0, 3, z, AMETHYST)
            r.set(x1 if x0 == 1 else x0, 4, z, CLUSTER)
            r.set(inner, 3, z - 1, CANDLE)
            r.set(inner, 3, z + 1, CANDLE)
        I.lancet_x(r, 1, 5, z, -1, 0, 4)
        I.lancet_x(r, 9, 5, z, 1, 0, 4)
    r.fill(4, 6, 1, 1, 7, 7, CARPET_PURPLE)


def gambler(r: Room) -> None:
    """A wheel in the floor of orange and black sectors round a glowing pumpkin, a chandelier over it, gaming tables and great dice at the sides."""
    base(r, BLACKSTONE, I.TILES, 5)
    cx, cz = 5, 7
    for x in range(W):
        for z in range(D):
            rr = math.hypot(x - cx, z - cz)
            if rr <= 4.6:
                sector = int((math.atan2(z - cz, x - cx) + math.pi) / (2 * math.pi) * 12) % 2
                r.set(x, 0, z, ORANGE if sector == 0 else DARK)
            if rr <= 1.5:
                r.set(x, 0, z, SHROOM)
    vault_over(r, zs=(4, 10), lamps=(1, 13), lamp=SOUL, drop=1)
    hang(r, 5, 7, LANTERN, 2)
    for z0 in (1, 11):                                       # gaming tables: black stone, felt, candles
        r.fill(1, 2, 1, 1, z0, z0 + 1, DARK)
        r.fill(1, 2, 2, 2, z0, z0 + 1, PURPLE_WOOL)
        r.set(1, 3, z0, CANDLE)
        r.set(2, 3, z0 + 1, CANDLE)
    for dz0, dx0 in ((6, 8), (6, 1)):                        # the great dice
        r.fill(dx0, dx0 + 1, 1, 2, dz0, dz0 + 1, WHITE)
        r.set(dx0, 2, dz0, DARK)
        r.set(dx0 + 1, 2, dz0 + 1, DARK)
    for x in (0, 10):
        r.fill(x, x, 3, 6, 5, 9, ORANGE_WOOL)
        r.fill(x, x, 3, 3, 5, 9, PURPLE_WOOL)


def forge(r: Room) -> None:
    """A hot smithy: anvils and a grindstone on red brick, a glowing pit under a hood of black stone, lava pots, racks of bars on the walls."""
    base(r, NETHER_BRICKS, I.POLISHED, 6)
    vault_over(r, lamp=LANTERN, drop=2)
    for z in (3, 6, 9):
        r.set(1, 1, z, ANVIL_E)
    r.set(2, 1, 12, SMITHING)
    r.set(1, 1, 1, GRINDSTONE)
    r.fill(7, 9, 0, 0, 5, 7, MAGMA)
    r.fill(8, 9, 1, 1, 4, 8, BLACKSTONE)
    r.fill(8, 9, 2, 2, 5, 7, "minecraft:orange_stained_glass")
    r.set(8, 2, 6, SHROOM)
    r.fill(8, 9, 3, 3, 4, 8, I.CHISELED)
    r.fill(9, 9, 4, 8, 5, 7, BLACKSTONE)
    r.set(8, 1, 10, LAVA_CAULDRON)
    r.set(9, 1, 11, LAVA_CAULDRON)
    r.set(8, 1, 2, LAVA_CAULDRON)
    r.mirror_x(1, 2, 1, 2, 0, 0, IRON_BLOCK)
    for z in (1, 5, 9, 13):
        r.fill(0, 0, 3, 6, z - 1, z + 1, IRON_BARS)


def library(r: Room) -> None:
    """Seven-high bookcases on both walls, low shelves, ladders and a reading desk with a candle in two bays, soul lanterns over the aisle."""
    base(r, I.POLISHED, I.TILES, 7)
    vault_over(r, lamp=SOUL, drop=2)
    for x in (0, 10):
        for z in range(1, 14):
            if z not in (3, 7, 11):
                r.fill(x, x, 1, 7, z, z, BOOKSHELF)
    for z in (2, 4, 6, 8, 10, 12):
        r.fill(1, 1, 1, 2, z, z, BOOKSHELF)
        r.fill(9, 9, 1, 2, z, z, BOOKSHELF)
    for z in (5, 9):
        r.set(2, 1, z, "minecraft:spruce_slab[type=top]")
        r.set(2, 2, z, CANDLE)
        r.set(8, 1, z, "minecraft:spruce_slab[type=top]")
        r.set(8, 2, z, CANDLE)
        r.fill(1, 1, 1, 6, z, z, "minecraft:ladder[facing=east]")
        r.fill(9, 9, 1, 6, z, z, "minecraft:ladder[facing=west]")


def vault(r: Room) -> None:
    """Gold behind bars down both walls, a gilded floor, lodestones high on the gold and amber light on the wall above."""
    base(r, GILDED, I.POLISHED, 8)
    vault_over(r, lamp=SOUL, drop=1)
    for x0, x1 in ((1, 2), (8, 9)):
        r.fill(x0, x1, 1, 1, 1, 13, GOLD)
        r.fill(x0, x1, 2, 2, 2, 12, GOLD)
        r.fill(x0, x1, 3, 3, 4, 10, GOLD)
    for x in (3, 7):
        r.fill(x, x, 1, 3, 1, 13, IRON_BARS)
    for x in (1, 9):
        r.set(x, 4, 7, LODESTONE)
        for z in (2, 12):
            r.set(x, 3, z, CANDLE)
    for z in (1, 5, 9, 13):
        for x in (0, 10):
            r.fill(x, x, 5, 7, z, z, I.FROG_AMBER)


def pool(r: Room) -> None:
    """Two basins of prismarine and still water, lit from beneath, lily pads on the surface, a dark pillar with a sea lantern in each, cold light above."""
    base(r, PRISMARINE, "minecraft:dark_prismarine", 9)
    vault_over(r, lamp=SOUL, drop=2)
    for x0, x1 in ((1, 3), (7, 9)):
        r.fill(x0, x1, 0, 0, 3, 11, PRISMARINE)
        r.fill(x0, x1, 1, 1, 3, 3, PRISMARINE)
        r.fill(x0, x1, 1, 1, 11, 11, PRISMARINE)
        r.fill(x0, x0, 1, 1, 3, 11, PRISMARINE)
        r.fill(x1, x1, 1, 1, 3, 11, PRISMARINE)
        r.fill(x0 + 1 if x0 == 1 else x0, x1 - 1 if x1 == 9 else x1, 1, 1, 4, 10, WATER)
        r.fill(x0 + 1, x1 - 1, 0, 0, 4, 10, SEA_LANTERN)
    r.mirror_x(3, 3, 1, 1, 3, 11, PRISMARINE)
    for x, z in ((2, 5), (2, 9), (8, 5), (8, 9)):
        r.set(x, 2, z, "minecraft:lily_pad")


def trick_or_treat(r: Room) -> None:
    """The Jack-o'-Lantern Vault's room: a brick dais at the east wall, off the aisle (the vault stands on it, facing west), candles round it, a lit window
    of violet and amber glass above and behind it, a column of carved pumpkins down the west wall, pennants, soul lights overhead. The vault is drawn at run time."""
    base(r, BLACKSTONE, I.TILES, 10)
    vault_over(r, lamp=SOUL, drop=2)
    r.fill(7, 9, 1, 1, 4, 6, BRICKS)                         # the dais, exactly where RoomDresser puts the vault
    r.fill(7, 9, 0, 0, 3, 7, TRIM)
    for x, z in ((7, 3), (7, 7), (9, 3), (9, 7)):
        r.set(x, 1, z, CANDLE)
    for z in (2, 5, 8, 12):
        r.set(1, 1, z, JACK)
        r.set(1, 2, z, JACK)
    I.lancet_x(r, 9, 4, 5, 1, 1, 4)                          # above the chest, glass at x 9 and the lamp at x 10
    r.fill(10, 10, 1, 3, 4, 6, SHROOM)                       # and a glow low behind the vault
    for z in (5, 9):
        r.fill(0, 0, 4, 7, z - 1, z + 1, ORANGE_WOOL)
        r.set(0, 4, z, PURPLE_WOOL)


ROOMS = {
    "battle": battle, "rest": rest, "shrine": shrine, "gambler": gambler,
    "forge": forge, "library": library, "vault": vault, "pool": pool, "trick_or_treat": trick_or_treat,
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
