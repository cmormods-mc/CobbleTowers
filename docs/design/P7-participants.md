# P7: knockouts, disconnects, rejoining, and the watchdog

Written before the code it describes, as the TDS gate requires.

P1 built participant state as three orthogonal axes — connection, combat, membership — arguing that
one enum could not hold "knocked out *and* disconnected", and that a reconnect should restore what a
player left rather than guess at it. **Nothing has ever driven them.** Every participant in every run
so far has been `joined()` and stayed that way.

P7 drives them, and closes two gaps earlier phases wrote down rather than solved.

## Decisions taken into P7

| Decision | Choice | Why |
|---|---|---|
| A player who stops answering | **Dropped; the floor continues** | One AFK player cannot hold three others hostage, which is the failure that matters on a real server. The run ends only when everybody is out. (User's call.) |
| Disconnect grace | **Five minutes** | Long enough to survive a crash or a client restart on a modded pack; short enough that a cell and its chunk tickets are not held all evening. (User's call.) |
| A timed-out disconnect | **Not `VOLUNTARILY_LEFT`** | TDS #39 keeps them distinct, and leaving is a choice only the player makes. Timing out means out of the floor, not out by choice. |
| Spectator panel and teammate cycling | **P11** | Those are GUI (TDS #25). This phase makes the state true and puts the player somewhere sensible to watch from. |
| Configurability | **Named constants for now** | TDS #36 says configurable; this mod still has no config system, and inventing one for two numbers is worse than two constants with one caller each -- the same call retention and the warm pool made. |

## 1. `runtime/ParticipantService`

One place that moves a participant and writes the run. Every transition it needs already exists on
`ParticipantState` and was unit-tested in P1: `knockedOut`, `spectating`, `revivePending`, `revived`,
`disconnected`, `reconnected`, `left`. This phase adds no new state -- it finally uses it.

Scattering these across the handlers that trigger them is the alternative, and it is how a rule ends
up meaning two different things in two places.

## 2. Disconnect, grace, and coming back

- **Leaving** touches **only the connection axis**. That is the whole reason P1 separated them: their
  combat state is still there to come back to. Their prerequisite battle ends; CobbleRaids reports the
  same thing for a boss through `onParticipantLeft`, which P6 already wired.
- **The window** is five minutes. The run keeps their slot, and its cell, for that long.
- **Coming back inside the window** returns them as a **spectator until the next intermission**
  (TDS #37), placed on the spectator anchor P4 validated, rejoining properly at the intermission --
  but only while a floor is actually being fought. A run between floors has moved on without them in
  no way at all, and marking them a spectator there would make them sit out an intermission they are
  standing in.
- **Past the window** they are out of the floor, and deliberately *not* marked as having left.

## 3. Knockout and spectating (TDS #25)

Losing a battle is `KNOCKED_OUT`, then `SPECTATING_TEAM`, and the player is moved to the spectator
anchor rather than left standing in an arena they are no longer in. At the intermission they become
`REVIVE_PENDING` and then `ACTIVE` -- the cycle P1 designed and nothing has run until now.

## 4. The watchdog (TDS #59)

A throttled sweep, never per tick, that distinguishes the two cases the TDS asks to distinguish:

- **A player has stopped answering** while the floor is otherwise alive -> that player is dropped, and
  the floor carries on. This is the case P5 hit for real: a bot whose lead could not cast the move it
  kept choosing answered "Invalid action choice" forever, and the floor stayed open, holding the
  round, the cell and its chunk tickets.
- **The whole floor has produced nothing** for far longer -> the run parks as a technical fault,
  because at that point nothing can be trusted to be scored.

Activity is measured in time, from the battle events the adapters already receive. That is honest
about what can be observed without reaching into Showdown's internals, and the interval is set well
beyond a human's thinking time -- the live test measures a real battle's pace before the number is
fixed.

## 5. Leaving on purpose

`VOLUNTARILY_LEFT`, which `ParticipantState` already makes terminal: once set, every transition
returns `this`. If the last member leaves, the run ends.

**Leaving mid-floor is not an escape hatch.** A player who walks out while their battle is still
being fought is dropped from the floor exactly as a disconnect is, so if they were the last one
fighting, the floor settles as a wipe and the unclaimed pool is forfeited. Leaving between floors
ends the run as `ABANDONED` instead. That difference is the point: cashing out at an intermission is
supposed to be a decision, and a run that could be abandoned mid-battle to dodge a loss would make it
a formality. Both paths were run live.

## 6. Recovery sweeps the cell it parks

The gap P5 wrote down: a crash mid-floor strands whatever was fighting -- a player's own Pokemon as
readily as an opponent -- and the next tenant's release quarantines the cell for contents it did not
put there. Correct behaviour, reporting somebody else's mess. Recovery now resets the cell of a run
it parks; a cell that cannot be swept is quarantined, as before.

## 7. What building it changed

- **Ending a battle was only ever half done.** `endRun` forgot the battle and discarded the
  opponent, but never told Cobblemon, so Showdown kept the actors and the player kept a battle UI
  they could not leave. It had never shown because every path that used it also ended the run. A
  dropped player stays on the server, so it would have shown immediately. Both paths now call
  `PokemonBattle.end()`, which is what CobbleRaids does wherever no win packet is coming.
- **A gate on the root command node gates everything anybody ever hangs beneath it.** Brigadier
  merges a re-registered literal into the node already there and keeps *that* node's requirement, so
  four files each registering `cobbletowers` with `hasPermission(2)` meant the first one decided for
  all of them -- and `runs leave`, the one subcommand a player is meant to run, would have been
  silently unavailable. The permission moved down to each subcommand.
- **The watchdog needed a way to be run.** Its caps are ten and thirty minutes, and a test that waits
  those out is a test nobody runs. `/cobbletowers runs watchdog player|floor` runs the real sweep
  over the real floors with the clock wound past a cap -- no test-only threshold, no second code
  path, and useful to an operator asking what the watchdog makes of a floor that looks stuck.
- **The recovery sweep could not be four lines inside `RunRecovery`.** At the moment recovery runs
  the dimension holds no tickets, so the cell's chunks are not loaded -- and an unloaded chunk
  reports no entities. Swept there, it would have returned "clean" every time and been believed,
  which is the exact shape of the bug P3 and P4 each hit once. So the sweep takes the cell's tickets,
  waits for the chunks *and* their entity sections, then looks, then gives the tickets back.

- **A player's own Pokemon was being left standing in the cell.** Opponents were tidied up; the
  party's lead was not, and the cleanup sweep does not care whose an entity is -- so every completed
  run would have quarantined its cell. The floor now recalls the party when it resolves and again
  before the cell is handed back. It had been invisible because the live check for it probed with
  `execute ... run say`, which returns nothing over RCON: the check could only ever pass. Both the
  probe and the leak are fixed, and `floor_encounter_test.py` now says "the party included".

## 8. Performance

- **The watchdog is the only new recurring work**, throttled, over active floors only, and doing
  nothing at all when no floor is running.
- **Disconnect and join are events**, not polling.
- Participant changes are a few fields, written with the floor's existing checkpoint.
- Recovery's sweep costs one P4 reset per interrupted run, once, at start.

## 9. Assumptions and constraints

- No GUI: the spectator panel and teammate cycling are P11's.
- **The watchdog measures time, not turns.** Cobblemon raises no per-turn event, so the signals are
  the two it does raise: a battle starting, and something fainting in it. A battle producing neither
  for ten minutes is not being played. A future phase with a turn hook could sharpen this; until
  then the cap is deliberately generous, because a wrong drop costs a player their floor.
- **The grace window lives in memory.** A restart parks every interrupted run, so there is no floor
  left for a window to expire into; persisting a countdown that could only be read back after it had
  stopped meaning anything would be worse than not persisting it.
- **The recovery sweep reaches the arena, not the whole cell.** It sees what the cell's tickets load,
  which is the 7x7 chunks the arena sits in -- the same limit `CellCleanup.verify` has had since P3,
  and the same reason: an unloaded chunk has nothing to report. Anything a crash leaves outside the
  arena is still found at release, and quarantines the cell as before.
- Nothing here grants anything. P9 still owns the economy.
