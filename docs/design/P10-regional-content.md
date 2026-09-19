# P10: giving a reserved regional id somewhere to point

Written before the code it describes, as the TDS gate requires.

Two fields have carried a regional id since the first commit and resolved to nothing: `TowerDefinition
.regionalTheme` and `EncounterPoolDefinition.regionalPool`, each documented in P1 as "reserved for
P10; parsed and carried, never resolved here." Nine phases have built a tower that can be climbed,
fought through, drafted for and paid out, and every one of them is Neutral -- the permanent, non-
themed tower TDS #11 calls the default. P10 is what lets a `regional_theme` id mean something: a
themed tower's five signature Cobblemon (TDS #76) show up more often as floors deepen (TDS #73), the
same way `RewardTableDefinition`'s growth step already scales a grant by depth.

This phase does **not** build Tideforge, Rootvale or Duskvale. Their actual rosters, jersey textures
and models are content -- authored against the approved concept art, not derived from a TDS decision
number -- and the audit already recommends tracking that content as unimplemented until it exists
(`CobbleRaids_CobbleTowers_TDS_Audit.md` §7, row "Regional content (#76-85)"). P10 builds the framework
those rosters plug into and proves it with placeholder content, the same way P1's "example data proving
the schemas, not balanced gameplay" worked for Neutral.

## Decisions taken into P10

| Decision | Choice | Why |
|---|---|---|
| Scope | **Only the two ids already reserved for this phase** | Vendor services and scouting profiles were reserved by the same P1 sentence but are not this phase's charter. TDS §13 assigns vendor/economy transactions to P9, and P9's own decision table already closed the reward side without a currency ("there is nothing to award a balance in") -- reopening that is a currency decision, not a regional-content one, and does not belong in this phase by accident. |
| What a regional theme declares | **Five jersey signatures and a display doctrine, nothing else** | TDS #76's "roughly ten core supporting species" and "expanded tagged pool for Ascensions" are not a second roster: the supporting species are simply the other entries a themed encounter pool already authors, the way every non-jersey opponent is authored today. A second list here would be two sources of truth for the same fact, the mistake P9 avoided by pricing off `LedgerEntry.Kind` instead of re-deriving it. |
| How jersey weighting rises with depth | **One pure function, mirroring `RewardTableDefinition`'s per-floor growth step** | TDS #45's "do not scatter level math through encounter code" generalizes to any number that grows with floor depth; this is the second one in the codebase (after reward growth) and must not become a third pattern living somewhere else. |
| Guaranteeing jersey presence at milestones | **Content authoring, not new code** | TDS #73's "guarantees signature presence at important milestones" is already satisfied by `MilestoneDefinition` forcing one hand-picked boss per milestone floor (P1): a themed tower's author names a jersey boss there, the same way Neutral names whatever it wants. Nothing needs to force it at runtime. |
| A jersey aspect Cobblemon does not recognize | **Retry once without aspects instead of failing the encounter** | Found while reading `CobblemonBattleAdapter.spawn` for this phase: a bad or renamed aspect string currently throws inside `PokemonProperties.parse(...).create()`, and the existing catch logs it and returns `null` -- which fails the whole floor, not just the cosmetic layer. TDS #85 asks for a safe base-model fallback; a stalled floor is the opposite of one. |
| Aspect legality at load time | **Not checked, and said so** | The same honesty `BossPoolDefinition` already states for CobbleRaids ids: Cobblemon's aspect/species registries are not available in the plain-JVM unit tests this repo's cross-reference checks run in ([[cobbletowers-registry-bootstrap]]), so a check here would be fake or untestable. The runtime fallback above is the real safety net, not a load-time guess. |
| Jersey numbers (TDS #67) | **Deferred to P11 in full** | The decision only requires the number be seed-derived so a restart cannot reroll it -- it does not require P10 to compute one. There is no presentation layer to paint it on yet, and a field nothing reads is exactly what P1 warned against: "schemas written before their runtime exists tend to be wrong." |

## 1. Public API -- `com.cobbletowers.api.regional`

Per P1's rule of an interface only where it creates a stable extension boundary: P11's presentation
work and any addon that wants to show "this tower is Tideforge" both need to read a theme without
touching `com.cobbletowers.definition`.

- **`RegionalThemeView`** -- id, display name, doctrine (a display string, e.g. `"Momentum"`), and the
  jersey species ids only. Not the aspects, and not weights: an addon can say "this is a jersey
  Pokemon," which is all TDS #79's exclusivity promise asks anyone outside this mod to know.

## 2. Definitions -- `RegionalThemeDefinition`

Data-driven, from `data/<ns>/cobbletowers/regional_themes/*.json`, loaded by the same registry
`load()` helper as the other eight kinds, with the same per-file skip-and-report and `ContentDigest`
-- the ninth folder, not a special case.

```
RegionalThemeDefinition(
    id, schemaVersion, revision, displayName, doctrine,
    jerseySignatures: List<JerseySignature>,      // exactly five (TDS #76)
    jerseyWeightGrowthPercentPerFloor: int)

JerseySignature(species: ResourceLocation, aspects: List<String>)
```

The record constructor rejects anything but exactly five signatures, the same way `RulesetDefinition`
rejects an out-of-range level bound: a themed tower with four or six jerseys is malformed content, not
a variant to support.

Neutral names no `regional_theme` at all, matching TDS #75 -- "the pure competitive/adaptation tower"
needs no signatures to weight and no doctrine to display beyond what its absence already says. This is
the existing `Optional<ResourceLocation> regionalTheme` behaving exactly as it does today; P10 does not
touch it.

**Doctrine is display-only.** TDS #80 pins one to each of the four towers, including Neutral
("Adaptation") -- but nothing in this codebase currently reads a doctrine to change behavior, and this
phase does not invent a mechanism for it to. A themed tower's actual field mechanics (TDS #74) are
`FloorDefinition.modifierIds` doing exactly what P8 already built them to do: naming which of the
typed modifiers a floor leans on. Doctrine is prose for P11 to show a player, the same way a tower's
`displayName` already is.

## 3. Resolving the two reserved ids

- **`TowerDefinition.regionalTheme`**, when present, must name a loaded `RegionalThemeDefinition`.
  `TowerContent.of` gains one more cross-reference check, the same shape as every other dangling-id
  problem it already reports ("`X` names `Y`, which is not loaded") -- not a new category of problem,
  the same list.
- **`EncounterPoolDefinition.regionalPool`**, when present, must also resolve to one. Its meaning is
  now concrete: *which theme's jerseys this pool favors*, not a second authored roster. A pool's
  entries are exactly what they are today -- species, aspects, weight, level offset -- and an entry is
  treated as a jersey entry when its `species()` matches one of the resolved theme's signatures. Two
  themed pools can name the same theme and still list entirely different supporting casts; only the
  five jersey species are shared.

## 4. `RegionalWeighting` -- the pure escalation

`com.cobbletowers.encounter.RegionalWeighting`, pure, tested without Minecraft, the same discipline
`EncounterDraw` and `TowerLevelPolicy` already hold to.

- **Input**: an `EncounterPoolDefinition`, an `Optional<RegionalThemeDefinition>`, and a floor index.
- **Output**: the weight `EncounterDraw.pick` should use for each entry -- an entry whose species
  matches a jersey signature gets `weight * (1 + growthPercentPerFloor * floorIndex / 100)`, rounded
  the same direction `TowerLevelPolicy` already rounds level maths; every other entry is unchanged.
- No theme, or a pool that does not name one: every weight passes through unchanged, so an untouched
  pool draws exactly as it does today. This is what keeps Neutral's existing tests green without
  touching them.

`EncounterDraw.pick` takes the resolved theme as a new parameter (an `Optional`, defaulting through
the existing no-theme overloads the way `modifierLevelOffset` was added in P8) and asks
`RegionalWeighting` for each entry's effective weight instead of reading `entry.weight()` directly.
`TowerEncounters.begin` and `sendNextOpponent` resolve the theme once per floor, from
`content.pools().get(...).regionalPool()`, the same lookup pattern they already use for the pool and
ruleset themselves.

## 5. The spawn-time aspect fallback

`CobblemonBattleAdapter.spawn` currently builds one property string and fails the whole opponent if
`PokemonProperties.parse(...).create()` throws for any reason, aspect included. `EncounterSnapshot`
gains `toProperties(boolean includeAspects)` alongside the existing `toProperties()` (which becomes a
call to it with `true`). `spawn` tries the full string first; on a `RuntimeException`, it logs which
aspect string was involved and retries once with `includeAspects = false` before giving up. Only a
species Cobblemon itself does not know (already possible today, unrelated to this phase) still fails
the encounter outright.

This is the actual fallback TDS #85 asks for: a themed floor whose jersey aspect is missing --
resource pack not installed, aspect renamed upstream, Cobblemon version drift -- degrades to the plain
base Pokemon rather than stalling the floor the way an unhandled exception here would. `UncatchableProperty`
and the rest of `spawn` are unaffected; only which property string reaches Cobblemon changes.

## 6. Cross-reference validation

Two more entries in `TowerContent.of`'s existing problem list, both the established "names X, which is
not loaded" shape:

- A tower's `regional_theme` id that does not resolve.
- An encounter pool's `regional_pool` id that does not resolve.

Neither is a new kind of check. `TowerContent.EMPTY` and the `of(...)` signature gain the ninth map
(`regionalThemes`) the same way `rewardTables` was added in P9 -- every existing caller that builds a
`TowerContent` by hand needs one more argument, not a new code path.

## 7. Commands

`/cobbletowers definitions`'s per-kind count line gains regional themes, the same line P9's reward
tables should already be on and are not -- P10 fixes its own count, not that pre-existing gap. No new
command: a themed tower's summary line already prints via the existing per-tower loop, and doctrine plus
jersey species are exactly the two facts worth adding to it, the same "per kind, not just a total" reasoning
`DefinitionsCommand`'s own comment already gives for why an operator needs the breakdown.

## 8. Performance impact

- **No new per-tick work.** Theme resolution is a map lookup already happening at the same two call
  sites (`begin`, `sendNextOpponent`) that resolve the pool and ruleset today.
- **`RegionalWeighting`** is arithmetic over a pool's existing entry list -- bounded by pool size, the
  same cost `EncounterDraw.pick` already pays to sum weights.
- **The aspect retry** only runs on the failure path; the common case (aspect known, or no aspect at
  all) costs nothing beyond the property string it already built.
- **Reload cost** gains one more parse-and-digest pass, identical in shape to the other eight.

## 9. Assumptions and constraints

- **No real regional rosters.** Tideforge, Rootvale and Duskvale's actual five-signature lists, their
  supporting casts and their jersey textures/models are content authored separately against the
  approved concept art; P10 ships the schema and proves it with placeholder theme(s), not the finished
  towers. Tracked as unimplemented per the audit's own recommendation.
- **No jersey numbers.** TDS #67 is deferred to P11 in full -- generation and presentation both --
  rather than half-built here with nothing to consume it.
- **No scouting profiles.** TDS §9 lists them as data-driven content, but every fact a scouting reveal
  would show (species, aspects, level, pool composition) already exists in `EncounterPoolDefinition`
  and `EncounterSnapshot`. A scouting profile is a reveal-threshold config for P11's presentation, not
  a new source of gameplay data -- P10 spends none of that reservation, the same phrase P9 used for
  regional pools and vendor services in its own assumptions.
- **Vendor services and CobbleDollars stay reserved.** P9 already closed the reward side of the economy
  without a currency; P10 does not reopen that decision. If vendor services are ever built, they need
  their own economy phase, not a rider on this one.
- **No Ascension/infinite-mode content.** TDS #76's "expanded tagged pool for Ascensions" has nothing
  to attach to yet -- infinite mode is unbuilt (README: "architecture must permit a future infinite
  mode"). `RegionalThemeDefinition` does not reserve a field for it; when infinite mode exists, its own
  design doc is where that pool's shape gets decided, the same way P8's modifiers waited for their own
  phase instead of guessing at P1.
- **Doctrine has no mechanical effect.** It is display data for P11, matching TDS #80's four values;
  a themed tower's actual difference in play comes entirely from its encounter pools' jersey weighting
  and whatever modifiers its floors already name.
