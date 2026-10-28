# Session state: Jack-o'-Lantern Vault (as of revision 5 + Blockbench tutorial)

Branch `cmor/amazing-hopper-1r6071` in CobbleTowers (also set up, untouched by this work: AscensionLib, SnobblemonRaids, SpookySurge). Latest commit `7e0adeb`.
CobbleTowers commits carry NO attribution lines; commit and push once tested; no PRs unless asked.

## Where everything is
* Generator: `tools/vault/` (`vault_spec.py` geometry, `vault_paint.py` atlas, `vault_anim.py`, `vault_events.py`, `vault_export.py`, `vault_render.py`, `vault_build.py`, `vault_verify.py` 39 checks, `realclient_scene.py`).
  Build: `python3 tools/vault/vault_build.py` then `python3 tools/vault/vault_verify.py`. Java: `./gradlew test build -Pcobbleraids_version=0.8.191-world-boss` (CobbleRaids must be in mavenLocal: `./gradlew publishToMavenLocal` in SnobblemonRaids).
* Runtime: `com.cobbletowers.halloween.vault` (+ `command/VaultCommand`), 17 vault tests, 967 total tests green at last run. Assets: `src/main/resources/assets/cobbletowers/{models/halloween/jack_o_lantern_vault,textures/item/halloween/jack_o_lantern_vault}`, data in `data/cobbletowers/halloween/jack_o_lantern_vault/{animation,events}.json`.
* Docs/art: `art/jack_o_lantern_vault/` (README, HANDOFF-REPORT, VISUAL-CHECKLIST, BLOCKBENCH-TUTORIAL, blockbench_tables/*.csv, .bbmodel, previews, previews/realclient).
* Not wired to a tower floor: nothing spawns a vault or listens for `reward_trigger` / `encounter_trigger` (P54 Trick or Treat owns that).

## Design now (revision 5)
Organic pumpkin split horizontally at y 12.5 into equal halves; rings of 16 boards (22.5 steps), 22 half-unit rings per half; hoop radius 11.1; hinge (8, 12.5, 19.6); face straddles the seam (eyes/upper jaw on lid, lower jaw on body, two latch teeth = `face_plate` piece); ribbed curling stem; 1165 cubes; 1024 atlas.
Contract: client turns item models 180 deg about Y; sent q = Rnet*R*F, T = Rnet*Tc (measured in a real client; first version missed it).

## Real-client test rig (scratchpad)
`/tmp/claude-0/-home-user/2df97ef2-20a0-5a98-ab01-d5146231f20a/scratchpad/{srv,client,srv_up.sh}`; Xvfb :99 must run; copy jar to `srv/mods/CobbleTowers.jar` (needs polymer-virtual-entity jar there); console via `srv/console.txt`; client via `client/launch2.sh` with SHOT_ARGS quickPlay; driver `tools/vault/realclient_scene.py` (SP, OUT env, TOGGLE_HUD=1 once per client launch).

## Known gaps (honest)
.bbmodel never opened in Blockbench (no network to blockbench.net); motion never judged live; face reads as stacked strips and dome is faceted; client without pack, multiplayer, restart, TPS untested; Phoenix crate page unreachable (allowlist needed). User's open choices: redesign eyes/mouth bolder, or hand-model in Blockbench from the tutorial.
