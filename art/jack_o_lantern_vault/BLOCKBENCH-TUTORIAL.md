# Jack-o'-Lantern Vault: Blockbench tutorial and remodel handoff

Purpose: rebuild the vault **by hand in Blockbench** to a quality the generator cannot reach, while staying drop-in compatible with the runtime
(`com.cobbletowers.halloween.vault`). Everything here is derived from the generator (`tools/vault/vault_spec.py`), so numbers are exact.

**Honesty note.** I could not run Blockbench in the environment this was written in (no network to it, no GUI), so menu names are from memory of
Blockbench 4.x and may differ slightly in your version. The numbers, rules and file contracts are verified (they are what `vault_verify.py` and the real
Minecraft client accepted); the *clicks* are not. Where a step needs a feature I am not sure about, it says so.

## 0. Files you will use

| File | Use |
|---|---|
| `art/jack_o_lantern_vault/jack_o_lantern_vault.bbmodel` | The generated model. **Open it first** (File > Open Model) and use it as a visual and numeric reference. It has 1165 cubes, one group per piece. |
| `art/jack_o_lantern_vault/blockbench_tables/*.csv` | Every number in this tutorial as a spreadsheet: `all_cubes.csv` (every cube: piece, name, from, to, rotation), ring tables, face strips, stem rings, a worked ring. |
| `art/jack_o_lantern_vault/previews/` | Reference renders. `compare_reference.png` is the concept art next to the model. |
| `art/jack_o_lantern_vault/README.md`, `HANDOFF-REPORT.md` | The runtime contract and what was tested. |
| `src/main/resources/data/cobbletowers/halloween/jack_o_lantern_vault/animation.json` | Hinge, pieces, animation. Do not change without changing the Java. |

## 1. What you are building

A pumpkin chest, 1.5 blocks across and about 1.9 tall, **split horizontally at its equator into two equal halves**. The top half is the lid and hinges at the
back. A jack-o'-lantern face straddles the seam and locks the halves: eyes and upper jaw on the lid, lower jaw on the body, two latch teeth across the seam.
Iron hoop at the seam, two iron straps, four feet, a ribbed curling stem, vines, a hollow bowl heaped with wrapped candy.

**Runtime = 7 separate models** (the server moves them as rigid pieces; there is no animation in the model files):

| Piece file | Contents | Moves |
|---|---|---|
| `body.json` | lower half: 20 pumpkin rings + 2 hoop rings, lower half of the face, straps, feet, floor, body vines, hinge leaves | never |
| `lid.json` | upper half: 2 hoop rings + 20 dome rings, upper half of the face, straps, stem, leaves, lid vines, ceiling and cap, hinge leaves | hinge rotation |
| `face_plate.json` | the two latch teeth only | presses 0.6 unit in at unlock |
| `candy_fill.json` | stationary candy: 3 stepped slabs + 12 wrapped candies | never |
| `reveal_a/b/c.json` | one wrapped candy each, **centred on (8, 8, 8)** | spawned after opening |

All seven are authored in ONE shared space, so every piece keeps its rest position (no per-piece offsets). That is the key rule of the contract.

## 2. Blockbench setup

1. File > New > **Java Block/Item** (not Bedrock, not Generic Model). This enforces vanilla rules (cube rotation 22.5 degree steps, one axis, max 45).
2. Project name `jack_o_lantern_vault`. Texture size: pick **512 x 512** to start (the generator uses 1024; any square power of two works, because UVs are exported in 0..16 space).
3. Keep the default `box_uv` OFF (per-face UV). The generator uses per-face UV islands; you can keep that or switch to box UV for hand painting, either exports fine.
4. Settings > Display: no `display` block is needed (the runtime uses display transform NONE). Do not add one.
5. Turn on **Snapping** to 0.25 or free-numeric entry; you will type numbers in the Element panel (From / To / Rotation / Origin), not drag.
6. Texture path in the exported JSON must be `cobbletowers:item/halloween/jack_o_lantern_vault/vault` (texture id `0` and `particle`). In Blockbench set the texture's
   namespace/folder accordingly or edit the JSON afterwards (section 12).

## 3. Coordinates and the hard rules

* Units are model units (16 per block). **x east, y up, z south. The vault's FRONT is the north face (-z).** The floor is y = 0. The pumpkin is centred on x = 8, z = 8.
* **Vanilla limits you must respect (1.21.1):** every cube coordinate stays in -16..32; a cube may be rotated about ONE axis only, by 0, +-22.5 or +-45 degrees;
  rotation is about the cube's own origin. Groups/bones do not export their rotation in Java Block format: put real rotations on cubes.
* **How a round shape is made from those limits** ("staves"): a ring is 16 flat boards (cubes) laid tangent to a circle, one every 22.5 degrees. For a board facing
  direction phi (0 = south, 90 = east, 180 = north/front, 270 = west):
  * split phi = 90 x m + a with a in {0, +-22.5, +-45} (m = quarter turns). Use m even: board long side along x, thickness along z. m odd: long side along z, thickness along x.
  * the board is an axis-aligned cube centred on the ring at that direction, then **rotated about Y by a around its own centre**.
  * outer face by m: 0 south, 1 east, 2 north, 3 west.
* Never rotate a cube by anything else. If Blockbench lets you (some versions allow free rotation) the 1.21.1 game will not render it correctly.

## 4. Outliner structure (match this exactly)

```
vault_root            origin (8, 0, 8)
  body                origin (8, 0, 8)      cubes: shell.*, floor.*, feet.*, straps.*, vines_body.*, hinge.*, face.* (lower half)
  lid                 origin (8, 12.5, 19.6)   <- THE HINGE PIVOT (x 8, y 12.5, z 19.6)
                      cubes: dome.*, dome_fill.*, straps.*, stem.*, vines_lid.*, hinge.*, face.* (upper half)
  face_plate          origin (8, 12, -3)    cubes: latch.tooth0, latch.tooth1
  candy_fill          origin (8, 3, 8)      cubes: pile.*, candies.*
  reveal_a, reveal_b, reveal_c   origin (8, 8, 8)   one candy each, centred at (8, 8, 8)
```

Keep cube names as in `all_cubes.csv` (`shell.l10_3` = ring layer 10, direction index 3). Names are only for your sanity; the runtime reads models, not names,
but my verifier groups by name prefix.

## 5. Master measurements

| Measure | Value |
|---|---|
| Body floor ring y | 1.5 |
| Seam (equator) y | 12.5 |
| Ring height (both halves) | 0.5 (22 rings per half) |
| Seam radius | 10.9 (hoop outer radius 11.1) |
| Wall thickness | 1.6 (cavity ~ 1.6 inside every outer radius) |
| Lobe | +0.2 proud on even directions (every 45 degrees), -0.2 on odd |
| Lid top (before cap) | 23.5 ; cap plate 23.5 to 24.3 radius 3.2 |
| Stem | from y 24.3, 12 rings of 0.5, curls +3.2 in x |
| Hinge axis | parallel to x through (8, 12.5, 19.6) |
| Whole model | x -3.45..19.3, y 0.0..31.0, z -4.8..20.0 |

Profiles (both exact; the tables in section 6 are these evaluated):

* lower half radius at height fraction t (0 floor ring, 1 seam): `6.3 + 4.6 * sin(90deg * t)`
* upper half radius at t (0 seam, 1 top): `3.4 + 7.5 * cos(90deg * t^1.25)`

## 6. Build the body (ring by ring)

### 6.1 Ring table (body)
| layer k | y range | outer radius | material | lobe | board width |
|---|---|---|---|---|---|
| 0 | 1.5-2.0 | 6.46 | pumpkin | 0.2 | 2.72 |
| 1 | 2.0-2.5 | 6.79 | pumpkin | 0.2 | 2.85 |
| 2 | 2.5-3.0 | 7.12 | pumpkin | 0.2 | 2.98 |
| 3 | 3.0-3.5 | 7.44 | pumpkin | 0.2 | 3.11 |
| 4 | 3.5-4.0 | 7.75 | pumpkin | 0.2 | 3.23 |
| 5 | 4.0-4.5 | 8.06 | pumpkin | 0.2 | 3.36 |
| 6 | 4.5-5.0 | 8.36 | pumpkin | 0.2 | 3.48 |
| 7 | 5.0-5.5 | 8.65 | pumpkin | 0.2 | 3.59 |
| 8 | 5.5-6.0 | 8.92 | pumpkin | 0.2 | 3.7 |
| 9 | 6.0-6.5 | 9.19 | pumpkin | 0.2 | 3.81 |
| 10 | 6.5-7.0 | 9.43 | pumpkin | 0.2 | 3.9 |
| 11 | 7.0-7.5 | 9.67 | pumpkin | 0.2 | 4.0 |
| 12 | 7.5-8.0 | 9.88 | pumpkin | 0.2 | 4.08 |
| 13 | 8.0-8.5 | 10.08 | pumpkin | 0.2 | 4.16 |
| 14 | 8.5-9.0 | 10.26 | pumpkin | 0.2 | 4.23 |
| 15 | 9.0-9.5 | 10.41 | pumpkin | 0.2 | 4.29 |
| 16 | 9.5-10.0 | 10.55 | pumpkin | 0.2 | 4.35 |
| 17 | 10.0-10.5 | 10.66 | pumpkin | 0.2 | 4.39 |
| 18 | 10.5-11.0 | 10.76 | pumpkin | 0.2 | 4.43 |
| 19 | 11.0-11.5 | 10.83 | pumpkin | 0.2 | 4.46 |
| 20 | 11.5-12.0 | 11.1 | hoop (charcoal, no lobes) | 0 | 4.57 |
| 21 | 12.0-12.5 | 11.1 | hoop (charcoal, no lobes) | 0 | 4.57 |

Layers 20 and 21 are the **iron hoop** (charcoal, radius 11.1, no lobes, no ribs).

### 6.2 One ring = 16 boards
For every layer: 16 cubes, directions phi = 0, 22.5, ... 337.5. For direction index i (phi = 22.5 i):
1. outer radius R = table radius + lobe (even i) or - lobe (odd i). Hoop layers: lobe 0.
2. board width W = `2 * radius * tan(11.25deg) + 0.15` (table column), thickness T = 1.6, height 0.5 (y range from the table).
3. centre of the board: `cx = 8 + (R - T/2) * sin(phi)`, `cz = 8 + (R - T/2) * cos(phi)`.
4. size along x/z: (W, T) if m is even, (T, W) if m is odd. Cube From = centre - size/2, To = centre + size/2, y from the table.
5. rotation: **Y axis, angle a**, origin = the cube's centre (cx, ymid, cz). a is 0 for phi multiples of 90.

Worked example: body layer 10 (outer radius 9.43, y 6.5..7.0). All 16 cubes, ready to type
(also `blockbench_tables/example_ring_body_k10.csv`):

| phi | outer face | rotate Y | from x | from y | from z | to x | to y | to z |
|---|---|---|---|---|---|---|---|---|
| 0.0 | south | 0.0 | 6.049 | 6.5 | 16.03 | 9.951 | 7.0 | 17.63 |
| 22.5 | south | 22.5 | 9.275 | 6.5 | 14.988 | 13.177 | 7.0 | 16.588 |
| 45.0 | south | 45.0 | 12.293 | 6.5 | 13.444 | 16.194 | 7.0 | 15.044 |
| 67.5 | east | -22.5 | 14.988 | 6.5 | 9.275 | 16.588 | 7.0 | 13.177 |
| 90.0 | east | 0.0 | 16.03 | 6.5 | 6.049 | 17.63 | 7.0 | 9.951 |
| 112.5 | east | 22.5 | 14.988 | 6.5 | 2.823 | 16.588 | 7.0 | 6.725 |
| 135.0 | north | -45.0 | 12.293 | 6.5 | 0.956 | 16.194 | 7.0 | 2.556 |
| 157.5 | north | -22.5 | 9.275 | 6.5 | -0.588 | 13.177 | 7.0 | 1.012 |
| 180.0 | north | 0.0 | 6.049 | 6.5 | -1.63 | 9.951 | 7.0 | -0.03 |
| 202.5 | north | 22.5 | 2.823 | 6.5 | -0.588 | 6.725 | 7.0 | 1.012 |
| 225.0 | north | 45.0 | -0.194 | 6.5 | 0.956 | 3.707 | 7.0 | 2.556 |
| 247.5 | west | -22.5 | -0.588 | 6.5 | 2.823 | 1.012 | 7.0 | 6.725 |
| 270.0 | west | 0.0 | -1.63 | 6.5 | 6.049 | -0.03 | 7.0 | 9.951 |
| 292.5 | west | 22.5 | -0.588 | 6.5 | 9.275 | 1.012 | 7.0 | 13.177 |
| 315.0 | south | -45.0 | -0.194 | 6.5 | 13.444 | 3.707 | 7.0 | 15.044 |
| 337.5 | south | -22.5 | 2.823 | 6.5 | 14.988 | 6.725 | 7.0 | 16.588 |

**Fast workflow (recommended):** build the ring of layer 10 once from the table above. Select its 16 cubes, duplicate (Ctrl+D), then for each new layer
you must change the radius, so do it per direction: moving a cube radially = changing its centre; easiest is to copy the numbers from `all_cubes.csv`
(`shell.lK_I`) into the Element panel. Expect about 20 minutes per 100 cubes. A scripting plugin could generate rings in one go; I could not test one, so I
have not written it.

### 6.3 Faces that are visible (saves texture space and render cost)
For each board keep: the outer face, the inner face, the **up** face if the ring above has a different radius, the **down** face if the ring below has a different
radius (or it is layer 0), and for lobe (proud) boards also the two end faces. Delete (set to no texture) the rest. Hidden faces are harmless to look at but waste atlas.

### 6.4 Body extras
* **Floor**: one disc, y 1.5..3.0, radius 4.9: four cubes of half-size 4.08 turned 0, 22.5, 45, -22.5 about Y at (8, 8) (listed as `floor.d0*`).
* **Four feet**: 3.2 x 1.6 x 3.2 charcoal blocks at 45, 135, 225, 315 degrees, 5.2 from the axis, y 0..1.6, rotated Y 45 about their centre (`feet.f*`).
* **Two hinge leaves** (body half): x 2.5..4.5 and 11.5..13.5, y 10.7..12.5, z 18.0..20.0 (`hinge.body*`).

## 7. Build the lid

### 7.1 Ring table (lid)
| layer k | y range | outer radius | material | lobe | board width | board thickness |
|---|---|---|---|---|---|---|
| 0 | 12.5-13.0 | 11.1 | hoop (charcoal, no lobes) | 0 | 4.57 | 1.6 |
| 1 | 13.0-13.5 | 11.1 | hoop (charcoal, no lobes) | 0 | 4.57 | 1.6 |
| 2 | 13.5-14.0 | 10.86 | pumpkin | 0.2 | 4.47 | 1.6 |
| 3 | 14.0-14.5 | 10.81 | pumpkin | 0.2 | 4.45 | 1.6 |
| 4 | 14.5-15.0 | 10.73 | pumpkin | 0.2 | 4.42 | 1.6 |
| 5 | 15.0-15.5 | 10.61 | pumpkin | 0.2 | 4.37 | 1.6 |
| 6 | 15.5-16.0 | 10.47 | pumpkin | 0.2 | 4.32 | 1.6 |
| 7 | 16.0-16.5 | 10.28 | pumpkin | 0.2 | 4.24 | 1.6 |
| 8 | 16.5-17.0 | 10.06 | pumpkin | 0.2 | 4.15 | 1.6 |
| 9 | 17.0-17.5 | 9.79 | pumpkin | 0.2 | 4.04 | 1.6 |
| 10 | 17.5-18.0 | 9.49 | pumpkin | 0.2 | 3.93 | 1.6 |
| 11 | 18.0-18.5 | 9.15 | pumpkin | 0.2 | 3.79 | 1.6 |
| 12 | 18.5-19.0 | 8.76 | pumpkin | 0.2 | 3.63 | 1.6 |
| 13 | 19.0-19.5 | 8.33 | pumpkin | 0.2 | 3.46 | 1.6 |
| 14 | 19.5-20.0 | 7.87 | pumpkin | 0.2 | 3.28 | 1.6 |
| 15 | 20.0-20.5 | 7.36 | pumpkin | 0.2 | 3.08 | 1.6 |
| 16 | 20.5-21.0 | 6.83 | pumpkin | 0.2 | 2.87 | 1.6 |
| 17 | 21.0-21.5 | 6.26 | pumpkin | 0.2 | 2.64 | 1.6 |
| 18 | 21.5-22.0 | 5.66 | pumpkin | 0.2 | 2.4 | 1.6 |
| 19 | 22.0-22.5 | 5.04 | pumpkin | 0.2 | 2.16 | 1.6 |
| 20 | 22.5-23.0 | 4.39 | pumpkin | 0.2 | 1.9 | 1.6 |
| 21 | 23.0-23.5 | 3.73 | pumpkin | 0.2 | 1.63 | 1.6 |

Same 16-board recipe as the body, thickness = the last column (thin near the top so it stays hollow), lobe 0.2. Layers 0 and 1 are the iron hoop.
Inside the lid: a **ceiling disc** (flesh colour) just under the layer where the radius first drops below 6.6 (cubes `dome_fill.ceil*`), and a **cap plate**
y 23.5..24.3, radius 3.2 (`dome_fill.cap*`), both built like the floor disc.

### 7.2 Hinge leaves (lid half)
x 2.5..4.5 and 11.5..13.5, y 12.5..14.3, z 18.0..19.6. **Nothing of the lid may extend past z = 19.6** (the hinge). This is
what lets the lid swing without sinking into the body; the verifier fails if it does.

## 8. The face (the hard part): carved across the seam

The face is a relief laid on the skin. Mask (13 columns, 15 rows; `#` raised, `.` hole). **The seam is between rows 8 and 9**: rows 0-8 belong to the LID model,
rows 9-14 to the BODY model.

```
r00     #######   
r01   ########### 
r02  ##....#....##
r03  #.....#.....#
r04  ##....#....##
r05  ###..###..###
r06  ######.######
r07  #..#.....#..#
r08  #..#.....#..#
r09  #...........#
r10  #...........#
r11  ##.........##
r12  #############
r13  #############
r14     #######   
```

Cell width = 0.75. Column c spans x = 3.125 + 0.75 c .. + 0.75. Row r spans y = 19.25 - 0.75 r downward
(row 8 bottom is exactly y 12.5). Nothing of the lid half may go below the seam and nothing of the body half above it.

Three layers, each built from horizontal run-boxes of adjacent `#` cells:

| Layer | Cells | Depth (radially, + is outward from the skin) | Extra |
|---|---|---|---|
| hollow (dark) | every non-space cell of the mask, holes included | -1.4 .. -0.1 | dark brown/black texture (the dark inside of the mouth and eyes) |
| bezel | `#` cells | -0.1 .. +0.7 | 0.15 wider in x and y than the face, darker orange |
| face | `#` cells | +0.7 .. +1.7 | bright orange |

**Every mask row is cut into 2 strips** (half-row each) so the face can curve. For each strip the table gives the centre height, the skin radius there and the tilt:
a cube is placed with its centre at `z = 8 - (skin radius + layer depth mid)`, then **rotated about X** by the tilt, around its own centre. Tilt is +22.5/+45 on the
dome (top leans back), -22.5 on the lower belly, 0 within 1.3 units of the seam. Strip table (also `face_strips.csv`):

| mask row | strip | piece | centre y | skin radius | rotate X |
|---|---|---|---|---|---|
| 0 | 0 | LID | 19.062 | 8.7 | 45.0 |
| 0 | 1 | LID | 18.688 | 9.01 | 45.0 |
| 1 | 0 | LID | 18.312 | 9.3 | 45.0 |
| 1 | 1 | LID | 17.938 | 9.57 | 45.0 |
| 2 | 0 | LID | 17.562 | 9.81 | 22.5 |
| 2 | 1 | LID | 17.188 | 10.03 | 22.5 |
| 3 | 0 | LID | 16.812 | 10.23 | 22.5 |
| 3 | 1 | LID | 16.438 | 10.4 | 22.5 |
| 4 | 0 | LID | 16.062 | 10.55 | 22.5 |
| 4 | 1 | LID | 15.688 | 10.69 | 22.5 |
| 5 | 0 | LID | 15.312 | 10.8 | 22.5 |
| 5 | 1 | LID | 14.938 | 10.89 | 22.5 |
| 6 | 0 | LID | 14.562 | 10.96 | 0.0 |
| 6 | 1 | LID | 14.188 | 11.01 | 0.0 |
| 7 | 0 | LID | 13.812 | 11.05 | 0.0 |
| 7 | 1 | LID | 13.438 | 11.1 | 0.0 |
| 8 | 0 | LID | 13.062 | 11.1 | 0.0 |
| 8 | 1 | LID | 12.688 | 11.1 | 0.0 |
| 9 | 0 | BODY | 12.312 | 11.1 | 0.0 |
| 9 | 1 | BODY | 11.938 | 11.1 | 0.0 |
| 10 | 0 | BODY | 11.562 | 11.1 | 0.0 |
| 10 | 1 | BODY | 11.188 | 11.02 | 0.0 |
| 11 | 0 | BODY | 10.812 | 10.97 | 0.0 |
| 11 | 1 | BODY | 10.438 | 10.9 | 0.0 |
| 12 | 0 | BODY | 10.062 | 10.82 | -22.5 |
| 12 | 1 | BODY | 9.688 | 10.73 | -22.5 |
| 13 | 0 | BODY | 9.312 | 10.63 | -22.5 |
| 13 | 1 | BODY | 8.938 | 10.52 | -22.5 |
| 14 | 0 | BODY | 8.562 | 10.39 | -22.5 |
| 14 | 1 | BODY | 8.188 | 10.25 | -22.5 |

Run-box x range: from the first `#` column to the last in the run; strip y range = centre +- (0.1875 + 0.06) (+0.15 on the outermost strips for the bezel). Clip the lid's
lowest strip at y = 12.5 and the body's highest strip at y = 12.5.

**Latch teeth (piece `face_plate`)**: two vertical cubes, each one cell wide, at columns 5 and 7, y 10.25 .. 13.25
(they cross the seam), depth 1.1 centred at z = -3.95. They sit in the open mouth in front of the iron hoop.

If you want a better face than mine: keep the *structure* (lid carries the top half, body the bottom half, teeth interlock, nothing crosses the seam) and redraw the
shapes: bolder triangular eyes, a wider grin, more rows. Keep every row boundary at the seam as above, and keep cubes of each half out of the other half's volume.

## 9. Straps, vines, stem

* **Straps** (2 per half): directions phi = 135 and 225 (the front is 180). Per ring layer one board: outer radius = ring radius + 0.2 + 0.8 (body) / + 0.7 (lid),
  thickness 0.9 (body) / 0.8 (lid), width 2.2 / 2.0, charcoal. Body layers 1..19, lid layers 2..21 (skip the hoop). One stud each (charcoal light, 1.4 x 0.4).
* **Vines**: single-board spirals, width 1.0, thickness 0.3, radius = ring radius + 0.2 + 0.3; body vine starts at phi 270 and steps back 22.5 every 7 layers
  (layers 1..19, never the hoop); lid vine starts at phi 112.5 and steps 22.5 every 5 layers (lid layers 3..16, never the hoop). Add two leaf boards (flat, width 2).
  **No vine may cross the seam** (verifier check).
* **Stem**: 12 rings of 8 boards (phi every 45 degrees; rib = every other board stands 0.22 proud, the others sit 0.12 back), 0.5 high, from y 24.3.
  Ring centre slides along an arc. Table (also `stem_rings.csv`):

| ring k | y bottom | radius | centre x | centre z | board thickness | board width |
|---|---|---|---|---|---|---|
| 0 | 24.3 | 2.8 | 8.0 | 8.0 | 1.4 | 2.44 |
| 1 | 24.8 | 2.3 | 8.016 | 7.955 | 1.4 | 2.03 |
| 2 | 25.3 | 1.95 | 8.075 | 7.909 | 1.4 | 1.74 |
| 3 | 25.8 | 1.8 | 8.184 | 7.864 | 1.4 | 1.61 |
| 4 | 26.3 | 1.7 | 8.346 | 7.818 | 1.4 | 1.53 |
| 5 | 26.8 | 1.65 | 8.565 | 7.773 | 1.4 | 1.49 |
| 6 | 27.3 | 1.6 | 8.843 | 7.727 | 1.4 | 1.45 |
| 7 | 27.8 | 1.55 | 9.184 | 7.682 | 1.4 | 1.4 |
| 8 | 28.3 | 1.5 | 9.588 | 7.636 | 1.35 | 1.36 |
| 9 | 28.8 | 1.45 | 10.058 | 7.591 | 1.3 | 1.32 |
| 10 | 29.3 | 1.4 | 10.595 | 7.545 | 1.26 | 1.28 |
| 11 | 29.8 | 1.3 | 11.2 | 7.5 | 1.17 | 1.2 |

  Board width = `2 * radius * tan(22.5deg) + 0.12`; the m/a split works the same with 45-degree steps (a is 0 or 45). A cap box 1.8 x 0.7 x 1.8 closes the top. Two leaf pads are flat
  boards rotated Y +-22.5 at the stem base.

## 10. Candy (piece `candy_fill`) and the reveal candies

* **Pile body**: three stepped discs: y 3.0..4.5 radius 4.8; y 4.5..6.0 radius 6.6; y 6.0..7.8 radius 7.2 (each built like the floor disc), candy-mix texture.
* **12 wrapped candies**: body 3.0 x 1.75 x 1.75 plus a 0.875-long, 0.6-wide twist fin at each end; positions: 6 on a ring of radius 4.6 at y 8.8 (angles 30 + 60 i),
  5 on a ring of radius 3.0 at y 10.0 (angles 10 + 72 i), plus two on top at (8.6, 11.2, 7.6) and (7.0, 12.0, 8.9). Orientation along x or z and yaw in {0, +-22.5, +-45}: free
  choice, keep every candy inside radius 7.6 of the axis and below the lid ceiling.
* **Reveal candies**: one candy each, body centred exactly on (8, 8, 8). Texture colours: red, purple, yellow.

## 11. Texturing guide (the look)

Style target: the clean, hand-tiled look of the supplied Mystery Box pack: flat tonal ramps, one-pixel lit/shaded outlines, no noise.

* **Pumpkin ramp** (dark to light): (120,46,10) (188,76,12) (230,108,18) (255,140,30) (255,172,64). Boards: lit centre, darker edges (seam lines), a slightly darker bottom row, and
  baked ambient occlusion: the belly gets darker toward the ground (about 7%), the dome brighter toward the top.
* **Iron/charcoal ramp**: (22,22,26) (38,38,44) (56,56,64) (78,78,88) (106,106,118), 1.5-unit blocks with a mortar line and a lit top-left edge.
* **Stem ramp**: (34,90,22) (54,124,28) (80,158,36) (112,190,50) (156,218,76). **Vines/leaves**: (26,72,20) .. (146,214,70).
* **Face**: raised face (255,146,32), bezel (200,84,12), hollow near black (10,4,3) with a faint ember glow (140,56,10) low in the holes.
* **Candy**: two-tone checks (red/white, pink, purple, green, yellow, orange, blue), see `vault_paint.py` for exact ramps.
* Inside of the bowl and the lid underside: flesh ramp (150,64,12)..(255,164,52), faint horizontal plank lines.
* Texel density: the generator uses 3 texels per unit with a 1-texel extruded border around every island so mipmaps do not bleed. When painting by hand, leave a 1-2 px
  gap between islands and extend the edge colours into it. Everything must be fully opaque.
* UV islands: per-face UV (box UV off). In Blockbench use Texture > "Auto UV" per element only as a start, then pack.

## 12. Export to the runtime

The server needs 7 JSON model files plus ONE shared PNG, at these paths:

```
src/main/resources/assets/cobbletowers/models/halloween/jack_o_lantern_vault/body.json
                                                                           lid.json  face_plate.json  candy_fill.json
                                                                           reveal_a.json  reveal_b.json  reveal_c.json
src/main/resources/assets/cobbletowers/textures/item/halloween/jack_o_lantern_vault/vault.png
```

1. Because the file/game format has no sub-model export, make **one Blockbench project per piece** (copy/paste the group into a fresh Java Block/Item project that uses the same texture) and
   **File > Export > Export Block/Item Model** each. Keep every cube at its original coordinates (do not re-centre; the contract says the rest transform is the identity).
2. Open each exported JSON and check `"textures": {"0": "cobbletowers:item/halloween/jack_o_lantern_vault/vault", "particle": "...same..."}`, `"texture_size": [N, N]`
   equal to your PNG size, every face `"texture": "#0"`, every uv inside 0..16.
3. `vault_full.json` (all four persistent pieces in one model) is optional and not used at runtime.
4. Run the project's validator against your files: `python3 tools/vault/vault_verify.py` reads the runtime JSONs and atlas from `src/main/resources`, so drop yours there first.
   It checks bounds, rotations, UV overlap, hinge clearance sweep 0..118 degrees (exact oriented boxes), the seam rules, candy bounds, vine rules, and the contract. A hand
   model will change element names, so some name-based checks (`shell.l0_`, `face.`, `dome`, `vines_`) will complain: keep the prefixes from `all_cubes.csv` to keep them meaningful.
5. `./gradlew test` (Java side) then play-test with `/cobbletowers vault spawn south` and `/cobbletowers vault start <id> treat`.

## 13. Preview animation inside Blockbench (optional, does not affect the game)

Select the `lid` group, set its pivot to (8.0, 12.5, 19.6), create an animation 0.7 s: rotation X from 1.5 (0 s) to 118 (0.5 s) back to 115 (0.7 s).
Positive X lifts the front. The real animation is data in `animation.json`; the generated `.bbmodel` contains 7 baked preview animations you can copy.

## 14. Accuracy checklist before you export

- [ ] Every cube inside -16..32; every rotation on exactly one axis and in {0, +-22.5, +-45}.
- [ ] Seam at y 12.5: no lid cube below it, no body cube above it, the face rows split at the seam.
- [ ] Nothing in `lid` has z > 19.6; hinge pivot of the lid group is (8, 12.5, 19.6).
- [ ] The latch teeth are the only content of `face_plate`; they do not touch the lid's or body's cubes more than 0.05.
- [ ] Reveal candies centred on (8, 8, 8).
- [ ] One shared texture, all faces opaque, islands padded.
- [ ] `vault_verify.py` passes (or you understand each remaining complaint).

## 15. Pitfalls I hit (so you do not)

* **The client turns the item model half a circle about Y.** Not your problem in Blockbench (model front is still -z) but it is why the Java sends `q = Rnet R F`;
  never "fix" the front by rotating the model 180 degrees.
* A lid that extends behind the hinge sinks into the body as it swings. Keep z <= 19.6.
* Coplanar faces of two cubes z-fight: offset by 0.02 (the floor/ceiling discs do this).
* Hidden faces still cost atlas space; delete them.
* Rotating a stave by anything other than 22.5 steps looks right in Blockbench and wrong in game.
* Meshes are not supported by the vanilla item model renderer: cubes only.
* Item models cannot glow. The glow in the concept art is the particle effects in `events.json`.
