#!/usr/bin/env python3
"""A quick oblique picture of a generated tower, for judging shapes without a Minecraft client: python tools/halloween_tower/preview.py out.png [yaw_degrees]."""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

COLOURS = {
    "polished_blackstone_bricks": (58, 54, 64), "polished_blackstone": (62, 58, 68), "blackstone": (46, 42, 50), "deepslate_bricks": (70, 70, 74),
    "deepslate_tiles": (52, 52, 58), "cobbled_deepslate": (74, 74, 80), "tuff": (96, 98, 88), "crying_obsidian": (62, 24, 110), "obsidian": (22, 14, 36),
    "purple_terracotta": (120, 72, 108), "orange_stained_glass": (235, 150, 40), "purple_stained_glass": (140, 70, 190), "shroomlight": (250, 175, 80),
    "bone_block": (225, 220, 200), "pumpkin": (214, 120, 24), "jack_o_lantern": (250, 170, 50), "chiseled_polished_blackstone": (60, 56, 66),
    "amethyst_block": (150, 100, 200), "black_concrete": (12, 12, 16), "purple_concrete": (100, 32, 160), "orange_concrete": (230, 100, 10),
    "iron_bars": (120, 120, 120), "purple_wool": (110, 40, 150), "black_wool": (20, 20, 24), "white_concrete": (220, 220, 220),
}


def colour(name: str) -> tuple[int, int, int]:
    base = name.split("[")[0].removeprefix("minecraft:")
    if base in COLOURS:
        return COLOURS[base]
    if "glass" in base:
        return (190, 120, 40)
    if "stairs" in base or "slab" in base or "wall" in base:
        return (60, 56, 66)
    return (90, 90, 96)


def render(grid, palette, yaw_deg: float = 30.0, scale: int = 3) -> Image.Image:
    pal = np.array([colour(n) if n != "minecraft:air" else (0, 0, 0) for n in palette], dtype=np.float32)
    xs, ys, zs = np.nonzero(grid)
    ids = grid[xs, ys, zs]
    cx, cz = grid.shape[0] / 2, grid.shape[2] / 2
    a = math.radians(yaw_deg)
    rx = (xs - cx) * math.cos(a) - (zs - cz) * math.sin(a)
    rz = (xs - cx) * math.sin(a) + (zs - cz) * math.cos(a)
    sx = (rx - rz) * 0.866
    sy = ys - (rx + rz) * 0.5
    depth = rx + rz
    order = np.argsort(depth, kind="stable")
    sx, sy, ids, xs2, ys2, zs2 = sx[order], sy[order], ids[order], xs[order], ys[order], zs[order]
    px = ((sx - sx.min()) * scale).astype(int) + 2
    py = ((sy.max() - sy) * scale).astype(int) + 2
    w, h = px.max() + scale + 3, py.max() + scale + 3
    img = np.zeros((h, w, 3), dtype=np.uint8)
    sky = np.linspace((150, 190, 255), (200, 220, 255), h)[:, None, :]
    img[:] = sky.astype(np.uint8)
    above_empty = np.ones(len(ids), dtype=bool)
    inside = ys2 + 1 < grid.shape[1]
    above_empty[inside] = grid[xs2[inside], ys2[inside] + 1, zs2[inside]] == 0
    shade = np.where(above_empty, 1.15, 0.78)
    glow = np.isin(ids, [i for i, n in enumerate(palette) if "shroomlight" in n or "jack_o" in n or "orange_stained" in n])
    shade = np.where(glow, 1.0, shade)
    col = np.clip(pal[ids] * shade[:, None], 0, 255).astype(np.uint8)
    for dy in range(scale):
        for dx in range(scale):
            img[np.clip(py + dy, 0, h - 1), np.clip(px + dx, 0, w - 1)] = col
    return Image.fromarray(img)


if __name__ == "__main__":
    here = Path(__file__).parent
    sys.path.insert(0, str(here))
    import exterior
    import hollow_tower as ht
    t = ht.build()
    canvas = exterior.assemble(t)
    exterior.dress(canvas)
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("preview.png")
    yaw = float(sys.argv[2]) if len(sys.argv) > 2 else 30.0
    render(canvas.grid, canvas.palette, yaw).save(out)
    print("wrote", out, int((canvas.grid != 0).sum()), "blocks")
