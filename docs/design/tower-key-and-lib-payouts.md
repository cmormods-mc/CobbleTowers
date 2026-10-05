# Tower key and AscensionLib payouts (2026-10-05)

Decided with the owner; numbers on the library side are provisional (see AscensionLib's `docs/BUILD-STATUS.md`).

## AscensionLib payouts

A cleared floor pays in AscensionLib's shared wallet, outside the run's unclaimed pool (so, unlike the pool, a milestone's
wallet pay is not forfeited if the run is lost later). `TowerEncounters.payAscensionLib` runs on every boss victory:

- every floor: a Scouter roll per player (5%, 15% when the run has drafted a scouting bonus);
- a milestone floor (F5 and F10 of each cycle): the library's bands for the floors since the previous milestone.

Everyone who is still a member of the run is paid, knocked out or offline included: the boss is a team fight. A player who
left the run is not. Trials are skipped for now; they will get rewards tied to their difficulty.

The library pays each (encounter, player, kind) once, so a settlement can always be repeated. `AscensionLibRewards`
therefore writes each settlement to disk (`TowerLibSettlementStore`) before asking, trims it to the players the library has
not confirmed, and retries at server start and every 30 seconds, for up to a week, when the library is disabled, the wallet
refuses, or the call throws. The library is reached by reflection, so it stays optional.

## Tower key

`cobbletowers:tower_key` is an ordinary item (stackable, any source can hand it out by id: the daily vote crate, a Raids shop
entry, `/give`). An ordinary run (a new run, a run code, an Ascension start) costs **one key per player**, each from their
own inventory. Trials and rental-draft runs cost none, and resuming a parked run is not a start.

The lobby refuses to start ("X needs a Tower Key") if anyone lacks one when the countdown ends. Keys are taken only after the
run has really started, so a launch that fails costs nothing.

**Off by default.** `config/cobbletowers-keys.json` with `{"required": true}` turns it on; a missing, empty or malformed file
means not required. Turn it on once the key has a source.

## Scouting (Scouters)

Towers tells AscensionLib about the enemies a floor puts up (`AscensionLibScouting`, by reflection, fire and forget), so a
player can spend a Scouter on one. A floor's opponents are separate battles, so each is its own encounter
(`<run>-f<floor>-o<ordinal>`, ended when that battle is) with a **personal** reveal. The floor's boss is CobbleRaids'
encounter (its id is the boss encounter's) with a reveal **shared with every player still standing**; the boss's species is
read from its raid definition (`RaidSpecies`, `data/<ns>/raids/<name>.json`), and a definition without one cannot be scouted.

Tiers (`ScoutingTiers`): floors 1-4 `trial_rank_1`, 5-9 `trial_rank_2`, 10 and up (every later cycle included) `trial_rank_3`;
a milestone boss (F5, F10 of each cycle) `boss`. No boss carries a Unique yet. Players open the screen with the AscensionLib
keybind (default J) or `/ascend scout`. What is shown is generated from the encounter and is **not applied in battle**: combat
effects do not exist yet.

## Ascension effects in battle

Every opponent and boss is declared to AscensionLib and the next battle armed with it **before** the battle starts
(`AscensionLibScouting.armed`; the boss through the `beforeStart` hook of `TowerBossAdapter.start`, because its encounter id only
exists inside `start`), so the enemy that was scouted is the one whose rarity and modifiers act in that battle. An Echo duel is
armed as explicitly native. Without AscensionLib nothing changes. The effects themselves run in AscensionLib's Showdown module.
