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
    "K": (36, 26, 32, 255),      # outline
    "S": (206, 208, 218, 255),   # stone light
    "s": (150, 152, 172, 255),   # stone mid
    "d": (92, 94, 120, 255),     # shade and windows
    "b": (64, 140, 235, 255),    # water
    "w": (176, 218, 255, 255),   # water highlight
    "n": (30, 84, 172, 255),     # water shade
    "g": (96, 200, 100, 255),    # leaf
    "G": (44, 126, 62, 255),     # leaf vein and shade
    "p": (156, 100, 226, 255),   # dusk
    "q": (240, 220, 255, 255),   # star
    "y": (248, 204, 66, 255),    # gold
    "Y": (200, 140, 30, 255),    # gold shade
    ".": (0, 0, 0, 0),
}

ICONS = [
    # neutral: a stone tower
    ["KK.KK.KK",
     "KSKSSKSK",
     "KSSSSSsK",
     "KSSdSSsK",
     "KSSSSSsK",
     "KSSddSsK",
     "KSSddSsK",
     "KKKKKKKK"],
    # tideforge: a drop of water
    ["...KK...",
     "..KbbK..",
     ".KbwbbK.",
     ".KbwbbK.",
     "KbbbbbbK",
     "KbbbbbnK",
     ".KbbbnK.",
     "..KKKK.."],
    # rootvale: a leaf
    [".....KKK",
     "...KKggK",
     "..KgggGK",
     ".KggGggK",
     ".KgGgggK",
     "KgGggGK.",
     "KGgGGK..",
     "KKKK...."],
    # duskvale: a crescent moon and a star
    ["..KKKK.q",
     ".KppppK.",
     "KpppKK..",
     "KppK....",
     "KppK....",
     "KpppKK..",
     ".KppppK.",
     "..KKKK.."],
    # any other tower: a gold star
    ["...KK...",
     "..KyyK..",
     "KKKyyKKK",
     "KyyyyyyK",
     ".KyyyyK.",
     ".KyYYyK.",
     "KyyKKyyK",
     "KKK..KKK"],
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
