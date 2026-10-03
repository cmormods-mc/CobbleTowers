# P17: the intermission, driven by the players

Written before the code it describes, as the TDS gate requires.

P16 lets a team start a run and lands it in a fight on floor 1. After that floor is cleared the server
already does the automatic part (banks the floor, revives the knocked out, opens a modifier draft and
enters INTERMISSION), and then everything stops: leaving the intermission, opening the next floor, and
cashing out are all operator commands. P17 hands them to the players.

## What was found while scoping

- **Nothing rebuilds a cell between floors.** A run leases one cell for its whole life and the floor's
  structure is pasted into it once, at allocation. The tower's own content has two structures
  (`arena_floor` for ordinary floors, `boss_arena` at the F5 and F10 milestones), so floor 5 would be
  fought in floor 4's arena. No test ever got past floor 1, so this never showed. P17 rebuilds the leased
  cell in place (`CellPreparer.reset` then `prepare`) when the next floor's structure differs.
- **`runs cashout` lets any one player end the run for everyone.** It applies `CASH_OUT_CHOSEN` to the
  whole run on a single player's say-so.
- The draft is voted on only through `runs draft vote`; no screen exists for it, and nothing says when
  everyone is done.

## Decisions (user, 2026-10-02)

| Question | Answer |
|---|---|
| Who may cash out? | **A team vote.** A strict majority of the electorate cashes out; a tie does not (see below). |
| What starts the next floor? | **Everyone ready**, then the same five-second countdown P16 uses. Untimed, per TDS #21. |
| How much UI? | **One intermission screen**: modifier voting, a vendor button, Ready, and the cash-out vote. Commands stay as the fallback and for tests. |

Decisions made here, not asked, because each is small and easy to change:

- The **electorate** is `DraftService.voters(run)`, the same set the modifier draft already uses (TDS
  #23): everyone in the run who can still fight. Ready and cash-out count the same people.
- A **tied** cash-out vote does not cash out. The TDS says ties go to the party leader, but a run has no
  leader once it starts (it is owned by its UUID, not an initiator), so "keep playing" is the default a
  tie falls back to. *Flagged for revisiting.*
- **Readiness and votes are in memory only.** A restart returns a run to INTERMISSION with everyone
  un-ready, which costs a click, not progress. The draft's votes are already persisted separately.
- Changing a ready flag or a cash-out vote during the countdown **cancels** it, so nobody is moved while
  they were still deciding.

## 1. The round

`IntermissionRound` (pure, per run, in memory) holds who is ready, how each player voted on cashing out,
and the countdown deadline. `cashOutPasses(electorate)` is a strict majority of the electorate voting
yes; `allReady(electorate)` is everyone in it ready. The electorate is passed in, never stored, so a
player who drops out of the run stops being counted without anything having to tell the round.

## 2. The service

`IntermissionService` keeps one round per run in INTERMISSION. It is created on arrival, in the same
place the transition service already revives people and opens the draft, so every road into an
intermission behaves alike. After every action it re-evaluates:

1. a passing cash-out vote applies `CASH_OUT_CHOSEN` (banking and the reward reveal are already
   attached to that arrival);
2. otherwise, if the draft is settled (or there is none) and everyone is ready, the countdown starts;
3. at the deadline `RunLifecycle.openNextFloor` runs.

A draft still open **blocks ready**: a ready click before the draft settles is refused with the reason,
the same rule the transition service already enforces on `INTERMISSION_COMPLETE`.

## 3. Opening the next floor

`RunLifecycle.openNextFloor`: `INTERMISSION_COMPLETE`, `NEXT_FLOOR_CONFIRMED` (the table's floor
increment), rebuild the cell if the next floor's structure differs from the current one, then the same
begin-floor step P16 uses. P16's private `openFloor` is split so both share `beginFloor`
(`ENCOUNTER_STARTED` plus `TowerEncounters.begin`, parking the run with `TECHNICAL_FAILURE` if either
fails). A failed rebuild parks the run the same way: broken content is not a loss for the team.

## 4. Presentation

`IntermissionStatePayload` (server to client) carries the floor, the draft's cards with their vote
counts, the player's own vote, each team member's ready and cash-out state, the countdown and a message.
`IntermissionActionPayload` (client to server): `PICK_CARD`, `READY`, `UNREADY`, `CASH_OUT`, `STAY`,
`VENDOR`, `REFRESH`. The screen opens on arrival, *before* the reward reveal, so the reveal (which is
sent when a floor banks) still lands on top; closing the reveal asks the server to reopen the
intermission screen. Bare `/cobbletowers play` opens the intermission screen for a player in an
intermission, and the tower picker otherwise.

## 5. Commands

`/cobbletowers play ready|unready|pick <n>|cashout|stay` -- the screen's actions, for players without it
and for tests. `/cobbletowers runs cashout` now **casts a cash-out vote** instead of cashing out
unilaterally; for a solo player a vote of one is a majority, so a solo run behaves as before.

## Not in P17

- A physical intermission chamber (TDS #17); players stay in the arena they fought in.
- Teammate-targeted vendor purchases and the PC chooser (P18).
- The infinite-mode cash-out/ascend decision (TDS #15, #24): this is the normal-mode cash-out.

## Tests

- Pure `IntermissionRoundTest` (7): majority and tie, empty electorate, a leaver no longer awaited, silence
  as staying, the countdown and its cancellation.
- Live `intermission_test.py` (9/9): two players start a run through the lobby; floors are cleared by
  operator event (see below); the draft is open at the intermission; a ready click is refused while it is;
  two votes settle it; one ready of two does not start the countdown and the second does; floor 2 opens by
  itself; one of two voting to cash out does not end the run and the second vote does.
- Live `milestone_floor_test.py` (7/7): played from floor 1 to floor 5 through the real pick/ready/
  countdown path; the cell was rebuilt as `boss_arena` for the F5 milestone, which is what the log line
  `prepared with cobbletowers:boss_arena` proves, since the warm pool only builds floor 1's structure.
- `reward_test.py` 19/19 (its cash-out check reads the command's reply, so a passing vote answers "The
  team cashed out."), `lobby_test.py` 12/12, `participant_test.py` 26/26.
- The screen is not live-testable (headless bots), as with every other screen.

## Outcome

Built as designed. Two things worth recording:

- **Floors in the live tests are cleared by operator event, not fought.** The first version of
  `intermission_test.py` fought floor 1 with the rig's battle bots and stalled: the bot casts one move and
  gets "Invalid action choice" back (Blizzard has five PP, Haunter knows Disable), and on some opponent
  draws every fallback move was refused too. `floor_encounter_test.py` stalled the same way in the same
  session, so this is the rig, not P17 -- but it means fighting a floor to the end is not a dependable
  live check right now. What P17 adds begins at the intermission, so nothing is lost by clearing the floor
  by operator event.
- **A real bug found by the live run:** the countdown action bar was sent every tick instead of once per
  second. Fixed with `announce()` on both rounds.

Not proven: the intermission screen itself; the reward reveal reopening it; a cash-out vote at floor 10;
a restart mid-intermission (readiness is in memory by design, so the run returns un-ready).
