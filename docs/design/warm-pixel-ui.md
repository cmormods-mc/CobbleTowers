# Warm pixel UI (test build)

Branch `ui/warm-pixel`, version `0.21.0-p21-warm-pixel-preview`. Implements the approved handoff
(`CobbleTowers-approved-pixel-ui-handoff`) on top of current `main` (`69433d4`).

## What changed

- The native Tower Hall and feature menus (`TowerHallScreen`, `TowerFeatureScreen`, `TowerMenuScreen`, payloads
  `tower_hall_v1` / feature request+state, `menu/TowerHallService`, `TowerFeatureService`) were ported from the
  `codex/stylized-tower-ui` checkout, which was based on P33b. `/tower` and `/tower menu` open the Hall;
  `/tower lobby` keeps the old lobby/intermission entry. `LobbyService.playState` was split out of `sendState`.
- The industrial renderer is gone. `TowerShader` and the `tower_ui` core shader (framebuffer capture, CRT, blur) are
  deleted. `PixelUi` draws the supplied 48x48 nine-slice frames (corners at 8 GUI px, nearest sampled) and 16x16
  tiles from `textures/gui/pixel/`. No shader can fail to compile and no render state is touched.
- Palette: oak, bronze, parchment, chocolate and burgundy (`TowerUi` constants match `tokens.json`). Parchment
  canvas with ink text; dark panels with cream text.
- `TowerButton`: burgundy primary, chocolate secondary, active-tab face, 1 px press depression for 100 ms, disabled face.
  `GlassToggle` became `BronzeSwitch`.
- `TowerPanorama` paints the regional landscapes (sea, forest, dusk, neutral stone) procedurally with integer fills.
- `RewardRevealScreen`: parchment grant label inside an oak coffer; brass seal then two lid halves retract. Reduced
  motion shows the final state immediately. The grant is made server-side before the screen opens.
- Settings: Graphics now has one "Torch glow" switch. The old shader/CRT/blur/opacity controls are removed. Old config
  files still load (`shaders` is read as `glow`; `blur` and `opacity` are ignored).
- Hard-coded blue/cyan/gold values in card faces, rental pack, mastery, vendor, scouting-adjacent and partner screens
  were mapped to the warm tokens.

## Verification (this machine, 2026-10-05)

- `validation/ci_local.sh`: build, full unit suite, architecture/API/persistence bytecode checks (class and jar),
  Cobblemon 1.8.1 binary compatibility: all passed.
- Real Fabric client (Cobblemon 1.8.1, Fabric API, CobbleRaids) launched with the harness; screenshots at GUI scale 2
  and 3 for Hall (5), features (13) and gameplay screens (26): `build/*-screens-scale{2,3}`. No harness step failures.
  Harness data is synthetic ("Screenshot sample / no server actions").

## Not done / limits

- Not run against a live server: club, party, invite, spectate, title, Echo, contract and run-code mutations, stale
  packets, double clicks. The server services were not changed, but nothing here proves them under the new screens.
- Native regional dioramas for modifiers/relics, vendor/ledger/inspection illustrations and the rental case are not
  painted as new art: the rental cards and modifier cards use the warm frames and tokens only. Rental rarity glows keep
  their rarity colours.
- Primary actions on feature pages (Confirm, Refresh) use the chocolate secondary face, not burgundy.
- Mastery tab labels truncate at GUI scale 3 ("Ascensio"); the layout is unchanged from before.
- Keyboard navigation, localisation with long names, narrow windows, and texture-reload behaviour were not exercised.
- Pixelify Sans is not bundled; text is Minecraft's font.
- Pokemon sprites in `textures/gui/partners` are from the earlier checkout; their licence was not re-checked.
- Reduced-motion and glow-off paths compile and are exercised by the harness steps, but only glance-checked.
