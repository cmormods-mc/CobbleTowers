# P8: typed modifiers, the intermission draft, voting and lock-in

Written before the code it describes, as the TDS gate requires.

Seven phases have built a tower that plays the same way twice. P5 gave a floor opponents, P6 gave it
a boss, P7 made the people standing on it real. Nothing yet makes floor 7 of one run different from
floor 7 of the next — which is the whole of TDS #1: *floors change how players fight, not merely
enemy statistics.*

P1 stopped short of this deliberately. `FloorDefinition.modifierIds` has been parsed, checked for
well-formedness and then carried untouched since the first commit, with a test asserting the reserved
id survives, and `api.modifier` was left unwritten on the stated grounds that *schemas written before
their runtime exists tend to be wrong*. P8 redeems that reservation.

Implements TDS **#2** (three-card draft, the selected challenge affects the next boss), **#23**
(majority vote), **#56** (typed modifier API), **#57** (lock-in every fifth challenge) and **#58**
(compatibility resolver).

## Decisions taken into P8

| Decision | Choice | Why |
|---|---|---|
| A tied vote | **Resolved from the run seed** | See the amendment below. (User's call.) |
| Draft timing | **Untimed** | TDS #21: normal-mode preparation is effectively untimed. The watchdog is the only backstop, and #59's decision timeout is left to the Infinite/competitive modes #21 anticipates. (User's call.) |
| Which types bite now | **Enemy, encounter, reward** | These have real seams today. Player-constraint and field are typed now and given effect in P8b, through CobbleRaids. (User's call.) |
| New run states | **None** | P6's finding, and it still holds: a table that grew a state every time gameplay gained a step would stop being a contract worth having. The draft lives inside `INTERMISSION`. |
| Where a modifier's numbers are applied | **The existing draw functions** | TDS #45 keeps level maths in one place. `ModifierEffects` produces parameters `EncounterDraw`, `BossDraw` and `TowerLevelPolicy` already accept; it does no arithmetic of its own. |
| Reward modifiers' worth | **Recorded, not valued** | The ledger has recorded *what happened* and never *what it is worth* since P5, because the economy is P9's. A reward modifier is recorded the same way. |

## The amendment to #23

**TDS #23 says a tied modifier vote "resolves to party leader". There is no party leader, by a
stronger lock.** The product-vision line says a run is owned by its own UUID *"not by a leader or
initiator"*, and #12 repeats it: *"Run UUID is authoritative; participant membership attaches to the
run."* No leader concept exists anywhere in seven phases of code, and introducing one to satisfy #23
would break the higher-level requirement.

**A tie is therefore broken from the draft's own seed.** This is recorded here as an amendment to
#23, in the same way P1 recorded the participant-axes amendment to §8.

It is also the better rule on its own merits. It needs no new concept; it inherits #29's property
that a crash cannot reroll the outcome, so a tie survives recovery unchanged; and it cannot be gamed,
because the tie-break is fixed before anybody votes. A leader-based rule would have handed one player
a silent permanent advantage for the length of a run.

## 1. Public API — `com.cobbletowers.api.modifier`

The domain TDS #137 reserves, added now that there is a runtime to describe. Read-only views, per
P1's rule of *an interface only where it creates a stable extension boundary*.

- **`ModifierType`** — `ENEMY`, `ENCOUNTER`, `PLAYER_CONSTRAINT`, `FIELD`, `REWARD`, the taxonomy #56
  names. All five exist from the start even though two do nothing until P8b. A type that is declared
  and inert is honest about the plan; a type invented later is a schema change that breaks content
  written against the old one.
- **`ModifierView`** — id, type, display name, risk tier, group, stack limit.
- **`DraftView`** / **`DraftCardView`** — the offered cards and the tally, which is what P11's GUI
  will render and what the debug command prints today.
- **`RunModifiersView`** — what a run has accumulated, reachable from `TowerRunView`.

`validation/validate_api_boundary.py` already refuses a public signature naming an internal type, so
these are covered without a new check.

## 2. Definitions — `ModifierDefinition`

Data-driven, per #139, from `data/<ns>/cobbletowers/modifiers/*.json`. Loaded by
`TowerDefinitionRegistry` through the same `load()` helper as the other six kinds, with the same
per-file skip-and-report and the same `ContentDigest`, because a dedicated server that cannot finish
its initial reload refuses to start.

Cross-reference validation grows three rules: every `excludes` and `requires` id resolves; no modifier
requires what it also excludes (unsatisfiable, and the sort of thing only a machine notices); and
**`FloorDefinition.modifierIds` finally resolves** against the loaded set rather than merely being
well-formed.

## 3. Internal — `com.cobbletowers.modifier`

The internal domain #138 names. Split pure-from-writing the way `RunTransitions` and
`ParticipantService` are, so every rule is a unit test with no server in reach.

- **`ModifierResolver`** (#58) — exclusions, groups, prerequisites, stack limits. Its contract is the
  one #58 actually asks for and which is easy to weaken by accident: it validates **the complete
  resulting ruleset**, not the card in isolation. A card is offered only if the accumulation that
  would result from taking it is itself legal.
- **`DraftDraw`** — three cards, deterministic from the run seed, in **its own ordinal space**. This
  is `BossDraw`'s `BOSS_ORDINAL` trick and it is here for the same stated reason: sharing a space
  would let a party read one draw and predict another. A crash cannot reroll a draft (#29).
- **`DraftVote`** — a majority over participants who `canFight()`, ties broken from the draft seed.
  Pure.
- **`LockInDraft`** (#57) — every fifth accumulated challenge, drawn from what the run has already
  accumulated rather than from the general pool.
- **`ModifierEffects`** — accumulated modifiers turned into the parameters the draw functions already
  take. No arithmetic lives here.

## 4. Persistence — schema 4

`PersistedRun` gains its accumulated modifiers, the open draft and its votes. `SCHEMA_VERSION` 3 → 4
with one `RunMigrations` entry: an absent field means the run drafted nothing, which is true of every
run written before this build. That is the pattern P2 built and P3 proved — one entry, one test, no
change to the reader.

The draft is written at a **forced checkpoint**, because #145 lists intermission among them and a
vote lost to a crash would come back as three different cards.

## 4a. What locking in actually does

The TDS calls it "a permanent lock-in mechanic" (#2, #57) and does not say what the mechanic is.
**A locked-in modifier is counted a second time** when effects are summed.

The alternative was a flag that marks a modifier permanent and changes nothing, which would make the
Lock-In Draft a ceremony -- three cards, a vote, and no difference either way. Counting it twice
makes it a real decision about which challenge to intensify for the rest of the run, and it needs no
new machinery at all: `ModifierEffects` already sums offsets and compounds percentages, so a second
copy is just another element in the list.

This is written down here because it is invented rather than specified, and a later reader should
know which parts of P8 came from the TDS and which did not.

## 5. Where it runs

Inside `INTERMISSION`, adding no run states. `RunTransitionService.apply` already does arrival-keyed
work there — P7 put the revive on arrival rather than on the event, so that *every* road into an
intermission revives the same people. The draft opens by that same door and for that same reason.
`INTERMISSION_COMPLETE` is refused while a draft is unresolved.

## 6. A gap this opens, and closes with it

An untimed draft that nobody answers would hold a cell and its chunk tickets forever. **P7's watchdog
sweeps active floors only** — a run sitting at an intermission is invisible to it. The sweep is
extended to cover a run parked at an unresolved draft with nobody connected.

Worth saying plainly: this was found by writing the plan, not by a live stall. The untimed decision
created it, and it would otherwise have surfaced weeks later as a cell that never came back.

**And the first version of the fix was wrong, which the live test caught.** "Nobody online" is not
"abandoned": the sweep runs after a restart too, before a single player has had time to reconnect, so
the first sweep following every crash settled every open draft on the party's behalf. They would come
back to a challenge nobody chose. Emptiness now has to *last* -- for the same five minutes a
disconnected player already gets, since it is the same question asked about a party rather than a
person -- and the clock starts when the emptiness is first **observed**, so a restart gives the full
window rather than counting the downtime against the people who were waiting through it.

That stamp is real time while the verdict is read against the sweep's `now`, which is what lets
`runs watchdog` wind the clock forward and reach a five-minute verdict without waiting five minutes.
Stamping with the wound clock instead would compare it against itself, and the difference would be
zero forever -- a rule that could never fire, tested by a command that could never prove it.

## 7. Commands

`/cobbletowers runs draft show|vote|force`, on the existing tree, with **permissions on each
subcommand**. P7 learned this the expensive way: Brigadier keeps the *first* registration's `requires`
on a merged literal, so a player-facing `vote` under a tree first registered at permission 2 would be
silently unavailable to every player it exists for. Diagnostics per #60: `runs show` prints the
accumulated modifiers, the open draft and the seed.

## Deferred to P8b — the CobbleRaids boundary

`PLAYER_CONSTRAINT` and `FIELD` modifiers need a battle's rules changed, and **CobbleTowers must not
be the mod that reaches into Showdown** (#48). CobbleRaids already is, and already owns every
mechanism: `RaidBannedMoves` with its mixin rejects a move before it reaches Showdown,
`Side.prototype.chooseSwitch` is already overridden in `raid-patch.js`, and `showdown/mods/conditions.js`
already registers a custom Showdown condition.

So the effect travels as data across the API boundary that exists — a sibling to `EncounterPolicy` —
and CobbleRaids enforces it. **This reaches the boss only**, since a floor's prerequisite battles are
ordinary Cobblemon battles run by our own adapter. That is not a compromise: #2 says the selected
challenge affects *the next boss*.

## What the live run changed

Two things this phase's own code got wrong, both invisible to the unit tests and both found by the
smoke test on a real server.

**The watchdog settled every draft after a restart.** Covered above: "nobody online" is not
"abandoned", and the first sweep after any crash was settling the party's choice for them.

**A floor that could not start said nothing about why.** `TowerEncounters.begin` returned an empty
Optional when no opponent could be drawn, and the operator got "the floor could not be started;
parking the run" with not one word in the log. The cause -- a party with no Pokemon to measure a
level against -- was two silent returns away from the message. Both now log what they found, which
is the standing rule: a stall with a clean log means something is being swallowed.

Neither is a P8 feature. They are what P8 exposed, and they would have been somebody's mystery later.

## Performance impact

- **No new per-tick work** (#151). The draft is event-driven off arrival at `INTERMISSION`. The only
  recurring cost is P7's existing watchdog sweep, whose candidate set grows by runs sitting at an
  intermission — bounded by cell count, over a collection it already walks.
- **Resolution is pure set arithmetic** over a run's accumulated modifiers, at most once per
  intermission (nine times in a ten-floor run), never on a battle or entity path.
- **A draw is three seed mixes.** No allocation beyond the three cards.
- **Reload** costs one more folder: a parse and a SHA-256 per file, on the reload thread, into the
  precomputed immutable maps the registry already builds — the shape `RaidDefinitionRegistry` adopted
  after tab completion was re-sorting 130 entries per keystroke.
- **Persistence** adds a short list to a record already written at a forced checkpoint: no new write
  path and no extra flush.
- **Effects are resolved once per floor** into parameters the draw functions already take, rather
  than being consulted per encounter, per turn or per entity.
