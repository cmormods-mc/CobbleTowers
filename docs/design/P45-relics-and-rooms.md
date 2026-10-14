# P45: Relics in depth, and more event rooms

Follows P34 (relics) and P34b (intermission events). Owner decisions (2026-10-13): **more event rooms** (not branching doors), and **more relics plus tag set bonuses** (no new Showdown hooks).

## Relics

### Data-driven battle effects
A modifier's `effect` block may carry `"battle": [ ...ops ]`: the same P23 operations an armor set bonus carries (`boost`, `hp`, `status`, `weather`, `terrain`, `sidecondition`, `damage`, `resist`, `drain`, `suppress_items`), validated at load by `TowerBattleFx.validate`, so a bad operation rejects the file visibly. A modifier with battle ops is type `custom`. `CustomEffects.battleOps()` appends them, so they reach every battle exactly as `swift_start` and friends do. No new Showdown code: the existing hooks already give damage scaling by type, drain, type resistance, field and side conditions.

Card text for ops comes from `SetBonusDescriber.effectLine` (one place that words ops); a line is green or red by `ModifierMenuText.opTone`.

### Tag set bonuses
A modifier file may declare `"tag_bonus": {"tag": "sustain", "count": 2}`. It is never drafted (like a relic it is out of every pool) and never held: it **applies** while the run holds at least `count` relics carrying `tag`. It is an ordinary modifier otherwise (any effect, battle ops included), so a bonus is data. Two bonuses per tag ship: at 2 relics (small) and at 4 (large). Active bonuses count in effects, customs, the run view (`RunEffects`) and the "This run" menu, and never in `challengeCount` (a bonus is not a challenge). Cap of six relics stays, so a 4-set is reachable but costs most of the slots.

### Content
Twelve relics become thirty-eight (26 new, all JSON). Tags: sustain, economy, tempo, offense, scouting, boss, risk. New ones use battle ops (type-themed damage, drain, resist, tailwind, foe lead burned or slowed) and the existing numeric effects.

## Rooms
A room is still a two-option vote on the draft screen, seeded from the run, holding the floor shut until answered. New outcome field: `walletEach` (CobbleDollars each participant gains or pays); a paying room is only offered when every participant can afford it, and settling debits exactly once through `TowerWalletStore.debit`.

| Room | Option A | Option B |
|---|---|---|
| Forge (new) | **Pay `100 + 25 x floor` each**: gain a random battle relic (offense, tempo, sustain or boss tag) | Walk away |
| Library (new) | **Study**: gain a random scouting relic | Skip |
| Vault (new) | **Crack it**: 50% every player gains `150 + 25 x floor` CobbleDollars, 50% an ambush (a harsh challenge for the rest of the run) | Leave it |
| Mystic Pool (new) | **Dive**: swap one held relic for a different random one | Stay dry |
| Shrine, Gambler, Rest, Echo Duel | unchanged | |

Eligibility gates every room (the Forge needs relic room, a battle relic left to give and money; the Library needs relic room and an unheld scouting relic; the Vault needs a harsh challenge to charge on a bad roll; the Pool needs a held relic and another to swap for). Room choice among the eligible ones is **weighted** (constants in `IntermissionEvents`, untuned): Rest 30, Shrine 15, Gambler 15, Forge 15, Library 10, Vault 10, Pool 10. Frequency stays 50% of non-milestone floors; milestone floors keep paying a relic.

## Not built
Per-tower room weights, room art, a held-relics panel beyond the run view lines, relics that need new Showdown hooks, branching doors (declined by the owner).

## Tests
Unit: battle-op parse/validate/reject, ops reach `CustomEffects.battleOps`, tag bonus activation (below, at, above the threshold; counted in effects, not in challenges), each new room's eligibility, outcome and determinism, debit-once, shipped relics and bonuses parse and have lines. Live: `validation/smoke/event_test.py` and `relic_test.py` against the dev jar.

## As built (2026-10-13)
- `effect.battle` and `tag_bonus` are in `ModifierDefinition`; ops reach battles through `CustomEffects.ops` (`DraftService.customs` includes active set bonuses). `ModifierMenuText` words ops with `SetBonusDescriber.effectLine`, colours by `opTone`, and no longer claims a risk payout for a relic or set bonus (only held challenges pay one). `validation/validate_definitions.py` knows `battle` and `tag_bonus`.
- 26 new relics: offense 8 in all (Ember Brand, Tide Charm, Verdant Seed, Storm Coil, Keen Edge, Training Weights, Smoldering Torch, Tarnished Crown, plus Reckless Banner, Berserker's Mask, Vampiric Fang by tag), tempo (Gale Banner, Numbing Frost, Snare Wire, Pacemaker), sustain (Guardian Ward, Veil of Mist, Prism Shard, Vampiric Fang), boss (Siege Engine, Warden's Seal), scouting (Cartographer's Lens, Seer's Mirror, Oracle's Bone, Surveyor's Ledger), economy (Coin Purse, Golden Idol, Surveyor's Ledger). 14 set bonuses (`set_<tag>_<2|4>.json`). Every tag has at least four relics (a test pins it).
- Rooms: `IntermissionEvents` Forge, Library, Vault, Pool with `WEIGHTS`, `forgeCost`, `vaultPurse`, `labelAt` (cards say the real price); `roomFor`/`resolve`/`eligible` take a balance function; `DraftService.settleEvent` saves the run first, then debits or credits every participant once and checkpoints the wallet store.
- Tests: `TagBonusTest` (5), `ShippedModifiersTest` (counts, tag depth), `IntermissionEventsTest` (Forge, Library, Vault, Pool, reachability).

## Not proven
- Relic battle ops in a real battle, and the four rooms on a real client. The smoke rig `testserver-181` has no Polymer, and on `testserver-webgui` the smoke tests (`relic_test.py`, `event_test.py`) fail at floor 3 with Cobblemon's `BusyError` **on the previous commit too**, so they prove nothing about this change there. Needs a rig run or a human run.
- All numbers (weights, prices, op percentages, bonus sizes) are first guesses.
