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
| F1 | P1 | `BATTLE_FLED` was never subscribed. A fled floor battle fires no victory event, so the round stayed `FIGHTING` until the 10-minute watchdog. | Fled counts as a loss, once (idempotent), a tick later. Unverified live: whether a floor battle can be fled at all. |
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
