# Tower key and AscensionLib payouts (2026-10-05)

Decided with the owner; numbers on the library side are provisional (see AscensionLib's `docs/BUILD-STATUS.md`).

## AscensionLib payouts

A cleared floor pays in AscensionLib's shared wallet, outside the run's unclaimed pool (so, unlike the pool, a milestone's
wallet pay is not forfeited if the run is lost later). `TowerEncounters.payAscensionLib` runs on every boss victory:

- every floor: a Scouter roll per player (5%, 15% when the run has drafted a scouting bonus);
- a milestone floor (F5 and F10 of each cycle): the library's bands for the floors since the previous milestone.

Everyone who is still a member of the run is paid, knocked out or offline included: the boss is a team fight. A player who
left the run is not. A Trial (a floor-limited run) pays once, when its last floor is cleared, from the library's trial bands at the rank its length
implies (1-4 floors rank 1, 5-9 rank 2, 10+ rank 3): 6 / 10+1 facet / 16+2 facets, no Scouter rolls, Cores or fragments, and no key.
There is no daily budget yet, so a live test must show whether trials out-earn the tower before one is added.

The library pays each (encounter, player, kind) once, so a settlement can always be repeated. `AscensionLibRewards`
therefore writes each settlement to disk (`TowerLibSettlementStore`) before asking, trims it to the players the library has
not confirmed, and retries at server start and every 30 seconds, for up to a week, when the library is disabled, the wallet
refuses, or the call throws. The library is reached by reflection, so it stays optional.

## Tower key and rental key

`cobbletowers:tower_key` is an ordinary item (stackable, any source can hand it out by id: the daily vote crate, a Raids shop
entry, `/give`). An ordinary run (a new run, a run code, an Ascension start) costs **one key per player**, each from their
own inventory. A rental-draft run costs one **Rental Key** (`cobbletowers:rental_key`) per player instead; it drops from `boss_defeated` rolls at weight 12 from floor 2 in every tower table (about 0.7 per full clear). Trials cost none, and resuming a parked run is not a start.

The lobby refuses to start ("X needs a Tower Key") if anyone lacks one when the countdown ends. Keys are taken only after the
run has really started, so a launch that fails costs nothing.

**On by default.** `config/cobbletowers-keys.json` is written on first start with `{"required": true}`; set `false` to switch keys off. The file is re-read when it changes (checked every 10 seconds, no restart). A missing field means required; a malformed file keeps the current setting.

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

The first ordinary run of each trial day is free per player (`TowerFreeRunStore`, day from `TrialService.today()`); rental runs have no free run.

Trading (decided 2026-10-23): the rental key is an ordinary tradeable, sellable item. The tower key stays with its holder:
`PlayerDropKeyMixin` returns a deliberately tossed key to the inventory (a death drop is cancelled and the key is returned on respawn or rejoin, held in memory only) and `SlotKeyMixin` lets it into only a player's own inventory or ender chest, so chests, shulkers and trade containers
refuse it. Not covered: a restart while a player is dead loses the held key, and a trade or shop plugin that moves items without
slots needs its own blacklist for `cobbletowers:tower_key`. Mixins are compile-checked only; a live test on the rig is open.

Free run rule: available when the stored day is anything other than today (a clock moved back still frees a run).

Typed commands (2026-10-23): the screens do everything the commands did, so `/tower` subcommands other than `help`, `leave`, `accept`, `decline` and `report` (bare `/tower` opens the Hall; the `/cobbletowers` root is op-only, so players see only `/tower`) need operator level 2 (`CommandGate`, applied in `PlayCommand`). The admin trees were already op-only. Chat hints now point at the Hall.
