# Region rules, reward tuning and the F5/F10 chase items: options for the owner

Status (2026-10-08): nothing here is built. It needs your picks; each option is a data-file edit, no code, and `validate_definitions.py` plus the soaks in
`validation/smoke/battle_tower_test.py` check it.

## Where things stand

* All four towers use the one ruleset `cobbletowers:standard` (`enemy_level` 5 to 100, party of 6, health and PP carry over, `item_action_budget` 0). The regions
  differ in encounter pools, boss pools, themed reward tables and armor sets, not in rules.
* The four reward tables share the same shape: `growth_percent_per_floor` 8, three tiers, and an F5 `boss` and F10 `champion` milestone each with 1 XL candy, a pack, Raid Points
  (25 at F5, 75 at F10) and a bonus pool. The themed part is item flavour (berries, balls, stones, held items).
* F5 chase: a region-matched booster pack (water, grass, dark; plain for Neutral). F10 chase: `cobblemon-cards:god_pack_ticket` in **every** region. Both are the
  placeholders from P21 ("to be determined").
* Each region has an armor set (`tideforge`, `rootvale`, `duskvale`, plus `challenger`) whose pieces drop from that region's tables.

## 1. F10 chase item

| Option | What | Effect |
|---|---|---|
| A. Keep the God Pack Ticket everywhere | no change | one chase for the whole mod; regions feel the same at the top |
| B. A region armor piece as the F10 guarantee | the champion table's `guaranteed` list gets one piece of that region's set (a fixed piece, or one chosen by the run seed) | a full set takes four F10 clears of one region: a reason to return to a region; no more random-drop luck |
| C. Ticket guaranteed, one armor piece as a high-weight bonus | keep A, add a piece with weight ~40 to the champion bonus pool | softer than B; the set stays a chase |
| D. Region-specific cards | a cobblemon-cards pack per region at F10 | needs the pack ids on your server |

My lean: **C**, so the F10 payout is a sure thing plus a reason to repeat the tower. Say which, and which piece order for B.

## 2. Per-region rules

Today a region is only a theme. Cheap ways to make each one play differently, all fields the ruleset already has (add `rulesets/tideforge.json` etc. and point the tower at it):

| Region | Idea | Fields |
|---|---|---|
| Tideforge | Endurance: no PP carry-over between floors (floors are fresh but the party is drained by the boss) | `carryover.pp` false |
| Rootvale | Gentle: lower enemy ceiling, health carries over | `enemy_level.max` 80 |
| Duskvale | Harsh: enemies start higher, health does not carry over | `enemy_level.min` 30, `carryover.health` false |
| Neutral | unchanged `standard` | none |

These change difficulty, so pick deliberately; `docs/design/tuning-with-real-data.md` and `masteryadmin counters` (floor time, risk bonus, claim rates) are how to see whether a region is too hard once players are in it.
`item_action_budget` is still not enforced, so it is not an option here.

## 3. Reward balance (nothing to pick yet)

The tables are untuned guesses. With no player data the safe move is to leave amounts alone and read `masteryadmin counters` after a week. One thing worth deciding now: the F5 and F10 `raid_points`
(25 and 75) feed CobbleRaids' own economy; if that mod's points are scarce on your server these will feel large.

## 4. What was verified

`validation/smoke/battle_tower_test.py --tower <t> --floors 10` plays a whole tower with the bot (one strong lead, five level-1 fillers), 2026-10-08, build 0.22.1:

| Tower | Result |
|---|---|
| Rootvale | all 10 floors cleared, 44/44 checks |
| Duskvale | all 10 floors cleared, 44/44 checks |
| Neutral | wiped by the F5 boss (Cobalion at level 33) twice; in a later 5-floor run the F5 Cobalion was level 23 and was cleared. The old build (before the session's slicing work) also cleared it at level 28. |
| Tideforge | wiped at floor 7 once; the second attempt hit the harness's own time limit, so it has no clean full-clear yet |

The wipes track how high the bosses were drawn that run (19 to 33 at the same floors), not the cell work; the bot is one lead against legendary raid bosses, so a high draw beats it.
Neutral and Tideforge want a rerun with a stronger party before anyone reads a wipe as a content fault. "Cobblemon refused a tower battle ... BusyError" appears on floors 2 to 5
in the old build too: a second wave of opponents is skipped when the first battle has not finished closing. It costs that wave's rewards, never the run; worth its own look.
