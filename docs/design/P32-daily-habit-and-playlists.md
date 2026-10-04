# P32: The daily habit and Playlists

Status: **built** (P32a Playlists, P32b Seeded Trials, P32c the event stream and Contracts, P32d the Run Report and login summary; see the "As built" sections at the end). Designed first, per the project's design-gate habit. Part of the engagement plan (`engagement-roadmap.md`, sections 0, 3 and 8). Built in
four steps, each tested and pushed on its own: **P32a Playlists**, **P32b Seeded Trials**, **P32c the event stream and Contracts**,
**P32d the Run Report and login summary**.

## Decisions carried in (owner, 2026-10-04)

Heavy daily cadence (daily streaks matter); a **short five-floor daily trial** and a **full ten-floor weekly trial**, each with its
own board; Playlists in this phase so trials can rotate modes; mid-size community, solo and group.

## What this phase adds to a run

Two new per-run facts, stored together in `PersistedRun.options` (schema 6 to 7, migrated by absence):
**which playlist** the run is played under, and **which trial** (if any). Everything else is derived.

## P32a: Playlists

A playlist changes **who may enter and what the house rules are**, never the tower itself. Standard is "no playlist".

`PlaylistDefinition` (JSON, `playlists/*.json`): display name and description, **party clauses**, an optional enemy level ceiling,
forced modifiers, whether the vendor is closed, a maximum number of players, and a difficulty bonus.

| Playlist | Party clauses | Run rules | Bonus |
|---|---|---|---|
| Monotype | every registered Pokemon shares at least one type | none | +10 |
| Level Cap 50 | no registered Pokemon above level 50 | enemy level ceiling 50 | +5 |
| Underdog | no fully evolved Pokemon, no legendary, mythical, ultra beast or paradox | none | +10 |
| Hardcore | none | vendor closed; the *Empty Pockets* modifier forced (no items in boss fights) | +15 |
| Solo Gauntlet | at most three Pokemon registered | one player only | +10 |

* **Clauses are pure.** `PartyMember` gains species, types, evolution stage and labels (read from Cobblemon in `PartyReader`);
  `PartyValidation` checks them and adds problems in the same wording the other rules use ("Charizard is not a Water type").
* **One effective ruleset.** A playlist's level ceiling and party cap narrow the tower's ruleset; a `RulesetResolver` returns the
  ruleset for a run (floor override, then playlist, then the tower) and replaces the eight places that read it directly.
* **Re-checked at every floor.** Launch validates the party; opening the next floor re-validates the live party under the clauses
  and holds the floor with the reason if it no longer complies, so a Monotype run cannot swap in a Fire type at floor four.
* **Chosen in the lobby** by the host: `/tower playlist <id|standard>` and a "Mode" button on the play screen. Changing the tower
  resets it. A tower may name the playlists it offers; none named means all.
* **Boards are per playlist**: the leaderboard key gains the playlist, so Monotype clears are ranked apart from Standard.
  The playlist's difficulty bonus is added to the difficulty score.
* Mastery is unchanged in this step: a playlist clear counts as a clear. Playlist-specific achievements come later.

## P32b: Seeded Trials

* **Schedule.** A `TrialPoolDefinition` lists candidate towers, playlists and forced "mutator" modifiers for a kind (daily or weekly)
  and its floor count (5 or 10). The day's trial is a pure function of the **day key** (a date, shifted by a configured reset hour in
  a configured zone), so every server computes the same trial with no coordination. The seed is a stable hash of the trial id and day.
* **Run.** `/tower trial` (daily) and `/tower trial weekly` start a run of that trial through the lobby: the tower, playlist, seed,
  forced modifiers and floor limit come from the trial. The run ends at the limit as COMPLETED (no Ascension, and not a "cycle
  clear" for mastery or the all-time boards). Levels are locked by the trial's ruleset so results compare.
* **One scored attempt per trial per player**: the first run *started*. Later runs that day are practice and say so.
* **Score** (pure): floors cleared, then time, faints and difficulty fold into one integer; ranked higher-is-better. A wipe at
  floor 3 scores its three floors.
* **Boards.** A Trial board per trial instance, solo and team apart, top 50. Daily boards stay for seven days, weekly for eight weeks.
* **Streaks.** A day qualifies when the daily attempt clears at least three floors. Consecutive qualifying days build the streak; a
  missed day spends an earned **streak freeze** (one earned per seven-day streak, two held at most), otherwise the streak restarts.
  Milestones at 3, 7, 14, 30, 60 and 100 days record themselves and pay a **one-time, bounded CobbleDollar reward each**; titles and
  banners that the plan reserves for these arrive with the later cosmetics phase.
* `/tower trial` shows today's trial, the attempt state and the streak; the same line appears on login.

## P32c: the event stream and Contracts

* A small in-process **tower event stream** (`floor cleared`, `boss defeated`, `faint`, `purchase`, `modifier drafted`, `run ended`,
  with the run, the players and the numbers) that mastery and trials already compute internally. Mastery, trials and contracts
  subscribe; nothing else changes.
* **Contracts**: `ContractTemplateDefinition` (JSON), three daily and two weekly drawn from the templates by the day key, each a
  condition over events with a count and a bounded reward, one free reroll, and a depth requirement so it cannot be farmed with
  trivial runs. Progress and completion per player in a `TowerContractStore`.

## P32d: the Run Report and the login summary

* A card at the end of every run (chat, and a screen for clients that have it): floors, time, MVP Pokemon (most knock-outs),
  longest no-faint streak, modifiers, score or trial result, rank and streak change, achievements unlocked.
* On login: one line for today's trial, the streak and its time left, and the contracts outstanding.

## Not in this phase

Relics, intermission events, Echoes and Clubs, seasons, titles and banners, the Hall, the Rental Draft (the next phase). The
trial ladder's season points wait for seasons.

## Test plan

Pure unit tests for every rule (clauses, effective ruleset, day key and schedule, seed stability, scoring, streaks, contracts).
Live tests on the four-floor test tower: a playlist refuses a bad party and ranks apart; a trial gives every player the same run,
posts one attempt, ends at its floor limit, awards the streak and milestone; contracts progress and pay once. Screens are not seen in
a real client.

## As built: P32a Playlists and P32b Seeded Trials

* **`PersistedRun.options`** (schema 6 to 7, migrated by absence; an old run is an ordinary run): playlist, trial instance id, floor
  limit, whether the attempt is scored, and an enemy level lock. Every `with*` method carries it; the 19-argument constructor stays
  for tests and old callers and means "no options".
* **Playlists** (`PlaylistDefinition`, `PlaylistRegistry`, five shipped files: Monotype, Level Cap 50, Underdog, Hardcore, Solo
  Gauntlet). Clauses are pure (`PlaylistRules`, over a `PartyMember` that now carries species, types, evolution and labels read by
  `PartyReader`). `RulesetResolver` is the one place that answers "which ruleset applies" (floor override, then playlist, then
  the trial's level lock) and replaced the eight direct reads. The lobby has `/tower playlist <id|standard>` and a Mode button;
  launch validates the clauses and names the Pokemon; `PlaylistGuard` re-checks the **live party** before every floor opens.
  Hardcore closes the vendor (purchase, command, screen, and no villager) and forces Empty Pockets through the same path as
  Ascension's forced modifiers. Leaderboard keys gained the playlist; its difficulty bonus joins the difficulty score.
  Solo Gauntlet's "one player" is enforced when inviting. Not done: a tower naming which playlists it offers (all are offered
  everywhere), and playlist-specific mastery.
* **Trials** (`TrialPoolDefinition` + `TrialPoolRegistry`: a 12-entry daily pool and a 6-entry weekly pool; the pure `TrialSeed`,
  `TrialClock`, `TrialSchedule`, `TrialScoring`, `StreakRules`; `TowerTrialStore`; `TrialService`; `/tower trial`, `trial play
  daily|weekly`, `trial board`). The day's trial walks the pool in seeded shuffled cycles, so every entry comes up once per cycle
  and never twice in a row, with no lookback. The day turns over at a configured reset hour in a configured zone
  (`config/cobbletowers-trials.json`, default UTC 04:00). The seed is a stable hash of the trial id, so every attempt faces the same
  opponents. Enemy levels are locked by the trial through `RulesetResolver.locked`. A trial run ends on its floor limit
  (`isFinalFloor`), is not a cycle clear for mastery or the Clears board, and is judged by `TrialService.onTransition` before
  mastery drops its stats. The first run a player **starts** is their one scored attempt (spent only once the run really started,
  so a failed launch costs nothing); later runs are practice. Streak, freeze and milestone rules are pure and unit-tested; each
  milestone pays once, ever, in CobbleDollars. Daily boards are kept seven days and weekly ones eight weeks.
* **Tests:** 16 + 11 + 20 unit tests for the pure rules and files; live `playlist_test.py` 15/15 and `trial_test.py` 20/20 (the
  trial day is pinned by `/cobbletowers trialadmin day`).
* **Known limits.** The level lock fixes the *enemy*; players' own Pokemon still differ, so trials are only fully fair with
  the Rental Draft (next phase) or a level-capped playlist. A trial run's rewards use the normal tower reward tables (bounded, but a
  daily source). Scores are not yet shown on a screen (chat only).

## As built: P32c the event stream and Contracts, P32d the Run Report and login summary

* **Event stream** (`events.TowerEvent`, `TowerEvents`): `FloorCleared` (with the floor's own time and whether it was flawless),
  `BossDefeated`, `Purchased`, `Drafted`, `TrialFinished`, emitted where the runtime already computes them
  (`MasteryService` on a floor resolving, the vendor, the draft, the trial judge). A subscriber that throws is logged and
  skipped. Mastery and trials still read the run directly; moving them onto the stream is possible later but not needed.
* **Contracts** (`ContractTemplateDefinition`, 14 shipped: 10 daily, 4 weekly; the pure `ContractRules` and `ContractSchedule`;
  `TowerContractStore`; `ContractService`; `/tower contracts [reroll daily|weekly <slot>]`). A day draws three distinct daily
  templates and a week two weekly ones by the date, so everyone sees the same ones. The conditions are a small vocabulary over the
  stream (floors cleared with an optional time limit, no-faint, solo and depth constraints; bosses; purchases; severe drafts; trials
  finished). A completed contract pays its small bounded reward once (daily at most 100, weekly at most 250) and stops counting. One
  reroll per period moves only its own slot (the others and their progress are untouched) and forfeits that slot's progress.
* **Run Report** (`RunReport`, `RunSummaries`): at every run end a card goes to each participant in chat (outcome, floors, time,
  faints, best flawless streak, vendor purchases, modifiers, difficulty or trial score, achievements unlocked during the run, the
  streak line), is kept so `/tower report` shows it again, and `/tower report share` posts a one-line summary. No MVP Pokemon yet: that
  needs per-Pokemon knock-out tracking, which does not exist.
* **Login summary** (`LoginSummary`): today's daily trial and the player's state and streak, plus the open contracts, in a few lines on
  join; `/tower summary off|on`.
* **Tests:** 10 unit tests for contracts, 3 for the report; live `contract_test.py` (progress, one payment, boss and deep-dive
  contracts, reroll, a new day, the report, the summary switch).
* **Not done:** the trial, contracts and report as client screens (all chat for now), season points, titles and banners for streak
  milestones, and an MVP Pokemon in the report.
