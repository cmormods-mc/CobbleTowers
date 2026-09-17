# P2: persistence, the run registry, checkpoints and migrations

Written before the code it describes, as the TDS gate requires: *"Every code section delivered for
this project must be preceded by detailed notes covering exact functionality, architectural role,
important behavior, performance considerations and assumptions/constraints."*

P1 shipped contracts with nothing driving them. P2 makes a run something the server owns: stored,
checkpointed, reloaded and recovered. It still has nowhere to stand — no dimension, no instance, no
battle. That is P3/P4.

## Decisions taken into P2

| Decision | Choice | Why |
|---|---|---|
| Live run type | **The `PersistedRun` record is the live run** | A mutable `TowerRun` wrapper beside it would be a second source of truth and a place for the two to diverge. Mutations replace the record. A wrapper earns its place in P3, when a run holds an instance handle that must not be persisted. |
| Checkpoint policy | **Read off P1's table** | `Transition.checkpoint()` and `keyTemplate` already say which moves force a write. Restating that in the service would be the same rule in two places. |
| Retention | **Prune terminal runs older than 7 days, on load** | The SavedData file is rewritten whole on every save. CobbleRaids' `cobbleraids_player_records.dat` grows forever and is a known "fix it if it ever shows up"; here it costs one field to not have the problem. A constant, not config -- this mod has no config system yet, and inventing one mid-phase is worse than a named constant with one caller. |
| Resuming a run | **Stored, but refused in P2** | `checkpointState` is written so the data is there, and `RECOVERY_COMPLETED` is refused as `NOT_RESUMABLE_YET`: there is no instance to resume into until P3/P4. Parking is the honest outcome; pretending to resume is not. |
| `updatedAt` on v1 | **Extend v1 rather than bump to v2** | P2 is the first code that ever writes this file. No world contains one, so there is no old shape to migrate from. Bumping the version here would add a migration step whose "before" state has never existed. |

## 1. `persistence/TowerRunStore`

`extends SavedData`, one file per server on the overworld's `DimensionDataStorage`, file id
`cobbletowers_runs`. Directly follows CobbleRaids' `PendingRewardStore`.

- Holds `Map<UUID, PersistedRun>`, serialized through the existing `PersistedRun.toTag`/`fromTag`.
- **A malformed entry is dropped with a warning, never thrown.** Same rule as
  `TowerDefinitionRegistry`, for the same reason: one bad record must not stop a dedicated server
  from starting. The difference from a datapack file is that this one is ours, so the warning names
  the run id and says the record was discarded.
- **Retention** is applied at load: a run in a terminal state whose `updatedAt` is older than
  `TERMINAL_RETENTION` (7 days) is dropped, and the count is logged once.

### Dirty state versus a forced checkpoint

Two write paths, and the difference is the whole point of TDS #5C:

- `persist(server)` mirrors the run into the SavedData and marks it dirty. Minecraft writes it at the
  next autosave, minutes away.
- `checkpoint(server)` does that **and calls `DimensionDataStorage.save()` synchronously**.

This is the shape of `RaidRewardService.persistNow`, which CobbleRaids proved live: on the unfixed
jar a hard kill lost a granted reward, on the fixed jar it survived. The cost is bounded because
`SavedData.save` returns early unless the file is dirty and `NbtIo.writeCompressed` streams gzip
without an fsync.

## 2. `runtime/` — one domain for live state and the rules over it

`RunTransitions` and its test **move from `run/` to `runtime/`**. `com.cobbletowers.run` beside
`com.cobbletowers.runtime` would be two names for one idea, and TDS §9 names `runtime`,
`persistence` and `migration` as the internal domains. Pure rename; no logic changes.

- **`runtime/TowerRuns`** — the index. `runId -> PersistedRun` and `playerId -> runId`, both plain
  maps: TDS §11 forbids per-tick scans of tower players, so every lookup is a hit rather than a
  search. Loaded once at server start and cleared on `SERVER_STOPPED`, because these statics outlive
  a world on an integrated client and would otherwise be read back against the next one.
- **`runtime/RunTransitionService`** — the only thing that moves a run.

### Applying an event

1. Find the run. Unknown id is a refusal, not an exception.
2. Look up `(state, event)` in the table. No entry is `ILLEGAL_EVENT`, naming what *is* legal from
   there, since the caller is usually a command.
3. `RECOVERY_COMPLETED` is refused as `NOT_RESUMABLE_YET` (above).
4. Compute the idempotency key with `Transition.key(runId, floorIndex)` — **the floor before the
   move**, which is what makes `{nextFloor}` mean the floor being opened.
5. If that key is already in `committedTransactions`, refuse as `KEY_REUSED`. Two different
   outcomes under one key is the failure the key exists to prevent, so it is reported rather than
   absorbed.

   **There is deliberately no "already applied" success.** The design for this phase had one, and
   writing it showed it could never be reached: applying a move *moves the state*, so a replay of
   the same event finds no transition at all and is refused as `ILLEGAL_EVENT` several steps
   earlier. The state machine is its own replay guard. The committed-key ledger therefore earns its
   place for the economic commits of P9 (TDS #30), where the same key really can arrive twice
   against an unchanged state, and until then as the collision detector above. An unreachable
   success branch would have looked like safety and tested nothing.
6. Build the next record: state, floor (+1 only on `NEXT_FLOOR_CONFIRMED`), `lastCheckpoint` and
   `checkpointState` when the move checkpoints, the key appended, `updatedAt` refreshed.
7. Write: `checkpoint()` when the table says so, `persist()` otherwise.

The index write and the disk write are one call, and the order is what makes them safe rather than a
`finally`: the index is updated, then the store's in-memory map, and only then is the flush
attempted. A flush that throws therefore leaves an index and a store that agree and are merely not
yet on disk, instead of an index holding a state nothing else has. The failure is logged rather than
swallowed, because a checkpoint that did not reach disk is exactly what the caller thought it was
buying -- a barrier that hides that converts a crash into silent corruption, which is what
CobbleRaids shipped in 0.8.49.

## 3. `migration/RunMigrations`

`PersistedRun.fromTag` refuses anything that is not `SCHEMA_VERSION`. P2 puts a chain in front of it:
a known older version is migrated forward step by step; an **unknown future version is still
refused**, never read with today's rules, because reading it wrongly and then saving it that way is
worse than refusing to load it.

Only v1 exists, so there are no steps yet. What ships is the framework, the refusal, and a test that
every version from the oldest known to the current one has a complete path — which is what makes
adding v2 a one-file change rather than a rewrite.

## 4. Recovery at server start

`SERVER_STARTED` loads the store and, for each run:

- terminal -> left alone (already pruned by retention if old),
- `RECOVERY_REQUIRED` -> left alone; it is already parked,
- live -> `TECHNICAL_FAILURE` applied through the normal service, which parks it in
  `RECOVERY_REQUIRED` and keeps `lastCheckpoint`/`checkpointState` intact.

Using the ordinary transition path rather than a special one means recovery obeys the same table,
the same idempotency rule and the same write policy as everything else. A run that is already parked
is skipped before the service is called, which is what keeps a second crash from meeting its own
recovery key again.

## 5. Commands

- `/cobbletowers runs list` and `runs show <id>` — read-only, following `DefinitionsCommand`.
- `/cobbletowers runs advance <id> <event>` and `runs create <tower> <players>` — **dev-only**, gated
  the same way the battle spike is, so the machine can be driven without battles existing. This is
  what the durability test drives over RCON.

## 6. Validation

`validation/validate_persistence.py` walks the compiled classes under `persistence/` and fails if a
field or method signature names a Minecraft entity, level, player or Cobblemon type. TDS §10 says
never persist a live reference; this makes that bite on the day someone adds one, instead of leaving
it as a sentence in a document. It is a property check -- it re-derives its subject from the bytecode
every run, so it survives renames.

## 7. Performance

- **No per-tick work.** Everything is event-driven; nothing sweeps and nothing scans.
- **A checkpoint** is one gzip write of one small dirty file, well under a millisecond at these
  sizes, a handful of times per floor. Non-checkpointing moves cost only a dirty flag.
- **The file is rewritten whole**, so retention pruning is part of the design rather than a later
  fix: a run record is a few hundred bytes, and bounded runs mean a bounded file.
- **Lookups** are two map hits. **Recovery** is a single pass at start, bounded by stored run count.
- **Memory**: one record per run, holding ids and logical state. No entities, so nothing here can
  pin a closed world.

## 8. Assumptions and constraints

- Minecraft 1.21.1, Fabric, Java 21, Cobblemon 1.7.3, CobbleRaids >= 0.8.94-encounter-api.
- P2 does not call CobbleRaids at all, and nothing here reaches a client.
- A run cannot be resumed until P3/P4 give it an instance; P2 stores what a resume will need.
- The state machine is now driven, but only by commands and by recovery. No floor, encounter or
  reward code exists to drive it for real.
