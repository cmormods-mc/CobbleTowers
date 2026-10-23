#!/usr/bin/env python3
"""Generates the tower key's and rental key's placeholder icons (16x16) under src/main/resources. An artist's replacement
only has to overwrite assets/cobbletowers/textures/item/tower_key.png or rental_key.png.

    python tools/generate_tower_key.py
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers/textures/item"

# G main, L light, D dark, e edge
PALETTES = {
    "tower_key": {"G": (232, 182, 54, 255), "L": (252, 224, 120, 255), "D": (168, 122, 28, 255), "e": (84, 58, 14, 255)},
    "rental_key": {"G": (84, 176, 200, 255), "L": (170, 232, 244, 255), "D": (44, 112, 140, 255), "e": (16, 48, 66, 255)},
}

# A diagonal key: ring at the top left, shaft to the bottom right.
ROWS = [
    "................",
    "...eeee.........",
    "..eLLGGe........",
    ".eLGeeGGe.......",
    ".eGe..eGe.......",
    ".eGe..eGe.......",
    ".eGGeeGGe.......",
    "..eGGGGDe.......",
    "...eeGDe........",
    ".....eGDe.......",
    "......eGDe......",
    ".......eGDee....",
    "........eGDGe...",
    ".........eGDe...",
    "..........eee...",
    "................",
]
OUT.mkdir(parents=True, exist_ok=True)
for name, colours in PALETTES.items():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ROWS):
        for x, cell in enumerate(row):
            if cell in colours:
                image.putpixel((x, y), colours[cell])
    image.save(OUT / (name + ".png"))
    print("wrote", OUT / (name + ".png"))
