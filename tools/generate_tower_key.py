#!/usr/bin/env python3
"""Generates the tower key's placeholder icon (16x16) under src/main/resources. An artist's replacement only has to
overwrite assets/cobbletowers/textures/item/tower_key.png.

    python tools/generate_tower_key.py
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers/textures/item/tower_key.png"

GOLD, LIGHT, DARK, EDGE = (232, 182, 54, 255), (252, 224, 120, 255), (168, 122, 28, 255), (84, 58, 14, 255)

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
COLOURS = {"G": GOLD, "L": LIGHT, "D": DARK, "e": EDGE}

image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for y, row in enumerate(ROWS):
    for x, cell in enumerate(row):
        if cell in COLOURS:
            image.putpixel((x, y), COLOURS[cell])
OUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUT)
print("wrote", OUT)
