# Jack-o'-Lantern Vault: handoff report

Target: Minecraft 1.21.1, Fabric (loader 0.17.2, API 0.116.6), Java 21, Polymer 0.9.19, **server-side only**: a vanilla client needs the Polymer
resource pack and nothing else. Namespace `cobbletowers:halloween/jack_o_lantern_vault`. Built against this repo's own `build.gradle`,
`fabric.mod.json` and the real Polymer virtual-entity jar (API read with `javap`, not from memory).

Read this first: **what I tested, what I did not, and the one mistake the tests missed** are in sections 8 to 10.

## 1. Files

Editable source
* `art/jack_o_lantern_vault/jack_o_lantern_vault.bbmodel`: Blockbench source (Java Block/Item format, 157 cubes, 28 groups, embedded atlas, 7 preview animations).
* `tools/vault/vault_spec.py` (geometry), `vault_paint.py` (pixels), `vault_anim.py` (animation + state machine + transform maths), `vault_events.py`,
  `vault_export.py`, `vault_render.py` (preview renderer), `vault_build.py` (builds everything), `vault_verify.py` (36 checks), `realclient_scene.py` (real-client driver).

Runtime exports (in the jar, copied into the Polymer pack by the existing `addModAssets`)
* `assets/cobbletowers/models/halloween/jack_o_lantern_vault/{body,lid,face_plate,candy_fill,reveal_a,reveal_b,reveal_c}.json` and `vault_full.json` (static assembled, not requested at runtime).
* `assets/cobbletowers/textures/item/halloween/jack_o_lantern_vault/vault.png`.
* `data/cobbletowers/halloween/jack_o_lantern_vault/animation.json` and `events.json`.

Java (`com.cobbletowers.halloween.vault`): `VaultPose`, `VaultFacing`, `VaultPiece`, `VaultMath`, `VaultTransforms`, `VaultTimeline`, `VaultEvent`, `VaultMachine`,
`VaultReveal`, `VaultManifest` (all pure, no Minecraft types) and `VaultModels`, `VaultDisplay`, `VaultService` (Polymer); `com.cobbletowers.command.VaultCommand`;
two lines in `CobbleTowers.java` (install and command registration). Tests: `src/test/java/com/cobbletowers/halloween/vault/` (17 tests) and `src/test/resources/vault/animation_golden.json`.

Previews (`art/jack_o_lantern_vault/previews/`): `closed.png`, `open.png`, `compare_reference.png` (concept vs model, closed and open), `facings.png`, `pieces.png`,
`atlas_x3.png`, `animation_strip.png`, `animation_preview.gif` (119 frames, one per tick, with the reveal candy), and `realclient/` (screenshots from a real vanilla client).
Docs: `README.md`, `VISUAL-CHECKLIST.md`, `validation-report.json`.

## 2. Dimensions

Model units, 16 per block, vault space (x east, y up, z south, front = north face, floor y = 0).

| | |
|---|---|
| Footprint | 16 wide (x 0..16) x 13 deep over the posts (z 1.5..14.5), feet to z 1..15; the 0.5 unit rivet studs overhang x by 0.5 |
| Shell | 12 x 11 (x 2..14, z 2.5..13.5), walls 1 thick, cavity 10 x 9 x 6 (x 3..13, z 3.5..12.5, y 3..9) |
| Height | rim top 9.0; posts 13.0 (+0.5 cap nub); lid tiers top 14.25; stem top 18.0 (the closed vault is 1.125 blocks tall) |
| Lid | 11 wide (x 2.5..13.5), 12 deep, 5.25 thick without the stem; stepped: skirt 3.0, tier B 1.25, raised centre 1.0; stem 3.75 |
| Posts | 2.5 x 2.5, feet 3.5 x 3.5 x 2.25, cap 1.5 x 1.5 x 0.5 |
| Face plate | 6.0 x 6.5 x 2.2 in three layers: 0.4 dark backing, a 0.6 bezel 0.25 wider than the face, a 0.8 raised face; stands 1.7 proud of the rim front (z 0.3..2.5); eyes and mouth are about 1.3 deep |
| Candy | slab to 70% of the cavity, 12 wrapped candies 3 x 1.75 on top, mean top y 9.5 (rim 9.0), highest 10.4 |

## 3. Texture

One shared atlas, **512 x 512** RGBA, 4 texels per model unit (a texel is a quarter unit), every element face its own island with 3 extruded border texels,
nothing transparent, no overlapping islands (checked). 182 elements (the face plate is now 51).

## 4. Runtime pieces

7 models, **4 persistent display entities per vault** (body, lid, face plate, candy fill) plus **3 reveal candies** while a treat is revealed (7 at most). The persistent pieces
move as rigid bodies: only the lid rotates, only the face plate translates, all four share one root shake. Pack cost: JSON plus a 512 x 512 PNG, see the pack zip (plus `vault_full.json`, 113 KB, which nothing requests).

## 5. Hinge, contract, facing

* Hinge axis parallel to x through model (8, 9, 14) = root-local (0, 0.5625, 0.375) blocks, the back-top edge of the rim. Positive rotation about +x lifts the lid's front. Channel `lid.rot_x_deg`.
* Rest translation (0, 0.5, 0) for every persistent piece (the display is centred on model (8, 8, 8)), scale 1, `model_transformation` NONE.
* Lid: `Tc = H + R (c - H)`, `R = rotX(angle)`: `root x translate(H) x R x translate(-H) x rest`, no double offset (checked: at rest all four pieces share one transform).
* Facing: `Rnet` about +y (north 0, west 90, south 180, east -90) applied after, to translation and rotation. Sent: `T = Rnet Tc`, `q = Rnet R F`, `F = rotY(180)`, because the client turns the item model a
  half circle about its own centre axis (section 10). Entity yaw is always 0.
* Interpolation: one sample per tick, `interpolation_duration` 1 (looping idle states every 5 ticks, duration 5). The sagitta error of the chord at the fastest tick (about 18 degrees per tick) is about 0.005 block.

## 6. Durations, final angle, effect timings

| Sequence | Ticks | Seconds |
|---|---|---|
| closed_idle (loop) | 40 | 2.0 |
| anticipation | 10 | 0.5 |
| unlock | 5 | 0.25 |
| open | 14 | 0.7 |
| open_hold (loop) | 40 | 2.0 |
| close | 14 | 0.7 |
| reset | 0 | instant |

Closed to fully open: 29 ticks (1.45 s). **Final lid angle 115 degrees**, peak 118 (3 degrees overshoot) at open tick 10, settled at tick 14.

Effects (`events.json`, state tick; vanilla sound and particle ids only): anticipation 0 `block.chest.locked` + `soul_fire_flame`, 4 `entity.evoker.prepare_summon`, 6 `flame`; unlock 2 `block.stone_button.click_on` + `smoke`;
opening 0 `block.barrel.open`, 1 `block.wooden_trapdoor.open`, 4 `flame` x14, 10 `end_rod` x10; **at OPEN tick 0 the outcomes diverge**: treat gets `reward_trigger`, `reveal_spawn` (3 candies), `entity.player.levelup`, `happy_villager`, tick 2
`block.amethyst_block.chime`, tick 72 `reveal_collect`, tick 80 `close_request`; trick gets `encounter_trigger`, `entity.vex.charge`, `soul_fire_flame`, tick 2 `block.sculk_shrieker.shriek`, tick 20 `close_request`;
closing 0 `block.wooden_trapdoor.close`, 11 `block.barrel.close` + `poof`. Reward and encounter are triggers for a listener; the vault grants and spawns nothing.

## 7. Wired in, not wired to anything

`VaultService.install()` runs at startup and `/cobbletowers vault ...` exists, but **no tower floor spawns a vault yet and nothing listens for `reward_trigger` / `encounter_trigger`**. The Trick or Treat design (P54) still owns that.

## 8. Validation that was done

* `python3 tools/vault/vault_verify.py`: **36 of 36 checks** on the files on disk: 8 models, atlas shape, vanilla-legal elements and rotations (-16..32, 22.5 steps), no UV overlap, no transparent texel, plate centred, lid on the rim, hinge on the lid's back-bottom edge,
  candy inside the cavity and inside the lid's hollow, no vine bridging the seam, `vault_full` = the four pieces, reveal pieces centred, **a hinge clearance sweep 0..118 degrees in 0.5 steps** (about 7,000 lid points against every body, plate and candy box, no penetration over 0.03 unit),
  the contract in four facings, hinge points fixed for every angle, the lid transform against an independent matrix formula, timing (0.5 / 0.25 / 0.7 / 0.7 s), continuity at every sequence boundary, peak and settle, **120 automated cycles** (treat and trick alternating) with bit-identical first and last closed transforms,
  trick and treat identical to tick 29, no outcome-specific event before OPEN, reset from every state at 15 ticks each, reveal start and rise.
* Java: **967 tests, 0 failed** (3 pre-existing skips), of which 17 are new: durations, the 3 degree overshoot, guarded transitions, reset from every state and tick, trick/treat identity, **120 cycles**, hinge fixed points in four facings, front direction, the matrix formula,
  **Java against the Python reference at every tick of every sequence and 96 transform samples** (golden table), reveal motion, all eight model JSONs, the atlas, both manifests, vanilla-only sound and particle ids, no reward event before OPEN.
* Compiled against the real Polymer virtual-entity jar (`./gradlew build`).
* **Real client** (vanilla 1.21.1 client, no mods, Xvfb with software GL, scratch Fabric server with CobbleTowers, Cobblemon and CobbleRaids, Polymer pack served by `polymer-autohost`): the vault spawned with `/cobbletowers vault spawn`, closed front and three-quarter, **start, anticipation, unlock, open, hold, treat reveal with the floating candy, close**, and **all four facings closed and open, with the camera in front each time**. After the fix of section 10 the face plate faces the
  camera and the lid hinges away from it in all four. Images: `previews/realclient/`. 5 vaults spawned, 6 started (one after a reset) and 5 removed in the live server without an error in the log. Particles and sounds from the manifest fired (particles seen).

## 9. Not tested

* **The `.bbmodel` has never been opened in Blockbench.** It was generated and checked structurally only (every outliner child exists, every animator key is a group, the texture decodes to the atlas). If Blockbench rejects something, regenerate the model JSONs from `tools/vault` instead, they do not depend on it.
* **Motion in a real client.** The animation grabs (`realclient_animation_grabs.png`) are screen captures at about 0.14 s, not tick-exact; I did not judge smoothness or the interpolation feel at 20 TPS, nor measure the overshoot on screen.
* Sound was not heard (no audio device), only the events' presence in the manifest and the particles on screen.
* A client **without** the pack (the vault is then hidden by `requirePack`), a second player watching, a chunk unloading and reloading mid-animation, a server restart with vaults placed (they are not saved), and TPS with many vaults.
* The 120-cycle test ran on the state machine and the pose maths (Python and Java); the live server ran 6 cycles, not 100.
* No wiring to the tower: no reward was granted and no encounter was started.
* The AscensionLib, SnobblemonRaids and SpookySurge repos were not touched by this work.

## 10. The mistake the tests missed, and the fix

My first coordinate contract assumed an item display at yaw 0 draws its model unrotated. **It does not**: Minecraft's item display renderer turns the item model a half circle about y inside its own frame, so at identity the model's north face looks south.
Every unit test I wrote and all 36 Python checks passed, because they checked my assumption against itself. The first real-client run showed a vault with **no face plate** (the plate has no back faces) in all four facings and the lid hinged on the wrong side
(`previews/realclient/before_fix_plate_missing_wrong_facing.png`). I measured the flip with a bare `item_display` (plate seen from the south, not from the north), derived that the half turn acts on the model vector only, not on the translation
(a first attempt that flipped the translation too opened the lid toward the camera), and fixed the contract: send `q = Rnet R F`. The Python renderer, the Java transform, the manifest and the tests now all carry it, and the real client confirms all four facings.
Lesson kept in `animation.json` and the README: the contract is what the client draws, not what the maths suggests.

## 11. Revision: more depth, cleaner texture

After review the face plate was rebuilt in three layers (backing, bezel, raised face) so the silhouette, eyes and mouth step down visibly, the eyes and mouth were enlarged, and the atlas was repainted at 4 texels per unit in the clean style of the supplied Mystery Box pack (flat ramps, one-texel outlines, no noise). Python 36/36 and Java 967/967 pass again; the real client was re-checked (`previews/realclient/revision2_*`).

## 12. Revision 3: fully organic pumpkin (rings of 1 unit; superseded in shape by revision 4)

Requested after comparing with commercial Halloween crate models (research notes: they use Model Engine, i.e. the same vanilla item-model renderer, so quality comes from modelling craft, not a different renderer). The vault is rebuilt as a round pumpkin: 16-board rings (every direction a multiple of 22.5, so vanilla-legal), a belly profile, 8 lobes, a domed lid, a rim band, iron straps, 4 feet, hinge leaves, vines that spiral, a carved face plate on the front lobe, a ring-pattern candy pile.
Sizes: 23.4 across (x -3.7..19.7), 28.0 tall, rim top 13.5, lid dome top 23.4, stem 4.6; hinge (8, 13.5, 19); 532 cubes (body 233, lid 184, plate 55, candy 51, reveals 9); 512 x 512 atlas, 3 texels per unit.
Checks: Python 38/38 (the clearance sweep now uses exact oriented boxes; it caught the lid's overhang sinking into the body behind the old hinge, so the hinge moved to the lid's farthest back point), Java suite green.
The sections above that give old dimensions (posts, 16 x 13, stepped lid) describe revisions 1 and 2.

## 13. Revision 4: lock face, horizontal halves, smooth round shape

* **The pumpkin is split horizontally at the equator**: the body is the lower half, the lid the upper half, both with the same radius (10.9) at the seam, joined by a 1-unit iron hoop on each half. Hinge (8, 12.5, 19.5) at the lid's farthest back point.
* **Smooth round shape**: 22 half-unit rings per half (was 1-unit rings), a quarter-sine belly and a quarter-cosine dome that meet the seam vertically, board overlap cut from 0.55 to 0.3, lobes cut to 0.3 (the lobe look now comes mostly from the baked shading). 958 cubes; the silhouette is a 16-gon turned smooth by the fine layering, the faceting only shows at the rim.
* **Lock face**: an iron lock plate (bezel, four rivets, a charcoal mount that follows the belly) with the raised pumpkin face carved with two angled eyes over a keyhole (round top, slot, widening foot) that reads as the nose and mouth. The press animation still moves the plate 0.6 unit.
* Checks: Python 38/38, Java green, real client re-checked in four facings (`previews/realclient/revision4_*`). Sizes: x -3.7..19.7 across, 28 tall incl. stem (halves 11 each plus cap and stem), atlas 1024 x 1024 (44 KB PNG).
* Not re-done: animation grabs for this revision; motion still unjudged in a real client; the `.bbmodel` (now 958 cubes) still never opened in Blockbench.

## 14. Revision 5: the face locks the halves, ribbed stem, finer shaping

Corrects revision 4, which misread the request (it only lowered the face and turned the mouth into a keyhole).
* **The face straddles the seam and locks the two halves.** The eyes and the upper jaw with two hanging teeth are on the LID piece (rows above the seam), the lower jaw is on the BODY piece, and two rising teeth (the latch, the `face_plate` piece) stand in the mouth across the seam. When the lid opens the face comes apart: the top half goes up with the lid, the bottom half stays on the body. The `unlock` press now drives the latch teeth.
* **Deeper face**: three layers (a 1.3 deep dark hollow, a stepped bezel, a raised face 1.7 proud of the skin) and every mask row is cut into two strips, each centred on the skin at its height and tilted by the local slope (0 / 22.5 / 45 degrees about x), so the face curves with the belly and the dome. The first row on each side of the seam is flat and stops exactly at the seam.
* **Ribbed stem**: 12 rings of 8 boards (a rib every 90 degrees), tapering from 2.8 to 1.4 radius and curling 3.2 toward +x, plus a cap and two leaf pads.
* **Smoother lobes**: lobe amplitude 0.2 (was 0.3) and board overlap 0.15.
* Hinge (8, 12.5, 19.6). 1157 cubes, 1024 x 1024 atlas. Python 39/39, Java green, real client re-checked in all four facings (`previews/realclient/revision5_*`).
* **Not good enough yet**: the face reads as stacked strips from the front, the dome is still visibly faceted, the eyes are small. A hand-made Blockbench model would beat the generator here.

## 15. Deviations from the concept

* Face plate is larger and deeper than the concept (the carved face needs 11 cells across to read, and the depth was asked for).
* No glow, sparks or embers in the model (item models cannot be emissive): they are the manifest's particles. The concept's candy glow is painted flat.
* Two dark straps run over the lid top as the brief asks; the closed concept shows none on top.
* Rivet studs overhang the one-block footprint by 0.5 unit on the post sides.
* The atlas, the vines and the candy are original pixel work generated for this repo, matched to the concept by eye, not traced. The supplied Mystery Box pack was only looked at as an example of how paper + custom-model-data packs are laid out (it hardcodes `custom_model_data` 2000001); nothing from it is used.
