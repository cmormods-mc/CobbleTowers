# P26: The Test Tower (Poke Ball Spire)

Status: built and verified on a live server. Content, not a mechanism: it uses the floor layout machinery of P4/P17 as it is.

## What it is

`cobbletowers:test`, "Test Tower (Poke Ball Spire)": four floors fought inside one building supplied as a BlockArchitect
`.mcfunction` (`validation/schematics/poke_tower.mcfunction`, 15,083 `setblock` lines).

The building is a stack of sealed circular chambers, 31 x 64 x 31. Reading its cross-section showed which parts a
floor can use:

| Chamber | Standing level (y) | Room above the floor | Used as |
|---|---|---|---|
| base | 1 | 3 | not used (too low to fight in) |
| lower hall | 9 | 16, 21 wide | floor 1 |
| low room | 27 | 6, 27 wide | floor 2 |
| upper hall | 35 | 16, 21 wide | floor 3 |
| top room | 53 | 7, 27 wide | floor 4 |

Every floor names the **same structure** and differs only in its anchors, so the cell is pasted once and each floor
teleports the party to its own level (18, 8 and 18 blocks apart in the same column). The anchors are identical in x and z
on every floor: entry (15, y, 7) facing south, the opposing Cobblemon at (15, y, 22) facing north, the spectator at
(7, y, 15) facing east, the exit marker at (23, y, 15).

Encounter and boss pools, ruleset and reward table are the Neutral tower's, so the difficulty is unchanged ("same
difficulty across towers"); only the place differs. No milestones.

## The converter

`validation/mcfunction_to_structure.py` turns a `setblock` list into a vanilla structure `.nbt`, reusing the writer of
`schem_to_structure.py`. Only relative `setblock` lines are accepted; anything else (`fill`, `summon`, absolute
coordinates) is an error, because a skipped line is a hole in a floor found weeks later. `--check` re-reads the output and
compares its per-block histogram with the source; `ci_local.sh` runs it for every `.mcfunction` beside the schematics.

## Lighting, without touching the build

The chambers are sealed with tinted glass (which blocks light) and the artist placed only a handful of sea lanterns, so
most of a 16-high hall would be dark. An optional sidecar `poke_tower.options.json` (`{"lights": [9, 27, 35, 53]}`) makes
the converter add five invisible `minecraft:light` blocks (level 15) three blocks above each listed floor: one at the
centre and four seven blocks out. They have no collision and cannot be seen, so the build looks exactly as drawn.
`validate_definitions.py` treats them as neither something to stand on nor something in the way.

## Verified

* Offline: every new anchor stands on solid ground with headroom (176 anchors total, was 160).
* `validation/smoke/test_tower_test.py` (21 checks): the tower loads; the building pastes in about 200 ms and is judged
  playable by the runtime anchor check; a light is in the world at the heart of each chamber; on every floor the player is
  put at the right height in the same column and a battle starts; a bot clears all four floors; no CobbleTowers errors.
* `tools/render_poke_tower.py` draws the cross-section with the anchors marked.

## Not done

* The base chamber (y=1) and the dome above the top room are decoration only.
* Nothing connects the chambers: the party is teleported between floors. A visible staircase or lift would need openings
  cut into the artist's floors, which is the artist's call.
* The tower is open to everyone like every other tower (the unlock path is still to come); if it should be operator-only,
  that waits for that path.
* Replacing the building: re-export the `.mcfunction` over the committed one, check the standing levels in the floor files
  and the sidecar, run the converter, then `validation/validate_definitions.py`.
