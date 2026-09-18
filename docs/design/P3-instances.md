# P3: the tower dimension, cell leasing and quarantine

Written before the code it describes, as the TDS gate requires.

P2 gave a run durability. It still had nowhere to stand: `RECOVERY_COMPLETED` was refused outright,
because there was no instance to resume into. P3 is the phase that gives a run a place, takes it back
when the run ends, and refuses to hand it on when nobody has established it is clean.

Out of scope, and deliberately: structures, chunk tickets, floors, encounters, rewards, GUI.

## Decisions taken into P3

| Decision | Choice | Why |
|---|---|---|
| Warm pool | **Deferred to P4** (agreed with the user) | Warming a cell means having its structure pasted and validated, which is P4's work. In P3 the pool would be a queue of empty coordinates behind a `prepare` hook that does nothing -- an abstraction with no implementation, which the TDS's own over-abstraction rule warns against. #26 stays locked; only its phase moves. |
| Cell size | **512 blocks, one region file each** | Cells then share neither a chunk nor a region, so one run cannot load, tick or corrupt another's, and a cell's storage is separable. A unit test walks all 4,096 cells and asserts it. |
| Lease ownership | **The run holds it; the index is rebuilt from the runs** | One authority. A second file listing leases could disagree with the runs, and then no code could say which was right. Quarantine is the exception, because it must outlive the run that caused it. |
| Checkpoint vs key | **Separated** (see below) | P3 made a repeat legal for the first time, which broke an assumption P1 had baked in. |
| Resume | **Real now, guarded** | A parked run returns to the state its checkpoint recorded, but only when its cell is still held and not quarantined. |

## 1. The dimension

`data/cobbletowers/dimension_type/tower.json` and `data/cobbletowers/dimension/tower.json`, shipped
in the mod jar. A void flat generator, `natural: false`, `has_raids: false`, and both monster-spawn
light fields at 0 so nothing hostile ever spawns in an arena. The `dimension_type` schema was read
out of `server-1.21.1.jar` rather than recalled, and the whole thing was booted on the rig before
anything was built on top of it.

**The id is effectively permanent.** Adding a datapack dimension to an existing world is supported;
removing one from a world that has used it is not clean. `cobbletowers:tower` is not a name to
revisit.

## 2. `instance/CellGrid`

Index in, coordinates out, no state. One cell per 512-block square with the usable 256 centred
inside it, so a structure that overruns slightly still cannot reach a neighbour. The inverse --
which cell contains this position -- is deliberately *not* "the nearest cell": something standing in
the gap belongs to no run, and saying otherwise would hand one run's cleanup a position in another's
neighbourhood.

## 3. `instance/InstanceAllocator`

Indexed both ways (#34). The next free cell is the first clear bit of a `BitSet`, not a walk over
every cell asking whether it is busy; the choice is split out as `nextFree` so it is testable without
a server.

Two orderings are load-bearing:

- **The lease is written onto the run before the INSTANCE_ALLOCATED checkpoint**, so the checkpoint
  that reaches disk already names the cell. A crash in between would otherwise leave a run past
  allocation holding nothing, and no later event would fix it.
- **`release` drops the lease in a `finally`.** A lease nothing releases is the one leak the
  allocator cannot recover from, because no later event refers to it -- the lesson CobbleRaids
  learned when a fault barrier turned a crash into a permanently stranded raid.

Running out of cells is reported as `ALLOCATION_FAILED`, which the table already maps to
`RECOVERY_REQUIRED`: a full tower is a technical fault, never a player's loss.

## 4. What P3 had to change in P1's table

P1's `Transition` required that a checkpoint and an idempotency key go together. **That conflated two
different things**, and implementing resume is what exposed it:

- a **checkpoint** is durability: write this now, because a crash after it must not undo it;
- a **key** is idempotency of a commit that carries value (TDS #30).

Once a run can be parked, resumed and parked again -- which only became possible in P3 -- the second
park commits the same key a second time and was refused as `KEY_REUSED`, leaving the run live and
unparked. So the two wildcards, abandoning and breaking, now checkpoint and carry **no key**, and the
invariant is one-directional: a key implies a checkpoint, not the reverse.

That change has a consequence worth stating: **only a keyed move updates `lastCheckpoint`.** A
keyless checkpointing move forces a write but leaves the checkpoint where it was -- recording
"committed at RECOVERY_REQUIRED" would make a later resume return the run to the state it was trying
to escape.

## 5. Cleanup verification and quarantine (#35)

Release sweeps the cell and reports **every** stage that fails, not the first: an operator reading a
quarantine wants to know everything left behind. Two stages exist -- no players, no entities. Chunk
tickets are a third that **P4 adds**; a stage that always passed would read as verified and verify
nothing.

**Known limit.** An entity query only sees loaded chunks, so a cell whose chunks are unloaded reports
clean whatever is in it. P4's lifecycle-owned tickets are what make this sound. Until then it catches
the case that actually matters: releasing a cell somebody is still standing in.

Quarantine lives in its own `SavedData` and is **flushed immediately**, for the same reason a run
checkpoint is: the failure that caused it is exactly the kind of event a crash follows, and a
quarantine lost in that crash hands a dirty cell to the next run.

## 6. Resume

`RECOVERY_COMPLETED` returns a parked run to `RunCheckpoint.state` -- the state P2 started storing for
exactly this. Two refusals, both deterministic:

- **`NO_CHECKPOINT`** — parked before it ever committed anything, so there is no state to return to.
  That only happens before allocation, and such a run is abandoned rather than guessed at.
- **`CELL_UNAVAILABLE`** — the lease is gone or the cell is quarantined. Resuming into a cell nobody
  has verified is the thing quarantine exists to prevent.

The cell check lives in `apply`, not `decide`, because whether a cell is usable is a question about
the world and `decide` is deliberately pure.

## 7. Performance

- **Nothing per tick.** Allocation, release and verification are event-driven.
- **Allocation is a `nextClearBit`**, and every lookup is a map hit.
- **Verification is one AABB entity query per release**, bounded by the cell volume.
- **An extra `ServerLevel` costs something even when empty** — it ticks and saves. That is the price
  of #6, and it is one level for the whole server rather than one per run. Natural spawning is off,
  so it is not paying for mob ticking.
- **One region file per cell** keeps a cell's storage separable and stops a quarantined cell bloating
  a neighbour's.

## 8. Assumptions and constraints

- Minecraft 1.21.1, Fabric, Java 21, Cobblemon 1.7.3, CobbleRaids >= 0.8.94-encounter-api.
- 4,096 cells, which is 32,768 blocks square — far inside the world border.
- Cleanup verification is best-effort until P4's tickets exist.
- Nothing here reaches a client, and no floor, encounter or reward code exists to drive it yet.
