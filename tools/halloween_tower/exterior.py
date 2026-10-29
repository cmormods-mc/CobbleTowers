"""Hollow's Eve: the outside of the tower (P55), after a gothic cathedral-castle: a rocky floating island under a stepped base with a grand stair and a
pointed portal, buttresses between glowing windows, a great rose window, round towers at the corners with conical roofs and a floating crystal, a tall central
spire with a lit finial, giant orange crystal spikes and a few dead white trees.

The building itself is generated in its own coordinates (see hollow_tower.py) and never touched here except for its outer wall skin: window glass and the
roofline. `assemble` copies it into a larger canvas at an offset (M, D, M) so there is room round it for the island and the towers, and `dress` adds the
outside. Every anchor and box of the layout moves by the same offset (`shift`), nothing else about the interior changes.

Canvas coordinates are absolute. Nothing here writes inside the building's footprint except to its outer skin and above its roof.
"""
from __future__ import annotations

import math

import numpy as np

M = 24                  # room round the building on every side (x and z)
D = 14                  # room under it for the island's underside
EXTRA = 80              # room above the roof for spires
GATE_DEPTH = 12         # the gatehouse in front of the south wall
ROOF_H = 36             # the hipped roof's height above the parapet
BX, BY, BZ = 97, 121, 97

BRICK = "minecraft:polished_blackstone_bricks"
BLACKSTONE = "minecraft:blackstone"
POLISHED = "minecraft:polished_blackstone"
TUFF = "minecraft:tuff"
COBBLED = "minecraft:cobbled_deepslate"
TILES = "minecraft:deepslate_tiles"
TRIM = "minecraft:crying_obsidian"
TERRA = "minecraft:purple_terracotta"
ORANGE_GLASS = "minecraft:orange_stained_glass"
PURPLE_GLASS = "minecraft:purple_stained_glass"
SHROOM = "minecraft:shroomlight"
BONE = "minecraft:bone_block[axis=y]"
PUMPKIN = "minecraft:pumpkin"
JACK = "minecraft:jack_o_lantern[facing=north]"
CHISELED = "minecraft:chiseled_polished_blackstone"
AIR = "minecraft:air"
STAIR_N = "minecraft:polished_blackstone_brick_stairs[facing=north,half=bottom,shape=straight]"


class Canvas:
    def __init__(self, size: tuple[int, int, int]) -> None:
        self.size = size
        self.grid = np.zeros(size, dtype=np.uint16)
        self.palette = [AIR]
        self.index = {AIR: 0}

    def block_id(self, name: str) -> int:
        found = self.index.get(name)
        if found is None:
            found = len(self.palette)
            self.palette.append(name)
            self.index[name] = found
        return found

    def at(self, x: int, y: int, z: int) -> str:
        return self.palette[int(self.grid[x, y, z])]

    def inside(self, x: int, y: int, z: int) -> bool:
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    def fill(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int, block: str) -> None:
        x0, x1 = max(0, min(x0, x1)), min(self.size[0] - 1, max(x0, x1))
        y0, y1 = max(0, min(y0, y1)), min(self.size[1] - 1, max(y0, y1))
        z0, z1 = max(0, min(z0, z1)), min(self.size[2] - 1, max(z0, z1))
        if x0 > x1 or y0 > y1 or z0 > z1:
            return
        self.grid[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = self.block_id(block)

    def set(self, x: int, y: int, z: int, block: str) -> None:
        if self.inside(x, y, z):
            self.grid[x, y, z] = self.block_id(block)


def noise(x: int, z: int, salt: int = 0) -> int:
    return ((x * 73856093) ^ (z * 19349663) ^ (salt * 83492791)) % 1000


def in_building(x: int, z: int) -> bool:
    return M <= x <= M + BX - 1 and M <= z <= M + BZ - 1


def assemble(building) -> Canvas:
    """A canvas with the building copied in at (M, D, M)."""
    canvas = Canvas((BX + 2 * M, D + BY + EXTRA, BZ + 2 * M))
    remap = np.zeros(len(building.palette), dtype=np.uint16)
    for i, name in enumerate(building.palette):
        remap[i] = canvas.block_id(name)
    canvas.grid[M:M + BX, D:D + BY, M:M + BZ] = remap[building.grid]
    return canvas


def crop(canvas: Canvas) -> tuple[Canvas, tuple[int, int, int]]:
    """The canvas trimmed to the blocks it holds, and where the building now sits in it. The structure converter anchors on the lowest block, so the saved
    schematic must start exactly there for the layout anchors to stay true."""
    xs, ys, zs = np.nonzero(canvas.grid)
    lo = (int(xs.min()), int(ys.min()), int(zs.min()))
    hi = (int(xs.max()), int(ys.max()), int(zs.max()))
    cropped = Canvas((hi[0] - lo[0] + 1, hi[1] - lo[1] + 1, hi[2] - lo[2] + 1))
    cropped.palette, cropped.index = canvas.palette, canvas.index
    cropped.grid = canvas.grid[lo[0]:hi[0] + 1, lo[1]:hi[1] + 1, lo[2]:hi[2] + 1].copy()
    return cropped, (M - lo[0], D - lo[1], M - lo[2])


def building_view(canvas: Canvas):
    """The building's own box of the canvas, with the building's interface, so the building's validator can run on the finished canvas."""
    class View:
        pass
    view = View()
    view.grid = canvas.grid[M:M + BX, D:D + BY, M:M + BZ]
    view.palette = canvas.palette
    view.at = lambda x, y, z: canvas.palette[int(view.grid[x, y, z])]
    view.block_id = canvas.block_id
    return view


def shift(layout: dict, offset: tuple[int, int, int] = (M, D, M)) -> dict:
    """The layout with every coordinate moved by where the building sits in the (cropped) canvas."""
    ox, oy, oz = offset
    def walk(value):
        if isinstance(value, dict):
            if set(("x", "y", "z")) <= set(value):
                out = dict(value)
                out["x"], out["y"], out["z"] = value["x"] + ox, value["y"] + oy, value["z"] + oz
                return out
            return {k: ([v[0] + ox, v[1] + oy, v[2] + oz] if k in ("min", "max") else walk(v)) for k, v in value.items()}
        if isinstance(value, list):
            return [walk(v) for v in value]
        return value
    return walk(layout)


# ---- the island ------------------------------------------------------------------------------------------------------------------

def island(c: Canvas) -> None:
    cx = cz = M + 48
    radius = 60.0
    for x in range(c.size[0]):
        for z in range(c.size[2]):
            dx, dz = x - cx, z - cz
            ang = math.atan2(dz, dx)
            edge = radius * (1.0 + 0.10 * math.sin(ang * 5 + 1.3) + 0.06 * math.sin(ang * 11) + (noise(x // 4, z // 4, 3) % 100) / 1500.0)
            r = math.hypot(dx, dz)
            if r >= edge:
                continue
            t = r / edge
            depth = int(2 + 11.0 * (1.0 - t) ** 0.75 + (noise(x, z, 5) % 3))
            for y in range(D - depth, D + 1):
                if y >= D and in_building(x, z):
                    continue
                n = noise(x, z, y)
                if y == D:
                    block = BLACKSTONE if n < 500 else POLISHED if n < 650 else TUFF if n < 800 else COBBLED if n < 940 else TERRA
                else:
                    block = TUFF if n < 300 else COBBLED if n < 650 else BLACKSTONE if n < 900 else TRIM
                c.set(x, y, z, block)


def path_and_stair(c: Canvas) -> None:
    cx = M + 48
    south = M + BZ + GATE_DEPTH         # the first block outside the gatehouse
    # the processional path: seven wide, brick, edged with crying obsidian, from the foot of the stair out to the island's edge
    for z in range(south + 6, c.size[2] - 6):
        for x in range(cx - 3, cx + 4):
            if c.at(x, D, z) != AIR:
                c.set(x, D, z, BRICK if (x + z) % 5 else POLISHED)
        for x in (cx - 4, cx + 4):
            if c.at(x, D, z) != AIR:
                c.set(x, D, z, TRIM)
    # the grand stair: five broad steps up the plinth to the portal, with brick cheeks and a pumpkin on every post
    for i in range(1, 6):
        z = south + (5 - i)
        for x in range(cx - 9, cx + 10):
            c.fill(x, x, D + 1, D + i - 1, z, z, BRICK)
            c.set(x, D + i, z, STAIR_N)
    for x in (cx - 10, cx + 10):
        c.fill(x, x, D + 1, D + 6, south, south + 5, BRICK)
        c.set(x, D + 7, south, JACK)
        c.set(x, D + 7, south + 5, JACK)
    c.fill(cx - 12, cx - 11, D + 1, D + 4, south, south + 5, BRICK)
    c.fill(cx + 11, cx + 12, D + 1, D + 4, south, south + 5, BRICK)


# ---- the walls --------------------------------------------------------------------------------------------------------------------

def skin(c: Canvas) -> None:
    """Window glass glows (orange glass with shroomlight behind it, a pointed head above), the portal and the rose window, and pilasters between."""
    cx = M + 48
    for floor in range(1, 11):
        f = D + 6 + 10 * (floor - 1)
        for x in range(8, 90, 12):                         # north and south faces
            for face, outer, inner in (("n", M + 0, M + 1), ("s", M + BZ - 1, M + BZ - 2)):
                for xx in range(M + x, M + x + 3):
                    c.fill(xx, xx, f + 2, f + 5, outer, outer, ORANGE_GLASS)
                    c.fill(xx, xx, f + 2, f + 5, inner, inner, SHROOM)
                c.fill(M + x + 1, M + x + 1, f + 6, f + 7, outer, outer, ORANGE_GLASS)
                c.fill(M + x + 1, M + x + 1, f + 6, f + 7, inner, inner, SHROOM)
        for z in range(8, 70, 8):                          # west and east faces
            for outer, inner in ((M + 0, M + 1), (M + BX - 1, M + BX - 2)):
                for zz in range(M + z, M + z + 3):
                    c.fill(outer, outer, f + 2, f + 5, zz, zz, ORANGE_GLASS)
                    c.fill(inner, inner, f + 2, f + 5, zz, zz, SHROOM)
                c.fill(outer, outer, f + 6, f + 7, M + z + 1, M + z + 1, ORANGE_GLASS)
                c.fill(inner, inner, f + 6, f + 7, M + z + 1, M + z + 1, SHROOM)
    # pilasters between the windows: three wide, two out, a trim band every ten blocks and a pointed pinnacle above the roof
    roof = D + 106
    for x in (14, 26, 38, 50, 62, 74, 86):
        for face in ("n", "s", "w", "e"):
            if face == "s" and abs(x - 48) <= 8:
                continue
            for k in (-1, 0, 1):
                for depth in (1, 2):
                    if face == "n":
                        px, pz = M + x + k, M - depth
                    elif face == "s":
                        px, pz = M + x + k, M + BZ - 1 + depth
                    elif face == "w":
                        px, pz = M - depth, M + x + k
                    else:
                        px, pz = M + BX - 1 + depth, M + x + k
                    if face in ("w", "e") and not (M + 6 <= pz <= M + BZ - 7):
                        continue
                    c.fill(px, px, D + 1, roof, pz, pz, BRICK)
                    for y in range(D + 10, roof, 10):
                        c.set(px, y, pz, TRIM)
            # pinnacle
            if face == "n":
                bx, bz = M + x, M - 1
            elif face == "s":
                bx, bz = M + x, M + BZ
            elif face == "w":
                bx, bz = M - 1, M + x
            else:
                bx, bz = M + BX, M + x
            if face in ("w", "e") and not (M + 6 <= bz <= M + BZ - 7):
                continue
            c.fill(bx - 1, bx + 1, roof + 1, roof + 2, bz - 1, bz + 1, BRICK)
            c.fill(bx, bx, roof + 3, roof + 8, bz, bz, BRICK)
            c.set(bx, roof + 9, bz, SHROOM)


# ---- towers, spire, crystals ------------------------------------------------------------------------------------------------------

def drum(c: Canvas, cx: int, cz: int, radius: int, height: int, crystal: bool) -> None:
    """A round tower on the island, only where it is outside the building: a lit window row every nine blocks, a trim ring at each, a cone and a spire."""
    top = D + height
    for x in range(cx - radius, cx + radius + 1):
        for z in range(cz - radius, cz + radius + 1):
            if in_building(x, z):
                continue
            r = math.hypot(x - cx, z - cz)
            if r > radius + 0.4:
                continue
            c.fill(x, x, D + 1, top, z, z, BRICK)
            edge = r > radius - 1.0
            if edge:
                for y in range(D + 9, top, 9):
                    c.set(x, y, z, TRIM)
    # windows: four per row, facing out along the diagonals, three blocks tall, lit from within the solid body
    for row_y in range(D + 12, top - 6, 9):
        for k in range(8):
            ang = math.pi / 8 + k * math.pi / 4
            wx = cx + round(math.cos(ang) * radius)
            wz = cz + round(math.sin(ang) * radius)
            if in_building(wx, wz):
                continue
            c.fill(wx, wx, row_y, row_y + 3, wz, wz, ORANGE_GLASS)
            ix = cx + round(math.cos(ang) * (radius - 1))
            iz = cz + round(math.sin(ang) * (radius - 1))
            c.fill(ix, ix, row_y, row_y + 3, iz, iz, SHROOM)
    # the cone roof, stepped inwards, then the spire and its finial
    cone = 16
    for h in range(cone):
        r = radius * (1.0 - h / cone) + 0.6
        for x in range(cx - radius - 1, cx + radius + 2):
            for z in range(cz - radius - 1, cz + radius + 2):
                if in_building(x, z):
                    continue
                if math.hypot(x - cx, z - cz) <= r:
                    c.set(x, top + 1 + h, z, TILES if h % 4 else TRIM)
    c.fill(cx, cx, top + cone + 1, top + cone + 10, cz, cz, BRICK)
    c.set(cx, top + cone + 11, cz, SHROOM)
    if crystal:
        crystal_at(c, cx, top + cone + 22, cz, 7)


def crystal_at(c: Canvas, cx: int, cy: int, cz: int, r: int) -> None:
    """A floating octahedron of lit glass with a shroomlight heart."""
    for dx in range(-r, r + 1):
        for dy in range(-r - 3, r + 4):
            for dz in range(-r, r + 1):
                d = abs(dx) + abs(dy) * 0.7 + abs(dz)
                if d <= r:
                    c.set(cx + dx, cy + dy, cz + dz, SHROOM if d <= r * 0.45 else ORANGE_GLASS)


def roof(c: Canvas) -> int:
    """A steep hipped roof of deepslate tiles over the whole top, with lit gable windows and a flat top under the spire. Returns the y of its apex."""
    base = D + 106
    hollow = 3
    for h in range(ROOF_H):
        inset = int(h * 1.25)
        x0, x1 = M - 1 + inset, M + BX + inset * 0 - inset
        z0, z1 = M - 1 + inset, M + BZ - inset
        if x1 - x0 < 8:
            break
        y = base + h
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x - x0 < hollow or x1 - x < hollow or z - z0 < hollow or z1 - z < hollow:
                    c.set(x, y, z, TILES if (x + z + h) % 7 else TRIM if h % 9 == 0 else TILES)
        # lit dormer windows on the slopes, three wide, at two heights
        if h in (8, 18):
            for k in range(10, 90, 14):
                for (wx, wz, sx, sz) in ((M + k, z0, 0, 1), (M + k, z1, 0, -1)):
                    for dx in range(3):
                        c.fill(wx + dx, wx + dx, y, y + 2, wz, wz, ORANGE_GLASS)
                        c.fill(wx + dx, wx + dx, y, y + 2, wz + sz, wz + sz, SHROOM)
    apex = base + min(ROOF_H, h)
    return apex


def gatehouse(c: Canvas) -> None:
    """A great front in the south: a tall block with a pointed gable, a rose window, lancets, a tunnel to the portal and pinnacles."""
    cx = M + 48
    z0, z1 = M + BZ, M + BZ + GATE_DEPTH - 1
    top = D + 118
    for x in range(cx - 16, cx + 17):
        for z in range(z0, z1 + 1):
            c.fill(x, x, D + 1, top, z, z, BRICK)
    # pointed gable above it, stepped
    for h in range(17):
        half = 16 - h
        for x in range(cx - half, cx + half + 1):
            for z in range(z0, z1 + 1):
                c.set(x, top + 1 + h, z, TILES if h % 5 else TRIM)
    c.fill(cx, cx, top + 18, top + 28, (z0 + z1) // 2, (z0 + z1) // 2, BRICK)
    c.set(cx, top + 29, (z0 + z1) // 2, SHROOM)
    front = z1
    # trim bands and corner buttresses
    for y in range(D + 10, top, 10):
        c.fill(cx - 16, cx + 16, y, y, front, front, TRIM)
    for x in (cx - 17, cx + 17):
        c.fill(x, x, D + 1, top + 4, z0, front, BRICK)
        c.fill(x, x, top + 5, top + 12, (z0 + front) // 2, (z0 + front) // 2, BRICK)
        c.set(x, top + 13, (z0 + front) // 2, SHROOM)
    # the rose window on the front face
    ry = D + 84
    for dy in range(-12, 13):
        for dx in range(-12, 13):
            r = math.hypot(dx, dy)
            if r > 12:
                continue
            a6 = math.atan2(dy, dx) / (math.pi / 6)
            spoke = r > 3 and abs(a6 - round(a6)) < 0.08
            ring = r > 7.5 and abs(r - 8.0) < 0.6
            if r >= 11:
                c.set(cx + dx, ry + dy, front, TRIM)
            elif r <= 3:
                c.set(cx + dx, ry + dy, front, SHROOM)
                c.set(cx + dx, ry + dy, front - 1, SHROOM)
            else:
                c.set(cx + dx, ry + dy, front, BRICK if (spoke or ring) else ORANGE_GLASS)
                c.set(cx + dx, ry + dy, front - 1, BRICK if (spoke or ring) else SHROOM)
    # lancets: tall pointed windows in two columns either side of the rose, and a row below it
    for lx in (cx - 12, cx + 12, cx - 7, cx + 7):
        for (ly, hh) in ((D + 40, 12), (D + 58, 12)):
            for h in range(hh):
                half = 1 if h < hh - 3 else (1 if h < hh - 1 else 0)
                for x in range(lx - half, lx + half + 1):
                    c.set(x, ly + h, front, ORANGE_GLASS)
                    c.set(x, ly + h, front - 1, SHROOM)
    # the tunnel to the portal: nine wide, a pointed arch twelve high, running back to the south wall
    for z in range(z0, z1 + 1):
        for h in range(0, 15):
            half = 4 if h <= 9 else max(0, 4 - (h - 9))
            for x in range(cx - half, cx + half + 1):
                c.set(x, D + 6 + h, z, AIR)
    for x in range(cx - 4, cx + 5):
        c.fill(x, x, D + 1, D + 5, z0, z1, BRICK)
        c.set(x, D + 5, (z0 + z1) // 2, POLISHED)
    # the portal at the end of the tunnel: lit glass in the wall
    for h in range(0, 15):
        half = 4 if h <= 9 else max(0, 4 - (h - 9))
        for x in range(cx - half, cx + half + 1):
            c.set(x, D + 6 + h, M + BZ - 1, ORANGE_GLASS)
            c.set(x, D + 6 + h, M + BZ - 2, SHROOM)


def spire(c: Canvas, roof: int) -> None:
    """The central spire: an octagon-ish shaft tapering from the roof, lit slits, a lit finial."""
    cx = cz = M + 48
    c.fill(cx - 16, cx + 16, D + 106, D + 118, cz - 16, cz + 16, AIR)
    height = 62
    for h in range(height):
        r = 8.0 * (1.0 - h / height) ** 1.15 + 0.6
        y = roof + 1 + h
        for x in range(cx - 9, cx + 10):
            for z in range(cz - 9, cz + 10):
                d = max(abs(x - cx), abs(z - cz)) * 0.6 + math.hypot(x - cx, z - cz) * 0.55
                if d <= r:
                    c.set(x, y, z, TILES if h % 6 else TRIM)
        if h % 10 == 5 and r > 2.2:
            for dx, dz in ((int(r) , 0), (-int(r), 0), (0, int(r)), (0, -int(r))):
                c.fill(cx + dx, cx + dx, y, y + 3, cz + dz, cz + dz, ORANGE_GLASS)
    c.fill(cx, cx, roof + height, roof + height + 6, cz, cz, SHROOM)


def spikes(c: Canvas) -> None:
    """Giant leaning crystals of lit glass round the island."""
    cx = cz = M + 48
    for k, (ang, dist, height, lean) in enumerate(((20, 52, 28, 0.35), (70, 51, 22, -0.3), (118, 53, 30, 0.3), (160, 50, 20, 0.35), (205, 52, 26, -0.35),
                                                    (250, 51, 24, 0.3), (295, 53, 29, -0.3), (340, 50, 21, 0.35), (45, 55, 16, 0.4), (225, 55, 17, -0.4))):
        a = math.radians(ang)
        bx = cx + math.cos(a) * dist
        bz = cz + math.sin(a) * dist
        base_r = 3.4 if height > 24 else 2.8
        for h in range(height):
            r = base_r * (1.0 - h / height) ** 0.9 + 0.4
            px = bx + math.cos(a) * h * lean
            pz = bz + math.sin(a) * h * lean
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    d = math.hypot(dx, dz)
                    x, z = round(px) + dx, round(pz) + dz
                    if d <= r and not in_building(x, z):
                        c.set(x, D + 1 + h, z, SHROOM if d <= r * 0.4 else ORANGE_GLASS)


def floating(c: Canvas) -> None:
    """Small rocks hanging in the air with a crystal each."""
    cx = cz = M + 48
    for ang, dist, y, size in ((35, 70, D + 60, 6), (135, 72, D + 80, 5), (215, 69, D + 55, 6), (320, 72, D + 90, 5)):
        a = math.radians(ang)
        bx, bz = round(cx + math.cos(a) * dist), round(cz + math.sin(a) * dist)
        for dy in range(-size, 2):
            r = size * (1.0 + dy / (size + 1.0))
            for dx in range(-size, size + 1):
                for dz in range(-size, size + 1):
                    if math.hypot(dx, dz) <= r:
                        n = noise(bx + dx, bz + dz, dy)
                        c.set(bx + dx, y + dy, bz + dz, TUFF if n < 400 else COBBLED if n < 800 else BLACKSTONE)
        for h in range(7):
            c.set(bx, y + 2 + h, bz, ORANGE_GLASS if h % 3 else SHROOM)
            if h < 4:
                c.set(bx + 1, y + 2 + h, bz, ORANGE_GLASS)
                c.set(bx, y + 2 + h, bz + 1, ORANGE_GLASS)


def trees_and_pumpkins(c: Canvas) -> None:
    """A few dead white trees and pumpkin patches on the ground round the stair."""
    cx = cz = M + 48
    for k, (ang, dist) in enumerate(((10, 54), (95, 53), (140, 56), (185, 54), (255, 55), (330, 54))):
        a = math.radians(ang)
        tx, tz = round(cx + math.cos(a) * dist), round(cz + math.sin(a) * dist)
        if in_building(tx, tz) or c.at(tx, D, tz) == AIR:
            continue
        height = 8 + (noise(tx, tz, 9) % 4)
        c.fill(tx, tx, D + 1, D + height, tz, tz, BONE)
        for b in range(3):
            direction = ((1, 0), (-1, 0), (0, 1), (0, -1))[(noise(tx, tz, b) % 4)]
            y0 = D + 4 + b * 2
            for step in range(1, 4 + b):
                c.set(tx + direction[0] * step, y0 + step // 2, tz + direction[1] * step, BONE)
    south = M + BZ
    cxs = M + 48
    for i in range(26):
        x = cxs + (noise(i, 1, 4) % 41) - 20
        z = south + 8 + (noise(i, 2, 4) % 14)
        if abs(x - cxs) <= 5 or c.at(x, D, z) == AIR or c.at(x, D + 1, z) != AIR:
            continue
        c.set(x, D + 1, z, PUMPKIN if noise(i, 3, 4) % 3 else JACK)


def dress(c: Canvas) -> None:
    island(c)
    path_and_stair(c)
    apex = roof(c)
    skin(c)
    gatehouse(c)
    for sx in (M, M + BX - 1):
        for sz in (M, M + BZ - 1):
            crystal = (sx, sz) == (M + BX - 1, M)
            drum(c, sx, sz, 9, 128, crystal)
    spire(c, apex)
    spikes(c)
    floating(c)
    trees_and_pumpkins(c)
