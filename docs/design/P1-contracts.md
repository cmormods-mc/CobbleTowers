# P1: API contracts, definition schemas, run state and transitions

Written before the code it describes, as the TDS's Phase 1 gate requires: *"Do not begin full battle
implementation until API contracts, persisted schemas and state transitions are reviewed together."*

P1 ships data, contracts and rules. No dimension, no allocator, no runtime state machine, no battles
beyond the existing dev spike. Everything here is testable without a server, and most of it without
Minecraft.

## Decisions taken into P1

| Decision | Choice | Why |
|---|---|---|
| Participant state | **Three orthogonal axes** | TDS §8 lists one enum, but a disconnected player can also be knocked out or revive-pending. One enum cannot hold both, so a reconnect would have to guess what to restore. Amends the LOCKED enum; every §8 name survives as a value on an axis. |
| Content scope | **Core five schemas** | Tower, floor, encounter pool, ruleset, milestone. Modifiers, vendor services, scouting profiles and regional pools are reserved ids only until P8–P11 give them behaviour. Schemas written before their runtime exists tend to be wrong. |
| Definition format | **Hand-written Gson** | Matches CobbleRaids (`RaidDefinition.fromJson`). The cost is that persistence cannot reuse the schema, so persisted state gets its own explicit NBT read/write with round-trip tests. |

## 1. Public API

Four domains in P1, not the six the TDS lists, per §9's *"introduce an interface only where it creates
a stable extension boundary"*. `api.modifier` waits for P8; an encounter-facing API waits for P5,
where CobbleRaids' own `com.cobbleraids.api.encounter` already covers the boss battle itself.

- **`api.tower`** — `RunState`, `RunEvent`, and the read-only views `TowerRunView`, `ParticipantView`,
  `FloorView`.
- **`api.tower.participant`** — the three axes and `ParticipantState`.
- **`api.rules`** — `RulesetView`: level bounds and rounding, party size, readiness, item budget.
- **`api.registry`** — read-only definition lookups.
- **`api.event`** — `TowerRunListener`: state changed, floor resolved, run ended.

**Boundary rule.** No public signature in `com.cobbletowers.api` names a CobbleTowers internal type, a
Cobblemon type or a CobbleRaids type — only `java.*`, `net.minecraft.*` and the API itself.
`validate_api_boundary.py` (ported from CobbleRaids) enforces it on the bytecode, alongside the
existing `validate_architecture.py`, which keeps CobbleRaids internals out of the whole mod.

## 2. Participant state: three axes

| Axis | Values | Meaning |
|---|---|---|
| Connection | `ONLINE`, `DISCONNECTED` | Whether the player is on the server. |
| Combat | `ACTIVE`, `KNOCKED_OUT`, `SPECTATING_TEAM`, `REVIVE_PENDING` | What they may do in the run. |
| Membership | `MEMBER`, `VOLUNTARILY_LEFT` | Whether they belong to the run at all. |

Rules, each with a test:
- `canFight()` is true only for `MEMBER` + `ONLINE` + `ACTIVE`.
- A disconnect changes **only** the connection axis, so a reconnect restores the combat state the
  player left. This is the case the single enum could not express (TDS #36, #37).
- A full party faint sets `KNOCKED_OUT`, then `SPECTATING_TEAM`; a teammate clearing the floor sets
  `REVIVE_PENDING`; the next intermission returns them to `ACTIVE` (TDS §8).
- `VOLUNTARILY_LEFT` is terminal: no event returns a participant from it (TDS #39).
- Knocked out is never a Minecraft death (TDS §8).

## 3. Run states and the transition table

States are the TDS's nine, plus its five terminal/branch states: `COMPLETED`, `CASHED_OUT`, `FAILED`,
`ABANDONED`, `RECOVERY_REQUIRED`.

`RunTransitions` maps `(RunState, RunEvent)` to a `Transition`: the next state, whether it forces a
checkpoint, the intent persisted before the side effect, an idempotency key, and whether it is
terminal. The table is the audit's missing piece — the TDS gives names and arrows but no guards,
retry ownership or resume paths.

| From | Event | To | Checkpoint / idempotency key |
|---|---|---|---|
| CREATED | PARTY_SUBMITTED | VALIDATING_PARTY | — |
| VALIDATING_PARTY | PARTY_VALIDATED | ALLOCATING_INSTANCE | — |
| VALIDATING_PARTY | PARTY_REJECTED | ABANDONED | terminal |
| ALLOCATING_INSTANCE | INSTANCE_ALLOCATED | PREPARING | yes · `run:<id>:allocated` |
| ALLOCATING_INSTANCE | ALLOCATION_FAILED | RECOVERY_REQUIRED | technical, never PLAYER_LOST |
| PREPARING | PREPARATION_COMPLETE | FLOOR_READY | yes · `run:<id>:floor:<n>:ready` |
| FLOOR_READY | ENCOUNTER_STARTED | ENCOUNTER_ACTIVE | yes · `run:<id>:floor:<n>:encounter` |
| ENCOUNTER_ACTIVE | ENCOUNTER_RESOLVED_CLEARED | FLOOR_RESOLVING | yes · `run:<id>:floor:<n>:resolved` |
| ENCOUNTER_ACTIVE | ENCOUNTER_RESOLVED_WIPED | FAILED | yes · terminal (legitimate defeat) |
| FLOOR_RESOLVING | REWARDS_BANKED | INTERMISSION | yes · `run:<id>:floor:<n>:banked` |
| FLOOR_RESOLVING | FINAL_FLOOR_CLEARED | COMPLETED | yes · terminal |
| INTERMISSION | INTERMISSION_COMPLETE | NEXT_FLOOR_READY | yes · `run:<id>:floor:<n>:intermission` |
| INTERMISSION | CASH_OUT_CHOSEN | CASHED_OUT | yes · terminal |
| NEXT_FLOOR_READY | NEXT_FLOOR_CONFIRMED | FLOOR_READY (floor + 1) | yes · `run:<id>:floor:<n+1>:ready` |
| any live state | ABANDON_REQUESTED | ABANDONED | yes · terminal |
| any live state | TECHNICAL_FAILURE | RECOVERY_REQUIRED | yes · keeps the last checkpoint |
| RECOVERY_REQUIRED | RECOVERY_COMPLETED | the checkpointed state | resumes; the table marks it `resumeFromCheckpoint` |
| RECOVERY_REQUIRED | RECOVERY_ABANDONED | ABANDONED | yes · terminal |

"Live" excludes `RECOVERY_REQUIRED`, which is not terminal but leaves only through
`RECOVERY_COMPLETED` or `RECOVERY_ABANDONED`: a run already in recovery does not need a second way
to report a fault.

`lookup` answers the two wildcards **before** it reads the table, so a state-specific entry for
either would be written, compile, and never once be read. `requireNotWildcard` refuses one while the
class initializes rather than letting it sit there looking effective.

Invariants, each a test: every non-terminal state has an outgoing transition; no terminal state has
one; `RECOVERY_REQUIRED` always has a resume path; every `RunState` and `RunEvent` value appears; every
checkpointing transition names an idempotency key; two moves share a key only when they commit the
same outcome (abandoning is deliberately one key from many states, because a run is abandoned once
however it got there); and every floor-scoped key changes with the floor. `Transition.key` takes the
floor the run is on **before** the move, which is what makes `{nextFloor}` mean the floor being
opened rather than the one after it.

## 4. Definition schemas

Loaded from `data/<namespace>/cobbletowers/{towers,floors,encounter_pools,rulesets,milestones}/*.json`.
Each record validates in its constructor, the way `RaidDefinition` does, so an invalid definition
cannot exist as an object.

- **Tower** — display name, `schema_version`, `revision`, ordered floor ids, ruleset id, reserved
  regional theme id, milestone slots.
- **Floor** — index, encounter pool id, ruleset overrides, milestone flag, reserved `modifier_ids`.
- **Encounter pool** — weighted entries (species/aspect id, level offset), reserved `regional_pool`.
- **Ruleset** — level bounds and rounding, party size, readiness, item budget, carryover.
- **Milestone** — floor index, kind (`BOSS`, `CHAMPION`), the CobbleRaids definition id an F5 boss
  uses, reward-bank marker.

**Reserved ids** are parsed and checked for well-formedness, then carried untouched. They are not
resolved, and nothing reads them until their phase.

**Revisions and digests (TDS #40).** Every definition carries `schema_version` and `revision`, and the
registry computes a SHA-256 **content digest** over its canonical JSON (keys sorted, whitespace
ignored). Digests are keyed by folder **and** id (`DefinitionKey`): an id is only a path below its
folder, so `towers/neutral.json` and `rulesets/neutral.json` are both `cobbletowers:neutral`, and
naming a ruleset after its tower is the obvious thing for a pack author to do. Sharing one key space
across the five folders let the last file loaded replace an earlier one's digest, which would leave a
tower reporting a digest that does not change when the tower is edited -- the one question the digest
exists to answer. A persisted run stores the digest of what it started with, so P2 can tell "the tower was
edited" from "the tower is missing" and refuse or migrate deliberately instead of silently running
different content.

## 5. Registry

One Fabric `IdentifiableResourceReloadListener` holding five immutable maps, following
`RaidDefinitionRegistry`'s behaviour exactly where it was learned the hard way:
- **A malformed file is skipped**, with a per-file error and one summary line. It never fails the
  reload: a dedicated server that cannot finish its initial reload refuses to start, so one bad JSON
  in any third-party datapack would take the server down.
- **Sorted id lists are precomputed at apply time**, not derived per lookup.
- **Cross-reference validation at apply**: floors exist, pools exist, rulesets resolve, F5/F10 slots
  are present, reserved ids are well-formed. Problems are reported, never thrown.

## 6. Persisted run state (schema only)

`PersistedRun`: run id, `schema_version`, tower id + revision + digest, ruleset revision, structure
revision, seed, floor index, `RunState`, participants, last checkpoint, transaction ids.
`PersistedParticipant`: player uuid, the three axes, registered Pokémon ids.

Explicit `toTag`/`fromTag` against `CompoundTag`, round-tripped in tests for every state and axis
combination. An unknown future `schema_version` is refused with a clear message rather than read
wrongly. **No live entity references are ever persisted** (TDS §10) — identifiers, seeds and logical
state only. P2 attaches this to a world; P1 does not store anything.

## 7. Performance

- **No per-tick work.** P1 adds nothing to the server tick.
- **Reload cost** is one parse and one SHA-256 per definition file, on the reload thread.
- **Lookups** are immutable maps and precomputed sorted lists — the pattern CobbleRaids adopted after
  tab completion re-derived and sorted 130 entries on every keystroke.
- **The transition table** is a static immutable `EnumMap`; a lookup is an array index.
- **Persisted records** are plain data, so writing one costs its own size and nothing else.

## 8. Assumptions and constraints

- Minecraft 1.21.1, Fabric, Java 21, Cobblemon 1.7.3, CobbleRaids ≥ 0.8.94-encounter-api.
- CobbleRaids is used **only** through `com.cobbleraids.api`; P1 does not call it at all.
- Definitions are server-side datapack data. Nothing in P1 reaches a client.
- Tower content is example data proving the schemas, not balanced gameplay.
- The run state machine is described and tested here but **not driven**: no code advances a run until
  P2 owns persistence and P3 owns the registry of live runs.
