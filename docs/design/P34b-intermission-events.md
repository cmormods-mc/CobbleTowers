# P34b: Intermission Events (run depth, part 2)

Roadmap item B2, with Relics (P34) as its partner.

## What a player sees
On an ordinary (non-milestone) floor, after the modifier draft settles, a **room** appears about half the time. It is a
two-option vote on the same draft screen, with the same tie-break, and it holds the floor shut until it is answered.
Milestone floors never have a room: they pay a relic.

| Room | Option A | Option B |
|---|---|---|
| Shrine | **Take the curse**: gain a random relic and a random harsh challenge for good | Walk away |
| Gambler | **Stake a relic**: even odds, win a second relic or lose the staked one | Keep your relics |
| Rest | **Rest**: the whole party is fully healed | Press on |

A room is only offered when it can do something: the Shrine needs relic room, a relic to give and a challenge to charge;
the Gambler needs a relic held; Rest is always possible.

## How it works (no new registry, screen or payload)
* `intermission/IntermissionEvents` is pure: `roomFor` (seeded chance + seeded pick among eligible rooms), `cardsOf`,
  `resolve` (an option to a new `RunModifierState`, a heal flag and a sentence). Its own ordinal space, so a shared seed
  shares its rooms and a crash cannot re-roll an outcome.
* An event is a `PersistedDraft` with `event = true`; each option is a card id `cobbletowers:event/<option>`. The intermission
  screen already names cards by looking them up, with a fallback to the option's label, so the client is unchanged.
* `DraftService.settle` chains it: settling an ordinary draft on a non-milestone floor opens a room if one rolled
  (after a relic draft, never). Settling an event applies `resolve` and, for Rest, heals every online participant.
* A shrine's challenge is added to `accumulated` (it is a real challenge, counts toward Lock-In, and is legal by the same
  resolver) but emits no `Drafted` event, so contracts and mastery counting drafted challenges are untouched.
* `RunModifierState.withoutRelic` is new, for a lost gamble.

## Behavior change
An open event room is an open draft, so a player or test that picked once and readied up is now held until the room is
answered. The smoke tests that drive the intermission now pick a second round (a pick with nothing open is refused
harmlessly).

## Not built
* **Forge** (a battle-only boon paid in CobbleDollars) and **Echo Hall**: Forge needs wallet spending; Echo Hall needs Echoes.
* Per-room art, descriptions beyond the option label, and a per-tower room weighting.
* Room frequency (50%) is untuned.

## Tests
Unit `IntermissionEventsTest` (7): room cards, no room on a milestone, the chance, eligibility, shrine, gambler (both
outcomes, deterministic, pass is a no-op), rest. Live `validation/smoke/event_test.py` 8/8 (rooms hold the floor, the choice
is applied; a 50% roll per floor, so rerun if it alone fails). Not proven: the room rendered in a real client; the
heal landing on a damaged party.

## Verification notes
Regression re-run after the change: milestone_floor 7/7, intermission 9/9, draft 25/25 (rooms switched off with
`-Dcobbletowers.events=off`, which an operator can also use), vendor_npc 12/12, custom_modifier 8/8, contract 16/16,
playlist 15/15, trial 20/20, card_reward 10/10, mastery 19/19, milestone_reward 10/10, ascension 13/13 (twice), relic 6/6,
event 8/8. `ascension_test` failed once (12/13: "forced modifier ... 5 -> 6", one fewer modifier than expected) before the
tests were corrected to vote two full rounds; it passed three times after and the cause of that one failure was not
reproduced. Treat it as unexplained if it recurs.
