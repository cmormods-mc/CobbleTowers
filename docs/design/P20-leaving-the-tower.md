# P20: leaving the tower

Written before the code it describes, as the TDS gate requires.

## What was found

The bot stress test built in P19's follow-up (`bot_stress_test.py`) ran a dozen runs back to back and
logged `Cell N quarantined: 1 player(s) still inside` after every one. Nothing in the mod ever moves a player
**out** of the tower dimension. Reading the code confirms it: the only teleports are the two that put players
*in* (`TowerEncounters`). Each floor layout declares an `exit` anchor, but it is an anchor inside the cell and
nothing reads it. The TDS says nothing about where a player goes when a run ends; it only fixes the arena side.

The consequences, in production rather than in a test:

- When a run reaches a terminal state, `releaseInstance` resets the cell (every block cleared) and verifies
  it is empty. A player still standing in it fails the check, so **the cell is quarantined**. One cell is
  lost per run until an operator clears it by hand; there are 4096, so the tower slowly runs out.
- The player is left standing in an emptied void in the tower dimension.
- A player who leaves a run (`runs leave`) or whose connection is dropped stays in the tower too.
- A player who logs in after their run ended appears wherever they logged out, inside the tower dimension.

Earlier phases never saw this because every live test ends its runs by operator command and then discards the
world.

## Decisions (user, 2026-10-03)

| Question | Answer |
|---|---|
| Where do players go? | **Where they started.** Their position and dimension are remembered when they first enter the tower for a run; if that place is gone, the overworld spawn. |
| When? | **After a short beat** (five seconds) when a run ends, so the reward screen or wipe message can be read; a player who leaves goes at once. |
| Priority | Before the test-tooling work. |

## Rules (one idempotent sweep, like the party journal)

The single rule is: **nobody stays in the tower dimension without a live place in a run.** A once-a-second
sweep enforces it, which covers a normal ending, a leave, a drop, a crash and a late login with one mechanism.

For each online player in the tower dimension:

- an active member of a live run (not terminal, membership not left) stays;
- in a run that ended, they leave once the beat has passed (five seconds after the run's last update);
- anyone else (left voluntarily, no run at all) leaves immediately;
- an operator in creative or spectator mode is left alone, so someone inspecting the tower is not yanked
  out every second.

`ExitRules` (pure) makes that decision from plain values, so it is unit tested without a server.

## Releasing the cell

The cell must not be reset while a player is standing in it. So when a run reaches a terminal state:

- if nobody is in the tower dimension, the cell is released at once, as before;
- otherwise the run keeps its lease for the beat, the players are told, and the **same sweep** releases the
  cell once they have been moved. The release is deferred, never skipped.

Leases are already rebuilt from every run that holds a cell, terminal or not, so a restart during the beat
finds the run still holding its cell and the sweep finishes the job (the beat has long passed by then).

## The return point

`TowerReturnStore` (a `SavedData`, same shape as the others) maps player to `ReturnPoint`: dimension id and
position and facing. It is written, and flushed, in `TowerEncounters.begin` just before the first teleport
into the tower, and only if the player is **not already in the tower dimension**, so a second floor never
overwrites the real starting place with a spot in the arena. It is deleted once the player has been sent
back. A run created by an operator has a return point too, because the hook is the entry, not the lobby.

If the stored dimension no longer exists, or there is no entry at all (a player who somehow arrived another
way), the destination is the overworld's shared spawn.

## Not in P20

- Healing on exit (TDS #16 says no free healing between floors; leaving is not healing).
- A choosable lobby location (the user picked "where they started"; a `setlobby` is a later option).
- Returning a player's *saved Pokemon* layout, which is P18's journal and already independent.

## Tests

- `ExitRulesTest` (7): outside is left alone, an active member stays, the beat then leave, someone who left
  or has no run goes at once, an operator in creative or spectator is exempt, a restart long after the end
  leaves straight away, and the release timing.
- `TowerReturnStoreTest` (4): round trip, a later departure replaces the earlier one, removal, one bad entry
  does not take a good one with it.
- Live `exit_test.py` (12/12): both players start in the overworld and a started run puts them in the tower;
  a player who leaves is sent out within seconds and lands where they started while the other stays; when
  the run ends the other is **not** moved the instant it ends, is sent home after the beat and lands where
  they started; **no cell is quarantined**; a player who disconnected inside and logs back in after their run
  ended is moved out on login.
- Re-run clean on the new jar: `participant_test` 26/26, `reward_test` 19/19, `party_registration_test`
  13/13, `lobby_test` 12/12, `intermission_test` 9/9, `milestone_floor_test` 7/7, `vendor_test` 11/11,
  `teammate_vendor_test` 9/9.

## Outcome

Built as designed: `ExitRules` (pure), `ReturnPoint` and `TowerReturnStore`, `RunExitService` (remember, the
once-a-second sweep, evacuate, and the deferral check), `RunTransitionService.releaseCell` split out so the
sweep can finish a deferred release, and a `remember` call in `TowerEncounters.begin`.

One thing the first live run caught: `TowerRuns` drops a run from its player index the moment the run
finishes (so a finished run never stops a player starting another), which made a player whose run had just
ended look like they had never been in one -- so they were moved instantly and the beat never happened.
`standingOf` now looks through every run instead of using that index.

Not proven: a return point whose dimension has gone (falls back to spawn; not exercised live); an operator
in creative being left alone (rule is unit tested, not driven live); a restart in the middle of the beat
(the sweep treats the long-past beat as over, unit tested as a rule).

## Two more cell and battle leaks the same stress test found

Fixed in the same phase because they have the same shape (a finished run costing the tower something) and the
same finder:

- **Dropped items quarantined cells.** A Pokemon that faints drops what Cobblemon's drop rules give it, and
  nothing in a tower picks those up. `CellCleanup.verify` counts any non-player entity as "still inside", so a
  run that happened to drop an item lost its cell. `CellCleanup.sweepDebris` now discards **item entities and
  experience orbs only** before verifying, in `InstanceAllocator.release`. A stray Pokemon or a mob is still a
  surprise and still quarantines, which is what the check is for.
- **Abandoning a run during the boss left the player "in battle".** `TowerBossAdapter.abort` removes the boss
  through CobbleRaids but does not end the player's Pokemon battle with it, and `CobblemonBattleAdapter.endRun`
  only knows battles it started itself. The next battle then failed with Cobblemon's `AlreadyInBattleError`
  until the orphan timed out. `TowerEncounters.abandon` now also ends any battle the run's online participants
  are in (`CobblemonBattleAdapter.endBattleOf`). The refusal is logged in words rather than as an object id.

## The smoke bot

The stress test (`bot_stress_test.py`) deals the bot many opponents in one session and records what happened,
because one lucky pass proves nothing about a failure that depended on the draw. It found, in order: the old
bot's refused moves (no `targetPnx` for a move that must be aimed -- Cobblemon rejects Freeze-Dry without one),
its DEFAULT mode crashing CobbleRaids' patched Showdown on a forced switch, and the three leaks above.

`validation/smoke/battlebot.js` is the replacement, in this repo instead of the rig directory where
`raidbot.js` has vanished before. It decodes Cobblemon's own request packet, picks a move the battle will
accept, supplies the target when the move needs one, and answers a forced switch itself. Result: 12/12
opponents beaten, slowest 14 seconds, no refused choice, no quarantined cell. `SMOKE_BOT=raid` brings the old
bot back.
