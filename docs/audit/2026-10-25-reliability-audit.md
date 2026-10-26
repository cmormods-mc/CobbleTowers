# Reliability audit, 2026-10-25

Static audit of the server-side code, done while the owner slept, with permission to fix anything that does not change
gameplay or design. Nothing was run on a live server: the rig ports were held by a server the owner had running
(PID 18544, started 2026-10-24 09:42), which was left alone. Everything below marked "unverified live" needs the rig.

## Baseline

| | |
|---|---|
| Target | Minecraft 1.21.1, Java 21, Fabric Loader 0.17.2, Fabric API 0.116.6, Cobblemon 1.8.2 (binary-checked against 1.8.1 too), CobbleRaids API only |
| Source | 380 main + 141 test files; 5 mixins (all `defaultRequire: 1`); one access widener entry |
| Unit tests before | 889 run, 3 skipped, 0 failed |
| Unit tests after | 899 run, 3 skipped, 0 failed |
| `validation/ci_local.sh` | passes (architecture, API, persistence validators; binary compat with Cobblemon 1.8.2 and 1.8.1) |
| Live smoke rig | NOT run (ports in use) |

## Inspected

Read in full: `CobblemonBattleAdapter`, `TowerBossAdapter`, `FloorBoss`, `FloorPayout`, `RewardBankService`,
`RewardDelivery`, `TowerPendingRewardStore`, `TowerNetworking`, `PageActions`, `WebGuiBridge.onPage`, `TowerPresence`,
`RunExitService`, `TowerRuns`, `TowerRunStore`, all five mixins, `FaintDropGuard`, `TowerDropGuard`, `WornModelService`,
`NextOpponentRetries`, `TowerKeys` (tick, config, give-back). Pattern sweeps over the whole tree: every
`END_SERVER_TICK`, every Cobblemon event subscriber, every JOIN / respawn / entity-load callback, every static
UUID-keyed collection, every `catch (Exception|Throwable)`, every `orElseThrow`. Cobblemon 1.8.2 and 1.8.1 jars were
read with `javap` for the events used.

Not read: the rest of `lobby/`, `intermission/`, `season/`, `mastery/`, `club/`, `echo/`, `trial/`, `rental/`,
`definition/` parsing, `armor/` rendering, `instance/` allocator and cell clearing, `RunTransitionService` internals,
the web UI source. These have their own tests and soaks; they are unaudited here, not cleared.

## Fixed

| ID | Sev | Finding | Fix |
|---|---|---|---|
| F1 | P3 (was P1) | `BATTLE_FLED` was never subscribed. A fled floor battle fires no victory event, so the round would stay `FIGHTING` until the 10-minute watchdog. | Fled counts as a loss, once (idempotent), a tick later. **Verified live: not reachable today.** Cobblemon flees a battle only when the player is farther than the opponent's flee distance, and the tower starts every floor battle with `fleeDistance = Float.MAX_VALUE`; the Run button only raises an event and shows a prompt (seen live: `run_prompt`, battle unchanged). The handler is a safeguard for a changed flee distance or another mod. |
| F2 | P1 | A battle result with no participant left online was dropped: no `onResolved`, opponent leaked, then the whole floor parked 30 min later. | The adapter keeps the running server (`SERVER_STARTED`), no longer finds it through a participant. |
| F3 | P1 | Per-player "activity" was a start or a faint, so a long fight with no faint could drop an active player at 10 min. | A once-a-second poll of the battle's turn counter counts as activity. |
| F16 | P1 | `RewardDelivery` drained the queue before delivering; an item that did not resolve (card mod absent) or a throw lost it and every reward after it, against its own "not lost" comment. | Each reward is delivered on its own; anything not handed over is put back on the queue. |
| F17 | P1 | Overflow rewards (full inventory) were dropped with no thrower, and `TowerDropGuard` deletes every thrower-less item in the tower dimension, where rewards are delivered. | Overflow drops name the player as thrower. Confirmed from vanilla bytecode; unverified live. |
| F18 | P0 class | `NextOpponentRetries.tick` called `retryNextOpponent` unguarded inside a server tick callback (an exception there ends the server) and cleared its queue first, so one throw lost the rest. | Each retry is contained. The adapter's own tick callback is contained as well. |
| F19 | P1 | `TowerPresence.sweep` ran grace windows, draft settling and the floor watchdog in one guarded block: a fault in an early step left every floor unwatched, every sweep. | Each step, each floor and each draft settle fails on its own. |
| F20 | P1 | `RunExitService.sweep` released retired runs' cells in one loop: one run that threw blocked every cell behind it, every second. | Each release fails alone, logged once per run. |
| F4 | P2 | No limit on client screen actions (native payloads or web page): a modified client could force thousands of screen rebuilds a tick. | One gate: token bucket 30 burst / 20 per second per player, and an exception ends the action, not the tick. |
| F5 | P2 | Five exit paths each hand-cleared a subset of the battle indexes (`PROBE_AT` was cleared by none). | `BattleIndex` with one `register` and one `forget`; Cobblemon-free, so unit-tested. |
| F6, F14, F15 | P3 | `endPlayer` copied every battle per call; shutdown left retry/poll state; duplicate `inBattle`/`inLiveBattle`. | Tidied. |

## Open, by decision (design, not touched)

- **F7 (P3)** A tie (both sides faint the same turn; empty winner list) counts as a player loss. Whether a tie should re-roll is a design call.
- **F8 (P3)** `retryNextOpponent` gives up as `CLEARED` after 10 tries, so a wave never fought is credited. Lenient on purpose or not?
- **F21 (P3)** A full-inventory player who is given back a tower key at login (`TowerKeys.giveBack`) drops it with no thrower; only matters if they log in inside the tower dimension.

## Open, harmless or unverified

- **F9 (P3)** `handleIntermissionAction` / `handlePurchase` had no own `try/catch`; now covered by the shared gate.
- **F10 (P3)** `cobbletowers.accesswidener` widens `net/minecraft/client/gui/Font`, left from the archived native UI; nothing in `src/main` uses it. Harmless on a server (an entry for an absent class is ignored); remove together with the loom setting when convenient.
- **F11 (P3)** 11 C2S payload types from the archived native UI are still registered. They are rate-limited and validated now; removing them is a separate decision (the S2C side feeds `ClientUi.canShow`).
- **F13 (P3)** `sweepOrphanOpponents` scans every entity in the tower dimension every 5 s. Needed for opponents left by a previous crash, so it was kept. Cost grows with entities in the dimension; measure with `TowerMetrics` before changing.
- **F22 (P3, Potential)** `TowerBossAdapter.start` registers its binding after `CobbleRaidsEncounters.start` returns. CobbleRaids only returns `Started` after its session exists and a battle cannot end inside `start`, so this is safe today; registering before the call would be sturdier if that ever changes.
- `FaintDropGuard.BEFORE` and `ClubService.INVITES` are bounded by player count and cleared on stop: not leaks.
- `TowerRunStore` rewrites the whole file on every checkpoint, and runs are kept 7 days; fine now, grows with run count. Vanilla `SavedData` writes the file in place (no temp file), so a crash during the write can corrupt that one file; load drops unreadable records one by one, which limits the damage.

## Verification still owed (needs the rig)

1. Flee: `battlebot.js` sends a flee request in a floor battle; expect "counted as lost" in the log within a tick and the floor resolving.
2. Disconnect at the final faint (`disconnect_race_test.py`): the floor resolves, no stray opponent.
3. A stall-moveset fight past 10 minutes with no faint: the player is not dropped.
4. A full inventory at a milestone: overflow lies on the ground, owned by the player, and survives.
5. `bot_stress_test.py` (my standing note: run it after touching battle code) and a soak.
6. A flood of web-page actions from a test page: dropped with one warning per 30 s, the tick time unchanged.

## Second pass (2026-10-26)

Read: `PartyJournalService`, `PartyStorage`, `RentalStorage`, `RunTransitionService`, `InstanceAllocator`,
`IntermissionService`, `IntermissionRound`, `LobbyService.tick`, `RentalDraftService.handle`, `TowerFeatureService`,
`Watching`, `RunLifecycle`, `CellClearJobs`, `CellWarmPool`, `RecoverySweep`, `RentalPartyService`, `AscensionLibRewards`
(retry and invoke), the config loaders, the claim paths (club, track, season), the web UI's HTML-building code.
Cleared with no change: Pokemon moves (two-phase with rollback, rental deletes tag-guarded), every config loader
(guarded, ranges checked), client-string parsing (all `tryParse`/bounds-checked), web escaping, the settlement retry.

| ID | Sev | Finding | Fix |
|---|---|---|---|
| F23 | P1 | `InstanceAllocator.release` dropped the lease in `finally` even when the reset threw, so a cell the finished run still named could be handed to a new run and then wiped by the exit sweep's retry. | A throw quarantines the cell; `releaseCell` never resets a cell another run holds (it only clears the stale claim). |
| F24 | P1 | `RunTransitionService.apply` ran revive, draft, intermission, reward banking, cell release and the floor hooks in one sequence after saving; a fault in an early one skipped banking and release. | Each effect runs on its own and logs its own fault. |
| F25 | P1 | `RunLifecycle`: a throw from the structure rebuild left a run on a floor nothing fought; a floor that failed after starting a battle parked the run with that battle still bound. | Rebuild throw parks the run; a failed floor ends its battles first. |
| F26 | P2 | One broken journal entry stopped every player's Pokemon restore each sweep; a full box repeated its error line every second. | Per-player restore, logged once. |
| F27 | P2 | Intermission, lobby, recovery, rental, cell-clear and warm-build ticks each aborted for everything after one failing item, and repeated every tick. | Each item fails alone; a failed lobby cancels its countdown; a failed clear/build is dropped. |
| F28 | P3 | A malformed `contract-reroll` argument threw. | Answers "Unsupported action." |

## Third pass (2026-10-26)

Read: `ParticipantService`, `DraftService` (open, vote), `DraftVote`, `VendorPurchaseService`, `WornSets`, `LobbyLaunch`,
`EchoService`, `EchoDuels`, `SeasonProgressService`, `MasteryService` (transitions, cycle clear), `TowerRunStatsStore`,
`TowerWalletStore`, `TowerDefinitionRegistry` (reload), `WebUiFiles`, `WebGuiLink`, `PageSession`, plus sweeps for
unchecked `get(0)`, division by variable denominators, unsafe HTML in the web UI, and every static UUID-keyed map.

| ID | Sev | Finding | Fix |
|---|---|---|---|
| F29 | P1 | `VendorPurchaseService` debited and flushed before applying the effect; an effect that threw charged the player for nothing and left the purchase uncounted. | The price is credited back and the player told they were not charged (`Result.FAILED`). |
| F30 | P2 | Armor set attribute bonuses are transient modifiers; a respawn builds a new player without them, but the cache still matched, so with `keepInventory` they were never reapplied. | The refresh also checks the modifiers are actually on the player. |
| F31 | P1 | `LobbyLaunch.launch`: a throw after the run was saved left it registered with nothing started, the party moved, and the team unable to retry ("already in a run"). | The launch abandons the run and restores the parties on a throw. |
| F32 | P3 | Per-run stats were removed only when a run ended normally. | Stats of runs that no longer exist are dropped when the runs load (tested). |

**Verified, not assumed:** WebGUI 1.8.1 hops to the server thread (`MinecraftServer.execute`) before firing the page event
the bridge listens to, so every handler behind it runs on the server thread as the code assumes. Cobblemon 1.8.2 has
`BATTLE_FLED`; 1.8.1 lacks `BATTLE_ENDED` (the compatibility gate caught that).

**Cleared with no change:** `ParticipantService`, draft voting and tie-break (floorMod), wallet (long, never negative),
claim paths (idempotent by key), mastery cycle-clear (only from `FLOOR_RESOLVING`), `ContractSchedule` (the free-index
search always terminates), tower floor counts (validated >= 1), season step cost (validated >= 1), reload (prepare off
thread, content swapped as one volatile write), `WebUiFiles` (a bundle copy, stamp written last), web HTML building
(escaped; the unescaped fields are server-built numbers).

## Performance evidence

Measured with a throwaway test (not committed), synthetic finished runs, warm JIT, serialise + gzip of the whole runs
file, which every checkpoint rewrites:

| retained runs | gzipped | time |
|---|---|---|
| 100 | 2.7 KB | 1.2 ms |
| 1,000 | 22 KB | 5.3 ms |
| 5,000 | 109 KB | 23.7 ms |

Real runs carry a ledger, modifiers and a journal, so budget 3-5x. A checkpoint also writes every other dirty store.
At 1,000 retained runs and a few checkpoints per floor this is a few percent of a tick at worst; it grows linearly with
retained runs (7 days). Everything else in the tick path is either per-N-ticks, bounded, or throttled (see the
`END_SERVER_TICK` list in the first pass). No unbounded per-tick scan was found.

## Verification matrix

| Area | Verified how | Result |
|---|---|---|
| Battle index, turn tracker, action rate, run stats | unit tests (new) | pass |
| Everything else changed | `ci_local.sh` (build, 900+ unit tests, architecture / API / persistence validators, binary compat with Cobblemon 1.8.2 and 1.8.1) | pass |
| Flee, disconnect at last faint, no-faint stall, overflow reward, vendor refund, respawn bonus, failed launch, failed cell release | live rig | NOT RUN (ports held by the owner's server) |
| Soak, `bot_stress_test.py` | live rig | NOT RUN |

## Residual risk

- Behaviour that only a live Cobblemon shows: whether a floor battle can be fled, what `battle.getTurn()` does around forced switches, whether `Player.drop` with a thrower is picked up by teammates as expected.
- Vanilla `SavedData` writes in place; a crash during a write can corrupt that one file. Loads drop unreadable records one at a time, which limits the damage; nothing here can make the write atomic.
- Cross-store atomicity: an item handed to an inventory and the claim record are in different files (player data, mod data), so a crash between them can repeat or lose one grant. Claims are ordered to repeat rather than lose.
- Unaudited: the web UI's JS logic, art / structure resources, the rest of `trial/` scheduling and `definition/` field parsing (covered by their validators and tests), and the Showdown JS extensions.

## Live verification (2026-10-26, throwaway server on ports 25566/25576)

The new jar boots on Cobblemon 1.8.2 (mixins apply, 5 towers resolve, Done in 3.8 s). `bot_stress_test.py` (patched copy
on the alternate ports) beat 5 of 6 opponents in 8-10 s each with the changed adapter: start, win, resolve, discard and
cleanup work live.

**F33 (P1, not caused by this audit, unresolved): floor battles intermittently never advance.** Reproduces in the
stress rig at roughly 35-40% of rounds (pre-change build 5/12; this audit's build 1/6, 3/10, 5/12, 4/12; only the required
mods 4/12), often in clusters of 2-3. Trace (bot packet log with timestamps against the server log):
- The battle starts and the bot receives its request and "choose now" within about a second, answers, and the server
  echoes the choice. Then no turn result ever comes back: no further request, no messages, until the run is abandoned.
  Healthy battles get the next request and messages about 50 ms after the answer.
- Your probe line shows it on the server: `p1{mustChoose=false,request=false,responses=0} p2{...}` at turn 1.
- The server is not hung: a thread dump during a stall shows the server thread idle, nothing blocked, no deadlock.
- Ruled out: this audit's changes (the pre-change build stalls too); AscensionLib, cards, accessories, owo, WebGUI (still
  stalls without them); `battle.end()` versus `battle.stop()` when the tower force-ends a battle (no difference).
- Not ruled out: Cobblemon's Showdown service or CobbleRaids' Showdown/battle hooks (CobbleRaids is a required mod and
  patches the select-actions and Showdown paths). Stalls start after a few battles in one session.
Reproduce: copy `validation/smoke` with ports changed, run `bot_stress_test.py --rounds 12` against a copy of a rig;
add `BOT_TRACE=1` and a timestamp prefix to the bot's `console.log` to see the packet gap.
This is very likely the same stall your probe commit (ded11a6) was added for. A mitigation the tower could add: on the
20 s probe, end the battle and start a fresh one for that player (one retry) before the 10-minute watchdog; but the
stalls come in clusters, so the cause itself needs finding on the Cobblemon / CobbleRaids side first.

## For the owner to decide or check

See the end of the conversation summary; the same list is in memory (`cobbletowers-reliability-audit-2026-10-25`).

## F33 follow-up (2026-10-26): cause found, recovered in the tower

Showdown trace on the rig (Cobblemon 1.8.2): in a stalled battle Showdown receives `>p1 move ...` and never `>p2 ...`.
Cobblemon's `checkForInputDispatch` writes the actors that have answered, then clears every actor's responses and request;
the opponent's request had not been marked as awaiting a choice yet, so its choice is never written and the turn waits
forever. Not a Showdown, CobbleRaids or JS fault (no JS error, every message that reached Showdown was handled).
Mitigation: the 20 s probe now writes `>pN default` for an idle AI actor (`nudgeIdleOpponents`). Stress rig: 12/12
beaten, two stalls recovered in 28 s (before: about 40% of rounds timed out).

## Owner decisions applied (2026-10-26)

Tie counts as a player win; a wave that cannot start parks the run (technical failure) instead of crediting it; a fled
battle is a knockout; overflow rewards are held for `/tower claim` with a reminder on leaving the tower; a reward whose
item no longer exists is discarded with an error, one that throws is held, nothing re-queues on its own; the native C2S
payloads and the client access widener are removed; the action rate limit stays.
