# CobbleTowers Structure Reliability Contract

## Notes

### Exact functionality

This contract governs production Tower structure placement, reconciliation, cleanup, and failure containment for private Tower instances.

### Architectural role

The structure layer is the only component allowed to mutate the static Tower architecture inside an allocated instance region. Run progression requests transitions; the structure layer performs bounded world work and reports success/failure without owning gameplay state.

### Performance considerations

The supplied development clear volume spans roughly 61 x 61 x 137 blocks, so production code must never execute a full half-million-position clear synchronously on an entry/progression tick. Work is segmented by canonical structure sections and staged over server ticks.

### Assumptions and constraints

- Minecraft 1.21.1, Fabric, Java 21.
- One shared `cobbletowers:tower` dimension.
- Private 192 x 192 instance cells.
- Canonical production assets are structure-template/NBT sections, not runtime `.mcfunction` execution.
- Explicit air inside each template section is authoritative and may replace stale blocks inside that section's exact bounds.
- Players remain outside the Tower until the complete physical structure is READY.
- Explosions are disabled throughout the Tower dimension.
- No firework entities/effects.

## 1. Canonical section model

Production templates preserve logical architectural boundaries from the supplied source assets:

1. foundation
2. core
3. floor_01
4. floor_02
5. floor_03
6. floor_04
7. floor_05_boss
8. floor_06
9. floor_07
10. floor_08
11. floor_09
12. floor_10_champion
13. details

A section identifier is stable persistence data. Ordering is deterministic.

## 2. Build lifecycle

A new instance follows:

`ALLOCATED -> BUILDING -> READY -> ACTIVE -> TEARDOWN -> RELEASED`

Only `READY` or `ACTIVE` may contain Tower participants.

Structure construction is staged. At most the configured bounded amount of structure work may execute in one server tick. The implementation must not loop over every Tower block or every active instance in a single operation.

## 3. Placement semantics

Each section is placed idempotently at a deterministic origin derived from the allocated instance slot and Tower base Y.

Re-placing a section is the recovery/reset operation. Production code does not pre-scan every block to determine whether repair is required.

Templates include intended air inside their own bounded volume so stale blocks cannot survive in canonical empty spaces.

Structure templates must not contain runtime Pokémon, boss, reward, portal-state, or other gameplay entities. Dynamic objects are owned by their respective run systems and tracked by UUID.

## 4. Crash and exception containment

CobbleTowers must not intentionally propagate a recoverable structure/template exception out of the Tower work executor into the Minecraft server loop.

Every placement/cleanup operation must include contextual fault reporting containing at least:

- run UUID
- instance slot
- structure section ID
- operation type
- current run state

A failed section must not advance its persisted completion marker.

A structure operation failure must never cause the allocator to release/reuse that cell while its world state is uncertain.

Errors representing JVM/process integrity failures are not to be swallowed indiscriminately. The implementation should contain normal runtime/template failures while avoiding broad `catch (Throwable)` patterns that conceal `OutOfMemoryError`, `ThreadDeath`, or similar fatal VM conditions.

## 5. Restart reconciliation

Section progress is persisted before/after mutation boundaries.

If restart occurs while section N is in progress, startup does not scan the entire cell. It re-places section N idempotently and then continues subsequent sections.

Interrupted boss battles remain a run-level recovery concern and are restarted at full boss HP; structure reconciliation does not serialize Showdown state.

## 6. Cleanup

Run termination does not synchronously clear the entire Tower cell.

Cleanup is staged by bounded structure regions/sections. The instance slot remains reserved until:

1. Tower-owned dynamic entities/state are removed,
2. structure cleanup/reset completes successfully,
3. final reward persistence is committed,
4. the run manager explicitly releases the instance.

Only then may the allocator hand the slot to another run.

## 7. Explosion policy

Explosions are disabled in `cobbletowers:tower` at the explosion pipeline boundary.

An attempted explosion must not:

- damage entities,
- alter blocks,
- create fire,
- finalize explosion particles,
- finalize explosion sound/effects.

This applies regardless of whether the source is vanilla Minecraft, Cobblemon, CobbleRaids, or another installed mod that uses Minecraft's normal explosion pipeline.

## 8. Server-readiness validation

Before release, structure lifecycle testing must include:

- repeated build/teardown cycles,
- 20 simultaneous private instances,
- forced restart during each structure section,
- missing/corrupt template simulation,
- intentionally thrown placement exceptions,
- no cell release after failed cleanup,
- no participant admission before READY,
- no explosion damage/effects in Tower dimension,
- no duplicate structure work after successful completion,
- bounded per-tick work under concurrency,
- no leaked chunk tickets or runtime entities.

Passing compilation alone is insufficient. The system requires in-game soak and fault-injection validation before it can be called server-safe.
