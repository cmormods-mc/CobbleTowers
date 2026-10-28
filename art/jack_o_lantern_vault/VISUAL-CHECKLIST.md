# Visual checklist: the concept art against the model

Source of truth: the supplied concept image "Jack-o'-Lantern Vault" (closed view left, open view right; the stone pedestal excluded). The concept
readings below were **measured by eye on the image with a 2D overlay and are good to about +-10%**; they are in model units where the front width of
the closed vault is 16 (the concept has no scale of its own). Model numbers are exact, read from the runtime JSONs by `tools/vault/vault_verify.py`.
Side-by-side images: `previews/compare_reference.png` (closed and open, concept left, model right).

| # | Feature | Concept (read) | Model (exact) | Verdict |
|---|---|---|---|---|
| 1 | Footprint, width x depth | front : depth about 16 : 13 (side face about a third of the front at the shown angle) | 16 x 13 over the posts and feet (x 0..16, z 1..15 with the feet), walls 12 x 11 | match |
| 2 | Body height, floor to rim top | about 8.9 | 9.0 | match |
| 3 | Rim band (dark strap round the body top) | about 1.4 thick, dark, with a lighter edge | 1.5 thick, charcoal blocks, 0.5 proud of the walls | match |
| 4 | Lid thickness without the stem | skirt about 3.0, flat top level with the post caps (about 4.3) | skirt 3.0 (y 9..12), flat top 13.25 (4.25), raised centre to 14.25 (5.25 total) | match |
| 5 | Stepped lid profile | wide skirt, a stepped top with a raised centre | three tiers: skirt x 2.5..13.5, tier B inset 0.75, tier C 7 x 8 raised 1 | match |
| 6 | Lid width | between the posts | 11 wide (x 2.5..13.5), the posts stand outside it | match |
| 7 | Corner posts | about 2.6 square, tall, cap level with the lid top, a rivet stud on faces | 2.5 x 2.5, to y 13.0 plus a 1.5 x 1.5 cap nub to 13.5, a 1 x 1 stud on the outer faces | match |
| 8 | Feet | flat dark blocks a little wider than the posts | 3.5 x 3.5 x 2.25, 0.5 wider than the posts to the front and back | match |
| 9 | Strap locations on the lid | dark bands crossing the lid exterior (seen on the open lid), none on the closed front | two straps 2 wide at x 5..7 and 9..11, front to back over tiers C and B, 0.25 proud, **not** wrapped over the skirt front | match (the closed concept shows none on top; the brief asks for straps) |
| 10 | Stem | small blocky green stem, about 3.5 tall, leaf pads at the base | 3.75 tall: a 3 x 2 x 3 base, a 2 x 1 x 2 neck, a bent 2 x 0.75 x 1.75 tip, two leaf pads | match |
| 11 | Vines, lid | along the lid top edges and a drape on the right | on tier B's top (front and the viewer's right) and short drapes down the lid's own front and right faces; nothing below y = 9 | match |
| 12 | Vines, body | one climbing the right side and post | on the west panel from the floor to just under the rim band, one on the front-right post's outer face, one short drape beside the plate | match |
| 13 | **No vine bridges the seam** | no vine crosses the lid/body gap | enforced by `vault_verify.py`: lid vines y >= 9, body vines y <= 7.5 | match |
| 14 | Face plate shape and place | centred on the front, about 4.7 x 5.6, square-ish with chamfered corners, attached | 5.5 x 6.0, centred (x 5.25..10.75), chamfered corners, 0.6 deep over a 0.7 backing, in front of the rim band | **deviation: about 10% larger** (the carved face needs 11 cells across to read at this grain) |
| 15 | Face carving | two angular eyes, a wide jagged mouth with teeth | two 3 x 2 eyes, a 9 wide mouth with two top and two bottom teeth, the holes show a near-black backing with a faint ember glow | match in spirit; the concept's glow is a light effect, here it is painted |
| 16 | Candy fill height | heaped to about the rim, peaks standing a little above it | a mixed-candy slab to 70% of the cavity, wrapped candies on top, mean top 9.5 (rim top 9.0), highest 10.4, all inside the lid's hollow | match |
| 17 | Candy | chunky wrapped candy, checkered, red/white, pink, purple, green, yellow, orange | 12 wrapped candies 3 x 1.75 with twist fins, 7 colours, checkered | match |
| 18 | Maximum lid angle | the lid stands about 105 to 115 degrees from closed | final **115**, peak overshoot 118 | match |
| 19 | Hollow interior | a real cavity | cavity 10 x 9 x 6 behind 1-thick walls; the lid is a hollow shell too | match |
| 20 | Pumpkin skin | vertical ribs, flat orange with pixel patches | 2-unit ribs painted from world position, 6 orange steps, ordered dither | match |

Known differences from the concept, on purpose or by limit:

* **No glow, sparks or floating embers.** Item models cannot be emissive; the sparks are the `end_rod` and `flame` particles of the event manifest.
* **No stone pedestal** (excluded by the brief).
* The concept's candy pile looks fuller at the back because it is lit by that glow; the model's pile is the same height but flat-lit.
* The lid's underside is painted candle-lit but is not brighter than the rest in the dark; Minecraft item displays take the light level of their
  position (nothing in the pack changes that).
* Straps and studs are 0.25 proud boxes, not bevelled slabs.
