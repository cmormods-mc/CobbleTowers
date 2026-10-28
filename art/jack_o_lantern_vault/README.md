# Jack-o'-Lantern Vault

The Halloween trick-or-treat reward chest for CobbleTowers: a real pumpkin split horizontally into two equal halves (the lid is the top half, hinged at the back), 8 soft lobes, an iron hoop at the seam and iron straps, a green stem,
an iron lock plate on the front with the jack-o'-lantern carved into it (two eyes over a keyhole, three layers deep) and a real hollow full of wrapped candy. Target: **Minecraft 1.21.1, Fabric, server-side only, Java 21, Polymer
resource pack, no client mod.** It is original work for this repo, drawn from the concept art ("Jack-o'-Lantern Vault", the stone pedestal left out).
The Mystery Box pack that came with the brief was used only to see how such packs are laid out; nothing from it is in these files.

**Organic shape (revision 3).** The round body and dome are rings of 16 flat boards (staves) laid tangent to a circle every 22.5 degrees; a stave
facing direction phi = 90 m + a is a box turned by a in {0, +-22.5, +-45} with its long side along x or z, so every ring stays inside vanilla's
rotation limits (one axis, 22.5 steps). Rings are half a unit thick and stacked on a quarter-sine belly and a quarter-cosine dome that meet at the seam (radius 10.9) and every
other board stands proud, which cuts the 8 lobes. About 960 cubes in all; the atlas (1024 x 1024) is painted per board with baked ambient-occlusion shading.

The model is **separate rigid pieces drawn by item displays** whose transforms the server sets. There is no GeckoLib, Model Engine, ItemsAdder or
OptiFine feature, and the Blockbench animations in the `.bbmodel` are previews, not runtime animations.

## What is where

| File | What |
|---|---|
| `art/jack_o_lantern_vault/jack_o_lantern_vault.bbmodel` | **Editable Blockbench source** (Java Block/Item format, one group per piece, the lid group pivots on the hinge, preview animations baked from the manifest). |
| `art/jack_o_lantern_vault/vault.png` | The shared 512 x 512 texture atlas (copy of the runtime one). |
| `src/main/resources/assets/cobbletowers/models/halloween/jack_o_lantern_vault/*.json` | Runtime models: `body`, `lid`, `face_plate`, `candy_fill`, `reveal_a`, `reveal_b`, `reveal_c`, plus `vault_full` (the whole closed vault in one model, for checking; not used at runtime). |
| `src/main/resources/assets/cobbletowers/textures/item/halloween/jack_o_lantern_vault/vault.png` | The runtime atlas, referenced as `cobbletowers:item/halloween/jack_o_lantern_vault/vault`. |
| `src/main/resources/data/cobbletowers/halloween/jack_o_lantern_vault/animation.json` | **Animation manifest**: coordinate contract, pieces, channels, sequences (seconds and ticks at 20 tps), state machine, reveal motion. |
| `src/main/resources/data/cobbletowers/halloween/jack_o_lantern_vault/events.json` | **Event manifest**: sounds, particles, reward / encounter triggers, reveal spawn and collect, auto-close. Kept out of the animation file. |
| `src/main/java/com/cobbletowers/halloween/vault/` | The runtime: pure pose, timeline and state-machine classes, and the Polymer display service. |
| `src/main/java/com/cobbletowers/command/VaultCommand.java` | `/cobbletowers vault spawn|start|close|reset|remove|list` (operators; for placing and testing). |
| `src/test/java/com/cobbletowers/halloween/vault/` + `src/test/resources/vault/animation_golden.json` | Java tests; the golden table is the Python reference's output. |
| `tools/vault/` | The generator (`vault_build.py`), the checks (`vault_verify.py`) and the real-client driver (`realclient_scene.py`). |
| `art/jack_o_lantern_vault/previews/` | Preview images and the animation preview. |
| `art/jack_o_lantern_vault/VISUAL-CHECKLIST.md`, `HANDOFF-REPORT.md`, `validation-report.json` | Reference checklist, the final report, the machine-readable check results. |

Regenerate everything (deterministic; a clean checkout reproduces the same bytes) with `python3 tools/vault/vault_build.py`, check it with
`python3 tools/vault/vault_verify.py`. **Edit the generator, not the outputs**: `vault_spec.py` is the geometry, `vault_paint.py` the pixels,
`vault_anim.py` the animation. If you edit the `.bbmodel` in Blockbench instead, re-export the model JSONs from it and keep the element and face
names, because nothing else reads the `.bbmodel` back.

## The pieces and the coordinate contract

One shared **vault space** in model units (16 per block): x east, y up, z south; the vault's **front is the north face (-z)** as in any vanilla block
model; the floor is y = 0;  the pumpkin is about 23 units (1.46 blocks) across, centred on x 8 / z 8, and about 30 units (1.9 blocks) tall with the stem; the footprint leaves the one-block cell, coordinates stay inside vanilla's -16..32. Every piece is authored in
that one space, so its rest transform is the identity and the union of the persistent pieces is the whole vault (`vault_full`).

| Piece | Runtime? | Moves |
|---|---|---|
| `body` | persistent display | never (the root shake of `anticipation` moves all four together) |
| `lid` | persistent display | hinge rotation |
| `face_plate` | persistent display | presses 0.6 unit into the body (the latch), nothing else |
| `candy_fill` | persistent display | never (stationary candy, one merged assembly) |
| `reveal_a/b/c` | spawned after the lid is OPEN | float, spin, scale; authored centred on (8, 8, 8) so a display spins them about their own middle |

* **Root**: the floor centre of the footprint, model point (8, 0, 8). Every display entity is spawned at the root with entity yaw 0.
* **Rest**: an item display draws its model centred on the entity, so model point (8, 8, 8) sits at root + (0, 0.5, 0) blocks. Every persistent piece's
  rest translation is (0, 0.5, 0), scale 1. `model_transformation` is `NONE` (no `display` block in the model JSONs).
* **The client's half turn (measured, not assumed)**: Minecraft's item display renderer turns the *item model* a half circle about y inside its own frame
  (the client draws `T + q (F v)`, `F = rotY(180)`), so at identity the model's north face looks **south**. The half turn acts on the model only, not on the
  translation. The rotation sent is therefore `q = Rnet R F`, so the client's `F` cancels: the rest rotation sent is `rotY(180)` = (0, 1, 0, 0) xyzw.
  My first version of this contract left it out; the unit tests passed (they only checked my own assumption), the real client showed the face plate
  missing from the front. See the handoff report.
* **Hinge**: axis parallel to x through model point (8, 9, 14), the back-top edge of the body rim, i.e. root-local (0, 0.5625, 0.375) blocks.
  **Positive rotation about +x lifts the lid's front edge** (opens). Channel `lid.rot_x_deg`; final open angle **115 degrees**, peak overshoot 118.
* **Lid**: intended pose `Tc = H + R (c - H)` with `R = rotX(angle)`, `H` the hinge and `c = (0, 0.5, 0)`, i.e.
  `root x translate(H) x R x translate(-H) x rest` with no second offset anywhere; the 4 persistent pieces share the same rest, so nothing is offset twice.
* **Facing**: `Rnet`, a rotation about +y (north 0, west 90, south 180, east -90), applied *after* the above to both translation and rotation, never as
  entity yaw. Sent: `T = Rnet Tc`, `q = Rnet R F`. Verified in all four facings in Python, in the Java tests and in the real client.
* **Interpolation**: one transformation per tick with `interpolation_duration` 1 tick, so the lid follows the hinge arc instead of cutting the chord
  (the client slerps rotations but lerps translations). Looping idle states send every 5 ticks with a 5 tick interpolation.

## Animation at a glance (20 ticks per second)

| Sequence | Ticks | Seconds | What |
|---|---|---|---|
| `closed_idle` | 40 loop | 2.0 | barely visible breath, lid 0 to 0.35 degrees and back |
| `anticipation` | 10 | 0.5 | whole vault trembles (root x shake up to 0.22 unit) and the lid rattles up to 2.2 degrees; ends exactly closed |
| `unlock` | 5 | 0.25 | the face plate clicks in 0.6 unit and springs back; the lid pops 1.5 degrees |
| `open` | 14 | 0.7 | lid 1.5 to 118 degrees (tick 10, +3 overshoot), settles at 115 at tick 14 |
| `open_hold` | 40 loop | 2.0 | lid rests at 115 with a 0.4 degree sway; loops until `close` |
| `close` | 14 | 0.7 | falls to 0 at tick 11, bounces 1.8 degrees, plate clicks, rests closed at tick 14 |
| `reset` | 0 | 0 | instant: every channel to rest, reveal candy removed; allowed from every state |

State machine: `CLOSED -> ANTICIPATING -> UNLOCKING -> OPENING -> OPEN -> CLOSING -> CLOSED`, advanced only by one `tick()` call per server tick;
`start(outcome)` from CLOSED only, `close()` from OPEN only, `reset()` from any state. **Trick and treat are indistinguishable until the lid is open**:
the pose does not read the outcome, and `events.json` holds no outcome-specific event before `OPEN`. The first outcome-specific events are at
`OPEN` tick 0: `reward_trigger` (treat) with `reveal_spawn`, or `encounter_trigger` (trick). The vault never grants a reward or spawns an encounter
itself.

## Integrating

```java
// onInitialize (already wired in CobbleTowers.java): VaultService.install();  // requests the 7 models through Polymer, loads the manifests

UUID id = VaultService.spawn(level, floorPos, VaultFacing.SOUTH);      // root = floorPos's x/z centre at floorPos's y
VaultService.addListener((vault, event) -> {                           // reward_trigger, encounter_trigger, close_request, reveal_spawn, reveal_collect
    switch (event.type()) {
        case "reward_trigger" -> grantTreat(vault);                    // your code: pay the reward
        case "encounter_trigger" -> startTrick(vault);                 // your code: spawn the encounter
        default -> { }
    }
});
VaultService.start(id, VaultMachine.Outcome.TREAT);                    // from CLOSED; decide the outcome BEFORE calling, it is not shown until OPEN
```

* Models are requested with `PolymerResourcePackUtils.requestModel(Items.PAPER, cobbletowers:halloween/jack_o_lantern_vault/<piece>)`; the custom
  model data values are Polymer's. `addModAssets("cobbletowers")` (already called) copies the JSONs and the atlas into the pack.
* **A player who has not loaded the pack is not shown the vault** (otherwise four sheets of paper would float there); `VaultDisplay.requirePack`.
* Everything runs on the server thread. Vaults are keyed by UUID with a dimension key and a position, never a level reference; a vault whose chunk
  is not loaded pauses. Vaults are not saved: the owner (the tower floor) spawns them when it builds the room.
* Sounds and particles are vanilla ids only, so a vanilla client needs nothing but the resource pack.
* `/cobbletowers vault spawn [north|east|south|west]` puts one at your feet; `start <id> treat|trick`, `close`, `reset`, `remove`, `list` drive it.

## Texture

One 512 x 512 RGBA atlas, 3 texels per model unit, 1 extruded border texel per island, about 40% of it used. Every element face has its own
island with 2 extruded border texels, so mipmaps do not bleed one island into the next. The look is deliberately clean, after the supplied Mystery Box pack (flat tonal ramps, one-texel lit and shaded outlines, tiled blocks, no noise): every face island gets a lit top/left and shaded bottom/right edge, pumpkin ribs are three flat tones with grooves, charcoal is mortared blocks in three flat tones, candy is two-tone checks. Painted from world positions so seams agree across elements. Nothing is transparent. Sampling is nearest (Minecraft's default for
item models).

## Not verified

See the handoff report: the `.bbmodel` has never been opened in Blockbench (generated and structurally checked only), the pose interpolation was not
judged in motion in a real client (only static frames and a handful of grabs), and no multi-player or server-restart run was done.
