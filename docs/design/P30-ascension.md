# P30: Infinite / Ascension mode

Status: **built** (see "As built" at the end). Designed first, per the project's design-gate habit. Sources: TDS #9, #15,
#24, #45, #76, #89, #90 (`Cobblemon_Battle_Tower_v1_Technical_Design_Specification.md`), and the scoping answers below.

## Goal

A ten-floor run ends in a choice instead of an ending: cash out, or **Ascend** into another ten-floor cycle that is harder and
pays better, forever. Depth is the score. A team can also start directly at an Ascension everyone in it has already reached.

## Decisions (user, 2026-10-04)

| Question | Decision |
|---|---|
| Entry | **Both**: ascend from a normal run after floor 10, and start directly at a reached Ascension |
| Direct-start gate | Everyone in the team must have reached it |
| What grows | Boss health + extra opponents; **one forced modifier per Ascension**; **over-the-cap EVs** for infinite scaling |
| EV target | Opponents and bosses, **plus a matching boon for players** (battle-only, never written to a Pokemon) |
| Rewards | Diminishing growth with a hard cap (TDS #9) |
| Failure | Keep what was banked at the last boundary; the unclaimed pool since is forfeited (same as normal mode) |

## Design

### 1. Depth is derived, not stored

`run.floorIndex` keeps counting up (11, 12, ...), so the ledger, banking keys and `lastBankedFloor` need no change.
Two pure functions in a new `AscensionPolicy` (the one place this maths lives, like `TowerLevelPolicy`, TDS #45):

* `ascensionOf(floorIndex, floorCount)` = `(floorIndex - 1) / floorCount` (0 is the base cycle)
* `towerFloorOf(floorIndex, floorCount)` = `(floorIndex - 1) % floorCount + 1`

Every content lookup (`floorAt`, `milestoneAt`, layouts, pools, boss pools, `rebuildIfStructureChanged`) goes through
`towerFloorOf`, so floor 11 is floor 1's building again and floors 15 and 20 are the milestones again. **No schema change** for
in-run depth. A direct start simply creates the run at floor `A * floorCount + 1`.

### 2. The Ascend decision is the existing intermission

Today clearing the last floor goes `FLOOR_RESOLVING -> COMPLETED`. For a tower that opts in (`"ascension": true` in the tower
JSON, default false so nothing existing changes), the last floor of a cycle goes to **INTERMISSION** like any milestone floor:
the cycle's F10 reward banks (it already `banksRewards`), the modifier draft opens, and the team votes. Cash out = the existing
strict-majority `CASH_OUT_CHOSEN`; everyone ready = ascend (the existing `openNextFloor`). `COMPLETED` stays for non-ascending
towers. The intermission screen only needs different wording at a cycle boundary ("Ascend to N / Cash out"), no new payload.

### 3. What an Ascension A adds (all in `AscensionPolicy`, all pure)

| Growth | Rule (first numbers, tune later) | Delivered through |
|---|---|---|
| Boss health | `+12% * A` (linear, uncapped) | existing `boss_health_percent` path (`ModifierEffects`) |
| Extra opponents | `+1 per 3 Ascensions`, capped at +3 | existing `extra_opponents` path |
| Forced modifier | one per Ascension, drawn by seed from what the run is eligible for, not refusable | appended to `run.modifiers().accumulated()` on entering Ascension A |
| Over-cap EVs (enemy) | `+50 EVs per stat per Ascension` (`+12` stat points at level 100), at most 4000 per stat | new tower-fx op, see 4 (tuned, see "Tuning") |
| Boon (players) | the same op on the player side at **half** the enemy amount (`+25` per Ascension) | merged into battle ops beside armor and `CustomEffects` |
| Reward | `100 + 150 * (1 - 0.90^A)` percent: x1.15 at A1, x1.61 at A5, x2.27 at A18, never past x2.5 | one more factor in `RewardValuation` |

Level keeps using `TowerLevelPolicy` unchanged: the ruleset's max enemy level is the ceiling, and EVs are what keep scaling
past it. Guaranteed milestone items repeat each cycle (linear in effort, bounded per unit of play); revisit if inflation shows.

A direct start at A applies the A forced modifiers at creation, drawn one after another from the same seed, stopping early if
the eligible pool runs dry (stack limits and exclusions make that a real ceiling for modifiers; EVs and health are the
unbounded part).

### 4. Over-the-cap EVs (the one risky piece)

Cobblemon's `EVs` refuses values past 252 per stat and 510 total (`canSet`), so they can never be written to a Pokemon. They
are applied **inside the battle** instead, by a new `evs` operation in `tower-fx.js` (the P23 extension), validated in Java and
JS like the others: `{op: "evs", side, amount, stats: [..]|"all"}`. Showdown's own stat formula does not cap EVs
(`floor(ev/4)` inside `spreadModify`), so the op raises `pokemon.set.evs` and recomputes `baseStoredStats`/`storedStats` and
the HP stat before the first turn. Nothing on a player's real Pokemon changes, so nothing leaks out of the tower.
**Spike first** (the P23 lesson): prove on a live battle that recomputed stats take effect, for a floor opponent and for a
CobbleRaids boss, before building around it.

### 5. Records and the direct-start gate

New `TowerAscensionStore` (`SavedData`, `cobbletowers_ascension.dat`, persisted-state validator applies): highest Ascension
gate reached per `(player, tower)`, written when a team **enters** Ascension A (i.e. at the gate vote that ascends), for every
participant. `/cobbletowers play tower <id> ascension <n>` and a selector on the play screen offer `n` up to the lowest record in
the party; the lobby re-checks at start. Mastery and leaderboards (TDS #89/#90) are not built here, but this store is their
natural source (ruleset and definition revision are already on every run).

### 6. Failure

Unchanged: a wipe ends the run, banked rewards stay, the pool since the last bank is forfeited. Depth reached is already
visible as the run's last floor; the record only moves up at a gate.

## Touch list

`AscensionPolicy` (new, pure) · `TowerDefinition` (`ascension` flag, `floorCount` already exists) · `TowerContent` lookups ·
`TowerEncounters.isFinalFloor` · `RunLifecycle`/`RunTransitions` (last floor -> INTERMISSION when ascending) ·
`RewardValuation`/`RewardBankService` (reward factor) · `DraftService` (forced modifier on entering a cycle) ·
`tower-fx.js` + `TowerBattleFx` (`evs` op) · both battle adapters (enemy and boon ops) · `TowerAscensionStore` ·
`LobbyService`/`PlayActionPayload`/`PlayScreen` (choose Ascension) · `RunsCommand show` (print Ascension) · validators.

## Test plan

* Unit: `AscensionPolicy` (mapping both ways, every growth rule, reward curve monotone and under the cap), forced-modifier
  draw determinism and early stop, record store round-trip, gate = lowest record.
* Live: the 4-floor `cobbletowers:test` tower makes a cycle cheap: clear floor 4 by operator event, expect INTERMISSION not
  COMPLETED, ascend, expect floor 5 to use floor 1's building, a forced modifier held, reward factor applied; then a direct start
  gated by records; then a real battle spike for the `evs` op.
* Regression: `milestone_floor_test`, `intermission_test` and the reward tests, because the end of a normal run moves for
  towers that opt in.

## Open points for review

1. **Normal-mode ending changes** for opted-in towers: no automatic `COMPLETED` at floor 10, the team votes instead. I propose
   opting in all five shipped towers. Say if the Battle/regional towers should stay finite.
2. **The player boon is automatic** (half the enemy's EVs). Alternative: a vote for one of three boons at each gate.
3. **Forced modifier pool**: any eligible modifier including custom rare ones, or only severe/moderate? Proposed: any except
   boons-only cards (Fragile Champion and similar), so growth is never softened.
4. **Constants** above are first guesses; no pacing data exists for a long run yet.
5. **Not in this phase**: the expanded Ascension-tagged regional pool (TDS #76; needs roster authoring), mastery tracks,
   leaderboards, configurable preparation pressure (TDS #21).

## As built

Open points were taken at their proposed defaults when the user said "proceed": (1) all five shipped towers opt in
(`"ascension": true`), so a normal run no longer auto-completes at its last floor, the team votes; (2) the player boon is
automatic at half the enemy amount; (3) forced modifiers are ENEMY / ENCOUNTER / FIELD / PLAYER_CONSTRAINT above the minor
tier only (`ForcedModifiers.forcible`): no reward, scouting or custom modifier, nothing that softens; (4) constants as in the table.

* **EV spike result: works.** `tower-fx.js` gained a pre-start phase (`PRE_START`, run before `Battle.start` so the first
  switch-in line already reports the raised max HP) and an `evs` operation (`{side, amount 1-2000, stat?}`, a stat capped at
  4000 EVs in total), validated in Java (`TowerBattleFx`) and JS. It raises `pokemon.set.evs`, recomputes
  `baseStoredStats`/`storedStats`/HP with the simulator's own `spreadModify`, and keeps HP at the same fraction. 6 new harness
  checks (29/29 with the old ones) including "a stronger Pokemon really deals more damage". Live (`ev_scaling_test.py`): it
  reached a real floor battle and a real CobbleRaids boss battle (the boss side too). Caveat: a boss's HP pool is CobbleRaids'
  own (scaled by `boss_health_percent`), so EVs raise the boss's stats rather than its pool.
* **No schema change.** Depth is `AscensionPolicy` over `floorIndex`; `TowerContent.floorAt`/`milestoneAt` map through
  `TowerDefinition.contentFloor`, so every existing lookup cycles without being touched. A direct start creates the run on
  floor `A * floorCount + 1` with the forced modifiers already in `RunModifierState`.
* **The decision is the intermission.** For an ascending tower `isFinalFloor` is false and `RewardBankService.bankPoint` banks
  at every cycle end; the intermission broadcasts "Cycle complete! Cash out now, or ready up to ascend...". The operator
  `final_floor_cleared` event still reaches COMPLETED (the table is unchanged), so older tests that walk it still pass.
* **Reward growth is bounded in both directions.** The Ascension factor is the asymptotic curve; the table's own per-floor
  growth is counted within a cycle (`RewardValuation` takes the cycle length), otherwise floor 31 would have grown it 31 times.
* **Forced modifier + record** are applied by `AscensionService.onFloorConfirmed`, called from `RunTransitionService.apply`
  after `NEXT_FLOOR_CONFIRMED`. Known gap: a crash between that checkpoint and the follow-up save loses the forced modifier for
  that cycle (it is never applied twice). A direct start has no such window.
* **Records** (`TowerAscensionStore`) move up when a team enters an Ascension; `/cobbletowers play ascension <n>` and a button on
  the play screen (host only) offer up to the lowest record in the team; the launch re-checks. `Lobby` gained a nested `Depth`
  because the codec is at the six-field limit.
* **Live:** `ascension_test.py` 13/13 (refusal before any record, cycle end reaches INTERMISSION and banks, ascend to floor 5,
  forced modifier, records, `ascension 2` refused, direct start on floor 5 with its forced modifier, floor-5 battle raised the
  enemy +20 and the player +10). Operator-cleared floors leave the floor-1 battle running, which refuses every later floor's
  battle ("already in battle"), so the test reads the EVs from the direct-start run's own first battle.
* **Not done:** the expanded Ascension-tagged regional pools (TDS #76), mastery/leaderboards (#89/#90), preparation pressure
  (#21), a screen that shows the Ascension on the HUD, and any tuning of the constants. The play-screen button and the cycle-end
  wording are not seen in a real client.

## Tuning (2026-10-04)

`validation/showdown/ascension_sim.js` plays real Showdown battles with the real `tower-fx.js`: the player's six against
`1 + extra` enemies in a row (one battle models one floor, health carrying over like the waves do). The enemy is built like a
wild Cobblemon (last four level-up moves, no EVs, IV 15) and picks moves at random; the player is a greedy damage picker that
never sets up or switches voluntarily, at three training tiers (casual 0 EVs, mid 85 each, trained 252/252/4), level 100 (the
worst case: enemy levels cap at 100, so EVs are the only scaling left). It is a **relative** yardstick, not a prediction.
Not modelled: bosses, forced modifiers, items, healing between floors.

Floor win rate by Ascension (Tideforge pool, 100 floors per cell; HP left in brackets is for wins):

| Constants | A5 | A10 | A15 | A20 | A25 | A30 | A40 |
|---|---|---|---|---|---|---|---|
| +20 EVs, boon 50% (first guess), trained | 100 | 100 | 100 | 100 | 98 | 94 | 86 |
| +50 EVs, boon 25%, trained | 100 | 86 | 69 | 31 | 13 | 4 | 3 |
| +80 EVs, boon 25%, trained | 100 | 49 | 14 | 8 | 1 | 1 | 3 |
| **+50 EVs, boon 50% (chosen)**, casual | 100 | 88 | 76 | 64 | 46 | 30 | 23 |
| **chosen**, mid | 100 | 94 | 92 | 87 | 62 | 38 | 21 |
| **chosen**, trained | 100 | 97 | 90 | 86 | 66 | 37 | 9 |

The first guess was far too gentle: a trained team still won 86% of floors at Ascension 40 (400 floors in). The chosen
constants put the wall (floors going from comfortable to a coin flip) around Ascension 20-25, earlier for an untrained party,
and keep climbing past it. The boon matters as much as the enemy's growth: halving it (25%) moved the wall from about 25 to
about 13. Reward retention moved from 0.85 to 0.90 so the factor is still rising across the range a strong team plays
(x2.27 at A18) instead of flat by A12. The EV total now reaches a stat's 4000 limit at Ascension 80 (sent as operations of at
most 2000) instead of stalling at Ascension 40.

Things to know: the Neutral pool (Machoke, Haunter, Lairon...) is far weaker than Tideforge's, so Neutral is still beatable
at 70-90% per floor at Ascension 40; a stronger Neutral pool, not a steeper curve, is the fix if that matters. A floor
win rate of 90% still means a ten-floor cycle succeeds about a third of the time, so "wall" is meant loosely. Boss growth
(+12% pool per Ascension, plus the same EVs) is untested beyond reasoning and likely the first wall in practice.
