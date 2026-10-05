#!/usr/bin/env python3
"""Generates the season armor trim assets (P36b): the placeholder trim art, the template item icon and models, the trim pattern
data, the tags that make the tower armor trimmable and the templates usable, the atlas entries, and the language entries.

    python tools/generate_season_trims.py            # writes under src/main/resources

The output is committed; this script is how a human regenerates it, and the layout below is exactly what an artist's replacement
needs to match. A season's trim is data plus two textures: the code only pre-registers one template item per season number.

  data/cobbletowers/trim_pattern/season_<n>.json            the pattern, naming its template item
  assets/cobbletowers/textures/trims/models/armor/season_<n>.png            64x32, the outer layer (helmet, chestplate, boots)
  assets/cobbletowers/textures/trims/models/armor/season_<n>_leggings.png   64x32, the inner layer (leggings)
  assets/minecraft/atlases/armor_trims.json                 lists those textures so the game bakes one sprite per material
  assets/cobbletowers/textures/item/season_trim_template.png   16x16, shared by every season's template item
  assets/cobbletowers/models/item/season_trim_template_<n>.json
  data/minecraft/tags/item/trim_templates.json, trimmable_armor.json

Trim textures are painted in the game's own eight palette greys (224, 192, 160, 128, 96, 64, 32, 0), which the game recolours to
whatever material is used; anything else would not be recoloured. Layout is the humanoid armor layout: head (0,0) 32x16, legs (0,16)
16x16, body (16,16) 24x16, arms (40,16) 16x16; everything else on the sheet stays transparent.

ART_SEASONS is the seasons that have art. Adding a season is: add its number here, add its name to NAMES, run this, commit.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
MAX_SEASONS = 12          # the template items registered in code (SeasonTrimItems.MAX_SEASONS)
ART_SEASONS = (1, 2, 3)   # seasons with a pattern and textures
NAMES = {1: "The Rising Tide", 2: "Deep Roots", 3: "The Long Dusk"}
GREYS = (224, 192, 160, 128, 96, 64, 32, 0)

ARMOR_SETS = ("challenger", "tideforge", "rootvale", "duskvale")
SLOTS = ("helmet", "chestplate", "leggings", "boots")

# The parts of the 64x32 sheet the armor model actually draws.
USED = ((0, 0, 32, 16), (16, 16, 40, 32), (40, 16, 56, 32), (0, 16, 16, 32))


def used(x: int, y: int) -> bool:
    return any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in USED)


def tide(x: int, y: int) -> int | None:
    """Season 1: rolling waves with gaps, so the base armor shows between them."""
    band = (y + round(2.0 * math.sin(x / 2.2))) % 7
    if band in (0, 1):
        return 0 if band == 0 else 2
    if band == 3:
        return 5
    return None


def roots(x: int, y: int) -> int | None:
    """Season 2: crossing root lines."""
    if (2 * x + y) % 9 == 0:
        return 1
    if (x - 2 * y) % 11 == 0:
        return 3
    if (x + y) % 17 == 0:
        return 6
    return None


def dusk(x: int, y: int) -> int | None:
    """Season 3: a field of small crescents."""
    cx, cy = x % 6, y % 6
    if (cx, cy) in ((1, 1), (2, 0), (3, 0), (4, 1), (1, 2), (4, 2), (2, 3), (3, 3)):
        return 0 if cy < 2 else 4
    return None


PATTERNS = {1: tide, 2: roots, 3: dusk}


def trim_texture(season: int, leggings: bool) -> Image.Image:
    paint = PATTERNS.get(season, tide)
    image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(64):
            if not used(x, y):
                continue
            # The leggings layer reuses the same sheet; shift the pattern a little so it does not read as a tiled copy.
            index = paint(x + (3 if leggings else 0), y + (1 if leggings else 0))
            if index is not None:
                grey = GREYS[index]
                image.putpixel((x, y), (grey, grey, grey, 255))
    return image


def template_icon() -> Image.Image:
    """A 16x16 smithing-template card: a pale panel, a darker border and a small emblem."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    border, panel, emblem, shade = (62, 70, 96, 255), (176, 188, 206, 255), (36, 44, 72, 255), (132, 146, 172, 255)
    for y in range(1, 15):
        for x in range(2, 14):
            image.putpixel((x, y), border if x in (2, 13) or y in (1, 14) else panel)
    for y in range(3, 13):
        for x in range(4, 12):
            image.putpixel((x, y), shade)
    for (x, y) in ((7, 4), (8, 4), (6, 5), (9, 5), (5, 6), (10, 6), (5, 7), (10, 7), (6, 8), (9, 8), (7, 9), (8, 9),
                   (7, 6), (8, 6), (7, 7), (8, 7)):
        image.putpixel((x, y), emblem)
    for x in range(5, 11):
        image.putpixel((x, 11), emblem)
    return image


def write_json(path: Path, data: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main() -> None:
    assets = ROOT / "assets" / "cobbletowers"
    data = ROOT / "data" / "cobbletowers"

    # Trim art and the atlas that bakes it.
    armor_dir = assets / "textures" / "trims" / "models" / "armor"
    armor_dir.mkdir(parents=True, exist_ok=True)
    textures = []
    for season in ART_SEASONS:
        trim_texture(season, False).save(armor_dir / f"season_{season}.png")
        trim_texture(season, True).save(armor_dir / f"season_{season}_leggings.png")
        textures += [f"cobbletowers:trims/models/armor/season_{season}", f"cobbletowers:trims/models/armor/season_{season}_leggings"]
    palette = {name: f"minecraft:trims/color_palettes/{name}" for name in (
        "quartz", "iron", "gold", "diamond", "netherite", "redstone", "copper", "emerald", "lapis", "amethyst",
        "iron_darker", "gold_darker", "diamond_darker", "netherite_darker")}
    write_json(ROOT / "assets" / "minecraft" / "atlases" / "armor_trims.json", {"sources": [{
        "type": "paletted_permutations", "textures": textures,
        "palette_key": "minecraft:trims/color_palettes/trim_palette", "permutations": palette}]})

    # The template item: one icon, one model per season number.
    (assets / "textures" / "item").mkdir(parents=True, exist_ok=True)
    template_icon().save(assets / "textures" / "item" / "season_trim_template.png")
    for season in range(1, MAX_SEASONS + 1):
        write_json(assets / "models" / "item" / f"season_trim_template_{season}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": "cobbletowers:item/season_trim_template"}})

    # The patterns.
    for season in ART_SEASONS:
        write_json(data / "trim_pattern" / f"season_{season}.json", {
            "asset_id": f"cobbletowers:season_{season}", "decal": False,
            "description": {"translate": f"trim_pattern.cobbletowers.season_{season}"},
            "template_item": f"cobbletowers:season_trim_template_{season}"})

    # The tags: our templates work at a smithing table, and our armor can be trimmed.
    write_json(ROOT / "data" / "minecraft" / "tags" / "item" / "trim_templates.json",
               {"values": [f"cobbletowers:season_trim_template_{n}" for n in range(1, MAX_SEASONS + 1)]})
    write_json(ROOT / "data" / "minecraft" / "tags" / "item" / "trimmable_armor.json",
               {"values": [f"cobbletowers:{armor}_{slot}" for armor in ARMOR_SETS for slot in SLOTS]})

    # Language entries, merged into the existing file without disturbing its order.
    lang_path = assets / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    for season in range(1, MAX_SEASONS + 1):
        name = NAMES.get(season, f"Season {season}")
        lang[f"item.cobbletowers.season_trim_template_{season}"] = f"Season {season} Trim Template"
        lang[f"trim_pattern.cobbletowers.season_{season}"] = f"{name} Armor Trim"
    write_json(lang_path, lang)
    print(f"wrote trims for seasons {list(ART_SEASONS)}, templates 1-{MAX_SEASONS}")


if __name__ == "__main__":
    main()
