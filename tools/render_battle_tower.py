#!/usr/bin/env python3
"""Draws the four recoloured Battle Towers side by side (front elevation), for a quick look before loading a world.

    python tools/render_battle_tower.py [output.png]

Colours are a rough guess from the block name (a colour word, else a few known materials): enough to judge a theme, not
a render of the game.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "validation"))

from PIL import Image  # noqa: E402
from retheme_schem import SOURCE, THEMES, retheme  # noqa: E402
from schem_to_structure import blocks_from, read_schematic, split_state  # noqa: E402

WORDS = {"white": (240, 240, 240), "black": (30, 30, 36), "gray": (90, 92, 98), "light_gray": (160, 160, 165),
         "blue": (40, 70, 190), "light_blue": (110, 175, 240), "cyan": (30, 160, 170), "green": (60, 130, 50),
         "lime": (130, 205, 40), "red": (190, 40, 40), "orange": (235, 130, 30), "yellow": (240, 215, 60),
         "purple": (120, 50, 170), "magenta": (190, 70, 190), "pink": (240, 150, 190), "brown": (110, 70, 40)}
KNOWN = {"quartz": (232, 228, 220), "stone": (125, 125, 125), "deepslate": (70, 70, 76), "blackstone": (42, 38, 46),
         "basalt": (60, 60, 64), "prismarine": (80, 160, 150), "moss": (90, 130, 50), "leaves": (45, 110, 45),
         "grass": (95, 160, 65), "sea_lantern": (190, 240, 230), "froglight": (150, 230, 140), "shroomlight": (245, 160, 70),
         "glass": (150, 200, 235), "dirt": (120, 85, 55), "sand": (225, 210, 150), "snow": (245, 250, 252),
         "iron": (205, 205, 210), "planks": (150, 105, 60), "log": (100, 75, 45), "wood": (100, 75, 45), "diorite": (200, 200, 200),
         "andesite": (135, 135, 140), "granite": (155, 105, 90), "tuff": (110, 110, 100), "nether": (110, 30, 35),
         "obsidian": (25, 15, 40), "clay": (160, 165, 180), "tiles": (60, 70, 72), "coral": (200, 90, 140), "wart": (180, 40, 40)}


def colour(name: str):
    n = name.removeprefix("minecraft:")
    for key in sorted(KNOWN, key=len, reverse=True):
        if key in n:
            return KNOWN[key]
    for key in sorted(WORDS, key=len, reverse=True):
        if n.startswith(key):
            return WORDS[key]
    return (170, 170, 170)


def main() -> None:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("battle_towers.png")
    schematic = read_schematic(SOURCE)
    (w, h, l), placed = blocks_from(schematic)
    px = 4
    sheet = Image.new("RGB", (len(THEMES) * (w * px + 10), h * px), (120, 170, 230))
    for index, (theme, mapping) in enumerate(THEMES.items()):
        front = {}
        cache = {}
        for (x, y, z), state in placed:
            themed = cache.get(state) or cache.setdefault(state, retheme(state, mapping))
            if themed == "minecraft:air":
                continue
            if (x, y) not in front or front[(x, y)][0] > z:
                front[(x, y)] = (z, themed)
        base = index * (w * px + 10)
        pixels = sheet.load()
        for (x, y), (_, state) in front.items():
            c = colour(split_state(state)[0])
            for dx in range(px):
                for dy in range(px):
                    pixels[base + x * px + dx, (h - 1 - y) * px + dy] = c
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
