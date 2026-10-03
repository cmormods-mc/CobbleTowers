#!/usr/bin/env python3
"""Generates the placeholder art for the P24 armor sets: layer textures, item icons, item models.

    python tools/generate_armor_art.py            # writes under src/main/resources/assets/cobbletowers

The output is committed; this script is how a human regenerates it after changing a palette, and the file layout
below is exactly what a real artist's replacement needs to match:

  textures/models/armor/<set>_layer_1.png   64x32, helmet + chestplate + boots
  textures/models/armor/<set>_layer_2.png   64x32, leggings
  textures/item/<set>_<slot>.png            16x16 icons
  models/item/<set>_<slot>.json             item/generated, layer0 = the icon

Layer UVs are vanilla's humanoid armor layout: head (0,0) 32x16, legs (0,16) 16x16, body (16,16) 24x16,
arms (40,16) 16x16. Everything else on the sheet is left transparent.
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers"
SLOTS = ["helmet", "chestplate", "leggings", "boots"]

# outline, dark, main, light, accent
PALETTES = {
    "challenger": [(46, 50, 58), (104, 112, 126), (150, 160, 176), (205, 212, 224), (232, 190, 62)],
    "tideforge": [(14, 40, 74), (28, 96, 150), (46, 150, 196), (140, 218, 238), (240, 248, 255)],
    "rootvale": [(30, 44, 22), (52, 96, 40), (92, 150, 62), (170, 206, 110), (160, 112, 60)],
    "duskvale": [(26, 16, 44), (62, 40, 100), (104, 72, 156), (176, 140, 214), (240, 120, 190)],
}

# '.' transparent  O outline  D dark  M main  L light  A accent
ICONS = {
    "helmet": [
        "....OOOOOOOO....",
        "...OLLMMMMMMO...",
        "..OLMMMMMMMMMO..",
        "..OMMMMMMMMMDO..",
        "..OAAAAAAAAAAO..",
        "..OMMMMMMMMMDO..",
        "..OMMO....OMMO..",
        "..OMDO....ODMO..",
        "..OOOO....OOOO..",
    ],
    "chestplate": [
        ".OOOO......OOOO.",
        "OLLMMOOOOOOMMDDO",
        "OLMMMMMMMMMMMMDO",
        "OMMMMMMMMMMMMMDO",
        ".OOMMLLAALLMMOO.",
        "..OMMMLAALMMMDO.",
        "..OMMMMAAMMMMDO.",
        "..OMMMMMMMMMMDO.",
        "..OMMMMMMMMMMDO.",
        "..OOOOOOOOOOOOO.",
    ],
    "leggings": [
        "..OOOOOOOOOOOO..",
        "..OAAAAAAAAAAO..",
        "..OMMMMMMMMMMO..",
        "..OMMMMMMMMMDO..",
        "..OMMMMOOMMMDO..",
        "..OMMMO..OMMDO..",
        "..OMMMO..OMMDO..",
        "..OLMMO..OLMDO..",
        "..OMMMO..OMMDO..",
        "..OOOOO..OOOOO..",
    ],
    "boots": [
        "..OOOO....OOOO..",
        "..OMMO....OMMO..",
        "..OMMO....OMMO..",
        "..OLMO....OLMO..",
        ".OMMMMO..OMMMMO.",
        ".OAAAAO..OAAAAO.",
        ".OOOOOO..OOOOOO.",
    ],
}
KEY = {"O": 0, "D": 1, "M": 2, "L": 3, "A": 4}


def icon(set_id: str, slot: str) -> Image.Image:
    palette = PALETTES[set_id]
    rows = ICONS[slot]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    top = (16 - len(rows)) // 2
    for y, row in enumerate(rows):
        assert len(row) == 16, (slot, y, row)
        for x, char in enumerate(row):
            if char != ".":
                image.putpixel((x, top + y), palette[KEY[char]] + (255,))
    return image


def pattern(set_id: str, x: int, y: int) -> int:
    """Which of the main shades a pixel of the sheet takes: the set's visible identity on the worn armor."""
    if set_id == "challenger":
        return 3 if (x + y) % 6 == 0 else 2          # diagonal plating
    if set_id == "tideforge":
        return 3 if int(2 + 2 * math.sin(x / 2.0 + y / 3.0)) == 0 else 2   # waves
    if set_id == "rootvale":
        return 1 if (x % 4 == 0 and y % 4 in (0, 1)) or (x % 4 == 2 and y % 4 in (2, 3)) else 2   # leaf stitches
    return 3 if (x * 7 + y * 13) % 17 == 0 else (1 if (x + y) % 5 == 0 else 2)                   # starry weave


def face(image: Image.Image, set_id: str, x0: int, y0: int, w: int, h: int, band: bool) -> None:
    palette = PALETTES[set_id]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            shade = pattern(set_id, x, y)
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            if edge:
                colour = palette[1]
            elif band and y in (y0 + h // 2, y0 + h // 2 + 1):
                colour = palette[4]
            else:
                colour = palette[shade]
            image.putpixel((x, y), colour + (255,))


def cuboid(image: Image.Image, set_id: str, u: int, v: int, w: int, h: int, d: int, band: bool) -> None:
    """Paints the six faces of a w x h x d box whose net starts at (u, v), the way a Minecraft model lays it out."""
    face(image, set_id, u + d, v, w, d, False)            # top
    face(image, set_id, u + d + w, v, w, d, False)        # bottom
    face(image, set_id, u, v + d, d, h, band)             # right
    face(image, set_id, u + d, v + d, w, h, band)         # front
    face(image, set_id, u + d + w, v + d, d, h, band)     # left
    face(image, set_id, u + d + w + d, v + d, w, h, band)  # back


def layer(set_id: str, number: int) -> Image.Image:
    image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    if number == 1:
        cuboid(image, set_id, 0, 0, 8, 8, 8, True)       # head
        cuboid(image, set_id, 16, 16, 8, 12, 4, True)    # chest
        cuboid(image, set_id, 40, 16, 4, 12, 4, False)   # arms
        cuboid(image, set_id, 0, 16, 4, 12, 4, True)     # boots (legs region)
    else:
        cuboid(image, set_id, 16, 16, 8, 12, 4, True)    # belt and hips
        cuboid(image, set_id, 0, 16, 4, 12, 4, False)    # legs
    return image


def main() -> int:
    textures = ROOT / "textures"
    (textures / "models/armor").mkdir(parents=True, exist_ok=True)
    (textures / "item").mkdir(parents=True, exist_ok=True)
    (ROOT / "models/item").mkdir(parents=True, exist_ok=True)
    for set_id in PALETTES:
        for number in (1, 2):
            layer(set_id, number).save(textures / f"models/armor/{set_id}_layer_{number}.png")
        for slot in SLOTS:
            icon(set_id, slot).save(textures / f"item/{set_id}_{slot}.png")
            model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"cobbletowers:item/{set_id}_{slot}"}}
            (ROOT / f"models/item/{set_id}_{slot}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(PALETTES)} sets: {len(PALETTES) * 2} layer textures, {len(PALETTES) * len(SLOTS)} icons and models")
    return 0


if __name__ == "__main__":
    sys.exit(main())
