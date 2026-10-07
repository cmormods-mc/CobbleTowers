# Tuning with real play data

Several numbers in the mod were set before anyone had played: they are first guesses. This is where each one lives, what to look
at once real players have been through it, and the tools that exist. Nothing here changes by itself; every knob is a data file or
a named constant, and every change should be a deliberate edit with a test.

## The tool that reads real data

`/cobbletowers masteryadmin report` (operator, permission 2) prints, from what the server has actually stored:

* per tower: how many players have a record, the spread of **mastery levels**, **cycles cleared** and the **deepest Ascension**;
* every achievement with the share of players who hold it, most held first, and, once at least five players have a record, a flag on
  any achievement **nobody** holds (too hard, or unreachable) and any **90% or more** hold (too easy);
* every trial instance with attempts, how many finished, and the spread of floors cleared and score;
* every leaderboard with its min, median and max.

Below five players the report says its rates are anecdotes. It reads only; it is `mastery/TuningReport` (pure, tested).

## The knobs

| What | Where | Look at | A reasonable first move |
|---|---|---|---|
| Achievement thresholds (speed, flawless, solo, severe modifiers) | `data/.../achievements/*.json` (`max_seconds`, `min_score`, `min_severe`, `min_ascension`, `count`) | the achievement table in the report | an achievement nobody holds after a week: loosen one step; one 90% hold: tighten or retire |
| Mastery levels, ranks, perks and claimable rewards | `data/.../mastery_tracks/*.json` (shipped `default.json`; addons and `config/cobbletowers-tracks.json` merge on top, see `docs/TRACKS-GUIDE.md`) | level spread, and how many players have claimed each level's reward | levels bunched at the top mean the perks arrive too early; rewards nobody claims are not worth their place on the track |
| Risk payout bonus | `reward/RiskReward` (`MINOR` 3, `MODERATE` 8, `SEVERE` 15, percent per held modifier, final payout only) | the average bonus actually paid, and how often runs hold severe modifiers (add a counter) | if players take no severe modifiers, raise `SEVERE`; if every run is stacked with them, lower it or cap the sum |
| Season track | `data/.../season_tracks/*.json` (`step_cost`, steps, plus `add_steps` from addons) | steps reached per player at season end, claims per step | a track almost nobody finishes: lower `step_cost`; everyone finishing in week 2: raise it |
| Trial scoring | `trial/TrialScoring` (`PER_FLOOR`, `COMPLETION_BONUS`, `PAR_MILLIS_PER_FLOOR`, `FAINT_PENALTY`, `PER_DIFFICULTY_POINT`) | score spread per trial | if everyone who finishes scores within a few percent, speed or faints are not mattering: widen the par or the penalty |
| Trial pool | `data/.../trial_pools/*.json` | attempts and finished per instance | an instance few finish is too hard for a daily |
| Ascension growth | `ascension/AscensionPolicy` (`ENEMY_EVS_PER_ASCENSION`, `BOON_PERCENT_OF_ENEMY`, `ASCENSIONS_PER_EXTRA_OPPONENT`, reward caps) | deepest Ascension histogram | rerun `validation/showdown/ascension_sim.js` with the constants before changing them |
| Rental sets | `data/.../rental_sets/*.json` | `validation/showdown/rental_sim.js` | a set well above or below its rarity tier: change its item, moves or EVs and rerun |
| Contracts | `data/.../contract_templates/*.json` | how often each is completed (add a counter if this matters) | |

## Simulation, until there is data

`ascension_sim.js` and `rental_sim.js` play real Showdown battles with the real `tower-fx.js` and rank settings against each other.
Their greedy player under-rates support Pokemon and never sets up, so they say **which of two settings is harder**, not how a
particular player will do. Use them to keep a change from being a surprise; use the report to learn whether the change was needed.

## What has no data source yet

Contract completion rates, how often each modifier is drafted, and how long a floor takes are not stored per run in a form the
report can read. Add a counter (a small `SavedData` map keyed by id) when one of them becomes the question; the report is the place
to print it.

Also missing since P37, and worth adding before a public release so the first week of play answers the balance questions:

| Counter | Where it would be recorded | Answers |
|---|---|---|
| Track claims per node (mastery level, season step), and how many nodes were reached but never claimed | `MasteryClaims.claim`, `SeasonProgressService.claim` | are rewards wanted; do players find the claim button; what a lapsed season costs them |
| The risk bonus paid on each final payout, and the count of minor/moderate/severe modifiers held | `RewardBankService.bank` (final payout branch) | whether the +3/+8/+15% bonus moves behaviour |
| Season points per player per week | `SeasonProgressService.award` | whether `step_cost` fits the 42-day season |
| Cell prepare/release time, longest single stall, queue depth | `TowerMetrics.recordAllocation/recordCleanup` (extend) | whether the allocation work needs `docs/design/cell-allocation-async.md` |

Keep each as a small `SavedData` map and print it in the `masteryadmin report`.
