# P18: choosing a party from the whole collection, safely

Written before the code it describes, as the TDS gate requires.

P15 made party validation real and P16 let a team start a run, but registration is still "the first six
Pokemon in your party". TDS #41 asks for a preparation screen and the user chose to **pick from party and
PC**. Cobblemon fights with the live party, so a Pokemon in a box cannot battle: registering one means
moving it into the party, and putting everything back when the run ends. A bug in that restore loses or
duplicates a player's Pokemon, which no rerun of anything undoes. Most of this document is about not doing
that.

## Decisions (user, 2026-10-02)

| Question | Answer |
|---|---|
| A registered Pokemon in the PC? | **Swap into the party at lock**; displaced party Pokemon go to the PC, and the layout is restored when the run ends. |
| A registered Pokemon moved out mid-run? | Allowed; the level policy skips it (P15). "Locked" is a soft rule. |
| How cautious is the restore? | **Journal every move.** Before any swap, persist exactly where each moved Pokemon came from; restore replays that, idempotently; a sweep finishes any run that ended unrestored. |
| A moved Pokemon gone at restore time (released, traded)? | **Restore the rest and say what could not be.** Never block a run from ending. |

## 1. What the mod is allowed to touch

Only Pokemon it moves itself, and only to put them back. It never deletes, clones, renames, heals or
re-levels a Pokemon, and it never touches one that is neither registered nor displaced by registering. The
whole feature is a permutation of positions; the set of Pokemon a player owns is the same before, during
and after.

## 2. Two pure functions carry the safety argument

`PartyArrangement` (pure, no Cobblemon) works on a *layout*: which Pokemon id sits in which slot, where a
slot is a party position or a box position.

- **`plan(layout, chosen)`** returns what to move so the party is exactly `chosen`, in that order, and
  the journal of **original** positions (one entry per Pokemon that moves). A chosen Pokemon from the PC
  moves up; every party member that is not chosen moves to the first free box slot. It fails with
  `NO_ROOM` -- before anything is touched -- if the boxes cannot take the displaced Pokemon, and with
  `NOT_OWNED` if a chosen id is in neither the party nor the boxes. A party that already is the chosen
  list plans nothing.
- **`restore(layout, journal)`** returns where each journaled Pokemon should go. It first removes every
  journaled Pokemon from a copy of the layout, then places each at its recorded slot if free. A slot taken
  in the meantime by something else falls back to the first free slot of that kind, then to the boxes. A
  Pokemon not found at all is reported as missing and skipped. Because it starts from "take them all out",
  running it twice, or on a layout that is already restored, changes nothing.

Neither function mutates anything. The Cobblemon-facing adapter turns their answers into moves in **two
phases** -- remove every affected Pokemon from its store, then set each at its target -- so no target is
ever overwritten and no Pokemon is in two places. Both functions have heavy unit tests: a permutation bug
shows up there, not in a player's box.

## 3. The journal

A new `SavedData`, `TowerPartyJournalStore`, one entry per player: the run id and the originals. It is a
separate file from the run record for three reasons: no run-schema bump; it survives a run that is
forgotten or never finished being created; and the sweep iterates the journal, so a run that vanished
cannot hide an unrestored player.

Order is the whole of the safety: **journal, flush to disk, then move.** A crash after the flush and before
the moves leaves a journal describing positions the Pokemon are still at, and restore is a no-op. A crash
after the moves but before Cobblemon's own save reverts the storage to the original layout, and restore is
again a no-op. A crash after both leaves the swapped layout and the journal, and restore puts it back.

## 4. When it is restored

`PartyJournalService.reconcile` runs about once a second and when a player joins. It restores a player's
journal when the player is online, **not in a Cobblemon battle**, and any of: the run no longer exists, the
run is terminal, or the player's membership is `VOLUNTARILY_LEFT`. Then the entry is deleted. One idempotent
mechanism covers a normal end, a leave, a crash and recovery, and a player who was offline when the run
ended (they are restored when they next join). A run parked for recovery is not terminal, so it keeps the
swap until an operator or the recovery path settles it -- the Pokemon are still in the run.

A start that fails after the lock (a rejected party, no cell) abandons the run and restores immediately
rather than waiting for the sweep.

## 5. Choosing

`TowerLobby` keeps each player's chosen list in memory (default: none chosen, which means "the first N of
my party", exactly what P16 does). At launch every chosen Pokemon is looked up in party then boxes, validated
with `PartyValidation` (size cap, fainted), and `plan` is dry-run for every player before anyone is moved;
only if all succeed does the lock begin.

A `RegistrationPayload` lists the player's party and boxes (id, species, level, fainted, where); a
`RegistrationScreen` pages through them and toggles. Commands mirror it: `play register`,
`play register add|remove <id>`, `play register clear`, and an operator `play pokemon <player>` that lists
the same thing for a test.

## Not in P18

- Locking the PC or party UI mid-run (Cobblemon owns them; mid-run moves are allowed by decision).
- Choosing more than `registeredPartySize`, or a different ruleset per tower (still Neutral's `standard`).
- Teammate-targeted vendor purchases (still its own item).

## Tests

- `PartyArrangementTest` (15): promote from a box, reorder in the party, displace, already arranged, a
  Pokemon that does not move is not journaled, not owned, too many, no room, duplicates in the chosen list,
  restore as the exact inverse of plan, restore idempotent (and a no-op on a layout the plan never touched),
  a missing Pokemon, a slot something else took, a fallback that never takes a later Pokemon's home, a
  duplicate journal entry, and a **randomised test of 3000 collections** asserting nobody is ever lost or
  duplicated and `restore(plan(...))` returns the original layout exactly.
- `TowerPartyJournalStoreTest` (4): round trip, removal, and an unreadable entry **kept** and written back
  rather than dropped.
- Live `party_registration_test.py` (13/13, run four times): a bot with six party and two boxed Pokemon
  registers the two boxed ones; the party is exactly those two; the same eight ids exist; the run shows two
  registered; ending the run restores **every Pokemon to its exact original slot**; a hard kill with
  Cobblemon's autosave having written the swapped layout leaves the player swapped across the restart and
  restores them exactly after a recovery abandon; a hard kill before any save (Cobblemon's disk copy is the
  original layout) also ends exactly restored.
- `lobby_test.py` 12/12, `intermission_test.py` 9/9, `participant_test.py` 26/26 re-run clean.

## Outcome

Built as designed: `Slot`, `PartyArrangement` (pure), `PartyJournalEntry` and `TowerPartyJournalStore`,
`PartyStorage` (the Cobblemon adapter, two-phase with rollback), `PartyJournalService` (lock-in, restore,
the once-a-second sweep), per-player chosen lists on `TowerLobby`, the launch flow with dry-run, lock-in and
rollback, `RegistrationStatePayload`, `RegistrationScreen`, and `/cobbletowers play register [toggle <id>|clear]`
plus an operator `play pokemon <player>`.

Things worth recording:

- **Cobblemon saves a player's storage on disconnect and on a 30-second timer (`pokemonSaveIntervalSeconds`),
  never on `save-all`.** The first version of the crash test lost the bot's Pokemon before any of this code
  was involved; the test now persists by disconnecting the bot and lets the timer write the swapped layout.
- **A disconnect during a floor ends the run as a wipe at once**, so a run whose only player drops is FAILED
  within seconds. That exercised the "run ended while the player was offline" path by accident: the
  player's Pokemon came back on rejoin, which is what the sweep is for.
- **RCON joins `runs list` onto one line.** An early version of the test helper took the first UUID in the
  output (an old, abandoned run) and then "restored" the wrong run; it looked like a mod bug and was not.
  Any test that needs one run out of a list should pair each id with the state that follows it.

Not proven: the chooser screen itself; a restore triggered by a player leaving a run (it is the same
`runIsOverFor` rule, unit-level only); `NO_ROOM` live (planned for in the pure tests); a restore blocked by a
Cobblemon battle (the sweep retries).
