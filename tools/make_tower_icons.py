#!/usr/bin/env python3
"""Draws the placeholder tower icons of the mastery badge (P47c): one 8x8 picture per tower in one bitmap-font strip.

    python tools/make_tower_icons.py

Writes src/main/resources/assets/cobbletowers/textures/font/tower_icons.png. The order is the order of the characters in
font/tower_icons.json (U+E000 neutral, U+E001 tideforge, U+E002 rootvale, U+E003 duskvale, U+E004 any other tower).
"""

from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers/textures/font/tower_icons.png"

PALETTE = {
    "#": (196, 198, 208, 255),   # stone
    "o": (96, 98, 120, 255),     # stone shade
    "b": (64, 140, 235, 255),    # water
    "w": (200, 232, 255, 255),   # water highlight
    "g": (86, 190, 96, 255),     # leaf
    "G": (40, 120, 60, 255),     # leaf vein
    "p": (150, 96, 220, 255),    # dusk
    "q": (236, 214, 255, 255),   # star
    "y": (246, 200, 64, 255),    # gold
    "Y": (200, 140, 30, 255),    # gold shade
    ".": (0, 0, 0, 0),
}

ICONS = [
    # neutral: a stone tower
    ["#.#..#.#",
     "########",
     ".######.",
     ".##oo##.",
     ".######.",
     ".#o##o#.",
     ".######.",
     "########"],
    # tideforge: a drop of water
    ["...bb...",
     "..bbbb..",
     ".bbwbbb.",
     ".bwbbbb.",
     "bbbbbbbb",
     "bbbbbbbb",
     ".bbbbbb.",
     "..bbbb.."],
    # rootvale: a leaf
    ["......gg",
     "....gggg",
     "..gggGgg",
     ".gggGggg",
     ".gggGgg.",
     "ggGggg..",
     "gG.gg...",
     "G......."],
    # duskvale: a crescent moon and a star
    ["..pppp.q",
     ".ppp....",
     "ppp.....",
     "ppp.....",
     "ppp.....",
     "ppp.....",
     ".ppp....",
     "..pppp.."],
    # any other tower: a gold star
    ["...yy...",
     "...yy...",
     "yyyyyyyy",
     ".yyyyyy.",
     "..yyyy..",
     "..yYYy..",
     ".yy..yy.",
     "yy....yy"],
]


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
