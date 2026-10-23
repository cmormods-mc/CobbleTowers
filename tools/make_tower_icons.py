#!/usr/bin/env python3
"""Draws the placeholder tower icons of the mastery badge (P47c): one 8x8 gold-ringed medallion per tower in one bitmap-font strip.

    python tools/make_tower_icons.py

Writes src/main/resources/assets/cobbletowers/textures/font/tower_icons.png. The order is the order of the characters in
font/tower_icons.json (U+E000 neutral, U+E001 tideforge, U+E002 rootvale, U+E003 duskvale, U+E004 any other tower).
"""

from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers/textures/font/tower_icons.png"

PALETTE = {
    "K": (36, 26, 32, 255),      # outline
    "S": (206, 208, 218, 255),   # stone light
    "d": (92, 94, 120, 255),     # stone shade (neutral field)
    "n": (30, 84, 172, 255),     # water field
    "w": (176, 218, 255, 255),   # water highlight
    "W": (255, 255, 255, 255),   # glint
    "G": (44, 126, 62, 255),     # leaf field
    "h": (170, 236, 150, 255),   # leaf highlight
    "P": (82, 44, 130, 255),     # dusk field
    "q": (240, 220, 255, 255),   # moon
    "r": (150, 36, 50, 255),     # ruby field
    "y": (248, 204, 66, 255),    # gold
    "Y": (200, 140, 30, 255),    # gold shade
    "Z": (255, 238, 160, 255),   # gold highlight
    ".": (0, 0, 0, 0),
}

# the Byzantine medallion: a gold ring round a jewel-toned field; "B" is the field colour, the 4x4 emblem sits in the middle
RING = ["..KKKK..",
        ".KyZZyK.",
        "KyBBBByK",
        "KZBBBBYK",
        "KZBBBBYK",
        "KyBBBBYK",
        ".KyYYYK.",
        "..KKKK.."]

# (field, emblem): "." in an emblem shows the field
MEDALLIONS = [
    ("d", ["S..S", ".SS.", ".SS.", "SSSS"]),   # neutral: a stone tower
    ("n", ["..w.", ".ww.", "wWww", ".ww."]),   # tideforge: a drop of water
    ("G", ["..hh", ".hh.", "hh..", "h..."]),   # rootvale: a leaf
    ("P", [".qq.", "q...", "q...", ".qq."]),   # duskvale: a crescent moon
    ("r", ["..W.", "WWWW", ".WW.", ".W.W"]),   # any other tower: a white star
]


def medallion(field, emblem):
    rows = [list(row.replace("B", field)) for row in RING]
    for dy, line in enumerate(emblem):
        for dx, mark in enumerate(line):
            rows[2 + dy][2 + dx] = field if mark == "." else mark
    return ["".join(row) for row in rows]


ICONS = [medallion(field, emblem) for field, emblem in MEDALLIONS]


def main() -> None:
    image = Image.new("RGBA", (8 * len(ICONS), 8), (0, 0, 0, 0))
    for index, rows in enumerate(ICONS):
        assert len(rows) == 8 and all(len(row) == 8 for row in rows), index
        for y, row in enumerate(rows):
            for x, mark in enumerate(row):
                image.putpixel((index * 8 + x, y), PALETTE[mark])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    print(f"wrote {OUT} ({image.width}x{image.height})")


if __name__ == "__main__":
    main()
