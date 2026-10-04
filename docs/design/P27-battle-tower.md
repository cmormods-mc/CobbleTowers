# P27: The Battle Tower (one building, four themes)

Status: built; see "Verified" for what has and has not been seen running.

## What it is

The user supplied `battletower_use.schem` (Sponge v3, 93 x 163 x 87, 325,072 blocks): a ten-hall round tower with a glass
dome, on a hill. It replaces the old flat arenas (`arena_floor`, `boss_arena`) for the four real towers. The Test Tower
keeps its own Poke Ball Spire.

Every tower uses the **same shape**, so one set of anchors fits all of them. What differs is the block types:

| Tower | Look (from the user's reference image) | How |
|---|---|---|
| Neutral | white, red and cyan, as drawn | the base, unchanged |
| Tideforge | prismarine and navy, blue glass, sea lanterns | quartz becomes prismarine; red becomes blue |
| Rootvale | wood, mud brick and green growth, green glow | quartz becomes mud brick/birch; blue becomes green; sea lanterns become verdant froglights |
| Duskvale | blackstone and deepslate, ember-lit windows | white stone becomes blackstone; blue becomes red/orange; sea lanterns become shroomlights |

## The halls

Found by scanning the building for open air above a solid ring floor. The party stands at these y levels (structure
coordinates), all at the same x/z so the cell is pasted once and each floor teleports the party up:

`31, 42, 53, 64, 76, 88, 100, 111, 124, 141` for floors 1 to 10. Headroom is 5 to 9 blocks. Floor 10 is the top hall under the
dome. The hall at y 133 is not used (eleven halls, ten floors).

Anchors (the same on every floor, around the middle x 45, z 42): entry at z-8 facing south, the opponent at z+8 facing north,
the spectator at x-8, the exit marker at x+8. `tools/apply_battle_tower_layout.py` writes them; only each floor's `layout`
changes, so pools, milestones and indexes are untouched.

## The tools

* `validation/retheme_schem.py` reads `validation/schematics/battle_tower.schem` and writes
  `data/cobbletowers/structure/battle_tower_<theme>.nbt`. A theme is a table of "this block becomes that block". Block
  properties are carried across, so a mapping may not change family (slab to slab, stairs to stairs); that is an error, not
  a guess. With `--minecraft-jar` every name is checked against the real 1.21.1 block list.
* `validation/schem_to_structure.py` now also reads Sponge v3 (`Blocks.Palette` / `Blocks.Data`).
* `tools/render_battle_tower.py` draws the four themes side by side. Its colours are guesses from block names (unknown
  materials show grey), so it shows shape and contrast, not what the game looks like.

## Things that were deliberately not done

* The reference image shows waterfalls, a lake, roots, banners, bridges and a moon. None of that is in the schematic, so it is
  not in the towers; only block types change. Terrain is recoloured (sand, dark dirt) but not reshaped.
* Beehives in the schematic lose their (empty) block entities in conversion; they are plain beehive blocks.
* The invisible-light trick used by the Test Tower is not used: the halls carry their own sea lanterns, themed.

## Verified

See the commit message and the smoke test `validation/smoke/battle_tower_test.py` (`--tower`, `--floors`).
