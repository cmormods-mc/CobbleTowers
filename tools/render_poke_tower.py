#!/usr/bin/env python3
"""Draws a cross-section of the Test Tower building with each floor's anchors marked.

    python tools/render_poke_tower.py [output.png]

Reads validation/schematics/poke_tower.mcfunction (the artist's file) and the floor definitions, and draws the x/y plane
through the middle of the building (z = 15): which chamber each floor uses, where the party arrives and where the
opponent stands. A check on the anchors that a person can read at a glance.
"""
import json
import re
import sys
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
COLORS = {"minecraft:smooth_quartz": (232, 228, 220), "minecraft:tinted_glass": (86, 70, 100), "minecraft:blackstone": (46, 44, 52),
          "minecraft:red_concrete": (200, 40, 40), "minecraft:white_concrete": (250, 250, 250), "minecraft:sea_lantern": (170, 235, 225)}
PX = 14


def main() -> int:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("poke_tower_section.png")
    grid = {}
    for line in (ROOT / "validation/schematics/poke_tower.mcfunction").read_text(encoding="utf-8").splitlines():
        m = re.match(r"setblock ~(-?\d+) ~(-?\d+) ~(-?\d+) (\S+)", line)
        if m:
            grid[(int(m[1]), int(m[2]), int(m[3]))] = m[4]
    plane = 15
    width, height = 31, 64
    image = Image.new("RGB", (width * PX + 170, height * PX + 30), (24, 26, 32))
    draw = ImageDraw.Draw(image)
    for (x, y, z), block in grid.items():
        if z == plane:
            top = (height - 1 - y) * PX + 10
            draw.rectangle((x * PX, top, x * PX + PX - 1, top + PX - 1), fill=COLORS.get(block, (255, 0, 255)))
    floors = sorted((ROOT / "src/main/resources/data/cobbletowers/cobbletowers/floors/test").glob("floor_*.json"))
    for path in floors:
        floor = json.loads(path.read_text(encoding="utf-8"))
        anchors = floor["layout"]
        y = anchors["entry"]["y"]
        row = (height - 1 - y) * PX + 10
        draw.line((0, row + PX, width * PX, row + PX), fill=(90, 220, 120), width=1)
        draw.text((width * PX + 10, row - 2), f"floor {floor['index']}  (y={y})", fill=(90, 220, 120))
        draw.text((width * PX + 10, row + 10), "party z=%d, foe z=%d" % (anchors["entry"]["z"], anchors["presentation"]["z"]), fill=(150, 154, 165))
        px = anchors["entry"]["x"] * PX
        draw.ellipse((px + 2, row - 12, px + PX - 2, row + 2), fill=(90, 220, 120))
    image.save(out)
    print(f"wrote {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
