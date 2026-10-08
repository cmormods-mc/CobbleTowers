#!/usr/bin/env python3
"""Renders a contact sheet of the armor art without launching the game.

    python tools/preview_armor_art.py [output.png]

For each set: its four icons, then a flat front-view mock-up of a player wearing the full set, composited from the REAL
layer textures at the vanilla humanoid UV positions (head front at 8,8; body front at 20,20; arm front at 44,20; leg
front at 4,20 on a 64x32 sheet). It is a sanity check on the textures, not a substitute for seeing them on a
rendered model: there is no lighting, no 3D, and the mock-up does not show sides or back.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

ART = Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers/textures"
SETS = ["challenger", "recruit", "paragon", "tideforge", "tidewalker", "leviathan", "rootvale", "sprout", "heartwood", "duskvale", "duskwanderer", "nightfall"]
SLOTS = ["helmet", "chestplate", "leggings", "boots"]
SKIN = (222, 170, 130, 255)
SKIN_SHADE = (190, 140, 105, 255)
SCALE = 10


def crop(sheet: Image.Image, x: int, y: int, w: int, h: int) -> Image.Image:
    return sheet.crop((x, y, x + w, y + h))


def mannequin(set_id: str) -> Image.Image:
    """A 16x32 front view: head 8x8, body 8x12, arms 4x12 either side, legs 4x12 side by side."""
    layer1 = Image.open(ART / f"models/armor/{set_id}_layer_1.png").convert("RGBA")
    layer2 = Image.open(ART / f"models/armor/{set_id}_layer_2.png").convert("RGBA")
    canvas = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    # The player underneath, so a missing piece shows as skin rather than a hole.
    draw.rectangle((4, 0, 11, 7), fill=SKIN)
    draw.rectangle((4, 8, 11, 19), fill=SKIN_SHADE)
    draw.rectangle((0, 8, 3, 19), fill=SKIN)
    draw.rectangle((12, 8, 15, 19), fill=SKIN)
    draw.rectangle((4, 20, 7, 31), fill=SKIN_SHADE)
    draw.rectangle((8, 20, 11, 31), fill=SKIN_SHADE)

    canvas.alpha_composite(crop(layer1, 8, 8, 8, 8), (4, 0))        # helmet: head front
    canvas.alpha_composite(crop(layer1, 20, 20, 8, 12), (4, 8))     # chestplate: body front
    canvas.alpha_composite(crop(layer1, 44, 20, 4, 12), (0, 8))     # chestplate: one arm
    canvas.alpha_composite(crop(layer1, 44, 20, 4, 12), (12, 8))    # chestplate: the other
    canvas.alpha_composite(crop(layer2, 20, 28, 8, 4), (4, 16))     # leggings: the belt line of the body
    canvas.alpha_composite(crop(layer2, 4, 20, 4, 9), (4, 20))      # leggings: legs, down to the boots
    canvas.alpha_composite(crop(layer2, 4, 20, 4, 9), (8, 20))
    canvas.alpha_composite(crop(layer1, 4, 29, 4, 3), (4, 29))      # boots: the lowest three rows
    canvas.alpha_composite(crop(layer1, 4, 29, 4, 3), (8, 29))
    return canvas


def main() -> int:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("armor_preview.png")
    pad = 14
    icon_px = 16 * 6
    man_w, man_h = 16 * SCALE, 32 * SCALE
    col_w = max(icon_px * 4 + pad * 3, man_w) + pad * 2
    row_h = icon_px + pad + man_h + pad * 3
    sheet = Image.new("RGBA", (col_w * 3, row_h * ((len(SETS) + 2) // 3)), (52, 54, 60, 255))
    draw = ImageDraw.Draw(sheet)

    for index, set_id in enumerate(SETS):
        left = (index % 3) * col_w + pad
        top = (index // 3) * row_h + pad
        draw.text((left, top - 2), set_id, fill=(235, 235, 240, 255))
        for i, slot in enumerate(SLOTS):
            icon = Image.open(ART / f"item/{set_id}_{slot}.png").convert("RGBA").resize((icon_px, icon_px), Image.NEAREST)
            sheet.alpha_composite(icon, (left + i * (icon_px + pad), top + 14))
        figure = mannequin(set_id).resize((man_w, man_h), Image.NEAREST)
        sheet.alpha_composite(figure, (left + (col_w - pad * 2 - man_w) // 2, top + 14 + icon_px + pad))

    sheet.convert("RGB").save(out)
    print(f"wrote {out} ({sheet.width}x{sheet.height})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
