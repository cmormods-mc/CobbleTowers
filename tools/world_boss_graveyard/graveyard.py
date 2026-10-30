#!/usr/bin/env python3
"""The World Boss graveyard: an open night-time churchyard, 128 x 72 x 128, for the CobbleRaids World Boss arena.

    python tools/world_boss_graveyard/graveyard.py [--out validation/schematics] [--npz grid.npz] [--check]

Schematic coordinates: x east, z south, y up, (0,0,0) the north-west bottom corner. The reference is a moonlit churchyard: a dark stone church on a
terraced hill with three tall stained-glass windows glowing red, orange and green, a green-lit porch and a bell tower, stairs up from a flagstone plaza,
orange flame pillars, tombstones in purple-red grass, gnarled leafless trees and two small crypt towers behind an iron fence.

The arena contract (CobbleRaids docs/WORLD-BOSS-DESIGN.md 12c): the boss stands at a `cobbleraids:world_boss_spawn` marker, players warp to a
`cobbleraids:world_boss_entrance` marker, the arena radius is 96. The plaza (centre 64, 86) is open ground, 20 blocks clear on every side and open to the
sky; the sky, the moon and the time of day belong to the world, not to this file. No block entity is used.
"""
from __future__ import annotations

import argparse
import math
import random
import sys
from collections import deque
from pathlib import Path

import numpy as np

W, H, D = 128, 72, 128
S0 = 12                 # the top block of the plaza and the avenue
LIFT = 10
T = S0 + LIFT           # the top block of the church terrace
AIR = "minecraft:air"
CX, CZ = 64, 86         # the plaza centre and the boss
PLAZA_RX, PLAZA_RZ = 27, 23
TERRACE = (30, 104, 8, 38)          # x0, x1, z0, z1 of the flat terrace the church stands on
SPAWN = (CX, S0 + 1, CZ)
ENTRANCES = [(60, S0 + 1, 116), (64, S0 + 1, 118), (68, S0 + 1, 116), (64, S0 + 1, 112)]

MARKER_SPAWN = "cobbleraids:world_boss_spawn"
MARKER_ENTRANCE = "cobbleraids:world_boss_entrance"


def stairs(kind: str, facing: str, half: str = "bottom") -> str:
    return f"minecraft:{kind}_stairs[facing={facing},half={half},shape=straight]"


def slab(kind: str, where: str = "bottom") -> str:
    return f"minecraft:{kind}_slab[type={where}]"


def wall(kind: str) -> str:
    return f"minecraft:{kind}_wall[up=true,east=none,north=none,south=none,west=none]"


STONE_BRICKS = "minecraft:stone_bricks"
CRACKED_BRICKS = "minecraft:cracked_stone_bricks"
MOSSY_BRICKS = "minecraft:mossy_stone_bricks"
DEEP_BRICKS = "minecraft:deepslate_bricks"
DEEP_TILES = "minecraft:deepslate_tiles"
COBBLED_DEEP = "minecraft:cobbled_deepslate"
POLISHED_DEEP = "minecraft:polished_deepslate"
BLACKSTONE_BRICKS = "minecraft:polished_blackstone_bricks"
CHISELED = "minecraft:chiseled_stone_bricks"
TUFF = "minecraft:tuff"
MYCELIUM = "minecraft:mycelium"
PODZOL = "minecraft:podzol"
COARSE = "minecraft:coarse_dirt"
MOSS = "minecraft:moss_block"
NYLIUM = "minecraft:crimson_nylium"
DIRT = "minecraft:dirt"
STONE = "minecraft:stone"
DEEPSLATE = "minecraft:deepslate"
GRAVEL = "minecraft:gravel"
BARS = "minecraft:iron_bars"
SHROOM = "minecraft:shroomlight"
OCHRE = "minecraft:ochre_froglight[axis=y]"
VERDANT = "minecraft:verdant_froglight[axis=y]"
GLASS_ORANGE = "minecraft:orange_stained_glass"
GLASS_RED = "minecraft:red_stained_glass"
GLASS_LIME = "minecraft:lime_stained_glass"
CHAIN = "minecraft:chain[axis=y]"
SOUL = "minecraft:soul_lantern[hanging=false]"
LANTERN = "minecraft:lantern[hanging=false]"
LOG = "minecraft:spruce_wood"
LOG_RED = "minecraft:crimson_hyphae"
TERRA = "minecraft:orange_terracotta"
TERRA_RED = "minecraft:red_terracotta"
COBWEB = "minecraft:cobweb"


def noise(x: int, z: int, salt: int = 0) -> int:
    return ((x * 73856093) ^ (z * 19349663) ^ (salt * 83492791)) % 1000


def smooth(t: float) -> float:
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


class Grid:
    def __init__(self) -> None:
        self.grid = np.zeros((W, H, D), dtype=np.uint16)
        self.palette = [AIR]
        self.index = {AIR: 0}

    def bid(self, name: str) -> int:
        found = self.index.get(name)
        if found is None:
            found = len(self.palette)
            self.palette.append(name)
            self.index[name] = found
        return found

    def fill(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int, block: str) -> None:
        x0, x1, y0, y1, z0, z1 = min(x0, x1), max(x0, x1), min(y0, y1), max(y0, y1), min(z0, z1), max(z0, z1)
        x0, y0, z0 = max(x0, 0), max(y0, 0), max(z0, 0)
        x1, y1, z1 = min(x1, W - 1), min(y1, H - 1), min(z1, D - 1)
        if x0 > x1 or y0 > y1 or z0 > z1:
            return
        self.grid[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = self.bid(block)

    def set(self, x: int, y: int, z: int, block: str) -> None:
        if 0 <= x < W and 0 <= y < H and 0 <= z < D:
            self.grid[x, y, z] = self.bid(block)

    def get(self, x: int, y: int, z: int) -> str:
        return self.palette[int(self.grid[x, y, z])] if (0 <= x < W and 0 <= y < H and 0 <= z < D) else AIR

    def air(self, x0, x1, y0, y1, z0, z1) -> None:
        self.fill(x0, x1, y0, y1, z0, z1, AIR)


# ---- the ground -------------------------------------------------------------------------------------------------------------------

def in_ellipse(x: float, z: float, cx: float, cz: float, rx: float, rz: float) -> float:
    return ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2


def near_segment(x: float, z: float, a: tuple, b: tuple) -> float:
    """Distance from a point to a segment."""
    ax, az = a
    bx, bz = b
    dx, dz = bx - ax, bz - az
    t = 0.0 if dx == dz == 0 else max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz)))
    return math.hypot(x - (ax + t * dx), z - (az + t * dz))


PATHS = [((64, 127), (64, 106), 3.6),           # the avenue from the gate to the plaza
         ((64, 64), (48, 52), 2.8),             # to the porch stair
         ((64, 64), (80, 52), 2.8),             # to the wide stair
         ((40, 86), (22, 62), 1.8),             # to the west crypt
         ((88, 86), (106, 62), 1.8)]            # to the east crypt


def path_distance(x: int, z: int) -> float:
    best = 99.0
    for a, b, w in PATHS:
        best = min(best, near_segment(x, z, a, b) - w)
    return best


def ground_height(x: int, z: int) -> int:
    dx = max(TERRACE[0] - x, 0, x - TERRACE[1])
    dz = max(TERRACE[2] - z, 0, z - TERRACE[3])
    d = math.hypot(dx, dz)
    lift = LIFT * smooth(1 - d / (LIFT + 1.0))
    # a low rim of hills round the edge, so the arena sits in a bowl
    edge = min(x, z, W - 1 - x, D - 1 - z)
    rim = 4.0 * smooth(1 - edge / 22.0)
    wild = 1.4 * (0.5 + (math.sin(x * 0.17) + math.sin(z * 0.13) + math.sin((x + z) * 0.08)) / 6.0)
    flat = in_ellipse(x, z, CX, CZ, PLAZA_RX + 3, PLAZA_RZ + 3) < 1.0 or path_distance(x, z) < 1.5
    h = S0 + lift + (0 if flat else rim + wild)
    return int(round(h))


def paving(x: int, z: int) -> str:
    n = noise(x, z, 4)
    if n < 330:
        return DEEP_TILES
    if n < 520:
        return COBBLED_DEEP
    if n < 660:
        return STONE_BRICKS
    if n < 770:
        return CRACKED_BRICKS
    if n < 870:
        return POLISHED_DEEP
    if n < 930:
        return TUFF
    return GRAVEL


def blob(x: int, z: int, scale: float, salt: float) -> float:
    """Smooth low-frequency noise in 0..1, so soil comes in patches and not in speckle."""
    v = math.sin(x * scale + salt) + math.sin(z * scale * 1.3 + salt * 2) + math.sin((x + z) * scale * 0.7 + salt * 3) + math.sin((x - z) * scale * 0.9 + salt)
    return (v + 4) / 8


def turf(x: int, z: int) -> str:
    red, grey = blob(x, z, 0.21, 1.0), blob(x, z, 0.17, 5.0)
    if red > 0.66:
        return NYLIUM
    if grey > 0.68:
        return PODZOL
    if noise(x, z, 5) < 70:
        return COARSE
    return MYCELIUM


def build_ground(g: Grid) -> np.ndarray:
    height = np.zeros((W, D), dtype=np.int32)
    for x in range(W):
        for z in range(D):
            h = ground_height(x, z)
            height[x, z] = h
            on_plaza = in_ellipse(x, z, CX, CZ, PLAZA_RX, PLAZA_RZ) < 1.0
            on_path = path_distance(x, z) < 0
            on_terrace = TERRACE[0] <= x <= TERRACE[1] and TERRACE[2] <= z <= TERRACE[3]
            g.fill(x, x, 0, 3, z, z, DEEPSLATE)
            g.fill(x, x, 4, h - 3, z, z, STONE)
            g.fill(x, x, h - 2, h - 1, z, z, DIRT)
            if on_plaza or on_path or (on_terrace and not (noise(x, z, 6) < 140)):
                g.set(x, h, z, paving(x, z))
            elif on_terrace:
                g.set(x, h, z, turf(x, z))
            else:
                g.set(x, h, z, turf(x, z))
    return height


def inlay(g: Grid, height: np.ndarray) -> None:
    """A ritual circle in the plaza: a crying-obsidian ring, eight spokes in polished stone, a dark disc under the boss with a gilded heart."""
    for x in range(CX - 20, CX + 21):
        for z in range(CZ - 20, CZ + 21):
            r = math.hypot(x - CX, z - CZ)
            y = int(height[x, z])
            ang = math.atan2(z - CZ, x - CX)
            spoke = abs(ang / (math.pi / 4) - round(ang / (math.pi / 4))) < 0.09
            if 17.0 <= r < 18.5:
                g.set(x, y, z, "minecraft:crying_obsidian")
            elif 11.0 <= r < 12.0:
                g.set(x, y, z, "minecraft:polished_blackstone")
            elif r < 5.5:
                g.set(x, y, z, BLACKSTONE_BRICKS if r > 1.5 else "minecraft:gilded_blackstone")
            elif spoke and 5.5 <= r < 17.0:
                g.set(x, y, z, POLISHED_DEEP)


def scatter_plants(g: Grid, height: np.ndarray) -> None:
    """Purple-red undergrowth: crimson roots thick in the red patches, thinner elsewhere, a few dead bushes; only on soil, never on paving."""
    soil = {MYCELIUM, PODZOL, COARSE, NYLIUM}
    for x in range(W):
        for z in range(D):
            y = int(height[x, z])
            here = g.get(x, y, z)
            if here not in soil or g.get(x, y + 1, z) != AIR:
                continue
            n = noise(x, z, 9)
            dense = 380 if here == NYLIUM else 200
            if n < dense:
                g.set(x, y + 1, z, "minecraft:crimson_roots")
            elif n < dense + 25:
                g.set(x, y + 1, z, "minecraft:dead_bush")


# ---- little things ----------------------------------------------------------------------------------------------------------------

def flame_pillar(g: Grid, x: int, y: int, z: int) -> None:
    """A rust-orange pillar with a bright flame: terracotta body, a dark cap, shroomlight and orange glass."""
    g.fill(x, x + 1, y, y + 1, z, z + 1, DEEP_BRICKS)
    g.fill(x, x + 1, y + 2, y + 4, z, z + 1, TERRA)
    g.fill(x, x + 1, y + 5, y + 5, z, z + 1, TERRA_RED)
    g.fill(x - 1, x + 2, y + 5, y + 5, z - 1, z + 2, slab("deepslate_brick"))
    g.fill(x, x + 1, y + 5, y + 5, z, z + 1, DEEP_BRICKS)
    g.fill(x, x + 1, y + 6, y + 6, z, z + 1, SHROOM)
    g.fill(x, x + 1, y + 7, y + 7, z, z + 1, GLASS_ORANGE)
    g.set(x, y + 8, z, GLASS_ORANGE)


def lamp_post(g: Grid, x: int, y: int, z: int) -> None:
    g.fill(x, x, y, y + 2, z, z, wall("deepslate_brick"))
    g.set(x, y + 3, z, SOUL)


def tombstone(g: Grid, x: int, y: int, z: int, kind: int, rng: random.Random) -> None:
    """Standing at y (the first free block). kind 0 slab headstone, 1 cross, 2 obelisk, 3 flat slab on a mound."""
    face = rng.choice(["north", "south"])
    if kind == 0:
        g.fill(x, x + 1, y, y + 1, z, z, STONE_BRICKS if rng.random() < 0.6 else CRACKED_BRICKS)
        g.fill(x, x + 1, y + 2, y + 2, z, z, slab("stone_brick"))
        g.set(x, y, z + 1, COBBLED_DEEP)
    elif kind == 1:
        g.fill(x, x, y, y + 2, z, z, wall("stone_brick"))
        g.set(x - 1, y + 2, z, wall("stone_brick"))
        g.set(x + 1, y + 2, z, wall("stone_brick"))
        g.set(x, y + 3, z, wall("stone_brick"))
    elif kind == 2:
        g.fill(x, x, y, y + 1, z, z, DEEP_BRICKS)
        g.fill(x, x, y + 2, y + 3, z, z, wall("deepslate_brick"))
        g.set(x, y + 4, z, slab("deepslate_brick"))
    else:
        g.fill(x - 1, x + 1, y, y, z - 1, z + 1, COARSE)
        g.set(x, y + 1, z, slab("cobbled_deepslate"))
    del face
    # a candle on the soil beside many of them: a small warm light in the dark
    if rng.random() < 0.55 and g.get(x + 2, y, z + 1) == AIR and g.get(x + 2, y - 1, z + 1) not in (AIR, "minecraft:crimson_roots"):
        kind_c = rng.choice(["candle", "candle", "orange_candle", "purple_candle"])
        g.set(x + 2, y, z + 1, f"minecraft:{kind_c}[candles={rng.randint(1, 3)},lit=true]")


def boulder(g: Grid, x: int, y: int, z: int, r: float, rng: random.Random) -> None:
    ry = max(1.0, r * 0.7)
    for dx in range(-int(r) - 1, int(r) + 2):
        for dz in range(-int(r) - 1, int(r) + 2):
            for dy in range(0, int(ry) + 2):
                if (dx / r) ** 2 + (dz / r) ** 2 + (dy / ry) ** 2 <= 1.0:
                    n = rng.random()
                    g.set(x + dx, y + dy, z + dz, COBBLED_DEEP if n < 0.45 else TUFF if n < 0.7 else DEEPSLATE if n < 0.9 else STONE)


# ---- trees ------------------------------------------------------------------------------------------------------------------------

def stamp(g: Grid, p: tuple, r: float, block: str, axis: str) -> None:
    x, y, z = p
    blk = block + f"[axis={axis}]"
    ri = int(math.ceil(r))
    for dx in range(-ri, ri + 1):
        for dy in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                if dx * dx + dy * dy + dz * dz <= r * r + 0.3:
                    g.set(int(round(x)) + dx, int(round(y)) + dy, int(round(z)) + dz, blk)


def dominant_axis(d: tuple) -> str:
    return "xyz"[int(np.argmax(np.abs(d)))]


def branch(g: Grid, rng: random.Random, start: tuple, direction: tuple, length: int, radius: float, block: str, depth: int) -> None:
    pos = list(start)
    d = list(direction)
    for i in range(length):
        r = max(0.45, radius * (1 - i / (length * 1.25)))
        stamp(g, tuple(pos), r, block, dominant_axis(d))
        # a gnarled path: the heading wanders, curls and droops a little as the branch ages
        d[0] += rng.uniform(-0.45, 0.45)
        d[2] += rng.uniform(-0.45, 0.45)
        d[1] += rng.uniform(-0.25, 0.3) - (0.03 if i > length * 0.5 else 0.0)
        n = math.sqrt(sum(c * c for c in d)) or 1.0
        d = [c / n for c in d]
        pos = [pos[0] + d[0], pos[1] + d[1], pos[2] + d[2]]
        if depth < 2 and i > 3 and rng.random() < 0.22:
            sub = (d[0] + rng.uniform(-0.9, 0.9), d[1] * 0.6 + rng.uniform(0.0, 0.6), d[2] + rng.uniform(-0.9, 0.9))
            branch(g, rng, tuple(pos), sub, max(4, int(length * rng.uniform(0.35, 0.6))), max(0.5, r * 0.7), block, depth + 1)


def tree(g: Grid, rng: random.Random, x: int, y: int, z: int, height: int, thick: float, block: str) -> None:
    """A tall, thick, leafless trunk that bends, with long curling boughs that climb and droop."""
    pos = [float(x), float(y), float(z)]
    lean = [rng.uniform(-0.12, 0.12), 0.0, rng.uniform(-0.12, 0.12)]
    for i in range(height):
        t = i / height
        stamp(g, tuple(pos), thick * (1 - 0.55 * t), block, "y")
        pos[0] += lean[0] + math.sin(i * 0.35 + x) * 0.35
        pos[2] += lean[2] + math.cos(i * 0.3 + z) * 0.35
        pos[1] += 1.0
        if i > height * 0.35 and i % 3 == 0 and rng.random() < 0.7:
            ang = rng.uniform(0, 2 * math.pi)
            d = (math.cos(ang), rng.uniform(0.1, 0.7), math.sin(ang))
            branch(g, rng, tuple(pos), d, rng.randint(9, 20), max(0.8, thick * 0.55 * (1 - t * 0.5)), block, 0)
    for _ in range(3):
        ang = rng.uniform(0, 2 * math.pi)
        branch(g, rng, tuple(pos), (math.cos(ang), rng.uniform(0.5, 1.0), math.sin(ang)), rng.randint(8, 14), max(0.7, thick * 0.4), block, 0)


# ---- buildings --------------------------------------------------------------------------------------------------------------------

def lancet(g: Grid, xc: int, y0: int, z: int, behind: int, rows: int, idx: int) -> None:
    """A tall pointed stained-glass window in a wall along x: five wide, a lead mullion down the middle, lead transoms, a head that narrows to a point.
    Panes alternate orange and red with green accents; each is lit from behind (ochre for the warm glass, verdant for the green)."""
    def pane(dx: int, r: int) -> None:
        lead = dx == 0 or r in (3, 7)
        if lead and r < rows:
            g.set(xc + dx, y0 + r, z, BLACKSTONE_BRICKS)
            g.set(xc + dx, y0 + r, z + behind, BLACKSTONE_BRICKS)
            return
        green = (r in (1, 5) and abs(dx) == 1) or r >= rows
        colour = GLASS_LIME if green else (GLASS_RED if (r // 4 + (dx > 0) + idx) % 2 else GLASS_ORANGE)
        g.set(xc + dx, y0 + r, z, colour)
        g.set(xc + dx, y0 + r, z + behind, VERDANT if colour == GLASS_LIME else OCHRE)

    for r in range(rows):
        for dx in range(-2, 3):
            pane(dx, r)
    for r, half in ((rows, 1), (rows + 1, 0)):
        for dx in range(-half, half + 1):
            pane(dx, r)


def gable_end_x(g: Grid, x: int, z0: int, z1: int, y_eave: int, block: str) -> None:
    """The triangle of wall under a gable that runs along x, seen from the end, at column x."""
    mid = (z0 + z1) / 2.0
    for z in range(z0, z1 + 1):
        top = y_eave + int(round((z1 - z0) / 2.0 - abs(z - mid)))
        g.fill(x, x, y_eave, top, z, z, block)


def church(g: Grid) -> None:
    nx0, nx1, nz0, nz1 = 52, 86, 14, 31          # the nave, outer faces
    wall_top = T + 14
    # walls: two blocks thick, hollow inside, a floor of dark tiles
    g.fill(nx0, nx1, T, wall_top, nz0, nz1, STONE_BRICKS)
    g.air(nx0 + 2, nx1 - 2, T + 1, wall_top, nz0 + 2, nz1 - 2)
    g.fill(nx0 + 2, nx1 - 2, T, T, nz0 + 2, nz1 - 2, DEEP_TILES)
    for x in range(nx0, nx1 + 1):
        for y in range(T + 1, wall_top + 1):
            for z in (nz0, nz1):
                n = noise(x, y * 3 + z, 12)
                if n < 90:
                    g.set(x, y, z, CRACKED_BRICKS)
                elif n < 130:
                    g.set(x, y, z, MOSSY_BRICKS)
    # a plinth of darker stone round the foot
    for x in range(nx0, nx1 + 1):
        g.fill(x, x, T + 1, T + 1, nz1, nz1, DEEP_BRICKS)
        g.fill(x, x, T + 1, T + 1, nz0, nz0, DEEP_BRICKS)
    # the roof, slate stairs, ridge along x
    ridge_z0, ridge_z1 = 22, 23
    for z in range(nz0 - 1, ridge_z0 + 1):
        y = wall_top + 1 + (z - (nz0 - 1))
        g.fill(nx0 - 1, nx1 + 1, y, y, z, z, stairs("deepslate_tile", "south"))
        g.fill(nx0, nx1, wall_top + 1, y - 1, z, z, DEEP_BRICKS)
    for z in range(nz1 + 1, ridge_z1 - 1, -1):
        y = wall_top + 1 + (nz1 + 1 - z)
        g.fill(nx0 - 1, nx1 + 1, y, y, z, z, stairs("deepslate_tile", "north"))
        g.fill(nx0, nx1, wall_top + 1, y - 1, z, z, DEEP_BRICKS)
    ridge_y = wall_top + 1 + (ridge_z0 - (nz0 - 1)) + 1
    g.fill(nx0 - 1, nx1 + 1, ridge_y, ridge_y, ridge_z0, ridge_z1, slab("deepslate_tile"))
    for x in (nx0, nx1):
        gable_end_x(g, x, nz0, nz1, wall_top + 1, STONE_BRICKS)
    # buttresses and four tall lit windows on the long south wall (z = nz1), lamps in the wall behind the glass
    for i, xc in enumerate((58, 65, 72, 79)):
        lancet(g, xc, T + 2, nz1, -1, 10, i)
        for bx in (xc - 4, xc + 4):
            g.fill(bx, bx, T + 1, T + 11, nz1 + 1, nz1 + 1, STONE_BRICKS)
            g.set(bx, T + 12, nz1 + 1, stairs("stone_brick", "south"))
    for x in range(nx0, nx1 + 1):                               # a string course under the windows' sills
        g.set(x, T + 1, nz1 + 1, slab("deepslate_brick", "top")) if g.get(x, T + 1, nz1 + 1) == AIR else None
    # a cross on the east gable and the roof's west end
    for cx0 in (nx1 + 1,):
        g.fill(cx0, cx0, ridge_y + 1, ridge_y + 4, ridge_z0, ridge_z0, wall("stone_brick"))
        g.set(cx0, ridge_y + 3, ridge_z0 - 1, wall("stone_brick"))
        g.set(cx0, ridge_y + 3, ridge_z0 + 1, wall("stone_brick"))
    # the interior: a nave of pews with a lamp-lit aisle and an altar
    for x in range(nx0 + 4, nx1 - 6, 3):
        for z in list(range(nz0 + 3, nz0 + 8)) + list(range(nz1 - 7, nz1 - 2)):
            g.set(x, T + 1, z, stairs("spruce", "west"))
    g.fill(nx1 - 5, nx1 - 3, T + 1, T + 1, 20, 25, DEEP_BRICKS)
    for x in range(nx0 + 6, nx1 - 6, 8):
        g.set(x, wall_top, 22, CHAIN)
        g.set(x, wall_top - 1, 22, CHAIN)
        g.set(x, wall_top - 2, 22, "minecraft:soul_lantern[hanging=true]")


def porch(g: Grid) -> None:
    """A gabled porch on the west end, facing south, with a pointed doorway of green glass lit from behind, and the stair up to it."""
    px0, px1, pz0, pz1 = 40, 51, 24, 38
    wall_top = T + 9
    g.fill(px0, px1, T, wall_top, pz0, pz1, STONE_BRICKS)
    g.air(px0 + 2, px1 - 2, T + 1, wall_top, pz0 + 2, pz1 - 2)
    g.fill(px0 + 2, px1 - 2, T, T, pz0 + 2, pz1 - 2, DEEP_TILES)
    mid = (px0 + px1) // 2
    for x in range(px0 - 1, mid + 1):                           # gabled roof, ridge along z
        y = wall_top + 1 + (x - (px0 - 1))
        g.fill(x, x, y, y, pz0 - 1, pz1 + 1, stairs("deepslate_tile", "east"))
        g.fill(x, x, wall_top + 1, y - 1, pz0, pz1, DEEP_BRICKS)
    for x in range(px1 + 1, mid, -1):
        y = wall_top + 1 + (px1 + 1 - x)
        g.fill(x, x, y, y, pz0 - 1, pz1 + 1, stairs("deepslate_tile", "west"))
        g.fill(x, x, wall_top + 1, y - 1, pz0, pz1, DEEP_BRICKS)
    ry = wall_top + 1 + (mid - (px0 - 1)) + 1
    g.fill(mid, mid + 1, ry, ry, pz0 - 1, pz1 + 1, slab("deepslate_tile"))
    # the south gable: a triangle of brick above the doorway
    for x in range(px0, px1 + 1):
        top = wall_top + 1 + (min(x - px0, px1 - x) - 0)
        g.fill(x, x, wall_top + 1, top, pz1, pz1, STONE_BRICKS)
    # the doorway: five wide, a pointed head, glass lit green
    for r in range(11):
        half = 2 if r < 7 else max(0, 2 - (r - 6))
        for dx in range(-half, half + 1):
            g.set(mid + dx, T + 1 + r, pz1, GLASS_LIME)
            g.set(mid + dx, T + 1 + r, pz1 - 1, VERDANT)
    for dx in (-3, 3):
        g.fill(mid + dx, mid + dx, T + 1, T + 8, pz1, pz1, CHISELED)
    # the stair: eight steps down to the plaza level, a rail of wall blocks either side
    for i in range(1, LIFT + 1):
        z = pz1 + i
        y = T - i
        for x in range(mid - 3, mid + 4):
            g.air(x, x, y + 1, y + 8, z, z)
            g.fill(x, x, y, y, z, z, stairs("stone_brick", "north"))
            g.fill(x, x, S0 - 1, y - 1, z, z, STONE_BRICKS)
        for x in (mid - 4, mid + 4):
            g.fill(x, x, y, y + 1, z, z, STONE_BRICKS)
            g.set(x, y + 2, z, wall("stone_brick"))
    for r in range(4):                                          # a cross over the porch gable
        g.set(mid, wall_top + 3 + r + 1, pz1, wall("stone_brick"))
    g.set(mid - 1, wall_top + 5, pz1, wall("stone_brick"))
    g.set(mid + 1, wall_top + 5, pz1, wall("stone_brick"))


def tower(g: Grid) -> None:
    """The bell tower beside the nave: tall, with lit slits, an open belfry with green glass and a pyramid roof under a cross."""
    x0, x1, z0, z1 = 88, 98, 20, 32
    top = T + 34
    g.fill(x0, x1, T, top, z0, z1, STONE_BRICKS)
    g.air(x0 + 2, x1 - 2, T + 1, top, z0 + 2, z1 - 2)
    for y in range(T + 1, top + 1):
        for x in (x0, x1):
            for z in range(z0, z1 + 1):
                n = noise(x + z * 3, y, 13)
                if n < 100:
                    g.set(x, y, z, CRACKED_BRICKS)
                elif n < 150:
                    g.set(x, y, z, MOSSY_BRICKS)
    for y in (T + 11, T + 23):                                  # string courses
        g.fill(x0 - 1, x1 + 1, y, y, z0 - 1, z1 + 1, DEEP_BRICKS)
        g.fill(x0 - 1, x1 + 1, y, y, z0 - 1, z1 + 1, DEEP_BRICKS)
    for xc in (x0 + 3, x1 - 3):                                 # two narrow green windows on the south face, high on the shaft
        for r in range(5):
            g.set(xc, T + 14 + r, z1, GLASS_LIME)
            g.set(xc, T + 14 + r, z1 - 1, VERDANT)
    mid_x, mid_z = (x0 + x1) // 2, (z0 + z1) // 2
    for r in range(6):                                          # the belfry: an arched opening on each face
        for dx in range(-1, 2):
            if r == 5 and dx != 0:
                continue
            y = T + 27 + r
            for (px, pz) in ((mid_x + dx, z1), (mid_x + dx, z0)):
                g.set(px, y, pz, GLASS_LIME)
                g.set(px, y, pz + (-1 if pz == z1 else 1), VERDANT)
            for (px, pz) in ((x1, mid_z + dx), (x0, mid_z + dx)):
                g.set(px, y, pz, GLASS_LIME)
                g.set(px + (-1 if px == x1 else 1), y, pz, VERDANT)
    # the roof: rings of slate stairs shrinking to a point
    y = top + 1
    lo_x, hi_x, lo_z, hi_z = x0 - 1, x1 + 1, z0 - 1, z1 + 1
    while lo_x < hi_x - 1 and lo_z < hi_z - 1:
        g.fill(lo_x, hi_x, y, y, lo_z, lo_z, stairs("deepslate_tile", "south"))
        g.fill(lo_x, hi_x, y, y, hi_z, hi_z, stairs("deepslate_tile", "north"))
        g.fill(lo_x, lo_x, y, y, lo_z, hi_z, stairs("deepslate_tile", "east"))
        g.fill(hi_x, hi_x, y, y, lo_z, hi_z, stairs("deepslate_tile", "west"))
        g.fill(lo_x + 1, hi_x - 1, y, y, lo_z + 1, hi_z - 1, DEEP_BRICKS)
        lo_x, hi_x, lo_z, hi_z, y = lo_x + 1, hi_x - 1, lo_z + 1, hi_z - 1, y + 1
    g.fill(mid_x, mid_x, y, y + 3, mid_z, mid_z, wall("stone_brick"))
    g.set(mid_x - 1, y + 2, mid_z, wall("stone_brick"))
    g.set(mid_x + 1, y + 2, mid_z, wall("stone_brick"))


def wide_stair(g: Grid) -> None:
    """The broad stair of rough stone up to the terrace east of the porch, with flame pillars at the foot and the head."""
    x0, x1 = 74, 86
    for i in range(1, LIFT + 1):
        z = 38 + i
        y = T - i
        for x in range(x0, x1 + 1):
            g.air(x, x, y + 1, y + 8, z, z)
            g.fill(x, x, y, y, z, z, stairs("cobbled_deepslate" if noise(x, z, 21) < 500 else "stone_brick", "north"))
            g.fill(x, x, S0 - 1, y - 1, z, z, COBBLED_DEEP)
    for x in (x0 - 1, x1 + 1):
        for i in range(1, LIFT + 1):
            z = 38 + i
            g.fill(x, x, T - i, T - i + 1, z, z, COBBLED_DEEP)


def crypt_tower(g: Grid, x0: int, z0: int, face: str, height: int = 20) -> None:
    """A small crypt tower with a pointed roof and green windows facing the plaza."""
    x1, z1 = x0 + 8, z0 + 8
    h = int(g.get(x0, 0, z0) != AIR) and 0
    base = S0 + 1
    top = base + height
    g.fill(x0, x1, base - 1, top, z0, z1, STONE_BRICKS)
    g.air(x0 + 2, x1 - 2, base, top, z0 + 2, z1 - 2)
    for y in range(base, top + 1):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                if noise(x, y + z, 15) < 120:
                    g.set(x, y, z, CRACKED_BRICKS)
    mid_x, mid_z = (x0 + x1) // 2, (z0 + z1) // 2
    wx = {"east": x1, "west": x0}.get(face)
    for r in range(5):
        for dz in (-1, 0, 1) if r < 4 else (0,):
            if wx is not None:
                g.set(wx, base + 9 + r, mid_z + dz, GLASS_LIME)
                g.set(wx + (-1 if wx == x1 else 1), base + 9 + r, mid_z + dz, VERDANT)
    for r in range(6):                                          # a doorway arch in the south face
        for dx in range(-1, 2):
            if r < 4 or dx == 0:
                g.set(mid_x + dx, base + r, z1, AIR)
    y = top + 1
    lo_x, hi_x, lo_z, hi_z = x0 - 1, x1 + 1, z0 - 1, z1 + 1
    while lo_x < hi_x - 1 and lo_z < hi_z - 1:
        g.fill(lo_x, hi_x, y, y, lo_z, lo_z, stairs("deepslate_tile", "south"))
        g.fill(lo_x, hi_x, y, y, hi_z, hi_z, stairs("deepslate_tile", "north"))
        g.fill(lo_x, lo_x, y, y, lo_z, hi_z, stairs("deepslate_tile", "east"))
        g.fill(hi_x, hi_x, y, y, lo_z, hi_z, stairs("deepslate_tile", "west"))
        g.fill(lo_x + 1, hi_x - 1, y, y, lo_z + 1, hi_z - 1, DEEP_BRICKS)
        lo_x, hi_x, lo_z, hi_z, y = lo_x + 1, hi_x - 1, lo_z + 1, hi_z - 1, y + 1
    g.fill(mid_x, mid_x, y, y + 2, mid_z, mid_z, wall("stone_brick"))
    g.set(mid_x - 1, y + 1, mid_z, wall("stone_brick"))
    g.set(mid_x + 1, y + 1, mid_z, wall("stone_brick"))
    del h


def fence(g: Grid, height: np.ndarray) -> None:
    """An iron fence with stone piers all round, and the gate: two tall piers with pointed caps and an open arch of bars."""
    def pier(x: int, z: int) -> None:
        y = int(height[min(max(x, 0), W - 1), min(max(z, 0), D - 1)])
        g.fill(x, x, y + 1, y + 4, z, z, DEEP_BRICKS)
        g.set(x, y + 5, z, wall("deepslate_brick"))
        g.set(x, y + 6, z, SOUL)

    for x in range(1, W - 1):
        for z in (1, D - 2):
            y = int(height[x, z])
            if abs(x - 64) <= 6 and z == D - 2:
                continue
            if x % 8 == 0:
                pier(x, z)
            else:
                g.fill(x, x, y + 1, y + 3, z, z, BARS)
    for z in range(1, D - 1):
        for x in (1, W - 2):
            y = int(height[x, z])
            if z % 8 == 0:
                pier(x, z)
            else:
                g.fill(x, x, y + 1, y + 3, z, z, BARS)
    gz = D - 2
    for gx in (57, 71):                                         # the gate piers
        y = int(height[gx, gz])
        g.fill(gx - 1, gx + 1, y + 1, y + 9, gz - 1, gz + 1, DEEP_BRICKS)
        g.fill(gx, gx, y + 10, y + 11, gz, gz, wall("deepslate_brick"))
        g.set(gx, y + 12, gz, SOUL)
    for x in range(58, 71):
        r = 6 - abs(x - 64)
        y = int(height[x, gz]) + 8 + max(0, 4 - abs(x - 64) // 2) - 1
        g.fill(x, x, int(height[x, gz]) + 8, y, gz, gz, BARS)
        del r
    g.fill(58, 70, int(height[64, gz]) + 9, int(height[64, gz]) + 9, gz, gz, DEEP_BRICKS)


def mark(g: Grid) -> None:
    g.set(*SPAWN, MARKER_SPAWN)
    for e in ENTRANCES:
        g.set(*e, MARKER_ENTRANCE)


# ---- assembling ---------------------------------------------------------------------------------------------------------------------

def build(seed: int = 31) -> tuple[Grid, np.ndarray]:
    rng = random.Random(seed)
    g = Grid()
    height = build_ground(g)
    inlay(g, height)
    scatter_plants(g, height)
    # buildings
    church(g)
    porch(g)
    tower(g)
    wide_stair(g)
    crypt_tower(g, 12, 52, "east")
    crypt_tower(g, 108, 52, "west")
    fence(g, height)
    # flame pillars: the plaza, the avenue, both stairs; lamp posts along the avenue
    for (x, z) in ((40, 106), (86, 106), (45, 56), (82, 56), (35, 86), (92, 86)):
        y = int(height[x, z])
        flame_pillar(g, x, y + 1, z)
    for (x, z) in ((40, LIFT + 40), (52, LIFT + 40), (71, LIFT + 40), (88, LIFT + 40)):
        flame_pillar(g, x, int(height[x, z]) + 1, z)
    for z in range(120, 108, -6):
        for x in (58, 70):
            lamp_post(g, x, S0 + 1, z)
    for k in range(12):                                          # lamp posts round the rim of the plaza
        a = k * math.pi / 6
        x, z = int(round(CX + (PLAZA_RX + 1) * math.cos(a))), int(round(CZ + (PLAZA_RZ + 1) * math.sin(a)))
        if path_distance(x, z) > 1:
            lamp_post(g, x, int(height[x, z]) + 1, z)
    # graves, boulders and trees, kept off the plaza, the paths and the buildings
    def free(x: int, z: int, margin: float) -> bool:
        if in_ellipse(x, z, CX, CZ, PLAZA_RX + margin, PLAZA_RZ + margin) < 1.0:
            return False
        if path_distance(x, z) < margin:
            return False
        if TERRACE[0] - 3 <= x <= TERRACE[1] + 3 and TERRACE[2] - 3 <= z <= TERRACE[3] + 3:
            return False
        if 36 <= x <= 52 and 36 <= z <= 52 or 72 <= x <= 90 and 36 <= z <= 50:
            return False
        if 8 <= x <= 24 and 48 <= z <= 64 or 104 <= x <= 120 and 48 <= z <= 64:
            return False
        if 4 <= x <= W - 5 and 4 <= z <= D - 5:
            return True
        return False

    placed: list[tuple[int, int]] = []
    tries = 0
    while len(placed) < 78 and tries < 4000:
        tries += 1
        x, z = rng.randint(6, W - 7), rng.randint(40, D - 12)
        if not free(x, z, 2.5):
            continue
        if any(abs(x - px) < 4 and abs(z - pz) < 4 for px, pz in placed):
            continue
        placed.append((x, z))
        y = int(height[x, z]) + 1
        tombstone(g, x, y, z, rng.choice([0, 0, 0, 1, 1, 2, 3]), rng)
    for _ in range(14):                                          # lit pumpkins left in the grass
        x, z = rng.randint(6, W - 7), rng.randint(44, D - 14)
        y = int(height[x, z]) + 1
        if free(x, z, 3.0) and g.get(x, y, z) in (AIR, "minecraft:crimson_roots"):
            g.set(x, y, z, f"minecraft:jack_o_lantern[facing={rng.choice(['north', 'south', 'east', 'west'])}]")
    for _ in range(26):
        x, z = rng.randint(6, W - 7), rng.randint(10, D - 10)
        if free(x, z, 3.0):
            boulder(g, x, int(height[x, z]) + 1, z, rng.uniform(1.6, 3.2), rng)
    trees = [(16, 100, 24, 3.4, LOG), (110, 104, 26, 3.6, LOG), (30, 112, 18, 2.8, LOG), (98, 118, 19, 2.8, LOG), (8, 70, 22, 3.0, LOG),
             (120, 72, 23, 3.2, LOG), (20, 30, 20, 3.0, LOG_RED), (112, 28, 21, 3.0, LOG), (36, 60, 16, 2.4, LOG), (96, 62, 16, 2.4, LOG_RED),
             (50, 120, 15, 2.2, LOG), (80, 122, 15, 2.2, LOG), (10, 12, 20, 2.8, LOG), (120, 10, 19, 2.8, LOG)]
    for (x, z, h, th, block) in trees:
        tree(g, rng, x, int(height[x, z]) + 1, z, h, th, block)
    mark(g)
    return g, height


# ---- checks -------------------------------------------------------------------------------------------------------------------------

SOLID_NOT = {AIR, "minecraft:crimson_roots", "minecraft:warped_roots", "minecraft:dead_bush", "minecraft:fern", COBWEB, MARKER_SPAWN, MARKER_ENTRANCE}


def passable(g: Grid, x: int, y: int, z: int) -> bool:
    if not (0 <= x < W and 0 <= y < H and 0 <= z < D):
        return False
    name = g.get(x, y, z)
    return name in SOLID_NOT or name.startswith("minecraft:soul_lantern")


def standable(g: Grid, x: int, y: int, z: int) -> bool:
    return passable(g, x, y, z) and passable(g, x, y + 1, z) and not passable(g, x, y - 1, z)


def check(g: Grid, height: np.ndarray) -> list[str]:
    problems: list[str] = []
    if g.grid.shape != (W, H, D):
        problems.append("the envelope is not 128 x 72 x 128")
    for name, (x, y, z) in (("spawn marker", SPAWN),) + tuple((f"entrance marker {i}", e) for i, e in enumerate(ENTRANCES)):
        if not standable(g, x, y, z):
            problems.append(f"{name} at {(x, y, z)} is not a place to stand")
    # the plaza is open: 20 blocks clear round the boss, 14 high, nothing but air (and the flat floor)
    for x in range(CX - 20, CX + 21):
        for z in range(CZ - 20, CZ + 21):
            if (x - CX) ** 2 + (z - CZ) ** 2 > 400:
                continue
            for y in range(S0 + 1, S0 + 15):
                name = g.get(x, y, z)
                if name != AIR and name not in (MARKER_SPAWN, MARKER_ENTRANCE) and not name.startswith("minecraft:crimson"):
                    problems.append(f"the combat court is not clear at {(x, y, z)}: {name}")
                    return problems
    # on foot from every entrance to the boss and to both stairs' heads
    def reach(start):
        seen = {start}
        q = deque([start])
        while q:
            x, y, z = q.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                for ny in (y, y + 1, y - 1, y - 2):
                    if (nx, ny, nz) not in seen and standable(g, nx, ny, nz):
                        if ny == y + 1 and not passable(g, x, y + 2, z):
                            continue
                        if ny < y and not passable(g, nx, y + 1, nz):
                            continue
                        seen.add((nx, ny, nz))
                        q.append((nx, ny, nz))
                        break
        return seen
    seen = reach(ENTRANCES[0])
    for e in ENTRANCES[1:] + [SPAWN]:
        if e not in seen:
            problems.append(f"{e} is not reachable on foot from the first entrance")
    for goal, label in (((48, T, 39), "the porch stair head"), ((80, T + 1, 36), "the terrace by the wide stair")):
        if not any(c[0] == goal[0] and c[2] == goal[2] and abs(c[1] - goal[1]) <= 1 for c in seen):
            problems.append(f"{label} cannot be reached on foot from the entrance")
    return problems


def palette_check(g: Grid, blocks_dir: Path | None) -> list[str]:
    if blocks_dir is None:
        return []
    real = {p.stem for p in blocks_dir.glob("*.json")} | {"air", "iron_bars", "cobweb"}
    bad = []
    for name in g.palette:
        if name.startswith("cobbleraids:"):
            continue
        if name.split("[")[0].removeprefix("minecraft:") not in real:
            bad.append(name)
    return bad


def write_schem(g: Grid, path: Path) -> int:
    import mcschematic
    schem = mcschematic.MCSchematic()
    xs, ys, zs = np.nonzero(g.grid)
    for x, y, z in zip(xs.tolist(), ys.tolist(), zs.tolist()):
        schem.setBlock((x, y, z), g.palette[int(g.grid[x, y, z])])
    path.parent.mkdir(parents=True, exist_ok=True)
    schem.save(str(path.parent), path.stem, mcschematic.Version.JE_1_21)
    return len(xs)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=Path("validation/schematics"))
    ap.add_argument("--npz", type=Path, help="also save the grid for tools/halloween_tower/view.py --npz")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--blocks", type=Path, help="data/minecraft/loot_table/blocks from a 1.21.1 jar, to check every block id")
    ap.add_argument("--seed", type=int, default=31)
    args = ap.parse_args()
    g, height = build(args.seed)
    if args.npz:
        np.savez_compressed(args.npz, grid=g.grid, palette=np.array(g.palette))
    problems = check(g, height) + [f"unknown block id {n}" for n in palette_check(g, args.blocks)]
    print(f"graveyard: {W}x{H}x{D}, {int(np.count_nonzero(g.grid))} blocks, {len(g.palette)} block states")
    for p in problems:
        print("FAIL", p)
    if problems:
        return 1
    print("graveyard: PASS (markers stand, the court is clear, every entrance reaches the boss and both stairs)")
    if args.check:
        return 0
    n = write_schem(g, args.out / "world_boss_graveyard.schem")
    print(f"wrote {args.out / 'world_boss_graveyard.schem'} ({n} blocks)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
