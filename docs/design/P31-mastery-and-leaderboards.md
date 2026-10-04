# P31: Mastery and leaderboards

Status: **built** (see "As built" at the end). Designed first, per the project's design-gate habit. Sources: TDS #89 (regional mastery tracks clears, Ascension depth, difficulty and
special objectives, never awards jersey aspects) and #90 (leaderboards may track Ascension, difficulty score, speed,
solo/multiplayer and regional records, always recording ruleset/definition revision), plus the scoping answers below.

## Decisions (user, 2026-10-04)

| Question | Decision |
|---|---|
| What Mastery gives | **Ranks plus permanent small perks** |
| Objectives | Flawless clear, Speed clear, Lone wolf, Challenger, as **achievements: up to 30, each one adds a mastery level** |
| Leaderboards | **Four boards**, a command and an in-game screen (chat fallback when the client lacks it) |
| Difficulty score | Risk points of held modifiers, scaled by Ascension, plus a party-size handicap; one integer stored with the record |

## Model

**Mastery is per player and per tower** ("regional", TDS #89). A player has, for each tower, a set of unlocked achievements,
and their **mastery level in that tower is the number unlocked (0-30)**. The same 30 achievement definitions apply to every
tower; each tower tracks its own progress.

### A "cycle clear" is the unit everything is judged on

Clearing the last floor of a cycle (the run arriving at the cycle-end INTERMISSION, or COMPLETED on a tower that does not
ascend) produces a `CycleResult`:

| Field | Source |
|---|---|
| tower, ascension of the cycle just cleared | `TowerDefinition.ascensionOf` |
| participants (player ids and names), whether the run **started solo** | run + stats store |
| **active time** | the sum of this cycle's floor durations, from a floor opening to it resolving; intermissions (drafting, readying up) do not count |
| **flawless** | no player's Pokemon fainted during the cycle (counted from Cobblemon's `BATTLE_FAINTED` for run participants, which also covers boss battles) |
| severe modifiers held | the run's modifiers (drafted, forced, locked-in counted twice, as `DraftService.effects` does) |
| **difficulty score** | below |
| ruleset revision, tower revision and digest | pinned on the run (TDS #40, #90) |

### Difficulty score

`score = sum(risk points of each held modifier) + 5 * ascension + 5 * (4 - party size)` with minor 1, moderate 3, severe 6, and
custom modifiers by their own risk tier. Plain, transparent, and stored with every record so it can be re-derived. A solo run
at the base cycle holding nothing scores 15, four players with nothing score 0.

### The 30 achievements (data-driven)

JSON under `data/<ns>/cobbletowers/achievements/`, one file each. Two condition shapes:

* `clear`: a **single cycle clear** meeting every constraint given: `min_ascension`, `flawless`, `solo`, `max_seconds`,
  `min_severe`, `min_score`.
* `total`: a lifetime figure in this tower: `cycles_cleared` or `ascension_reached`.

| Group | Achievements (6 + 6 + 4 + 4 + 4 + 4 + 2) |
|---|---|
| Clears (6) | First Ascent (1 cycle), Regular (3), Veteran (10), Warden (25), Paragon (50), Eternal (100) |
| Depth (6) | reach Ascension 1, 3, 5, 10, 15, 20 |
| Flawless (4) | flawless clear at Ascension 0, 3, 6, 10 |
| Speed (4) | base-cycle clear in under 25, 15, 10, 7 minutes of active time |
| Lone wolf (4) | solo clear at Ascension 0, 3, 6; solo flawless clear |
| Challenger (4) | clear holding 2, 4, 6 severe modifiers; clear at Ascension 5 holding 3 |
| Difficulty (2) | clear with a difficulty score of at least 40, 100 |

Unlocking one is announced to the player (title and message), is permanent, and moves the level by one. Thresholds are first
guesses and live in the JSON, not in code.

### Ranks and perks (`MasteryPerks`, pure)

| Level | Rank | New perk (cumulative, applied only in runs of that tower) |
|---|---|---|
| 0 | Unranked | none |
| 1-4 | Bronze | none |
| 5-9 | Silver | vendor prices -3% |
| 10-14 | Gold | +5% CobbleDollar income |
| 15-19 | Platinum | vendor prices -6% (total) |
| 20-24 | Diamond | +10% CobbleDollar income (total) |
| 25-29 | Master | +10% Raid Points |
| 30 | Champion | vendor prices -10%, +15% CobbleDollars; a Champion title |

All per player at the point of use (vendor price, wallet credit, Raid Points), so a team's mix of masteries never has to be
averaged. No stipends or anything repeatable on run start (it could be farmed), and never a jersey aspect (#89).

## Leaderboards

`TowerLeaderboardStore` (SavedData) keeps, per `(board, tower, mode)`, the top 50 entries. Mode is **solo** or **team**
(the run started with one player or more); the Clears board is per individual and has no mode.

| Board | Value | Submitted |
|---|---|---|
| Ascension | deepest Ascension reached in a run (higher wins) | on entering each Ascension and when the run ends |
| Speed | fastest base-cycle clear, active time (lower wins) | at a cycle clear at Ascension 0 |
| Difficulty | highest difficulty score of a cleared cycle | at every cycle clear |
| Clears | cycles cleared in this tower, lifetime | at every cycle clear |

Each entry stores the players (id and the name at that moment), the value, run id, Ascension, **ruleset revision, tower
revision and digest** (#90), and the time. One run holds at most one entry per board (a better value replaces its own).

## Where it hooks in

* `RunTransitionService.apply`, after a move is saved: arriving at ENCOUNTER_ACTIVE starts a floor timer, arriving at
  FLOOR_RESOLVING stops it; from FLOOR_RESOLVING into the cycle-end INTERMISSION (or COMPLETED) is a cycle clear; a terminal
  state ends the run. `AscensionService` already runs on entering an Ascension and submits the depth.
* `TowerRunStatsStore` (SavedData, keyed by run, pruned when the run is retired): start size, this cycle's active time,
  faints, and the floor timer. Persisted so a crash costs at most the floor in progress; no change to `PersistedRun`.
* Perks: `ArmorBonusEffects.vendorPrice` (beside armor and Black Market), the wallet credit path, `raidPoints`.
* `/tower mastery [tower]` and `/tower leaderboard [board] [tower]`, each with a screen (new payloads and two client screens)
  and a chat fallback. Operator seam: `/cobbletowers mastery grant|reset <player> <tower> [achievement]` for tests.

## Not in this phase

Per-Ascension speed boards, seasonal resets, a physical leaderboard in the world, mastery for Pokemon rather than players,
and anything awarded for rank beyond the perks above. Perk numbers and achievement thresholds are first guesses.

## Test plan

Unit: difficulty score, each condition shape, evaluator (cumulative and single-clear), rank and perk ladders, leaderboard
ordering/tie-breaks/capping/replacement, JSON of all 30 achievements. Live (the four-floor test tower, one player): a clear
unlocks the expected achievements and announces them, the level and perks follow, the boards gain entries with revisions, a
second player appears in team mode, and the vendor price drops once the level reaches 5. Screens are not seen in a real client.

## As built

* **Pure rules** (`com.cobbletowers.mastery`): `DifficultyScore`, `MasteryEvaluator`, `MasteryPerks`, `LeaderboardRules`,
  `MasteryView` (words), `CycleResult`. 20 unit tests, including that all 30 shipped achievements parse, are reachable by
  some clear, and that a perfect deep solo clear unlocks all 30.
* **Achievements** are `AchievementDefinition` JSON under `achievements/`, loaded by their own `AchievementRegistry` reload
  listener (they reference nothing else, so they are not threaded through `TowerContent`). `validate_definitions.py` checks them.
* **Stores** (all `SavedData`, none touching `PersistedRun`): `TowerMasteryStore` (cycles, depth, unlocked achievements, per
  player and tower), `TowerRunStatsStore` (start size, this cycle's active time and faints; dropped when the run ends),
  `TowerLeaderboardStore` (top 50 per board, tower and mode, each entry with ruleset revision, tower revision and digest).
* **Hooks:** `MasteryService.onTransition` is called from `RunTransitionService.apply` (floor timer on ENCOUNTER_ACTIVE and
  FLOOR_RESOLVING; a cycle clear is FLOOR_RESOLVING into the cycle-end INTERMISSION or COMPLETED; a terminal state ends the run),
  `AscensionService` calls `onAscensionEntered`, and a `BATTLE_FAINTED` listener counts player faints for flawless.
  Everything is guarded: a mastery failure is logged and never disturbs a run.
* **Perks** are read per player at the point of use: `ArmorBonusEffects.vendorPrice` (beside armor and Black Market),
  `RewardDelivery` (CobbleDollar credit and Raid Points, using the run the reward was earned in).
* **Commands:** `/tower mastery [tower]` and `/tower leaderboard <ascension|speed|difficulty|clears> [tower]`; for a client that
  has the screen they open `MasteryScreen` (new `MasteryScreenPayload` / `MasteryRequestPayload`), otherwise they answer in chat.
  Operator tools: `/cobbletowers masteryadmin grant|reset|clearboards`.
* **Live:** `mastery_test.py` 19/19 on the four-floor test tower: a solo cycle clear is recorded and unlocks the eight
  achievements it meets (Silver), the 3% vendor perk is really charged (Full Heal 25 -> 24), the speed/difficulty/clears boards
  hold the run with its revisions, ascending adds the Ascension board entry and the depth achievement, a two-player run is ranked
  under Team with both names and not mixed into Solo, and the operator reset and board clear work. Codec round-trips for the new
  payloads (and the Ascension picker nested in the play state) are unit-tested.
* **Not proven:** the screen itself (headless bots cannot open one), faints actually counted in a live battle (so "flawless" is
  proven true, not proven to fail on a faint), and the CobbleDollar and Raid Points perks through a real reward delivery (the
  vendor perk is the live-checked one). Thresholds and perk numbers are first guesses; the speed thresholds in particular have no
  real-play data behind them.
