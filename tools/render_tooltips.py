#!/usr/bin/env python3
"""Draws the armor tooltips as they will read, from the REAL component output of the unit tests.

    ./gradlew test --tests 'com.cobbletowers.armor.ArmorTooltipTest'     # writes build/tooltip_preview.json
    python tools/render_tooltips.py [output.png]

A mock-up, not a screenshot: Minecraft's own font is not available here, so a system font stands in, and the tooltip
frame is imitated (the vanilla dark-purple panel with a violet border). Wording, order, colours, bold and italics are
exactly what the game is handed; only the glyph shapes differ.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "build" / "tooltip_preview.json"
FONTS = Path("C:/Windows/Fonts")
SCALE = 2
SIZE = 15 * SCALE // 2 + 3
PAD = 8 * SCALE
LINE = SIZE + 5 * SCALE // 2

NAMED = {
    "black": (0, 0, 0), "dark_blue": (0, 0, 170), "dark_green": (0, 170, 0), "dark_aqua": (0, 170, 170),
    "dark_red": (170, 0, 0), "dark_purple": (170, 0, 170), "gold": (255, 170, 0), "gray": (170, 170, 170),
    "dark_gray": (85, 85, 85), "blue": (85, 85, 255), "green": (85, 255, 85), "aqua": (85, 255, 255),
    "red": (255, 85, 85), "light_purple": (255, 85, 255), "yellow": (255, 255, 85), "white": (255, 255, 255),
}
SYMBOLS = set("\u25C8\u25C6\u25C7\u2714\u2605")


def colour(name: str) -> tuple[int, int, int]:
    if name.startswith("#"):
        return tuple(int(name[i:i + 2], 16) for i in (1, 3, 5))  # type: ignore[return-value]
    return NAMED.get(name, (255, 255, 255))


def load(name: str, size: int) -> ImageFont.FreeTypeFont:
    for candidate in name.split("|"):
        path = FONTS / candidate
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


TEXT = {
    (False, False): load("consola.ttf|cour.ttf", SIZE), (True, False): load("consolab.ttf|courbd.ttf", SIZE),
    (False, True): load("consolai.ttf|couri.ttf", SIZE), (True, True): load("consolaz.ttf|courbi.ttf", SIZE),
}
SYMBOL = load("seguisym.ttf|segoeui.ttf", SIZE)


def advance(font: ImageFont.FreeTypeFont, text: str) -> int:
    return int(font.getlength(text))


def width_of(line: list[dict]) -> int:
    total = 0
    for run in line:
        for ch in run["text"]:
            total += advance(SYMBOL if ch in SYMBOLS else TEXT[(run["bold"], run["italic"])], ch)
    return total


def panel(item_name: str, body: list[list[dict]]) -> Image.Image:
    header = [{"text": item_name, "color": "white", "bold": False, "italic": False}]
    stat = [{"text": "When on Body:", "color": "gray", "bold": False, "italic": False}]
    armor = [{"text": "+8 Armor", "color": "#5555FF", "bold": False, "italic": False}]
    lines = [header, [], stat, armor] + body
    inner_w = max(width_of(line) for line in lines)
    w = inner_w + PAD * 2 + 6 * SCALE
    h = len(lines) * LINE + PAD * 2
    image = Image.new("RGBA", (w, h), (16, 0, 16, 235))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, w - 1, h - 1), outline=(80, 0, 255, 255), width=SCALE)
    draw.rectangle((SCALE, SCALE, w - 1 - SCALE, h - 1 - SCALE), outline=(40, 0, 127, 255), width=SCALE)
    y = PAD
    for line in lines:
        x = PAD + 3 * SCALE
        for run in line:
            rgb = colour(run["color"])
            for ch in run["text"]:
                font = SYMBOL if ch in SYMBOLS else TEXT[(run["bold"], run["italic"])]
                # The classic one-pixel drop shadow.
                draw.text((x + SCALE, y + SCALE), ch, font=font, fill=tuple(v // 4 for v in rgb) + (255,))
                draw.text((x, y), ch, font=font, fill=rgb + (255,))
                x += advance(font, ch)
        y += LINE
    return image


def main() -> int:
    if not DATA.exists():
        print(f"{DATA} not found: run the ArmorTooltipTest first", file=sys.stderr)
        return 2
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("armor_tooltips.png")
    entries = json.loads(DATA.read_text(encoding="utf-8"))
    names = {"challenger": "Challenger's Chestplate", "tideforge": "Tideforged Chestplate",
             "rootvale": "Rootwoven Chestplate", "duskvale": "Duskbound Chestplate"}
    rows = []
    for set_id in ["challenger", "tideforge", "rootvale", "duskvale"]:
        picked = [e for e in entries if e["set"] == set_id and ((e["worn"] == 0 and not e["expanded"])
                  or (e["worn"] == 2 and e["expanded"]) or (e["worn"] == 4 and not e["expanded"]))]
        picked.sort(key=lambda e: e["worn"])
        rows.append([panel(names[set_id], e["lines"]) for e in picked])

    gap = 14 * SCALE
    col_w = [max(row[i].width for row in rows) for i in range(3)]
    row_h = [max(p.height for p in row) for row in rows]
    sheet = Image.new("RGBA", (sum(col_w) + gap * 4, sum(row_h) + gap * 5 + 12 * SCALE), (30, 31, 36, 255))
    caption = ImageDraw.Draw(sheet)
    for i, label in enumerate(["nothing worn", "two pieces worn, holding Shift", "full set worn"]):
        caption.text((gap + sum(col_w[:i]) + gap * i, 6 * SCALE), label, font=TEXT[(False, False)], fill=(150, 154, 165, 255))
    y = gap + 12 * SCALE
    for r, row in enumerate(rows):
        x = gap
        for c, image in enumerate(row):
            sheet.alpha_composite(image, (x, y))
            x += col_w[c] + gap
        y += row_h[r] + gap
    sheet.convert("RGB").save(out)
    print(f"wrote {out} ({sheet.width}x{sheet.height})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
