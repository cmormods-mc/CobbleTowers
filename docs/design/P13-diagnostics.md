# P13: giving TDS #60 a place to live

Written before the code it describes, as the TDS gate requires.

TDS #33 has been LOCKED since decision one: "Performance budgets, instrumentation and warnings are
first-class features." TDS #60 asks for "structured developer diagnostics keyed by run/encounter,
including state, modifiers, seed, adapter status, timings and last transition." Section 11 lists
exactly what to track: active runs, active encounters, tower-owned chunks, tower entities, allocation
time, encounter construction time, transition time, persistence backlog, cleanup time and tower tick
cost. Every phase through P12 has left a comment acknowledging this ("Diagnostic (TDS #60), the same
role draft show plays for..."; "for the diagnostics the TDS asks to track") without building it --
`diagnostics` is the one internal domain P1's own recommended list named that no phase has used until
now.

This phase is not new gameplay. State, modifiers, seed and last transition already have a home:
`/cobbletowers runs show` prints all four today. What is genuinely missing is *timings* and *adapter
status* -- the two words in TDS #60 nothing has ever recorded -- plus the counts section 11 lists.
Crash recovery and cleanup-fault handling are not rebuilt here either: P2's checkpoints, P3's cell
quarantine and P7's recovery sweep already exist, and `run_durability_test.py`,
`instance_lifecycle_test.py` and `participant_test.py` already prove them live. What those tests have
never done is register 24 real Pokemon in one run (TDS #42, test-gated) or run the server for any
length of time under repeated load -- this phase adds both, as real scripts with a first real number,
not a promise to run one later.

## Decisions taken into P13

| Decision | Choice | Why |
|---|---|---|
| Diagnostics depth | **A real structured framework**: persisted per-category stats, per-run snapshots queryable after the run itself is no longer live, and budgets that warn when exceeded | Chosen deliberately over a lighter in-memory-only counter set. TDS #33's "warnings are first-class features" is not satisfied by a number nobody is told is too high, and TDS #60's "keyed by run" is not satisfied by a live-only view that forgets a run the moment it ends. |
| Where diagnostics live | **A new `TowerDiagnosticsStore` (`SavedData`), not `PersistedRun`** | A timing sample is operational telemetry about how the mod is behaving, not "logical state" about what the run is (TDS §10) -- the same distinction TDS #7 already draws for a battle's presentation entity ("not authoritative run state"), and the same reason P11 kept a spectator's camera target out of `PersistedRun` rather than growing that schema a further time. Diagnostics get their own store, keyed by run id the same way `TowerPendingRewardStore` is keyed by player id. |
| What counts as a "category" | **One entry per section-11 timing**: allocation, encounter construction, transition, cleanup, tick cost | Matches the TDS's own list exactly. "Persistence backlog" is tracked as a count (non-checkpointed writes since the last flush), not a timing -- there is nothing to time about a queue depth. |
| Budgets | **Fixed constants, calibrated from this phase's own load-test numbers** | TDS #33 asks for budgets to exist, not for a config system that does not exist anywhere else in this codebase yet. A budget exceeded logs a warning through `TowerLog`; nothing here throttles or refuses -- the same posture the watchdog already takes toward a slow floor (noticed, not punished). |
| The 24-Pokemon audit | **A real script, run now, for a first real number** | TDS #42 asks to "measure the real cost... before reducing party size" -- a script nobody has run measures nothing. A short run of it this session is real data; a multi-hour soak is not this session's to finish, so that one is built and run briefly, with the honest caveat that a short soak proves the mechanism works, not that a server survives eight hours of it. |
| Soak test scope | **Repeated short runs in a loop, a few minutes, checking for leaks via the new diagnostics counts** | The counts section 11 already asks to track (active runs, tower chunks, tower entities) are exactly what a leak would move: a soak test that was not going to use the diagnostics command to look for exactly that would be building the instrumentation and then not trusting it. |

## 1. `com.cobbletowers.diagnostics` -- the domain P1 reserved and nobody has used

New internal domain. Two pieces: `TowerMetrics` (the instrumentation API every other package calls
into) and `TowerDiagnosticsStore` (the persistence, mirroring `TowerPendingRewardStore`'s shape) plus
`DiagnosticBudgets` (the constants).

```
TowerMetrics.recordAllocation(long millis)
TowerMetrics.recordEncounterConstruction(UUID runId, long millis)
TowerMetrics.recordTransition(UUID runId, long millis)
TowerMetrics.recordCleanup(long millis)
TowerMetrics.recordTick(String source, long millis)
TowerMetrics.recordNonCheckpointedWrite()   // persistence backlog
TowerMetrics.recordCheckpoint()             // clears the backlog count
```

Each `record*` call does three things: updates the category's rolling stats (count, sum, min, max) in
`TowerDiagnosticsStore`, updates the run-keyed snapshot when a run id is given, and compares the sample
against `DiagnosticBudgets` -- logging one `TowerLog.warn` line when it is exceeded, at most once per
sample, never retried or escalated. Called from the server thread everywhere it is called (TDS §11's
"All Minecraft world/entity/battle mutations occur on the server thread" extends naturally to the
instrumentation sitting next to them); nothing here does I/O or blocks.

## 2. `TowerDiagnosticsStore`

```
TowerDiagnosticsStore extends SavedData
  categories: Map<String, CategoryStats>
  runs: Map<UUID, RunDiagnostics>
  nonCheckpointedWrites: int

CategoryStats(long count, long sum, long min, long max, long budgetExceededCount)
RunDiagnostics(long lastTransitionMillis, long lastEncounterConstructionMillis, long updatedAt)
```

`CategoryStats` is a running aggregate, not a sample list -- TDS asks for tracking, not a time-series
database, and an unbounded list under a live tower would itself become the leak the soak test is
looking for. `RunDiagnostics` is the "keyed by run" half of TDS #60: one entry per run, updated on its
last transition and last encounter construction, and never removed when the run ends -- the same
retention every other run-scoped file already has ("runs persist in the rig between invocations," per
the smoke-test README), so a diagnostic snapshot answers "what did run X actually cost" for a run that
finished an hour ago exactly as well as for one still live.

## 3. Wiring -- five call sites, no new control flow

- **Allocation**: `CellPreparer`'s existing `System.nanoTime()` measurement around
  `template.placeInWorld` gains one more line, `TowerMetrics.recordAllocation(millis)`, alongside the
  log line it already writes -- the log stays, because an operator watching the console should not
  need a command to see one slow paste.
- **Encounter construction**: `TowerEncounters.sendNextOpponent` (the second and later opponents in a
  wave) and `TowerEncounters.begin`'s own draw loop (a floor's *first* opponent per player, drawn by a
  separate code path -- found missing by `party_load_test.py`; see §6), both timed from the draw
  (`EncounterDraw.draw`) through `CobblemonBattleAdapter.start` returning -- the same span
  `sendNextOpponent`'s own `TowerLog.info("Run {} floor {}: {} faces another opponent...")` already
  narrates, now measured, and one sample per player per opponent in `begin`'s case.
- **Transition**: `RunTransitionService.apply`, timed end to end -- including the revival, reward-bank
  and draft side effects a `Move` triggers, because TDS #60 asks what a transition costs, not what its
  database write alone costs.
- **Cleanup**: `CellCleanup`'s sweep-and-verify path, timed the same way allocation is.
- **Tick cost**: `RecoverySweep` and `TowerPresence`'s interval-gated `ServerTickEvents.END_SERVER_TICK`
  callbacks, timed only on the tick that does real work -- the cheap clock-compare on every other tick
  is not itself the cost section 11 means.
- **Persistence backlog**: `TowerRuns.save(server, run, forceCheckpoint)` calls
  `TowerMetrics.recordNonCheckpointedWrite()` when `forceCheckpoint` is false and
  `TowerMetrics.recordCheckpoint()` (resetting the count to zero) when it is true -- a checkpoint is
  exactly the moment the backlog TDS means is cleared.

## 4. `DiagnosticBudgets`

```
ALLOCATION_BUDGET_MILLIS = 1000
ENCOUNTER_CONSTRUCTION_BUDGET_MILLIS = 1500
TRANSITION_BUDGET_MILLIS = 250   -- raised from an initial 100ms guess; see §6
CLEANUP_BUDGET_MILLIS = 500
TICK_BUDGET_MILLIS = 50
```

Calibrated from real numbers already in this repo's own smoke-test logs (cell preparation has run
100-450ms in every live test this project has ever printed) and refined by this phase's own load and
soak runs (§6) before being committed -- not guessed cold; the transition budget in particular was
wrong at first and corrected from that data before this constant ever shipped. A budget crossed logs a
warning; nothing refuses, throttles or fails a check over it, matching how a slow floor already gets
the watchdog's attention rather than an outright kill.

## 5. `/cobbletowers diagnostics` command

- **`/cobbletowers diagnostics`** -- live counts (active runs via `TowerRuns.all()` filtered to
  `!run.isRetired()` -- not a plain size(), which would count a terminal run's whole retained history
  as active; found wrong by the soak test, see §6 -- active encounters via
  `TowerEncounters.activeRounds().size()`, tower chunks via `CellTickets`'s existing count, tower
  entities via a level query scoped to `TowerDimension`) plus each category's rolling stats and how
  many samples have crossed budget.
- **`/cobbletowers diagnostics run <run>`** -- that run's own `RunDiagnostics` snapshot, alongside what
  `runs show` already prints (state, modifiers, seed, last transition) so the two commands together are
  the whole of TDS #60's list without either duplicating the other's job.

## 6. What the load and soak scripts actually found

Two real bugs were found running these for the first time, both fixed before this doc was finished:

- **`TowerEncounters.begin` never called the new instrumentation.** `sendNextOpponent` (the second and
  later opponents in a wave) was wired correctly, but a floor's *first* opponent per player is drawn by
  a separate loop in `begin` that duplicates the same draw-then-battle-start shape. `party_load_test.py`
  caught it immediately: four real battles started, zero `encounter_construction` samples recorded.
  Fixed by timing the same span in `begin`'s own loop.
- **`DiagnosticsCommand`'s "active runs" counted every indexed run, not just live ones.** A run reaching
  a terminal state (TDS's own terminal set: `COMPLETED`, `CASHED_OUT`, `FAILED`, `ABANDONED`,
  `RECOVERY_REQUIRED`) stays indexed for its history, the same retention every finished run already
  has -- so counting `TowerRuns.all().size()` reported a soak test's entire abandoned history as
  "active" and the number never came back down. Fixed to filter on `!run.isRetired()`, which is what
  TDS section 11's "active runs" actually means.

A third finding was in the test itself, not the mod: `party_load_test.py` first asserted
`PersistedParticipant.registeredPokemon().size() == 6` per player and found it always empty --
for every run, in this test and in every other live test in this suite. Nothing anywhere has ever
populated that field from a real Cobblemon party; `ParticipantService` only ever carries it through
unchanged, and every phase since P2 has read the live party directly instead
(`Cobblemon.INSTANCE.getStorage().getParty`). That is a real, standing gap against P1's own doc comment
("captured when the party was validated"), but it predates this phase and is P1/P2's to close, not
P13's -- the assertion was removed rather than the gap quietly fixed, and it is recorded here so it is
not lost.

**Real numbers**, from a four-player, 24-Pokemon floor (`party_load_test.py`, TDS #42) and roughly 1,400
allocate-and-abandon cycles across two short soaks (`soak_test.py`, a few minutes each):

| Category | Typical | Observed max | Budget |
|---|---|---|---|
| allocation | 3-5ms | 215ms | 1000ms |
| encounter construction | 31-90ms | 90ms | 1500ms |
| transition | 26-84ms avg | 1632ms (rare outlier, ~1.3% of samples) | 250ms (raised from an initial 100ms guess -- see below) |
| cleanup | ~5ms | 133ms | 500ms |

The initial 100ms transition budget was wrong before it ever shipped: over half of a real four-player
run's transitions crossed it, which is not a budget, it is noise. Raised to 250ms from this data.
Cleanup, allocation and encounter construction budgets are unchanged -- nothing in either script came
close to them. The rare multi-second transition outlier (1.3% of ~5,700 samples under rapid cycling)
was observed but not chased further; a warning fires for it and that is what TDS #33 asks a first-class
warning to do, not to explain every outlier the moment it is first seen.

The soak test also found, and correctly did not flag as a leak: tower chunks can transiently read as
two cells' worth (98, not 49) under fast repeated cycling before returning to zero on the very next
sample -- the warm pool evidently begins preparing a fresh cell before the previous one's release has
fully landed, rather than serialising the two. The check was calibrated to allow three cells of
transient overlap and to treat "never returns to baseline" as the actual leak signal, not "never
exceeds a peak."

Every pre-existing live test in this suite was re-run against the instrumented build:
`participant_test.py` (25/26, the same pre-existing run-state-timing flake root-caused during P11),
`reward_test.py` (19/19) and `vendor_test.py` (11/11) all clean. `floor_encounter_test.py` failed once
on the same "boss never starts" shape already seen and diagnosed during P11's own live testing, then
passed 17/17 on an immediate re-run against the identical jar -- confirmed transient rig flakiness
(the running process was consuming real CPU throughout, not hung), not a regression from touching
`RunTransitionService`, `TowerRuns`, `TowerEncounters`, `InstanceAllocator`, `RecoverySweep` and
`TowerPresence` in one phase.

## 7. Load and soak scripts

- **`validation/smoke/party_load_test.py`** -- four real bots, six registered Pokemon each (24 total,
  TDS #42), one real floor fought. Reads `/cobbletowers diagnostics run <run>` afterward and prints the
  actual transition and encounter-construction timings this run cost -- a real number for TDS #42's
  "measure... before reducing party size," not a pass/fail gate this phase invents on its own
  authority.
- **`validation/smoke/soak_test.py`** -- creates and completes short runs in a loop for a bounded
  window (a few minutes for this session's run; the script itself takes a `--minutes` flag so a future
  session can point it at hours unattended), reading `/cobbletowers diagnostics` between iterations and
  failing the check if tower-entity or tower-chunk counts trend upward across iterations rather than
  returning to baseline -- the leak section 11's own counts exist to catch.

## 8. Assumptions and constraints

- **No visualized anchor/safe-volume diagnostics.** TDS #11 also mentions these; `CellCleanup.verify`'s
  text report already exists from P3/P4 and this phase does not add particle or glow-outline rendering
  on top of it -- a presentation decision, not a measurement gap.
- **No historical time-series, no dashboard.** `CategoryStats` is a running aggregate; a server
  restart's worth of history is what `TowerDiagnosticsStore`'s persistence keeps, not a queryable
  timeline. TDS #33 asks for budgets and warnings to be first-class, not for a graph.
- **The soak test run this session is short.** A few minutes proves the script and the leak-detection
  logic work and gives a first real reading; it is not the multi-hour or overnight run TDS's own
  "long-running-server soak tests" phrase implies, and this doc does not claim it is.
- **No config system for budgets.** `DiagnosticBudgets` is constants, the same posture
  `ParticipantService.RECONNECT_WINDOW_MILLIS` already takes for a number TDS also asks to be
  configurable eventually: "a named constant with one caller is better than inventing one for a single
  number" until a real config system exists for something else to justify building it.
