# Release checklist

Written 2026-10-07 from the deferred-items list and the P37 session. Tick items as they are done; add the date and what was observed.
Ordered by how badly each blocks a release.

## Blockers

- [ ] **P37 battle tracks builds and passes tests.** The code (design: `docs/design/P37-tracks.md`) was written without ever being compiled or tested.
  - [ ] `./gradlew build` is green, including `MasteryTrackTest` (the shipped `mastery_tracks/default.json` must match the old rank and perk table at every level 0..30).
  - [ ] Progress tab screenshots at GUI scale 2 and 3 (add a `TrackStatePayload` sample to `ScreenshotHarness`), and with shaders (`validation/hall_screens.py <scale> <mode> iris`, `SHADERPACK` set).
  - [ ] Live claim check on the test rig: claim one mastery level and one season step; claim all; claim twice (refused); crash or restart between queueing and recording (no double pay).
  - [ ] Migration: a world that already has season progress keeps its granted steps as claimed (no re-grant, no loss).
  - [ ] Decide: season steps need a claim by default (a change for existing servers); `auto_claim: true` restores the old behaviour. Say so in the release notes.
  - [ ] Commit and push.
- [ ] **A human plays a full tower.** Everything so far is headless bots and screenshots. At least two real players, one full tower, watching: animation feel at a real frame rate, mouse feel, audio, the vendor villager, the HUD, worn armor, the Shift tooltip, the Test Tower chambers.
- [ ] **Cell allocation on the server thread.** A burst of allocate/abandon cycles (hundreds a minute) backs up chunk saves until the 60 s watchdog kills the server, and even ordinary run starts and ends stall a tick for 0.3 to 0.9 s. Options, costs and a recommended order are in `docs/design/cell-allocation-async.md`; the owner picks (three questions at its end).
- [ ] **Bump the build version string** (`0.21.0-p21-warm-pixel-preview`, never bumped) so a log says which jar runs.
- [ ] **Deployment.** Ship the CobbleTowers jar and the AscensionLib jar (with the rental craft lock) together; find out where the real server gets AscensionLib (source: `L:\Codex\CobbleAscend\ascensionlib`). Keep the CobbleRaids version label pinned (`0.8.162-admin-helper`).

### P37 code review (2026-10-07, read-only plus small fixes, still uncompiled)

Fixed in the working tree: two grants of the same item in one node collapsed into one (the pending queue deduplicates on id + item +
components, and every grant of a step shared an id; now one id per grant, which matters because merging addon rewards onto a node is the
headline feature); flipping `auto_claim` on mid-season would have granted steps already claimed by hand a second time; payload strings longer
than their codec limit would have thrown in `writeUtf` and broken the packet (now clipped inside the records); a modified client could grow the
merged-track cache with arbitrary tower ids (now only known towers); no request throttle on track actions (150 ms per player).

Still open, for the owner or the next session:
- [ ] **Decide: do unclaimed season steps lapse when the season ends?** Today they do (a battle pass that loses rewards is punishing). Recommended:
  allow claims through the off-season (needs the season lane to show the ended season, and a login/season-end reminder to claim).
- [ ] `claimAll` saves world data once per claim (`checkpoint` = `getDataStorage().save()`, twice per claim); claiming 30 steps = ~60 saves.
  Batch it: queue all, then one checkpoint.
- [ ] `SeasonTrackRegistry.current()` rebuilds the merged track on every call and `claimAll` calls it several times per step; cache the result
  keyed on the loaded files and the config.
- [ ] `TrackConfig` is read at server start only; `/reload` re-reads datapacks but not `config/cobbletowers-tracks.json` (document or hook it).
- [ ] An addon season file's own `step_cost` is ignored unless it is the base file (document; the guide says the first file with steps is the base).
- [ ] Mastery lane with no towers loaded sends an empty lane; the client should say "No towers" instead of an empty strip.
- [ ] `TowerUi.wrapped` has no line limit, so a long reward text can overflow the detail panel on a 240 px GUI.

## Should be refined before players see it

- [ ] **UI polish.** The pixel font is wider than vanilla and clips some labels (`Selected destina`); check every screen at scale 2 and 3. Spectator HUD in game, hover tooltips and keyboard-only navigation are owner-deferred. Modifier-card sprites are to be replaced (owner: later). Mastery-level cosmetics are recorded (`m:<name>`) but shown nowhere.
- [ ] **Balance numbers are first guesses.** Mastery thresholds and the shipped mastery grants (CobbleDollars 50/100/150/200/300/400/750), the risk payout bonus (+3% minor, +8% moderate, +15% severe, final payout only), trial and Ascension numbers, per-region tuning (each region has its own reward table with a regional booster pack at the boss floor, but all towers use the same `standard` ruleset, so difficulty and level curves are identical across regions). Add counters (contract completion rate, modifier draft frequency) and tune with real data (`docs/design/tuning-with-real-data.md`, `/cobbletowers masteryadmin report`).
- [ ] **Confirm the milestone chase rewards are final.** The boss floor guarantees an XL candy, a regional booster pack (CobblemonCards) and 25 Raid Points plus 2 bonus rolls; the champion floor guarantees an XL candy, a God Pack Ticket and 75 Raid Points plus 3 bonus rolls (`reward_tables/<region>.json`). They were "to be determined" before the CobblemonCards crossover; check they are what you want and that the server has CobblemonCards (a missing item is skipped with a log line, so players would silently get less).
- [ ] **Unenforced rules: not release blockers, but know what they are.**
  - Battle switching (TDS #48) is deliberately not CobbleTowers' business: switching and item rules reach Showdown only through CobbleRaids' `EncounterRules` for **boss** battles (`TowerBossAdapter.withSwitching/withItems`, set by the No Retreat / no-items modifiers). Ordinary floors use the live party untouched. Nothing to build unless you want ordinary-floor rules.
  - `item_action_budget` (TDS #47) is parsed, validated and unused (`standard.json` sets 0). Its first consumer would be a Hardcore playlist (`docs/design/engagement-roadmap.md`, "How it fits the code"). Either build it with Hardcore, or drop the field from the ruleset schema so datapack authors are not misled.
  - Mid-run party changes (P18 allows moves after lock-in) are the trap for any party-restricting playlist (Monotype and similar): re-validate at each floor start, or freeze the registered party (roadmap section 'The trap: mid-run changes').
  - Cash-out vote ties fall back to "keep playing" (`IntermissionRound`: no leader exists to break a tie). Confirm that is wanted; alternatives are "cash out" on a tie, or a coin flip seeded by the run.
- [ ] **Art.** Armor worn layers (visor, boot band, shading), jerseys (data-only today), Test Tower chambers not connected.

## Verification gaps that could hide bugs

- [ ] A multi-hour soak run, and a full 10-floor soak per tower for the F5/F10 milestone raids and the early/late boss pools.
- [ ] Fainted-party and offline-participant rejection (unit-tested only).
- [ ] Disconnect mid-lobby in a rental draft (no live test).
- [ ] Other mods reacting to rentals: only `POKEDEX_DATA_CHANGED_PRE` is guarded; check POKEMON_GAINED-style events and any rank track (StellarRankUp has a `pokedex` track) with that mod installed.
- [ ] Stellar Titles 1.1.2 compatibility jar: menu not tested with a player.
- [ ] Tab list and chat tag with another chat plugin installed (double or hidden tag; `chat_tags: false` switches it off).
- [ ] `floor_encounter_test.py` "boss never starts" flake (not reproduced, cause unknown).

## Already in good shape (this session)

Close button on every screen, Hall banner, bundled UI font with a toggle, modifier art per effect, risk payout, draw cost (Hall draw 3.1 to 1.0 ms), and Iris + Sodium with a shader pack verified for the Hall, modifier selection and the rental pack opening.
