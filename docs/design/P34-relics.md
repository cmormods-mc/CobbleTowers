# P34: Relics (run depth, part 1)

Roadmap item B1. Intermission Events (B2) are **not** in this phase; see "Not built".

## What a player sees
After each **milestone boss** (floors 5 and 10 of every tower, and again each Ascension cycle) the team settles the
ordinary modifier draft as before, and then a **relic draft** opens: three relics, voted on exactly like the modifier
draft (same screen, same tie-break, same "an open draft holds the floor shut"). The chosen relic is held for the rest of
the run. At most six are held; a run holding six is offered none.

## How it works (no new registry, no new screen)
* A relic is a `ModifierDefinition` file in `modifiers/` with `"relic": true` and optional `"tags": [...]`. It uses the
  ordinary `effect` block, so every delivery path that already exists carries it: level/boss/reward numbers,
  scouting, and the CUSTOM coded behaviors.
* `TowerContent.draftablePool` drops relics (they are never ordinary challenge cards); `relicPool()` returns them.
* `RunModifierState.relics` is a separate list, persisted under `relics` (a run saved before P34 reads as holding none, so
  no schema bump). Relics sum into `DraftService.effects` and `DraftService.customs` but never into `challengeCount`, so
  they do not bring a Lock-In closer.
* `PersistedDraft.relic` marks a relic draft. `DraftService.settle` chains it: settling an ordinary draft on a milestone
  floor immediately opens the relic draft (`relicCardsFor`: milestone floor only, room left, nothing already held, legal
  with what is held). If a milestone has no ordinary card to offer, `open` opens the relic draft directly.
* Draws are seeded from their own ordinal space (`RELIC_ORDINAL_BASE`) and own tie-break seed, like every other draw, so a
  run is reproducible and a shared seed shares its relics.
* A relic win does **not** emit the `Drafted` event: it is a boon, not a challenge, so contracts and mastery that count
  challenges are untouched.

## Content
Twelve relics ship: Healing Bell, Trader's Pouch, Swift Feather, Iron Hide, War Banner, Lucky Charm, Gambler's Coin,
Hunter's Compass, Titan's Bane, Giant Slayer, Veteran's Medal, Bounty Ledger. Three new coded behaviors back the battle
ones (`swift_start`, `iron_hide`, `war_banner`: one-stage boosts through the existing `boost` battle op).
Tags: sustain, economy, tempo, offense, scouting, boss, risk.

## Not built (deliberately)
* **Tag set bonuses** (2 and 4 of a tag): tags are stored and shown to content authors only. Next, once relic feel is known.
* **Behavior relics** from the roadmap (Metronome, Cracked Hourglass): they need new Showdown hooks, not data.
* **Intermission Events** (Shrine, Gambler, Forge, Rest): their own phase, P34b.
* **A relic row in the intermission screen**: relics are drafted on the existing draft cards; a held-relics list is a client
  change not yet made.

## Tests
Unit: relic pool exclusion, milestone-only offers, no repeats, the cap of six, effects sum, save/load and the pre-P34 read
(`DraftServiceTest`), shipped relics (`ShippedModifiersTest`), relic behaviors (`CustomEffectsTest`). Live:
`validation/smoke/relic_test.py` 6/6; `draft_test.py` 25/25 and `milestone_floor_test.py` 7/7 re-run clean. Not proven:
the relic cards rendered in a real client (they reuse the modifier draft screen, which has been screenshotted).
