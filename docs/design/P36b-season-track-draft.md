# P36b: season points and the free track (BUILT 2026-10-04; the draft below is kept, "As built" is at the end)

## Decided by the owner (2026-10-04)

| Question | Answer |
|---|---|
| 1. Neutral and Test towers | **Regional only.** Neutral and the Test tower earn no season points. Remove the 5-point row from section 2. |
| 2. Pace | **Dedicated finish** (section 3 as drafted: casual about step 15, regular about step 22, dedicated finish around day 35). |
| 3. Step 30 | **Plus a season-exclusive item**, alongside the Champion title and the season badge. See "The finale item" below. |
| 4. Delivery | **Automatic** grants through the pending-reward store. |

Everything else in the draft stands as proposed (team members earn their own points; rental and playlist runs count the same; no
catch-up bonus; 80 a day cap; a booster pack at every fifth step).

### The finale item (my proposal, to confirm before building)

"A season-exclusive item that can never be earned again" does not need new code per season if it is **one registered item with a data
component**: `cobbletowers:season_medal`, non-stackable, carrying `season = N` and the season's name, displayed as "Medal of The Rising
Tide" with a tooltip ("Finished the season track, Season 1"). One registration, one texture, and every future season's medal is the same
item with different data, so seasons stay data-only and never need a code release. The medal is cosmetic (no power), cannot be crafted or
bought, and is only ever granted by step 30 of a season's track. The registration and a generated placeholder texture (the same way the
armor art was done) land with P36b because the finale needs them; better art can replace the texture later with no other change.
Alternative: a per-season armor trim, which needs real art every season, so I do not recommend it.

Part of `P36-seasons-plan.md`. The owner decided the track is **free** and **bounded** (TDS #9), and that season **spotlight is drops and
points only**. This draft proposes the numbers; section 9 lists what I need from the owner.

## 1. The honest starting point: what the economy already pays

| Existing income | Size |
|---|---|
| Daily contracts (3 a day) | 30 to 100 CobbleDollars each, about 55 on average |
| Weekly contract (1) | 200 to 250 CobbleDollars |
| Streak milestones (one-time) | 3 days 50, 7 days 100, 14 days 200, 30 days 400, 60 days 800, 100 days 1500 |
| One opponent defeated | 6 to 12 CobbleDollars |
| A Full Heal at the vendor | 25 CobbleDollars |
| A boss milestone (floors 5 and 10) | 1 XL candy + 1 booster pack + 25 Raid Points guaranteed, plus bonus rolls |

So a regular player already earns roughly **1,400 CobbleDollars a week** from contracts alone, and **a booster pack, an XL candy and Raid
Points every time they beat a milestone boss**. A track that tried to out-pay that would either be irrelevant or break the economy.

**Design consequence: the track is not an income source. It is a progress meter with a prestige finish.** Item steps are garnish; the
real prizes are the banners and titles at the end, which nothing else in the game gives.

## 2. Season points

One currency, earned by playing what already exists. Every player on a team earns their own.

| Source | Points | Notes |
|---|---|---|
| Regional cycle clear (Tideforge, Rootvale, Duskvale) | 20 | at most 3 count per day |
| Same, in the **spotlight** region | 30 | the spotlight's "points" effect (decision 4) |
| Daily Trial, scored attempt posted | 15 | once a day by nature |
| Weekly Trial, scored attempt posted | 40 | once a week |
| Daily contract completed | 5 | three a day, so 15 |
| Weekly contract completed | 20 | |
| Streak milestone reached | 10 / 20 / 30 / 50 | at 3 / 7 / 14 / 30 days |
| Echo Duel won | 5 | at most one a day |
| Club weekly reward claimed | 20 | once a week |
| Neutral or Test tower cycle clear | 5 (see question 1) | at most 3 a day |

**Caps (so it cannot be ground):** 80 points a day from all sources together, and each source above has its own limit. The weekly
sources (weekly trial, weekly contract, club) add up to 80 a week on top.

## 3. Pacing: the track is 30 steps of 75 points (2,250 total)

| Player | What they do | Points a week | After 6 weeks | Result |
|---|---|---|---|---|
| Casual | 3 days a week: daily trial, contracts, one cycle; the weekly trial | about 190 | about 1,140 | **step 15**, halfway |
| Regular | 4 days: the same plus a second cycle some days | about 280 | about 1,680 | **step 22** |
| Dedicated | 6 days, two cycles a day, everything weekly | about 450 to 500 | passes 2,250 in week 5 | **finishes**, about day 35 |

The aim: everyone gets a good half, a regular player gets most of it, and only the dedicated finish, and with a week to spare so the
last step is not a panic. These are first estimates: P36b ships with the numbers in data so they can be tuned from real play (the tuning
report already exists for exactly this).

## 4. The track

| Step | Reward | Step | Reward | Step | Reward |
|---|---|---|---|---|---|
| 1 | 50 CobbleDollars | 11 | 100 CobbleDollars | 21 | 150 CobbleDollars |
| 2 | 50 CobbleDollars | 12 | **Banner II** | 22 | 50 Raid Points |
| 3 | 25 Raid Points | 13 | XL candy | 23 | 2 XL candy |
| 4 | 75 CobbleDollars | 14 | 100 CobbleDollars | 24 | **Title: "Challenger of the season"** |
| 5 | Booster pack | 15 | Booster pack (halfway) | 25 | Booster pack |
| 6 | **Banner I** | 16 | 50 Raid Points | 26 | 150 CobbleDollars |
| 7 | 75 CobbleDollars | 17 | 100 CobbleDollars | 27 | 50 Raid Points |
| 8 | XL candy | 18 | **Banner III** | 28 | 2 XL candy |
| 9 | 25 Raid Points | 19 | XL candy | 29 | 200 CobbleDollars |
| 10 | Booster pack | 20 | Booster pack | **30** | **Title: "Champion of the season" + the season badge + the finale (question 3)** |

Totals for a full track: 1,050 CobbleDollars, 200 Raid Points, 5 booster packs, 7 XL candy, 3 banners, 2 titles and a badge. That is a
small fraction of what ordinary play pays, by design.

* **Banners and titles are cosmetic and permanent**, named for the season ("The Rising Tide" banners, "Challenger of The Rising Tide"). They
  come with P36d (titles, banners, the shared chat hook); until then the track shows them as earned and records them, and P36d displays
  them. Nothing is ever taken away at season end.
* **Season-coloured**: each season's banners and titles carry that season's name, so an old one is a mark of when you played.

## 5. How rewards arrive

* A step is **granted automatically** when its points are reached, through the existing pending-reward store (so an offline player
  receives it at the next login, a full inventory loses nothing, and a crash cannot pay twice: each grant is keyed by season and step).
* The login summary gains one line: the step, the points to the next, and the next prize. `/tower season track` shows the whole ladder
  with what has been earned.
* There is no "premium" lane and no purchase anywhere. Points never come from spending.

## 6. Safeguards

* **Bounded**: the daily cap and per-source caps in section 2; a hard ceiling of 30 steps; no repeatable reward button.
* **No new farm**: every source already has its own anti-farm rule (a cycle must be cleared; a trial attempt is once; contracts need a
  minimum depth and a distinct run). The track adds no new way to earn anything, only a tally.
* **Never removes**: ending a season keeps every earned reward. Unearned steps simply stop. There is no carry-over of points.
* **Rental and playlists**: counted like any run (see section 9, default).
* **Disabled with seasons**: the master switch turns points off with it; points already earned stay.

## 7. What gets built (P36b)

`season/SeasonPoints` (pure: sources, values, caps, the step for a total), `season/SeasonTrack` (the 30 steps as data in
`seasons/` or one `season_track.json`), `persistence/TowerSeasonProgressStore` (per player: points by day for the cap, total, steps
granted), hooks at the existing events (cycle cleared, trial posted, contract completed, streak milestone, Echo Duel won, club reward claimed),
the pending-reward grants, `/tower season track`, the login line, an operator `seasonadmin points <player> <n>`, and tests: pure
points and caps, idempotent grants, a catch-up of several steps at once, and a live test through a real cycle and a contract.

## 8. Not in P36b

Banner and title display and the chat hook (P36d); the spotlight's drop weighting and the club season score (P36c).

## 9. Questions for the owner

1. **Neutral and Test towers**: give them a small amount of points (5 a clear), or regional only?
2. **Pace**: the table in section 3 (casual gets half, dedicated finishes by about day 35), easier, or harder?
3. **What does step 30 give besides the title and badge?**
4. **Delivery**: automatic grants (recommended) or a manual `claim` button?

Defaults I have picked unless you object: every team member earns their own points; rental and playlist runs count the same as
standard ones; no catch-up bonus for late joiners; the 80-a-day cap; a booster pack at every fifth step.


---

## As built (2026-10-04)

Built as drafted with the owner's answers (regional only, dedicated-finish pace, automatic delivery) and the finale as **a season-exclusive
armor trim** (the owner chose a trim over the medal I proposed).

**Points.** `season/SeasonPoints` (pure) holds the sources, values and caps exactly as in section 2: regional clear 20 (30 in the spotlight
region, three a day), daily trial 15, weekly trial 40, daily contract 5 (three a day), weekly contract 20, streak milestones 10/20/30/50
(once each), Echo Duel win 5 (one a day), club weekly claim 20; 80 a day across the daily sources, a final award trimmed to fit; weekly sources
and streak milestones outside the cap, counted once a week or once each. A scored trial attempt earns points only if it cleared at least three floors.
Hooks: `MasteryService.cycleCleared`, `TrialService.finish` and `advanceStreak`, `ContractService.complete`, `EchoDuels.onResolved`,
`ClubService.claim`. One thing to know: a full operator-cleared Tideforge cycle also completed two daily contracts (+5 each), so a "clean"
run earns 30 plus whatever contracts it finishes.

**Track.** `definition/SeasonTrackDefinition` + `SeasonTrackRegistry` load `data/.../season_tracks/default.json` (30 steps of 75 points; the
shipped file matches the table in section 4: 1,050 CobbleDollars, 200 Raid Points, 5 booster packs, 7 XL candy, three banners, two titles, the
badge). `SeasonProgressService` grants each step reached through the pending-reward store and delivers it at once; the step count is saved
before anything is handed over, so a replay or crash cannot pay a step twice. An item the server does not have (the card mod) is skipped with a
log line. Cosmetics (`s1:banner_1`, `s1:title_champion`, `s1:badge`) are recorded permanently in `TowerSeasonProgressStore` for P36d to display.
`/tower season track` shows the step, points, today's tally, the next three steps and what has been earned; the login summary gains a track line;
operator `/cobbletowers seasonadmin points <player> <n>` adds points outside every cap.

**The finale: season armor trims.** Step 30 grants **four `cobbletowers:season_trim_template_<season>`** (one per armor piece) plus the Champion
title and the badge. A trim is data in 1.21.1, but a smithing template needs one registered item per pattern, so `season/SeasonTrimItems`
pre-registers templates for seasons 1 to 12; a later season is then only data and art, never a code release. Per season:
`data/cobbletowers/trim_pattern/season_<n>.json`, `textures/trims/models/armor/season_<n>.png` and `_leggings.png` (painted in the game's eight
palette greys so every material recolours them), an entry in `assets/minecraft/atlases/armor_trims.json`, and language. All of it is generated by
`tools/generate_season_trims.py`. The 16 tower armor pieces are added to `minecraft:trimmable_armor` and the templates to
`minecraft:trim_templates`, so the ordinary smithing table applies a trim. Seasons 1 to 3 ship with **placeholder** art (waves, roots, crescents)
that an artist can replace file for file.

**Adding a season (checklist):** add `seasons/season_<n>.json`; add `<n>` to `ART_SEASONS` and its name to `NAMES` in
`tools/generate_season_trims.py`; run it; commit. `SeasonTrimAssetsTest` and `validate_definitions.py` fail if any piece is missing, so a
season cannot ship with a track finale that grants a template with no pattern. Past season 12, raise `SeasonTrimItems.MAX_SEASONS` (code).

**Verified.** Unit: `SeasonPointsTest` (caps, trimming, weekly and streak rules, a new day, the shipped track's totals and finale, the store) and
`SeasonTrimAssetsTest` (every authored season has a complete trim, textures use only the palette greys, tags). Live `season_points_test.py`
19/19: a spotlight clear earns exactly 30; operator points cross steps and grant each exactly once; all 30 steps granted once each; step 30
hands over four season 1 templates and nothing more once done; the data pack accepts every season's trim pattern and refuses an unknown one; both
tags work. **Real client** `validation/client_trim.py` 7/7: a real Fabric client wears the tower armor with season 1 (gold and diamond), season 2
(emerald) and season 3 (amethyst) trims and the inventory pictures show each pattern drawn and recoloured correctly, with no texture or atlas error
in the client log. `ci_local.sh` clean.

**Not built or not proven.** The trim is not drawn on the *item icon* (vanilla adds that with per-material model overrides; the armor on a
player is correct, the inventory slot icon is the plain piece). Banners, titles and the badge are recorded but not shown until P36d. The booster
pack steps were not delivered live (the rig has no card mod; the skip is logged). The login track line was not seen live (the day was pinned after
join). The smithing-table UI itself was not driven (the data and tags it relies on were verified). The trim art is placeholder.
