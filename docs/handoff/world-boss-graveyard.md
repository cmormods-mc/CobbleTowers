# World Boss graveyard (2026-10-29)

An open, night-time churchyard for the CobbleRaids World Boss arena (`docs/WORLD-BOSS-DESIGN.md` 12c in SnobblemonRaids), built from the owner's reference picture:
a dark stone church on a terraced hill with four tall stained-glass windows (red, orange, green), a porch with a pointed green-lit doorway and a stair, a bell tower with green windows and
a pyramid roof, a broad stair, orange flame pillars, tombstones and candles in purple-red grass, gnarled leafless trees, two small crypt towers and an iron fence with a gate.

* **Generator**: `python tools/world_boss_graveyard/graveyard.py --blocks <loot_table/blocks dir> [--npz grid.npz]` writes `validation/arena_schematics/world_boss_graveyard.schem` (128 x 96 x 128, about 305,000 blocks,
  the origin is the north-west bottom corner, `seed` 31 gives the same graveyard every run) and checks it. Pictures: `python tools/halloween_tower/view.py --npz grid.npz <out_dir> name:x,y,z,yaw,pitch[,fov]`
  (absolute coordinates, a night sky with a moon, the game's own block light). Preview sheet: `docs/handoff/interiors/world-boss-graveyard.jpg`.
* **Arena contract**: boss spawn marker `cobbleraids:world_boss_spawn` at (64, 13, 90) in the middle of the plaza; four `cobbleraids:world_boss_entrance` markers inside the gate, at (60,13,120), (64,13,122), (68,13,120) and
  (64,13,117). Paste with a normal paste including air, then `/cobbleraids worldboss arena scan` finds the markers. The plaza is open to the sky with 20 blocks clear on every side and 14 high; the avenue runs
  gate to plaza; both stairs and the plaza are reachable on foot from every entrance (the generator proves it). Arena radius 96 covers the whole build.
* **Checks** (all in the generator): markers stand on solid ground, the combat court is clear, on-foot reach, every block id is a real 1.21.1 block (or a CobbleRaids marker). No block entities.
* **Night and light**: the sky, moon and time of day belong to the world or dimension, not to the schematic. Vanilla night is dark; a dedicated dimension type with `ambient_light` around 0.1 to 0.2
  would give the moonlit look of the reference. The build carries its own light: flame pillars, lamp posts round the plaza and along the avenue, candles at graves, lit pumpkins, glowing windows.
* **Not done / unseen**: never pasted into a world or seen in a real client; trees and boulders are procedural and may want a second look; the church interior is a plain nave (sealed, no entrance);
  the old enclosed Mourning Court in ascensionDungeons is untouched and now superseded for this use.

## Revision 2 and the real-client check (2026-10-29)

* **Bigger**: the hill is 12 high, the nave 41 x 20 with 20-high walls and five tall lancets, the porch and the bell tower (13 wide, 44 high) taller, walls of blue-grey deepslate with lighter stone trim; about 150 graves.
* **Real client**: a vanilla 1.21.1 client on a scratch server (`L:/claude-cobbleraids-work/gy-rig`, port 25570, RCON 25576, flat world, the schematic as the datapack structure `gy:graveyard`, pasted with
  `/place template gy:graveyard 0 40 0` after `forceload add 0 0 127 127`; midnight, clear) showed the build as designed: the stained-glass mosaics, the green porch and doorway, flame pillars, lamp posts, the plaza
  ring and the fence all read well. Two fixes came out of it: brown podzol and coarse dirt clashed with the purple-red palette (now mycelium and crimson nylium only), and the flame pillar's glass was lopsided.
  Not fixed: the trees read as heavy dark silhouettes; vanilla night is very dark (use a dimension type with `ambient_light`).
* **Not photographed after the fixes**: the owner's desktop was in use, so no further screen captures were taken. The rig server is still the place to look: join `127.0.0.1:25570` with a vanilla 1.21.1 client (offline mode).

## Real-client look after the fixes (2026-10-30)

* `python validation/graveyard_look.py --rig L:/claude-cobbleraids-work/gy-rig --java <jdk21> --out <dir> [--origin 2560,0]` boots the scratch rig, pastes the build, joins a spectator client and photographs ten views at night and at dusk (the window goes to `--origin`, the top-left of the monitor to use, in physical pixels).
* Seen: the soil is purple-red everywhere, the stained-glass windows, green porch, flame pillars, plaza ring, lamp posts and fence all read well, at night (dark but readable) and at dusk (best).
* Found and fixed: the layer under the turf was plain dirt and showed brown on the hill steps in front of the church; it is netherrack now (30,359 blocks, nothing else changed).
* Still open: the trees are heavy black masses (one view stands inside a trunk); vanilla night is dark (an `ambient_light` dimension would help); the church interior is sealed; arena scan and 40-duel sizing.

