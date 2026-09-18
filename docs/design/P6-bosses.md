# P6: the CobbleRaids boss that finishes a floor

Written before the code it describes, as the TDS gate requires.

P5 made half a floor playable: the party fights its prerequisite encounters and the round clears.
P6 adds the half that finishes it. A floor is small ordinary encounters, then that floor's CobbleRaids
boss, and only then is the floor done.

Milestone 1 already proved the mechanism — a four-player owned encounter won in 58 combat ticks
through `com.cobbleraids.api.encounter`, with catching, raid rewards, progression and history all
turned off and HP and PP carried back to the party. This takes it out of the dev spike and into the
floor.

## Decisions taken into P6

| Decision | Choice | Why |
|---|---|---|
| Which boss a floor fights | **A weighted pool per floor, drawn from the run seed** | The API exposes only `start`, `abort` and `isActive` -- there is no listing and no tier query, so the tower must name the definitions itself. Pools rather than one boss per floor: ten floors share a couple of lists, and repeat runs vary. |
| Floors 5 and 10 | **The milestone's handpicked definition** | Already in the schema, and it is the distinction the user asked for: milestones are special through content, not through a second code path. |
| Losing the boss | **Forfeits the unclaimed pool** | It is what makes cashing out at an intermission a decision rather than a formality. Marked, not deleted, so it can still be read. |
| Where the phase lives | **In the round, not the transition table** | Both events already exist and already mean the right thing. A table that grew a state every time gameplay gained a step would stop being a contract. |

## 1. `boss_pools`, a sixth definition kind

`data/<namespace>/cobbletowers/boss_pools/*.json`: weighted CobbleRaids definition ids, the shape the
encounter pool already uses. Loaded by the same registry, checked by the same cross-reference pass.

**What cannot be checked offline:** whether CobbleRaids actually has a definition by that id. Those
live in the other mod, behind an API that does not list them. So the validator checks the pool exists,
its entries parse and its weights are positive; an id CobbleRaids does not know fails at `start` and
is reported as a technical fault. Saying so is better than a check that pretends to more.

## 2. `encounter/BossDraw`

`EncounterSeed` again, with a **separate ordinal space** so the boss is not simply "opponent zero".
A tower whose boss could be predicted from the first ordinary encounter would leak its own surprise,
and the two draws share a seed by design.

## 3. `battle/cobbleraids/TowerBossAdapter` (TDS #52, #53)

The only place this mod names the CobbleRaids API, as `battle/cobblemon/` is the only place it names
a Cobblemon battle. Both adapters exist so the floor's rules read the same whoever runs the combat.

- **Policy** `none().withCarryover(true, true)` — the spike's, proven: no catching, no raid rewards,
  no raid progression or history, HP and PP carried back (TDS #16).
- **Boss level** from `TowerLevelPolicy`, the same class that levels the ordinary encounters, so a
  floor's difficulty is decided in one place (#45).
- **Position** the presentation anchor P4 validated.
- **Listener**: `onEnded` routes the outcome back to the floor; `onParticipantLeft` is what the three
  participant axes from P1 were built for.
- **Aborts on every exit path.** A boss left standing when the cell is released is what P3's sweep
  quarantines the cell for.

## 4. Two phases in a round

`PREREQUISITE -> BOSS`. Clearing the prerequisite starts the boss rather than resolving the floor;
beating the boss raises `ENCOUNTER_RESOLVED_CLEARED`, losing to it raises `ENCOUNTER_RESOLVED_WIPED`.
`ABORTED` is neither — it is a technical fault, and the run parks.

## 5. Forfeiting

A lost boss appends a `POOL_FORFEITED` entry before the run ends. A new `Kind` needs no schema bump,
because older saves simply never contain one. P9 will read it as "grant nothing"; an operator can
still see what the run had earned, which is the point of marking rather than deleting.

## 6. Performance

- **No per-tick work.** The boss runs inside CobbleRaids, which owns and already measured that cost;
  this adds one listener per floor.
- **The boss replaces the prerequisite battles rather than joining them** — those are over before it
  starts, so peak battle load is unchanged.
- The draw is arithmetic. Forfeiting is one appended entry.

## 7. What building it changed

- **Floor 10 had no boss at all.** `raid_definition` was required only of a BOSS milestone, so the
  champion on floor 10 carried none -- fine under the old reading, unplayable under "every floor ends
  in a boss". The new definition check caught it on its first run. Both the content and the invariant
  are fixed: every milestone must now name the definition its floor is finished by, so the same hole
  cannot be dug again in a future tower.
- **Completing a floor stopped being logged.** The "floor cleared" line lived in the prerequisite
  path; when the boss became the end of a floor, the line was left behind and a won floor said
  nothing. The live test noticed before anybody else did. Both outcomes are logged now, from the
  place that actually decides them.

## 8. Assumptions and constraints

- CobbleRaids >= 0.8.94-encounter-api, used only through `com.cobbleraids.api`; the architecture
  check enforces it.
- At most four players in one encounter (`EncounterRequest.MAX_PLAYERS`), which is also the tower's
  party limit.
- CobbleRaids' own dynamic levelling has previously overridden a boss's scale; the request carries an
  explicit level and the live test reports what was actually fought.
- No rewards are granted here. P9 owns the economy; this phase only records.
