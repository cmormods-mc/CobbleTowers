# Handoff: a wiki UI for the CobbleTowers server

Task: build a read-only, searchable wiki that tells players what is in CobbleTowers (towers, bosses, modifiers, relics, rewards, achievements, playlists, seasons, vendor services, armor, rental Pokemon) and how the systems work. Nothing here changes gameplay.

Written 2026-10-23. Verify the counts below against the data before relying on them; they drift.

## Decisions to make first (owner, not yet taken)

1. **Where does it live?** Two candidates:
   - **A. A new page in the existing web UI** (WebGUI/Rinku, the `web/` bundle). Players open it in game, e.g. a Hall tab or `/tower wiki`. No new hosting. The page is untrusted input and the bridge drops payloads over 32,768 chars, so the wiki must ship its data in the bundle, not over the bridge.
   - **B. A static site** generated from the same data files and hosted anywhere. Needs hosting the server does not have today.
   - Recommended: A first. It reuses the Desk look, the sprite atlas and the build, and matches the owner's server-only goal (clients run Cobblemon + WebGUI + Rinku only). B can be generated from the same data module later.
2. **Static or live?** The data module below is built at bundle time from the mod's own files, so it shows the shipped defaults. A server with datapack overrides (an addon, a changed reward table) would show stale numbers. Static is fine for v1; say so on the page ("default values; this server may differ").
3. **Spoilers.** Boss pools, reward odds and modifier weights are all in the data. Decide what a player should see. Suggestion: show boss pools and reward *kinds*, hide raw weights.

## What already exists (reuse it)

| Thing | Where | Notes |
|---|---|---|
| Web UI sources | `web/src` (Preact + htm, esbuild) | `node build.mjs` writes the release bundle to `src/main/resources/web/cobbletowers/` (COMMITTED, rebuild after every `web/src` change, then the gradle build). `ci_local.sh` checks the bundle is current. |
| Build-time data module | `web/tools/gen-data.mjs` -> `web/src/gen/data.js` | Reads the mod's data files and emits `{rental, species, residents, version, guide}`. **Extend this**, do not add a second reader. `web/build.mjs` line ~26 writes it. |
| Player guide text | `src/main/resources/data/cobbletowers/guide.json` | Title + sections of lines. Already shown by `web/src/screens/Guide.js` (native look) and a Desk version, and as `/tower help` in chat. The wiki's "How it works" section can reuse it. |
| Sprites | `web/src/gen/atlas.js`, `web/tools/atlas.mjs` | 87 partner sprites (CC0) in an atlas. Species not in the atlas need a text fallback. |
| Item pictures | `web/src/itemart.js`, `web/src/gen/items.js` | Generic shape + colour by item name; AscensionLib material textures. |
| Look | `web/src/desk/*`, `web/src/styles` | "Frontier Desk" is the default look. Match it. Hall is mounted in `web/src/App.js` (`screen === 'hall' && look === 'desk' && view === 'guide'` shows `DeskGuide`). A wiki is a new `view` there. |
| Tests | `web/test/*.test.js`, `npm test` in `web/` | Pure view-model tests per screen. Do the same. |

Read first: `docs/design/P44-webgui-ui.md` (architecture, perf rules), `docs/design/webgui-bridge.md` (what the page can and cannot do), `docs/design/P51-frontier-desk-hall.md` (the look), and memory notes on web perf (idle frames ~0.2/s; no infinite animation loops, animate only transform/opacity).

## Content map: data files to wiki pages

All data lives in `src/main/resources/data/cobbletowers/cobbletowers/` (call it `$D`). Every file has `schema_version`. Names shown to players come from `display_name`; ids are `cobbletowers:<file stem>`. Labels for items and some strings are in `src/main/resources/assets/cobbletowers/lang/en_us.json`.

| Wiki page | Source | Fields worth showing | Count (2026-10-23) |
|---|---|---|---|
| **Towers** | `$D/towers/*.json` (skip `test`: dev-only) | `display_name`, `floors[]`, `milestones[]`, `ruleset`, `reward_table`, `regional_theme`, `ascension` | Neutral, Tideforge, Rootvale, Duskvale (+ dev Test Tower) |
| **Floors & bosses** | `$D/floors/<tower>/floor_NN.json`, `$D/boss_pools/<tower>/{early,late}.json`, `$D/encounter_pools/*.json`, `$D/milestones/<tower>/*.json` | floor `index`, `encounter_pool`, `boss_pool`; boss pool `entries[].definition` (a `cobbleraids:<species>` id) and `weight`; milestone `floor`, `kind` (boss / champion), `raid_definition`, `banks_rewards` | 10 floors per tower; milestones at floors 5 and 10 |
| **Rules** | `$D/rulesets/*.json` | `enemy_level` min/max, `registered_party_size`, `carryover`, `player_held_items`, `requires_battle_ready_party` | 4 (standard + one per region) |
| **Regional themes** | `$D/regional_themes/*.json` | `jersey_signatures[].species` (signature Pokemon) and theme text | 3 |
| **Modifiers** | `$D/modifiers/*.json` | `display_name`, `risk` (minor/moderate/severe), `tags`, `stack_limit`, `weight`, `relic` flag, `type` (custom/enemy/reward/scouting/field/encounter/player_constraint), `effect` | 81 files: 47 minor, 24 moderate, 10 severe |
| **Relics** | the modifiers with `"relic": true` | same as modifiers; group by tag (offense, risk, ...) | 38 |
| **Rewards** | `$D/reward_tables/*.json` | tiers (`opponent_defeated`, boss, champion, ...), each entry `item`, `min_amount`/`max_amount`, `weight`, `min_floor`/`max_floor`, table `growth_percent_per_floor` | 4 tables |
| **Achievements** | `$D/achievements/*.json` | `display_name`, `description`, `condition` | 30 |
| **Mastery & tracks** | `$D/mastery_tracks/*.json`, `$D/season_tracks/*.json`; format in `docs/TRACKS-GUIDE.md` | level, rank names, perks (vendor discount, Cobble Dollar bonus, raid points bonus), grants | 1 + 1 |
| **Seasons** | `$D/seasons/season_N.json` | `number`, `name`, `spotlight` tower | 3 |
| **Playlists** | `$D/playlists/*.json` | `display_name`, `description`, `party` rules, `difficulty_bonus` | 6 (hardcore, level cap 50, monotype, rental, solo gauntlet, underdog) |
| **Daily / weekly trials** | `$D/trial_pools/*.json`, `$D/trial_recipes/*.json` | pools the rotating trial draws from | 2 + 2 |
| **Contracts** | `$D/contract_templates/*.json` | `display_name`, `description`, `period`, `condition`, `reward` | 14 |
| **Vendor** | `$D/vendor_services/*.json` | `display_name`, `price_cobble_dollars`, `max_purchases_per_run`, `effect` | 2 (cure status, full heal) |
| **Rental Pokemon** | `$D/rental_sets/*.json` | already in `DATA.rental`: `species`, `name`, `level`, `types`, `rarity`, `item` | 150 |
| **How it works** | `data/cobbletowers/guide.json` | already in `DATA.guide` | 1 |
| **Currencies & materials** | `docs/design/tower-key-and-lib-payouts.md`, memory note "Armor archived + AscensionLib pays" | Cobble Dollars, Raid Points, and AscensionLib materials (resonance dust, facets, tower marks, scouters), paid only by AscensionLib | n/a |

### Do not put in the wiki

- **Armor sets** (`$D/armor_sets`, 12 sets): archived on 2026-10-23, out of drops, WIP names. Leave out, or add a "coming later" stub only if the owner asks.
- **Test Tower, test floors** (`towers/test.json`, `floors/test`): dev build only.
- Held items, mints and Tera Shards are not rewards. Do not list them as such.
- Ascension is paid by AscensionLib, not by tower reward tables (the tower tables no longer carry dust, facet, mark or scouter entries).

## Suggested design

- **Shell**: a left list of sections (the table above, grouped: Play, Content, Progress, Reference) and a right reading pane. On narrow widths the list collapses to a top picker. Search box over every entry name and description (client-side, a prebuilt lowercase index; no library needed at ~600 entries).
- **Cross-links**: a modifier mentions a tag, a tower lists its modifiers' risk; make ids links (`#wiki/modifier/glass_cannon`). Keep it hash-based inside the page; the bridge URL must not carry a fragment (`webgui-bridge.md`), so route with in-page state, not `location.hash` from the server.
- **Entry cards** reuse Desk components (`web/src/desk`) and `ItemIcon`/sprite atlas. Risk gets a label and a sign, never colour alone.
- **Performance**: windowed lists for modifiers, rewards and rental sets. No infinite animations. Budget: first paint under 150 ms, idle at ~0 timers.
- **Bundle size**: WebGUI has a file-count and size ceiling (~170 files / ~480 KB seen). The wiki data should be one JSON-in-JS module of a few tens of KB; check the bundle size after adding it (`node build.mjs` prints it; it was 582 KB before this).

## Build plan

1. Extend `gen-data.mjs` to return a `wiki` object: towers (with resolved floors, bosses, milestones, ruleset, theme), modifiers (+ relic flag), reward tables, achievements, playlists, trials, contracts, seasons, vendor, tracks. Resolve ids to display names at build time so the page needs no lookup. Add a Node test that every referenced id resolves and every `display_name` is non-empty.
2. Normalise effect text: `modifiers/*.json` carry machine `effect` blocks (`op: boost|hp|...`). Write one pure function that turns an effect into a sentence, and have the test fail on an unknown `op` so a new modifier cannot ship with a blank description. Check whether `lang/en_us.json` already has text for modifiers before inventing any.
3. Build the shell + two pages (Towers, Modifiers) end to end, with search. Screenshot with `node build.mjs --dev && node tools/shoot.mjs <dir>`.
4. Add the remaining pages one at a time.
5. Add a Hall entry point (Desk Hall tab or button) and `/tower wiki` if an open request is cheap. Opening a page from the server uses `WebGuiBridge`; read `docs/design/webgui-bridge.md` before adding a new payload kind (`SUPPORTED` must list it in the same change, and `TowerNetworking.handleAction` needs a branch for every action kind `PageActions.decode` returns). A pure client-side wiki tab inside an already-open Hall needs no new payload.
6. `npm test`, `node build.mjs`, gradle build, push (the pre-push hook runs `validation/ci_local.sh`).

## Constraints and traps

- **Web UI only.** The old native client is archived in `archive/native-ui`; do not touch it. Build is server-only.
- **Rebuild and commit the bundle** after any `web/src` change, or CI's freshness check fails. Beware `core.autocrlf=true`: the check compares line-ending-neutral hashes.
- **Tool trap**: in this harness a `\` in a shell heredoc or python string comes out as one backslash; write regex-heavy edits with the Edit tool.
- **Never build during a push.** Do not run a gradle build while the pre-push hook is running.
- **Commits**: no Co-Authored-By, no Claude-Session, no attribution lines of any kind. Commit and push once tested; no need to ask.
- **Comments**: short notes, not narratives.
- **Data is the single source.** Do not copy numbers into the web source by hand; the wiki goes stale the first time a reward table is retuned. Everything comes from `gen-data.mjs`.
- **Text**: English only, ASCII " * " as a separator in server text (the page turns it into a middle dot). Font is Jersey 15 (not Pixelify; its small C reads as O).
- **Species art**: only species with a sprite in the atlas get a picture. Boss `definition` ids are CobbleRaids definitions (`cobbleraids:<species>`); strip the namespace to find the sprite.

## Open questions for the owner

- Wiki inside the game (A) or on the web (B)?
- Show raw drop weights and boss pools, or hide odds?
- Should it read live server data (needs a new payload kind and paging because of the 32,768-char limit), or is shipped-defaults enough?
- Is a search result page enough, or should entries be linkable from chat (`/tower wiki <name>`)?

## Done when

- Every section above renders from generated data, with no hand-copied numbers.
- Search finds any modifier, relic, achievement or tower by name.
- Node tests cover id resolution and the effect-to-sentence function; `npm test`, `ci_local.sh` and the bundle check pass.
- Screenshots at 1280x720 and 1920x1080 are checked for every page, and the owner has seen it live on a test server through a stock client.
