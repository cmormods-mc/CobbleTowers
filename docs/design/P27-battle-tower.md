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

## Each tower keeps to its own colours

The base has a rainbow of floor medallions (one colour per hall). A themed tower must not show them, so
`retheme_schem.py` carries a list of the colours each theme may contain and **fails** if any other coloured block is left
after recolouring (`stray_colours`). It first caught pink and magenta on Rootvale's floors 7 and 8. Neutral is the base as
drawn and keeps its rainbow.

## Nothing drops in the tower

`instance/TowerDropGuard` discards, as it loads, any experience orb and any item with no thrower in the tower dimension.
That is every defeated opponent's loot, whatever produced it (Cobblemon, CobbleRaids, another mod); rewards are the run's,
banked and delivered by the reward service. A player's thrown item has a thrower and is untouched, and an unowned item
next to a dead or dying player is kept (their inventory drops that way). Live: `validation/smoke/drop_guard_test.py`.

## Commands that undo a tower

`/pokeheal`, a home or warp command, a PC or ender chest, a kit, `/give` and the like are refused for a **player in the
tower dimension** (`runtime/CommandRules` is the pure deny list, `TowerCommandGuard` the who-and-where, `mixin/CommandsMixin`
the hook: Fabric has no event before a command runs, so this is the mod's first mixin). It is a deny list, not an allow
list, because the server's command set is whatever its mods add. Matching is on the first word, so `cobblemon:pokeheal` is
caught too. Not refused: the console, RCON, command blocks (the live tests use them), and an operator in creative or
spectator mode (the same exemption the exit sweep gives). `/tower` and `/cobbletowers` can never be blocked, even by
listing them. Operators add commands in `config/cobbletowers/blocked_commands.txt` (one per line, `#` comments); the
built-in list cannot be shortened. `/tower leave` in a live run leaves the run (the exit sweep then takes the player home).
Live: `validation/smoke/command_guard_test.py` (the bot types the commands as a real player); `battlebot.js` gained a
`SAY <text>` line for that.

## The cell reset

`CellPreparer.reset` used to sweep 64 x 64 x 17 blocks. It now sweeps every held chunk to the top of the world, skipping
air-only sections, and runs before each paste too (a structure does not place its air, so anything already in the cell
shows through it). `validation/smoke/cell_reset_test.py` fails on the old sweep.

## Things that were deliberately not done

* The reference image shows waterfalls, a lake, roots, banners, bridges and a moon. None of that is in the schematic, so it is
  not in the towers; only block types change. Terrain is recoloured (sand, dark dirt) but not reshaped.
* Beehives in the schematic lose their (empty) block entities in conversion; they are plain beehive blocks.
* The invisible-light trick used by the Test Tower is not used: the halls carry their own sea lanterns, themed.

## Verified

See the commit message and the smoke test `validation/smoke/battle_tower_test.py` (`--tower`, `--floors`).
