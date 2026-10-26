# P54: Hollow's Eve, the Halloween event tower

Status: **proposal, 2026-10-25, nothing built.** Written before the code, as the TDS gate requires.
Event window **2026-10-31 to 2026-11-02** (same as the AscensionLib Halloween event,
`L:\Codex\CobbleAscend\docs\HALLOWEEN-EVENT-DESIGN.md`).

## Owner decisions so far

| Decision | Choice |
|---|---|
| Scope | **Full**: tower + Trick or Treat doors + disguise scouting + candy/essence payout |
| Boss skins | **Boss only**: bosses wear the Wardrobe skins; players do not unlock skins from the tower |
| Payer | AscensionLib only (standing rule), so the tower pays **Spooky Essence**, the event's existing material |

## What already exists (do not rebuild)

- **Spooky Essence** and **Hauntings** (AscensionLib, built, untested live). Hauntings act in raids, so
  they already work against tower bosses (a boss is a CobbleRaids encounter). Never in PvP.
- **World Boss zones** (CobbleRaids `world-boss` branch) are the event's group content. The tower is the
  solo/party counterpart, not a competitor: it must pay *less* per hour than a zone.
- Tower plumbing: tower JSON, ten floor JSONs, `boss_pools`, `milestones`, `reward_tables`, rulesets,
  `regional_themes`, the `battle_tower_*` structure. Duskvale is the closest template.

## Design

### Shape
`cobbletowers:halloween`, ten floors, milestone boss on 5, champion on 10, `ascension: true`.
Floors reuse the existing tower structure for the first cut; pumpkin/cobweb dressing is a stretch.
It is a **regional-style event tower**: earns season points like the regional towers, no armor drops
(armor is not part of this event).

### Bosses

| Floor | Boss | Notes |
|---|---|---|
| 3 | Pumpkin Rowlet | warm-up, teaches the Trick modifier |
| 5 (boss) | Witchchu | weather/status control |
| 7 | Scarecrow Eevee | the disguise boss (see Disguises) |
| 9 | Chucky Marshadow | fast sweeper; Marshadow definition already exists |
| 10 (champion) | Shiny Haunted Phantump, Shiny Pumpkin Togepi as the pair | legendary-rarity skins |
| Ascension finale | Pumpkin Mew | higher tiers only |

Halloween Mimikyu, Haunted Phantump, Pumpkin Togepi: ordinary encounter pool.

### Trick or Treat doors
Between floors (the intermission-events system, P34b) the party picks a door. **Treat**: relic, heal
or bonus essence. **Trick**: a harmless-but-annoying modifier for the next fight (P29 custom
modifiers). Doors are seeded from the run seed so run codes replay them.

### Disguises
Scouting (P22) shows a decoy species on flagged floors until the fight starts. Pure presentation:
the real opponent is still the drawn one. Needs a scouting-profile flag, no battle change.

### Payout
Spooky Essence through AscensionLib on boss clears only (floor 5: small, floor 10: larger, champion on
Ascension: larger again). Amounts are provisional and sit in the reward table so tuning is data.
The tower has no per-player cap (matches the event's "no cap" decision); the cap is the run itself,
which takes real time.

## What cannot be done from this repo (blocking)

1. **Boss definitions live in CobbleRaids** (`data/cobbleraids/raids/`), a sibling repo. Existing
   Ghost/Dark ones: darkrai, giratina, marshadow, lunala, tyranitar, hydreigon, dragapult, decidueye,
   calyrex, urshifu. There is **no definition** for Witchchu, Pumpkin Rowlet, Scarecrow Eevee, the
   Shiny Phantump or Togepi. Each needs a new JSON there. The Raids doc says that repo is the owner's
   to edit, so this needs a yes before anything is written.
2. **How a skin reaches a boss.** The Wardrobe skins look like Cobblemon aspects on a resource-pack
   model. Whether a raid definition can set an aspect (and whether the server pack's `aspect`
   property accepts the skin's string; it is permissive and never throws) is **unverified**.
3. **A time window.** No tower definition field restricts availability by date. Options below.

## Open questions for the owner

1. OK to add the new boss definitions in CobbleRaids, or will they be authored there?
2. What are the skin aspect ids (the exact strings the Wardrobe applies)?
3. How is the tower gated to Oct 31 - Nov 2? (a) a `window` field in the tower JSON checked at entry,
   (b) a server-side toggle/command and no code, (c) always on, event reward only paid in the window.
   I recommend (a), reusing `TrialClock` timekeeping, so it needs no restart.
4. Essence amounts per boss clear.

## Build order (each step shippable)

1. Tower JSON + floors + rules + boss pools using **existing** definitions as stand-ins; window gate. Proves the plumbing.
2. Swap in the skin bosses once the definitions exist.
3. Essence payout + reward table.
4. Doors, then disguises.
5. Rig test (`-Pdev` jar, raidbot), then deploy with the AscensionLib jar. Six days is tight: steps 1-3 are the safe core; 4 is the stretch.

## As built: step 1 (2026-10-25)

- `cobbletowers:halloween`: 10 floors cloned from Duskvale's layout (its structure, no new build), ruleset
  `standard`, `ascension: true`, Hall-listed as "Hollow's Eve". Armor is stripped from its reward table.
- Stand-in bosses (existing CobbleRaids definitions): milestone F5 decidueye, champion darkrai; early pool
  decidueye/tyranitar/hydreigon/marshadow, late pool dragapult/lunala/giratina/darkrai. Swap when the
  skin definitions exist (step 2).
- Encounter pool `halloween_common`: 27 ghost/spooky species, no aspects yet.
- **Window**: optional `window {start, end}` (ISO instants, end exclusive) on a tower JSON, `TowerWindow` +
  `TowerDefinition.isOpen`. Set to 2026-10-31T00:00Z to 2026-11-03T00:00Z (UTC; shift it if the server's
  Halloween is local time). Gated in `RunFactory.create` (so lobby launch and the admin command), refused
  in `LobbyService.select`, and hidden from the Hall and play lists (`openCatalogTowers`). Runs already
  started continue after the end. Windowed towers are kept out of route detours so run codes stay stable.
  A rig opens it early with `-Dcobbletowers.ignoreWindows=true`.
- Checks: `TowerWindowTest`, `validate_definitions.py` PASS (6 towers, 54 floors), full `gradlew test` green.
  Not run live.

## Blocker for step 3 (essence)

Spooky Essence is paid by AscensionLib (`store.grant` with `MaterialId.SPOOKY_ESSENCE`, exactly-once by
operation id). The library has no tower-facing call for it: `settleTowerBoss` pays the normal bands only.
Step 3 needs a new `AscensionRewards` method (e.g. `settleEventEssence(encounterId, outcome, amount,
players)`) in `L:\Codex\CobbleAscend`, which has uncommitted Halloween work in it, plus a reflective call
and a `PendingLibSettlement` kind here. Not started; needs the owner's go-ahead to edit that repo.

## Traps to respect

Four-traps checklist (bounded scans, UUID not player refs, main-thread hops, tick throttling); a
build never runs during a push; no attribution in commits; version bump for the deploy.
