#!/usr/bin/env python3
"""Recolours the Battle Tower schematic into one copy per tower, as vanilla structure .nbt files.

    python validation/retheme_schem.py [--minecraft-jar <client.jar>] [--preview <dir>]

Reads validation/schematics/battle_tower.schem (Sponge v3, 93 x 163 x 87) and writes
src/main/resources/data/cobbletowers/structure/battle_tower_<theme>.nbt for each theme below. The SHAPE never changes
(the floors' anchors are the same in every copy); only block types do, so a theme is a table of "this block becomes that
block". Block properties (slab half, stair facing, axis, ...) are carried across, so a mapping must stay in one family:
slab -> slab, stairs -> stairs, wall -> wall, and so on. That is checked: a mapping that changes family is an error.

Every target name is checked against a real Minecraft jar when one is given (a typo would otherwise become a missing
block in the world, found by looking at it).
"""
from __future__ import annotations

import argparse
import json
import sys
import zipfile
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from schem_to_structure import (blocks_from, histogram_of_schematic, histogram_of_structure, read_schematic,  # noqa: E402
                                split_state, write_structure)

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "validation/schematics/battle_tower.schem"
OUT_DIR = ROOT / "src/main/resources/data/cobbletowers/structure"

COLOURED_FORMS = ["concrete", "concrete_powder", "wool", "carpet", "terracotta", "glazed_terracotta", "stained_glass",
                  "stained_glass_pane"]
SUFFIXES = ["_slab", "_stairs", "_wall"]


def colours(mapping: dict[str, str], pairs: dict[str, str]) -> None:
    """Every coloured form of one colour becomes the same form of another."""
    for source, target in pairs.items():
        for form in COLOURED_FORMS:
            mapping[f"{source}_{form}"] = f"{target}_{form}"


def family(mapping: dict[str, str], source: str, target: str, forms=("", *SUFFIXES)) -> None:
    for form in forms:
        mapping[source + form] = target + form


# Neutral is the base as drawn: white, red and cyan. It is the identity, so no table.
THEMES: dict[str, dict[str, str]] = {"neutral": {}}


def _tideforge() -> dict[str, str]:
    m: dict[str, str] = {}
    # The red of the base becomes deep blue; the rainbow of the floor medallions becomes shades of water.
    colours(m, {"red": "blue", "orange": "cyan", "yellow": "light_blue", "lime": "light_blue", "green": "cyan",
                "magenta": "blue", "purple": "blue", "pink": "white", "brown": "cyan"})
    m["crimson_slab"] = "prismarine_brick_slab"
    m["brain_coral_block"] = "tube_coral_block"
    m["lilac"] = "large_fern"
    m["brown_mushroom_block"] = "prismarine_bricks"
    m["iron_block"] = "prismarine_bricks"
    m["iron_trapdoor"] = "warped_trapdoor"
    family(m, "polished_andesite", "prismarine_brick", forms=("_slab", "_stairs"))
    m["polished_andesite"] = "prismarine_bricks"
    # Walls: light stone turns to prismarine, trim to dark prismarine and navy.
    family(m, "smooth_quartz", "prismarine_brick", forms=("_slab", "_stairs"))
    m["smooth_quartz"] = "prismarine_bricks"
    family(m, "quartz", "prismarine_brick", forms=("_slab", "_stairs"))
    m["quartz_block"] = "prismarine"
    m["quartz_bricks"] = "dark_prismarine"
    m["chiseled_quartz_block"] = "prismarine_bricks"
    m["gray_concrete"] = "blue_terracotta"
    m["black_concrete"] = "blue_concrete"
    m["grass_block"] = "sand"
    m["moss_block"] = "prismarine"
    m["oak_leaves"] = "jungle_leaves"
    m["mangrove_leaves"] = "jungle_leaves"
    m["azalea_leaves"] = "jungle_leaves"
    return m


def _rootvale() -> dict[str, str]:
    m: dict[str, str] = {}
    # Water blues become leaf greens, the red becomes bark brown, and the medallion rainbow becomes greens and flowers.
    colours(m, {"blue": "green", "cyan": "green", "light_blue": "lime", "red": "brown", "orange": "brown",
                "yellow": "lime", "purple": "green", "gray": "brown", "black": "brown", "magenta": "green",
                "pink": "lime"})
    m["sea_lantern"] = "verdant_froglight"
    m["warped_wart_block"] = "moss_block"
    m["brain_coral_block"] = "moss_block"
    for form in ("planks", "trapdoor", "door", "pressure_plate"):
        m[f"warped_{form}"] = f"jungle_{form}"
    m["stripped_warped_hyphae"] = "stripped_dark_oak_wood"
    m["stripped_warped_stem"] = "stripped_dark_oak_log"
    m["warped_hyphae"] = "dark_oak_wood"
    m["warped_stem"] = "dark_oak_log"
    m["iron_block"] = "dark_oak_planks"
    m["iron_trapdoor"] = "dark_oak_trapdoor"
    m["prismarine_bricks"] = "mossy_stone_bricks"
    family(m, "dark_prismarine", "dark_oak_planks", forms=())
    m["dark_prismarine"] = "dark_oak_planks"
    m["dark_prismarine_slab"] = "dark_oak_slab"
    m["dark_prismarine_stairs"] = "dark_oak_stairs"
    m["packed_ice"] = "moss_block"
    m["snow_block"] = "moss_block"
    m["snow"] = "air"
    m["diorite"] = "mossy_stone_bricks"
    m["clay"] = "mossy_stone_bricks"
    m["stone"] = "mossy_cobblestone"
    # Walls: light stone turns to wood and mud brick, with green growing over it.
    family(m, "smooth_quartz", "mud_brick", forms=("_slab", "_stairs"))
    m["smooth_quartz"] = "mud_bricks"
    family(m, "quartz", "mud_brick", forms=("_slab", "_stairs"))
    m["quartz_block"] = "mud_bricks"
    m["quartz_bricks"] = "mud_bricks"
    m["chiseled_quartz_block"] = "chiseled_stone_bricks"
    m["quartz_pillar"] = "stripped_jungle_log"
    m["white_concrete"] = "birch_planks"
    family(m, "polished_diorite", "birch", forms=("_slab", "_stairs"))
    m["polished_diorite"] = "birch_planks"
    m["white_wool"] = "green_wool"
    return m


def _duskvale() -> dict[str, str]:
    m: dict[str, str] = {}
    # White stone turns to blackstone and deepslate; the blue and cyan windows glow like embers.
    colours(m, {"white": "black", "light_gray": "gray", "blue": "red", "cyan": "red", "light_blue": "orange",
                "green": "gray", "lime": "orange", "yellow": "orange", "magenta": "purple", "pink": "red",
                "brown": "black"})
    m["white_wool"] = "black_wool"
    family(m, "smooth_quartz", "polished_blackstone", forms=("", "_slab", "_stairs"))
    family(m, "quartz", "polished_blackstone_brick", forms=("_slab", "_stairs"))
    m["quartz_block"] = "polished_blackstone_bricks"
    m["quartz_bricks"] = "polished_blackstone_bricks"
    m["chiseled_quartz_block"] = "chiseled_polished_blackstone"
    m["quartz_pillar"] = "basalt"
    family(m, "polished_diorite", "polished_blackstone", forms=("", "_slab", "_stairs"))
    family(m, "diorite", "blackstone", forms=("", "_slab", "_stairs", "_wall"))
    m["snow_block"] = "blackstone"
    m["snow"] = "air"
    m["calcite"] = "smooth_basalt"
    m["sea_lantern"] = "shroomlight"
    m["end_rod"] = "end_rod"
    m["brain_coral_block"] = "netherrack"
    m["brown_mushroom_block"] = "red_mushroom_block"
    m["warped_wart_block"] = "nether_wart_block"
    m["warped_planks"] = "crimson_planks"
    m["warped_trapdoor"] = "crimson_trapdoor"
    m["warped_door"] = "crimson_door"
    m["warped_pressure_plate"] = "crimson_pressure_plate"
    m["stripped_warped_hyphae"] = "stripped_crimson_hyphae"
    m["stripped_warped_stem"] = "stripped_crimson_stem"
    m["warped_hyphae"] = "crimson_hyphae"
    m["warped_stem"] = "crimson_stem"
    m["iron_block"] = "polished_blackstone"
    m["iron_trapdoor"] = "dark_oak_trapdoor"
    m["prismarine_bricks"] = "nether_bricks"
    m["dark_prismarine"] = "deepslate_bricks"
    m["dark_prismarine_slab"] = "deepslate_brick_slab"
    m["dark_prismarine_stairs"] = "deepslate_brick_stairs"
    m["packed_ice"] = "obsidian"
    m["stone"] = "deepslate"
    m["stone_bricks"] = "deepslate_bricks"
    m["stone_brick_stairs"] = "deepslate_brick_stairs"
    m["smooth_stone"] = "polished_deepslate"
    family(m, "andesite", "deepslate_tile", forms=("_slab", "_stairs"))
    m["andesite"] = "deepslate_tiles"
    family(m, "polished_andesite", "polished_deepslate", forms=("_slab", "_stairs"))
    m["polished_andesite"] = "polished_deepslate"
    m["granite"] = "tuff"
    m["polished_granite"] = "polished_tuff"
    m["clay"] = "gray_concrete"
    m["grass_block"] = "coarse_dirt"
    m["dirt"] = "coarse_dirt"
    m["moss_block"] = "coarse_dirt"
    m["oak_leaves"] = "dark_oak_leaves"
    m["jungle_leaves"] = "dark_oak_leaves"
    m["mangrove_leaves"] = "dark_oak_leaves"
    m["azalea_leaves"] = "dark_oak_leaves"
    for plant in ("short_grass", "large_fern", "lilac", "beetroots"):
        m[plant] = "air"
    return m


# The only colours a themed tower may contain (neutral is the base as drawn, so it has no limit). A themed tower is
# checked against this after recolouring: the base has a rainbow of floor medallions, and a colour nobody mapped
# (pink on Rootvale's floors 7 and 8 was the first) would otherwise ship.
ALLOWED_COLOURS = {
    "tideforge": {"white", "light_gray", "gray", "black", "blue", "light_blue", "cyan"},
    "rootvale": {"white", "light_gray", "brown", "green", "lime"},
    "duskvale": {"black", "gray", "red", "orange", "purple"},
}
COLOUR_WORDS = ["white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime", "green", "cyan",
                "light_blue", "blue", "purple", "magenta", "pink"]


def stray_colours(theme: str, states) -> dict[str, int]:
    """Coloured blocks in a themed tower whose colour the theme does not allow, with how many there are."""
    allowed = ALLOWED_COLOURS.get(theme)
    found: dict[str, int] = {}
    if allowed is None:
        return found
    for state in states:
        name = split_state(state)[0].removeprefix("minecraft:")
        for colour in sorted(COLOUR_WORDS, key=len, reverse=True):
            if name.startswith(colour + "_"):
                if colour not in allowed:
                    found[name] = found.get(name, 0) + 1
                break
    return found


THEMES["tideforge"] = _tideforge()
THEMES["rootvale"] = _rootvale()
THEMES["duskvale"] = _duskvale()


def family_of(name: str) -> str:
    for suffix in ("_slab", "_stairs", "_wall", "_trapdoor", "_door", "_carpet", "_pressure_plate", "_stained_glass_pane",
                   "_leaves"):
        if name.endswith(suffix):
            return suffix
    return ""


def retheme(state: str, mapping: dict[str, str]) -> str:
    name, properties = split_state(state)
    short = name.removeprefix("minecraft:")
    target = mapping.get(short, short)
    if target == short:
        return state
    if target == "air":
        return "minecraft:air"
    new_family, old_family = family_of(target), family_of(short)
    # Coloured glass panes and carpets are their own families; other full blocks may become any other full block.
    if new_family != old_family and not (old_family == "" and new_family == ""):
        raise SystemExit(f"retheme: FAIL -- {short} -> {target} changes family ({old_family or 'block'} -> {new_family or 'block'})")
    if not properties:
        return f"minecraft:{target}"
    joined = ",".join(f"{key}={value}" for key, value in properties.items())
    return f"minecraft:{target}[{joined}]"


def real_blocks(jar: Path) -> set[str]:
    with zipfile.ZipFile(jar) as zf:
        lang = json.loads(zf.read("assets/minecraft/lang/en_us.json"))
    return {key.removeprefix("block.minecraft.") for key in lang if key.startswith("block.minecraft.")}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--minecraft-jar", type=Path, help="a Minecraft 1.21.1 client jar, to check every target name")
    parser.add_argument("--only", help="one theme instead of all")
    parser.add_argument("--check", action="store_true",
                        help="verify the committed structures against the schematic and tables instead of writing them")
    args = parser.parse_args()

    schematic = read_schematic(SOURCE)
    size, placed = blocks_from(schematic)
    known = real_blocks(args.minecraft_jar) if args.minecraft_jar else None

    failures = 0
    for theme, mapping in THEMES.items():
        if args.only and theme != args.only:
            continue
        if known is not None:
            for source, target in mapping.items():
                for name in (source, target):
                    if name != "air" and name not in known:
                        print(f"retheme: {theme}: '{name}' is not a Minecraft 1.21.1 block")
                        failures += 1
        cache: dict[str, str] = {}
        themed = []
        for position, state in placed:
            result = cache.get(state)
            if result is None:
                result = cache[state] = retheme(state, mapping)
            if result != "minecraft:air":
                themed.append((position, result))
        strays = stray_colours(theme, (state for _, state in themed))
        if strays:
            print(f"retheme: FAIL -- {theme} still has colours outside its scheme: "
                  + ", ".join(f"{n} x{c}" for n, c in sorted(strays.items(), key=lambda kv: -kv[1])[:12]))
            failures += 1
            continue
        target = OUT_DIR / f"battle_tower_{theme}.nbt"
        if args.check:
            if histogram_of_structure(target) != histogram_of_schematic(themed):
                print(f"retheme: FAIL -- {target.name} no longer matches the schematic and the {theme} table; re-run retheme_schem.py")
                failures += 1
            else:
                print(f"retheme: {theme} matches ({len(themed)} blocks)")
            continue
        write_structure(target, size, themed, schematic.get("DataVersion", 3955))
        histogram = Counter(split_state(state)[0] for _, state in themed)
        print(f"{theme}: {len(themed)} blocks, {len(histogram)} block types -> {target.relative_to(ROOT)} "
              f"({target.stat().st_size // 1024} KiB)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
