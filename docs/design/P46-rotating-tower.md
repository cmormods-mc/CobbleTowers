# P46: The rotating tower (daily and weekly, built from the seed)

Status: **draft for owner review. No code yet.** Follows P32b (Seeded Trials), P45 (relics and rooms), P38 (region rules). Written first, per the design gate.

## Why

Today's trials (P32b) pick one of 12 daily or 6 weekly **hand-written entries** (tower + playlist + mutators + level lock) in a seeded shuffle. Every entry is a fixed tower from floor 1, so a cycle repeats in 12 days and a player who has seen "Tide Pool Rush" knows exactly what it is. The goal here is a daily and weekly tower that **feels different each time** while staying fair (same seed, same run for everyone) and cheap to author (data, no per-day files).

## Decisions (owner, 2026-10-14: all four follow my recommendations)

1. **Replaces the trial entry lists.** Boards, streaks, the one scored attempt, contracts, the login line and `/tower trial` carry over unchanged.
2. **Mixed-region floors: yes.** A single-region daily stays possible as one roll outcome.
3. **Weekly** = the daily recipe at 10 floors (boss at 5, champion at 10) plus a **weekly rule**.
4. **Rewards:** trials keep the normal reward tables. A mixed tower has one table: the region of the **last segment** (the boss floor's region).

## The recipe: a trial is composed, not listed

A new `RotatingRecipeDefinition` (JSON, `trial_recipes/*.json`) replaces `TrialPoolDefinition.entries` for the daily and weekly kinds. A recipe lists **parts**, and the schedule rolls one of each, all from the period seed:

| Part | What it picks | Source today |
|---|---|---|
| **Region path** | a region for each floor (see below) | `towers/*.json` floor lists |
| **Playlist** | none, or one house-rule set | `playlists/*.json` |
| **Level lock** | enemy level from a list (e.g. 30, 40, 50, 60) | `TrialPoolDefinition.Entry.enemyLevel` |
| **Mutators** | 0 to 2 forced modifiers (curse or neutral, never relics) | `modifiers/*.json` |
| **Starting relic** | none, or one relic the whole party starts holding | P45 relics |
| **Room bias** | one room weighted up for the run (e.g. Forge-heavy) | `IntermissionEvents.WEIGHTS` |

The label shown on the board is **generated** from the parts ("Tide-Dusk Gauntlet, Level 50, Monotype, Downpour") so no name has to be authored per day. A recipe also carries an optional `named` list of hand-written entries (the current 12 and 6), which stay as a fallback and as occasional **featured** days (see Cadence).

### Region path

The tower for a trial is **synthesised**: a `TowerDefinition` is built in memory from the period seed, by choosing a region per floor from the tower definitions already loaded. Rules (pure, unit-tested):

* Floors come from existing `towers/<region>.json` floor ids, so every structure, encounter pool and boss pool already exists and is already validated.
* The run's tower id is a stable synthetic id (`cobbletowers:rotating/daily-2026-10-14`); the definition is rebuilt on boot from the same seed, so a restart or a second server gets the same tower.
* **Shape:** a region is chosen per *segment* (floors 1-2, 3-4, ...), not per floor, so a floor does not flip region every room. The daily has two or three segments, the weekly three or four.
* **Boss floors:** the daily ends on a boss from the last segment's region; the weekly puts a boss at 5 and a champion at 10 (the milestone defs of the regions at those segments).
* **Difficulty curve:** floor *n* of the trial keeps the difficulty of floor *n* of its source tower, so a Duskvale floor 2 and a Duskvale floor 8 do not swap places. The recipe picks source floor indices in rising order.
* **Floor numbers stay the source's.** Trial floor *n* is the source region's floor *n*, so difficulty is preserved and the region's own floor-5 boss and floor-10 champion (shared `milestones/` definitions) are reused as they are. Only the floor *ids* are new (a floor id is globally unique).
* **Region rules (P38) work per floor** (checked 2026-10-14, see "Region rules findings").
* **Ruleset:** `cobbletowers:standard` unless the playlist narrows it, as now. `RulesetResolver` is unchanged (floor override, playlist, trial level lock).

### Determinism

Everything above is a function of `(recipe id, period key)` through `TrialSeed` (FNV-1a plus SplitMix64, already stable across servers and Java versions). Each part draws from its own ordinal (`EncounterSeed.of(seed, 0, PART_ORDINAL_BASE + part)`) so adding a part later does not reshuffle the others. A **no-repeat guard** keeps the same region path and the same playlist from coming up two periods running, the way `indexFor` does today for entries.

### What stays exactly as it is

Run seed -> opponents, draft cards, relic offers and rooms (`DraftDraw`, `IntermissionEvents.seedOf`) are already seeded from the run, so a rotating trial inherits fair shared offers for free. One scored attempt per player, score and boards, streaks and freezes, contracts, the login line, the trial key, AscensionLib trial-band payouts: no change.

## Cadence

* **Daily:** composed from the recipe, 5 floors.
* **Weekly:** composed from the weekly recipe, 10 floors, **plus a weekly rule**: one extra constraint shown on the briefing, drawn from a short list (no vendor, no healing rooms, double risk bonus, relics offered every 2nd floor). This is what makes the weekly more than a long daily.
* **Featured day:** one day in seven (configurable, 0 disables) uses a hand-written `named` entry instead, so the best old entries and any holiday or event trial still get their turn.

## UI

* The briefing (Hall, lobby, web page `TowerHall`/trial briefing from P44) lists the generated parts as chips: path, level, house rule, mutators, starting relic. No new screen. The chip data is in the existing trial briefing payload; this adds a `parts` list.
* `/tower trial` prints the same parts in one line.
* Native screens are untouched (the P43 plan retires them).

## Tests

Pure unit tests: same seed gives the same recipe roll on a second call and a fresh JVM; no two consecutive periods repeat the path or playlist across 400 simulated days; every rolled tower has valid floor ids, rising difficulty and a boss where promised; every mutator and starting relic exists and is legal; a recipe with an empty part list is rejected at load. Live (dev jar, `trial_test.py` extended): two players join the same daily and get the same floors; the tower rebuilds identically after a restart; a mixed-region run completes a segment boundary without a stray cell; streak and board behaviour unchanged.

## Not in this phase

Per-player seeds, player-voted recipes, a trial ladder or season points, new floors or structures, cosmetics for streaks, new Showdown hooks, any change to the standard towers.

## Open questions / risks

1. ~~Region rules by floor~~ answered, see below.
2. **Cell warm pool.** It is keyed per structure, so mixed regions should work, but a daily that touches three structures may start with two cold allocations (2-3 s each, see the perf audit). A pre-warm for the day's structures at the trial-day turnover would fix it.
3. **Persisted runs.** A run stores a tower id. A synthetic id must be rebuildable from its id string alone (so a restart mid-run, or a checkpoint restore, finds it); the id encodes recipe and period for that reason.
4. **Board keys.** `boardId()` is derived from kind and period, so it is unaffected; confirm no board code assumes the run's tower id exists in `TowerRegistry`.
5. **Balance.** Every number (level list, mutator count, weekly rule list, featured-day rate) is a first guess. Read `masteryadmin counters` after a week before tuning.
6. **Dev-only seams.** Pin the day with `trialadmin day`, and add `trialadmin roll <date>` to print a day's parts without starting a run.

## Region rules findings (checked against the code, 2026-10-14)

**Mixed regions are cheap for the rules.** Rules live in a ruleset, and every battle-time reader resolves it **per floor**: `RulesetResolver.forRun(content, run, floor.rulesetOverride())` in `FloorSetup`, `FloorBoss` and `RegionFx`. `FloorDefinition` already has `rulesetOverride`. So a synthetic floor is a copy of the source floor with a new id and `rulesetOverride = <source tower's ruleset>` (`tideforge`, `rootvale`, `duskvale`), and Tideforge's no-held-items, Rootvale's drain and Duskvale's floor status apply on exactly those floors, with no new rule code. Also fine: the regional theme and jersey weighting hang off the **encounter pool** (per floor), not the tower, and Duskvale's status choice is `statusFor(seed, runFloor)`.

**Two small effects to accept.** Rootvale's drain curve uses `(contentFloor, tower.floorCount())`, so it ramps 10% to 20% across the **trial's** floors, not the source's: a Rootvale segment at trial floors 3-4 drains about 14-17%. Party clauses and the level range come from the tower-level ruleset (`standard`), which is what a trial wants.

**The real cost is the content overlay, not the rules.**
* `TowerContent` is an immutable record built once per datapack reload; `content.towers().get(run.towerId())` is read in 39 places, and `floorAt` matches floors by `index` inside the tower's floor id list. A synthetic tower therefore has to exist **inside** the loaded content.
* Plan: a pure `RotatingTowers.build(recipe, periodKey)` returns the `TowerDefinition` plus its copied `FloorDefinition`s; `TowerContent.withRotating(...)` returns a new content holding them (floors and tower in the maps, milestones reused, **not** added to `sortedTowerIds`, so Hall lists, mastery tower screens and loops over towers never show it); `TowerDefinitionRegistry` swaps it in atomically.
* It is (re)built **on demand from the id string** at: trial start, boot restore of a persisted run, and `/reload` (which would otherwise drop it). A run that started at 23:59 keeps resolving after the turnover because its id rebuilds; expired ids are dropped once no run uses them.
* `TowerContent.of` validation is reused unchanged on the extended maps, so a bad synthetic tower fails loudly in the log instead of mid-run.
* Pinned `towerDigest` for a synthetic run: use the digest of the id string (stable), since there is no datapack file to hash.

**Still to audit in P46b** (they key on `run.towerId()` and may misbehave with an id that is not in `sortedTowerIds`): `MasteryService` (trials already skip cycle clears), `TrackService`, `SeasonProgressService`, `RewardBankService` (banked armor drops by tower), `EchoService`, `ClubService`, `AscensionService` (trial ends at its limit, no ascension), `TowerFeatureService`.

## Build order (each step tested and pushed alone)

1. **P46a** recipe definition, parser, validator, shipped `trial_recipes/daily.json` and `weekly.json`; pure roll function and its tests. No behaviour change yet.
2. **P46b** synthetic tower: `RotatingTowers.build`, `TowerContent.withRotating`, on-demand rebuild by id, and the audit list above. No per-floor rule work is needed.
3. **P46c** wire `TrialSchedule.of` to the recipe (keeping `named` entries and the featured-day rule), generated labels, the briefing `parts`, `trialadmin roll`.
4. **P46d** weekly rule, pre-warm at the day turnover, live tests, balance counters.
