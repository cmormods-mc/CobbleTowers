# P35 (part 2): Echoes and the Echo Duel

Roadmap D1, regional towers only (Tideforge, Rootvale, Duskvale).

## What a player sees
* A team that makes the **top ten** of a regional tower's Ascension, Speed or Difficulty board is **recorded automatically as an
  Echo**: up to six Pokemon (species, level, nature, ability, moves, held item) for each player of that run who is online. It
  is named by the same name the board already shows. `/tower echo` shows your Echoes and how many challengers met them and
  how many it beat; `/tower echo off` opts out for good and removes your Echoes from the pool at once; `/tower echo on` allows
  them again.
* An Echo that falls out of the top ten leaves the pool the next time that tower's boards change.
* At a **regional milestone** (floors 5 and 10), after the modifier draft and the relic draft, an optional **Echo Duel** room
  appears when someone else's Echo exists. Two options: face a champion's team, or decline.
* The duel is an **exhibition**: each fighter faces one Pokemon of an Echo (a different one for each teammate, picked from
  the run's seed) in a real Cobblemon battle, on a **cloned and fully healed copy of their party**, so nothing about it
  touches the real party and there is no penalty for losing. A win pays **100 CobbleDollars** (bounded: one duel room per
  regional milestone). The run cannot ready up, cash out or move on while a duel is open.

## Why a room, not a floor opponent
The first build put an Echo in about 15% of ordinary regional opponent slots. It was reverted: it changed what a run's
opponents are depending on a pool that differs between servers and changes over time, which breaks run codes (P35 part 1) and
makes every regional run slightly unrepeatable. As a room it is a bonus on the side: the run's own path stays a pure function
of its seed.

## How it works
* `echo/Echo`, `persistence/TowerEchoStore` (SavedData: Echoes and opt-outs, saved as plain strings and ids).
* `echo/EchoPolicy` (pure): which runs are in the top ten (`topRuns`), the seeded pick of an Echo and a Pokemon that is never
  one owned by someone on the run, level substitution.
* `echo/EchoService`: `refresh` after a board changes (records, then prunes), `duelAvailable`, `beginDuel`.
* `echo/EchoDuels`: the duels in progress, in memory only. A player who disconnects or whose battle ended is dropped on the next
  look so a vanished battle cannot hold a run shut. `RunTransitionService` cancels them when the run leaves the intermission.
* `IntermissionEvents.Room.ECHO_DUEL`: offered only at a milestone, only with an Echo to meet, never on an ordinary floor, never
  in a trial or on a tower that is not regional. `DraftService` opens it after the relic draft.
* `CobblemonBattleAdapter.startExhibition` binds the battle with `exhibition = true` (cloned, healed party, no floor effects);
  `TowerEncounters.onResolved` hands such a battle to `EchoDuels` and never to the floor logic.
* The opponent is built from the Echo's property string at the level the tower's own rules give an opponent on that floor, never
  the Echo's own level (TDS #45).

## Not built
* Per-Echo moderation beyond the opt-out, and a cap on stored Echoes beyond "top ten per board".
* Telling an Echo's owner when it wins (the count is kept and shown in `/tower echo`; an offline owner could not be told).
* An Echo of a rental team is recorded like any other.
* Aspects (regional forms) are not recorded; an Echo's Pokemon is its base form.

## Tests
Unit: `EchoPolicyTest` (top ten, pick, level, store, opt-out), `IntermissionEventsTest` (duel room rules). Live
`validation/smoke/echo_test.py` (a battle bot): the room opens at a regional milestone and never on an ordinary floor, choosing
to fight starts a real battle against the Echo's Pokemon, the duel ends and is recorded, the team can then move on, the Echo
counts the challenger, no floor slot is ever an Echo, and the player can see and opt out of their own. `echo_record_test.py` 5/5 plays a
full ten-floor Tideforge cycle (floors cleared by operator command): the run posts to the boards, an Echo of the player is recorded
automatically, visible with `/tower echo`, and an unrelated Echo whose run is not on the board is pruned at that refresh.
Regression re-run clean: relic 6/6, event 8/8, milestone_floor 7/7, intermission 9/9, floor_encounter 17/17, runcode 7/7.
Not proven: the real party being untouched by the duel (cloning is Cobblemon's own option and is used, but HP was not compared),
a lost duel, and the opt-out preventing a later recording (the opt-out removal is tested; the skip is a one-line check).
