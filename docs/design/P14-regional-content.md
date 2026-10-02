# P14: giving Tideforge, Rootvale and Duskvale something to actually fight

Written before the code it describes, as the TDS gate requires.

P10 built the framework a themed tower's roster plugs into -- jersey weighting, the `regional_theme`
and `regional_pool` ids, a safe fallback when Cobblemon does not recognize an aspect -- and proved it
with placeholder content only, exactly as its own doc scoped it: "This phase does **not** build
Tideforge, Rootvale or Duskvale... authored against the approved concept art, not derived from a TDS
decision number." P12 shipped the scouting-reveal mechanism the same way, against zero real profiles.
Both gaps have sat in the phase log since, flagged but untouched, because no TDS decision number ever
named "author the roster" as its own phase -- P1 through P13 close every id the TDS ever reserved, and
this is the first phase since that had to be scoped from scratch. P14 is that content pass for the
regional roster only: three real five-signature jerseys, a real supporting cast around each, and the
boss/champion picks that put a jersey ace at the top of a themed climb. Real jersey art, the Ascension-
tagged expansion pool TDS #76 also mentions, and scouting-profile content are explicitly out of scope
-- each is its own decision, not a species list, and reopening them here would be exactly the kind of
scope creep P10's own doc warned against.

## Decisions taken into P14

| Decision | Choice | Why |
|---|---|---|
| Jersey signatures | **User-supplied, not scored by this doc** | TDS #82-84's scored-evaluation process (visual identity, role coverage, typing, jersey-model suitability, moveset depth, competitive usefulness) is a game-design judgment call, not something derivable from the codebase. The user supplied all fifteen signatures directly: Tideforge = Kyogre, Vaporeon, Blastoise, Golisopod, Empoleon; Rootvale = Celebi, Hydrapple, Leafeon, Snivy, Breloom; Duskvale = Darkrai, Umbreon, Obstagoon, Marshadow, Dusknoir. |
| Supporting roster (TDS #76's "~ten core supporting species") | **Proposed by this doc, approved by the user; authored straight into each region's encounter pool** | Per P10's own decision table, the supporting roster is not a second list anywhere in the schema -- it is simply the rest of a themed pool's entries, the same way every non-jersey Neutral opponent already is. Ten per region, picked for type/role diversity against each region's locked identity (TDS #63-65) and doctrine (TDS #80), not duplicated across regions. |
| Jersey visual assets | **Data-only: real species and aspect ids wired through the existing fallback path, no new textures/models** | No real jersey art exists to ship. P10 already built `CobblemonBattleAdapter.spawn`'s retry-without-aspects fallback for exactly this case (a jersey aspect Cobblemon does not recognize), so jerseys render as base models until art is produced separately -- the same gap P10 and P11 both shipped around rather than blocked on. |
| Ascension-tagged expansion pool (TDS #76) | **Deferred** | Nothing reads it: infinite/Ascension mode itself does not exist yet (TDS #1, #15). Populating a pool with no consumer is exactly the "schemas written before their runtime exists tend to be wrong" trap P10's own doc named. It becomes this phase's job again, or whichever phase builds Ascension mode's own. |
| F5 boss and F10 champion (TDS #70, #72) | **A single curated pick per milestone, distinct from the early/late boss pool entries** | Mirrors Neutral's own pattern exactly (F5 = `lucario`, F10 = `arceus`, neither drawn from `neutral/early` or `neutral/late`). F10 champion is always the region's most iconic jersey signature that CobbleRaids actually has a raid definition for -- Kyogre, Celebi and Darkrai all clear that bar, so all three regions get a real jersey ace at the top, satisfying TDS #72 directly. F5 uses a distinct thematic legendary picked for the same regional identity, not a jersey (most jersey signatures have no CobbleRaids raid definition at all -- see below), satisfying TDS #70's "curated thematic special boss" clause. |
| Early/late boss pool entries (floors 1-4, 6-9) | **Drawn only from species CobbleRaids actually has a raid definition for, verified against the bundled jar** | `BossPoolDefinition`'s own doc comment is explicit that a bad id "fails when the boss is started, reported as a technical fault" -- nothing here can check it offline. Rather than guess, every boss-pool and milestone pick in this phase was checked against `data/cobbleraids/raids/*.json` inside the CobbleRaids jar this project already builds against, so none of them can fail live for a missing definition. Only 130 species are covered (legendaries, mythicals, pseudo-legendaries and starter-line finals); most of the approved jersey signatures and supporting species are ordinary fully-evolved Pokemon that CobbleRaids has no raid boss for, so boss pools lean on thematically-matched species from that list instead (e.g. Tideforge's early pool weights Empoleon and Blastoise, its two jerseys CobbleRaids does cover, alongside Feraligatr and Samurott as curated water-starter extras). |
| Regular encounter pool entries | **No cross-reference check possible or added** | Unlike boss pools, `EncounterPoolDefinition` entries are ordinary Cobblemon spawns, not CobbleRaids raid bosses -- Cobblemon's aspect/species property is permissive and never throws for an unrecognized value ([[cobblemon-aspect-property-permissive]]), so there is nothing to validate against even at runtime, let alone offline. Every species named was still checked against the bundled Cobblemon jar's own `data/cobblemon/species/**` for this phase, catching one real substitution before it shipped: Basculegion does not exist in this project's pinned Cobblemon 1.7.3, only its pre-evolution Basculin, which replaced it in Tideforge's supporting roster. |
| Tower structure, ruleset and reward table | **Reused from Neutral, unchanged** | `arena_floor`/`boss_arena` are already generic, unthemed structures (TDS §12's "current tower visual priority is interior quality" -- no region-specific room exists to reuse instead), and reward/ruleset tuning is P9's economy domain, not a roster decision. All three new towers reference `cobbletowers:standard` and `cobbletowers:neutral`'s reward table exactly as Neutral does, so this phase touches nothing about the economy or floor layout. |
| The `regional_themes` KINDS gap | **Fixed in this phase** | P12's own doc flagged `validate_definitions.py`'s `KINDS` tuple as never having included `regional_themes` since P10 shipped it, "a pre-existing gap... worth a future look" -- deliberately not fixed then because P12 had zero regional-theme content of its own to validate. This phase ships the first real `regional_themes/*.json` files that gap would silently skip, so leaving it unfixed here would be shipping content nothing checks. |

## 1. The three regional themes

`data/cobbletowers/cobbletowers/regional_themes/{tideforge,rootvale,duskvale}.json`, each five jersey
signatures, a doctrine (TDS #80: Tideforge = Momentum, Rootvale = Growth, Duskvale = Disruption), and a
jersey weight growth of 15% per floor -- steeper than P10's placeholder (which used 10% only to prove
the arithmetic moved) now that there is a real roster for the curve to matter against.

| Region | Doctrine | Jersey signatures |
|---|---|---|
| Tideforge | Momentum | Kyogre, Vaporeon, Blastoise, Golisopod, Empoleon |
| Rootvale | Growth | Celebi, Hydrapple, Leafeon, Snivy, Breloom |
| Duskvale | Disruption | Darkrai, Umbreon, Obstagoon, Marshadow, Dusknoir |

## 2. The supporting roster, per region's identity (TDS #63-65)

Each region gets one shared encounter pool (`{region}_common`, the same one-pool-for-every-floor shape
Neutral already uses) naming its `regional_pool` as that region's theme id, so `RegionalWeighting`
picks up its five jerseys automatically. Fifteen entries per pool: five jerseys plus ten supporting
species, weighted the same 100/80/60-with-occasional-level-offset shape `neutral_common` already uses.

- **Tideforge** (Water foundation + storm/electric, industrial/steel, deep-sea, fast aquatic):
  Floatzel, Barraskewda, Toxapex, Lanturn, Corviknight, Perrserker, Greninja, Drednaw, Toxtricity,
  Basculin.
- **Rootvale** (vegetation, fungal/decay, woodland, ancient/defensive nature, aggressive growth):
  Rillaboom, Tsareena, Ferrothorn, Amoonguss, Trevenant, Torterra, Tangrowth, Roserade, Toedscruel,
  Decidueye.
- **Duskvale** (Dark foundation, nocturnal hunting, spectral/occult, trickery, intimidating ace):
  Kingambit, Weavile, Chandelure, Zoroark, Mismagius, Grimmsnarl, Absol, Sableye, Houndoom, Mimikyu.

No species repeats across regions or against another region's jerseys.

## 3. Boss pools and milestones (TDS #70, #72)

| Region | Early pool (F1-4) | Late pool (F6-9) | F5 boss | F10 champion (jersey ace) |
|---|---|---|---|---|
| Tideforge | Empoleon, Blastoise, Feraligatr, Samurott | Primarina, Inteleon, Swampert, Suicune | Regieleki | **Kyogre** |
| Rootvale | Rillaboom, Torterra, Decidueye, Sceptile | Serperior, Meganium, Venusaur, Virizion | Tapu Bulu | **Celebi** |
| Duskvale | Marshadow, Tyranitar, Hydreigon, Zarude | Spectrier, Giratina, Urshifu, Yveltal | Necrozma | **Darkrai** |

Early/late pools follow Neutral's own weight shape (two entries at 100, two at 80; late pool entries
carry `level_offset` +2/+3 the same as Neutral's). Where a region's own jersey has a CobbleRaids raid
definition (Tideforge's Empoleon/Blastoise, Duskvale's Marshadow), it anchors that region's early pool
at full weight, satisfying TDS #70's "weighted strongly toward jersey members" as far as the available
raid catalog allows; Rootvale's jerseys have none, so its early pool is entirely the "curated thematic
special bosses" TDS #70 explicitly permits instead.

## 4. `validate_definitions.py`

Added `regional_themes` to `KINDS` and a new `check_regional_themes`: schema/display fields present,
exactly five jersey signatures, each naming a species. Also added the two cross-reference checks P10's
own schema always supported but nothing ever exercised: a tower's `regional_theme` and an encounter
pool's `regional_pool` must resolve to a loaded theme -- previously silent because no content ever set
either field.

## 5. Testing

`ci_local.sh` passed clean: build, full unit suite, architecture/API-boundary/persistence bytecode
checks (174 classes, unchanged from P13 -- this phase added no Java), and `validate_definitions.py`
(4 towers, 40 floors, 4 encounter pools, 3 regional themes, every reference resolved).

**Live-smoke-tested** with a new `validation/smoke/regional_content_test.py`, 9/9: for each of
Tideforge, Rootvale and Duskvale, a solo run was created, allocated and its floor begun twice, reading
the real "battle ... started: X vs SPECIES" log line Cobblemon itself writes. All six draws (two per
region) landed inside that region's own fifteen-species pool and never in Neutral's or another
region's -- Barraskewda and Basculin for Tideforge, Tangrowth and Roserade for Rootvale, Sableye twice
for Duskvale. This is the check that actually mattered: it proves the real Cobblemon jar accepted
every jersey/supporting species and aspect id this phase authored (not just that the ids parsed as
valid JSON), catching the kind of typo or renamed-species mistake the offline jar-content check in the
decision table above could miss if it were wrong about what shipped in 1.7.3.

**Not live-tested**: the F5/F10 milestone raid definitions. Reaching floor 10 for real means clearing
nine floors first, for all three towers -- a full soak run, not a smoke test of new content -- so
those ids (Regieleki/Kyogre, Tapu Bulu/Celebi, Necrozma/Darkrai and all twelve early/late boss-pool
entries) are verified offline only, against the exact CobbleRaids jar this project builds against
(decision table above). Jersey visuals are not live-tested for the same reason P10/P11 could not: the
smoke rig's bots are headless with no rendering client, and this phase ships no new art for them to
render regardless.

One incidental finding while setting up the new test: `run_durability_test.py`'s `start_bot` and
`wait_online` helpers hardcode that module's own `BOT = "TowerDuraBot"` constant rather than taking a
username, so importing them under a different bot name silently checks for the wrong player online.
Not a CobbleTowers bug -- `floor_encounter_test.py` already avoided it by defining its own
`start_battle_bot` rather than importing the shared one -- but worth knowing before writing the next
smoke test that wants its own bot identity: define a local `start_bot`/`wait_online` rather than
importing the durability test's.

## 6. Assumptions and constraints

- CobbleRaids' raid catalog is fixed content this project does not control; every boss pick in this
  phase was checked against the exact jar version this project builds against as of 2026-09-20, not
  guessed from species popularity. A future CobbleRaids update could remove or rename one of these ids
  without this project knowing until a boss fails to start live.
- Cobblemon's species catalog was checked the same way against the pinned 1.7.3 jar for the same
  reason, catching the Basculegion substitution above.
- This phase does not touch vendor services, reward tables, or the ruleset -- all three new towers are
  playable exactly as thoroughly as Neutral already is, just with a different roster and jersey ace.
