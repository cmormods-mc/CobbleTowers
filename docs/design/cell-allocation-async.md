# Cell allocation off the critical path

Status: proposal (2026-10-07), for the owner to choose an option. Nothing is built. Release blocker #3 in `docs/RELEASE-CHECKLIST.md`.

## The problem

Every run needs a cell: about 325,000 blocks over 49 chunks of the tower dimension. All of the work runs **synchronously on the server thread**:

| When | Call | What it does | Measured |
|---|---|---|---|
| Run start / floor change | `CellPreparer.prepare` | `CellTickets.hold` (49 chunks), `reset` (scan every non-air section), `StructureTemplate.placeInWorld`, `CellAnchors.validate` | paste 0.3 to 0.9 s |
| Run end / abandon | `InstanceAllocator.release` | `reset` (clear every non-air block), `CellCleanup.sweepDebris`, `verify`, `CellTickets.drop` | reset ~0.3 s+ |
| After either | `CellWarmPool.topUp` | `allocate` + `prepare` up to 2 cells per structure, at most once per 5 s | same as prepare |

`reset` calls `level.getChunk(x, z)` for every chunk of the cell. That is a **blocking** load: if the chunk was just unloaded and its save is still queued (the previous
release dropped the tickets), the call waits on chunk I/O. A burst of releases and allocations backs the saves up, one blocked `getChunk` stalls the whole server,
and the 60 s watchdog stops it (found 2026-10-05 with `soak_test`, hundreds of cycles a minute; pre-existing since P27).

Two separate problems:
1. **Stalls (lag spikes)**: each paste or reset is 0.3 to 0.9 s of one tick, 6 to 18 ticks. Players on the server feel that on every run start and end, long before the watchdog.
2. **Collapse under bursts**: when blocking chunk loads queue behind saves, the stalls compound until the watchdog fires.

Ordinary load is a few run starts and ends a minute; a busy server at peak could be 10 to 20. That is 10 to 30 s of main-thread blocking per minute in 0.3 to 0.9 s lumps, which
is why this is a blocker for a public release even though a quiet server never notices it.

## Options

### A. Admission control only (small, safe, partial)
Queue run starts, floor changes and releases behind a limiter: at most one heavy operation (paste or reset) per N ticks, and refuse or delay new run starts when the queue is
long ("Your arena is being prepared..."). Fixes problem 2 (collapse), not problem 1 (each op is still a 0.3 to 0.9 s stall).
- Risk: low. Touches the callers' timing, not the block work. Needs a "preparing" state the party can wait in.

### B. Slice the work across ticks (the real fix for stalls)
Turn `prepare` and `release` into jobs with a per-tick time budget (for example 3 ms):
- **reset**: clear a few chunks per tick (the scan is already per chunk, and sections that hold only air are skipped).
- **paste**: `StructureTemplate.placeInWorld` honours `StructurePlaceSettings.setBoundingBox`, so the template can be pasted one chunk-sized box at a time (49 clipped pastes instead of one). Entities are already ignored.
- **chunk loading**: do not call the blocking `getChunk`. Add the ticket, then poll `ServerChunkCache.getChunkNow` (or `getChunkFuture`) each tick and continue when the chunk is full.
- A job state machine per cell: `HOLD -> WAIT_CHUNKS -> RESET -> PASTE -> VALIDATE -> READY`, and `SWEEP -> VERIFY -> DROP -> FREE` for release. A global queue drains by a time budget each tick.
- Callers that expect a cell *now* (`RunTransitionService`, lobby start) need a "cell not ready yet" result: the run waits in a `PREPARING` state and the party sees a progress message. The warm pool hides most of the latency (ready cells are taken instantly), so the common case is unchanged.
- Risk: **high**. It touches run recovery (a crash mid-job must leave the cell quarantined or reset, never half-built), the leasing index, and everything that assumes `prepare` returns a finished floor. Needs the soak and crash tests re-run.

### C. Do less work (cheap wins that combine with A or B)
1. **Skip the redundant reset.** `prepare` always runs `reset` first "so the paste is independent of history". After a verified release the cell is already clean. Track a
   `CLEAN` bit per cell in memory (set by a verified release, cleared on allocate); `prepare` skips `reset` for a clean cell. After a restart all cells are unknown, so the first use of
   each resets once. Saves a full 49-chunk scan per prepare.
2. **Do not drop the tickets immediately on release.** Keep a released cell's chunks loaded for a short grace period (for example 30 s) so a quick re-allocation, or the reset of the
   next prepare, does not have to reload chunks that are still being saved. Smooths the unload/save/reload churn that causes the blocking waits. Costs memory for the grace period only.
3. **Reuse an untouched cell for the same layout.** If an arena's blocks are never changed during a run (players cannot break blocks; entities are swept separately), a released cell
   whose structure matches the next request could skip reset and paste entirely. Needs proof that nothing changes blocks (a block-change counter per cell would give that).
   Highest payoff, but only safe once that is proven.
4. **Top the warm pool up away from run starts**, not in the same tick as an allocation.
5. **Cap burst size**: `soak_test` already paces itself (`--pause 2`); give the server the same guard (a hard limit of heavy jobs per second).

## Recommendation

1. **Now (low risk):** C1 (skip redundant reset), C2 (release grace period), C4, and A (admission control with a visible "preparing" wait). These remove the watchdog collapse and
   roughly halve the work, without changing how a cell is built.
2. **Then:** B for reset and chunk loading (the part that blocks on I/O), keeping the paste synchronous at first. Slicing the paste (per-chunk bounding box) can follow once the
   state machine is trusted.
3. **Later, only with proof:** C3.

Measure before and after with `FrameSampler`-style server numbers: `TowerMetrics.recordAllocation` already times prepares and releases; extend it to record the longest single-tick
stall and the queue depth, and run `soak_test.py` with `--pause 0` as the burst case and `--pause 2` as the ordinary case, watching MSPT.

## Decisions for the owner

- Is "your arena is being prepared" (a short wait under load) acceptable, in exchange for no lag spikes?
- How many simultaneous runs does a target server realistically see at peak? That sets the limiter and whether option B is needed for the first release or can follow it.
- Should a released cell keep its chunks loaded for a grace period (memory for smoothness)?

## Test plan

- Unit tests (no server): the limiter and queue ordering; the `CLEAN` bit transitions (verified release sets it, allocate clears it, restart treats every cell as unknown).
- Rig: `bot_stress_test.py` and `soak_test.py` (both `--pause 0` and `--pause 2`) with MSPT recorded; the cell-reset and quarantine smoke tests; a kill-and-restart during a prepare and during a release (the cell must come back reset or quarantined).
- Live: two bots start and end runs back to back while a third watches the tick time.
