# Session state: CobbleTowers (as of 2026-10-29)

Branch `cmor/amazing-hopper-1r6071`. Fabric 1.21.1, server-side Polymer, needs CobbleRaids (`./gradlew test build -Pcobbleraids_version=0.8.191-world-boss`, CobbleRaids in mavenLocal).
Commits here carry no attribution lines; commit and push once tested; no PRs unless asked. Vault notes are in `session-state-vault.md`.

## Done this session
* Balance pass (`docs/design/tower-key-and-lib-payouts.md`, last section): enemy level from the two highest registered levels, armor only from milestone and crown chests with no duplicates, floor and boss rolls 2 to 1, daily completion budget (3 full, 3 at 50%, then 25%), daily and weekly trials pay only on the scored attempt (practice: 10% for one rental key).
* Hollow's Eve physical tower (`docs/design/P55-explorable-towers.md`, last section): generator `tools/halloween_tower/hollow_tower.py` (+ 12 mutation tests), `battle_tower_halloween.nbt`, gates and walk-mode intermission, live test `validation/smoke/walk_tower_test.py` (137/137 on the rig, v2 doors and the exterior: two players climb floors 1 to 10 on foot, cycle end by teleport). 989 unit tests green.

## Not done
* Real-client look at the final pumpkin-lit rooms (the first pass was seen: arena, vendor room with pedestals, stair, exterior).
* Trick or Treat rooms (the gallery is where they would go), the vault is still not wired to anything, per-player run history.
* Untested live on a real server with real players: balance numbers, the 90 s straggler teleport with a slow player.

* 2026-10-29: Hollow's Eve exterior built (`tools/halloween_tower/exterior.py`, `apply_layout.py`, `preview.py`); structure is 136x215x137, anchors shifted by the crop offset; live climb 137/137 and real-client exterior shots taken. Island widened to a rounded square with a brick plinth (structure now 164x215x163, climb 137/137 again, roof and corner towers seen in the client). Open: event votes not yet in the rooms, Trick or Treat rooms, real-player balance.
* Rig recipe and scripts: `docs/handoff/rig-recipe.md`, `validation/rig/`.
* 2026-10-29: Trick or Treat vault built (`com.cobbletowers.trick`, `room_trick_or_treat`, `RouteGraph.Kind.TRICK_OR_TREAT`; rules in `docs/design/P55-explorable-towers.md`, last section). Proved live (143/143 on the rig): the stop appears on the route map, the vote opens the vault, treat pays, a mimic starts for both players, takes its price first and gives it back on a win; the climb carries on afterwards. **Not yet proved live**: a lost mimic (price stays), a relic actually taken (none was held on floors 1 and 2), the 5% cut seen in a real payout (unit-tested), and a real-client look at the vault on its dais. Not built: click-to-open, catchable rare, mimic skin, a mimic fight surviving a restart.
* 2026-10-29: Trick or Treat room seen in a real client (room, closed vault on its dais, opening onto candy; shots taken with `validation/rig/hold_trick.py` + `shots.sh`). Still unproved live: a lost mimic, a relic actually taken, the 5% cut in a real payout.
