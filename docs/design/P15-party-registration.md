# P15: registering a party, and validating it for real

Written before the code it describes, as the TDS gate requires. The proposals below were confirmed
("proceed") and built as written; see "Outcome" at the end.

`a9eebaf` made `PersistedParticipant.registeredPokemon()` carry a snapshot of each player's party, taken
when `runs create` builds the run. Nothing reads it. Looking for where it should be read turned up a
bigger gap than one unused field: **the whole party-validation step TDS #41 and #46 describe does not
exist.** What is actually in the code today:

- `RunEvent.PARTY_SUBMITTED`, `PARTY_VALIDATED` and `PARTY_REJECTED` are states in the transition table
  (`CREATED -> VALIDATING_PARTY -> ALLOCATING_INSTANCE | ABANDONED`) but **no production code ever emits
  them**. Only an operator typing `runs advance <run> party_submitted` / `party_validated` moves a run
  through, and every smoke test does exactly that. Nothing is checked on the way.
- `RulesetDefinition.registeredPartySize` (1..6) and `requiresBattleReadyParty` are parsed, validated
  as definitions, exposed on `RulesetView` -- and **enforced nowhere** (a grep of `src/main` finds no
  reader outside the definition and API types).
- Enemy level (TDS #45: "from the combined **registered** parties' average level") is computed by
  `TowerLevelPolicy` from `partyLevels` that `TowerEncounters` reads from the live party at floor
  start, not from the registered Pokemon.
- Battles start with `Cobblemon.getStorage().getParty(player)`, the live party (TDS #48 says tower
  rules must not invasively change Cobblemon's own switching, which is why that is left alone below).

So P15 is the phase that makes TDS #41 ("legality is validated and the registered party is locked
before instance allocation"), #45's "registered" wording and #46 true, using the snapshot as the thing
being validated and then read.

## Decisions taken into P15

| Decision | Proposal | Why |
|---|---|---|
| Where the rules live | A pure `PartyValidation` function in `runtime`, taking `List<PartyMember>` (uuid, level, fainted, egg) and a `RulesetView`, returning a `Result` (valid, or a list of `Reason`s) | Every other tower rule that matters (level policy, jersey weighting, reveal) is a pure function with unit tests; this keeps Cobblemon out of the rule, as `RunFactory` already is. The Cobblemon-reading shell stays in `RunsCommand`/a small adapter. |
| What validation checks | (1) at least one registered Pokemon; (2) no more than `registeredPartySize`; (3) if `requiresBattleReadyParty`, every registered member is neither fainted nor an egg | #43/#44 explicitly allow duplicate species and items, so nothing else is a legality rule in v1. Those three are the only ones the ruleset can already express. |
| Driving the events | New `RunLifecycle.validateParty(server, runId, now)`: emits `PARTY_SUBMITTED`, runs `PartyValidation` against each participant's **registered** snapshot (with current HP/egg state read live, since that is not snapshotted), then emits `PARTY_VALIDATED` or `PARTY_REJECTED` | Reuses the existing transition table and `RunTransitionService.apply`, so checkpointing and the staleness rule ([[cobbletowers-apply-staleness]]) are inherited, not rebuilt. |
| The operator path | `runs advance party_submitted`/`party_validated` **keep working unchanged**; a new `runs validate <run>` runs the real path | Every existing smoke test advances by hand; replacing that would rewrite five tests for no gain. The real path gets its own test. |
| "Locked" | The registered list is the only party a run reads for **level** (`TowerEncounters` resolves the registered uuids against the player's live party/PC and uses those Pokemon's levels). Battle start is unchanged | #45 says registered; #48 says do not intrude on switching. Reading levels from registered members is the part of "locked" that affects difficulty, and it is the half that is checkable. |
| What a missing registered Pokemon does | If a registered uuid cannot be found (released, traded), it is skipped for level purposes; if none are found, fall back to the live party and log a warning | Never fail a floor over a reshuffled party -- that would turn "locked" into a new way to stall a run. |
| Persistence | None. `registeredPokemon` already round-trips; no schema bump | |

## Not in this phase

- A player-facing registration screen (TDS #41's "preparation/registration screen"): a client screen in
  the style of `VendorScreen` is its own phase. This one makes the server-side check real so that screen
  has something to call.
- Enforcing which Pokemon lead or are switched in (TDS #48).
- Item-action budget enforcement (TDS #47) -- a separate unbuilt ruleset field.

## Tests

- `PartyValidationTest` (unit): empty, over-size, fainted/egg with and without
  `requiresBattleReadyParty`, duplicates accepted.
- `RunLifecycle` transition test: valid -> `ALLOCATING_INSTANCE`; invalid -> `ABANDONED`, with the reason
  surfaced.
- Level policy: levels come from registered members, ignore a swapped-out one, and fall back when none
  resolve.
- Live: new `party_validation_test.py` -- a healthy party validates; the same player with the party
  fainted (via `/pokeheal`'s opposite, or a lowered-HP give) is rejected; reshuffling the party after
  registration does not change the level a floor draws.

## Decisions on the three open questions

1. **Over-size party:** the first `registeredPartySize` members register; no rejection.
2. **Battle-ready:** every *registered* member unfainted. Cobblemon 1.7.3's `Pokemon` has no egg state,
   so fainting is the only readiness fact (checked with `javap` against the pinned jar). A fainted
   Pokemon outside the registered slots does not count.
3. **Snapshot timing:** re-snapshot at `runs validate`. `runs create`'s snapshot (`a9eebaf`) is kept as
   a draft, which is also what the existing operator path (`runs advance party_submitted/validated`)
   relies on, so every older smoke test still reads registered levels.

## Outcome

Built as proposed: `PartyValidation` (pure), `PartyReader` (the Cobblemon shell),
`RunLifecycle.validateParty`, `PersistedRun.withParticipants`, `runs validate <run>`, and
`TowerEncounters.levelsOf(run, fighters)` reading registered levels. `PartyValidationTest` (9 unit
tests) and the new live `validation/smoke/party_validation_test.py` (5/5: empty party rejected and
ABANDONED, six-Pokemon party validated and registered, a second validate refused, a validated run
takes a cell). `floor_encounter_test.py` 17/17 and `participant_test.py` 26/26 re-run clean, which
exercises the registered-level path through the create-time snapshot.

Not proven: a fainted party being rejected live (RCON cannot faint a Pokemon deterministically; unit
tested only) and an offline participant being rejected (also unit-level only -- `validateParty` takes
its party reader as a function precisely so it could be, but no test drives it without a server).
