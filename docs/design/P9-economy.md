# P9: turning the ledger into loot

Written before the code it describes, as the TDS gate requires.

Eight phases have built a tower a party can climb, fight through and draft challenges for, and none
of it pays out. The ledger has recorded *what happened* since P5 and refused to say *what it is
worth*, on the stated grounds that a value scheme invented before P9 existed would have to be
rewritten once it did. `REWARDS_BANKED`, `FINAL_FLOOR_CLEARED` and `CASH_OUT_CHOSEN` have all existed
in P1's transition table from the first commit, each with its own idempotency key -- but nothing in
production code fires any of them. The only way a live run has ever reached `INTERMISSION` or
`COMPLETED` is the `/cobbletowers runs advance` debug command P8's smoke test used to skip past the
gap. A real floor clear today stalls in `FLOOR_RESOLVING` forever. P9 closes that, and is what makes
"cashed out at the end of the tower" -- the product-vision line P5 already built half of -- true.

Implements TDS **#24** (a milestone decides whether clearing it banks the run's rewards) and the half
of **#30** that P2 wrote down and deliberately left unused: *"the committed-key ledger therefore earns
its place for the economic commits of P9 ... where the same key really can arrive twice against an
unchanged state."*

## Decisions taken into P9

| Decision | Choice | Why |
|---|---|---|
| What is granted | **Plain items, no currency** | Neither `build.gradle` nor `fabric.mod.json` depends on an economy mod, and the README already states tower rewards are independent of CobbleRaids' -- there is nothing to award a balance in. |
| What a grant is priced from | **The `LedgerEntry.Kind` the ledger already records** | `LedgerEntry`'s own doc says `BOSS_DEFEATED` is "separate from an ordinary opponent so P9 can weigh it." The distinction was built for this; re-deriving it from the encounter a second time would be asking the same question twice. |
| Where value config lives | **One reward table per tower** (`reward_table` on `TowerDefinition`, beside `ruleset`) | What varies per floor is *depth*, a number, not a distinct roster the way an encounter pool's opponents are. That is `ruleset`'s shape (one per tower, scaled by a formula), not `encounter_pool`'s (one per floor). Ten hand-authored tables for one tower would be the same content mistake `boss_pools` was written to avoid. |
| What "banked" actually gates | **The transition, not the grant** | See §4a. |
| Delivery mechanism | **CobbleRaids' `PendingRewardStore`/`RaidRewardService` shape** | P2 already committed to this for the run store itself ("Directly follows CobbleRaids' `PendingRewardStore`"), and the crash-survival lesson it was proven on live applies just as directly to a grant of items: written to a durable queue, flushed now, delivered when the owner is next online. |
| Reward choice | **None -- a table rolls, it does not offer** | CobbleRaids' GUI complexity comes from letting a player pick one of several reward choices. Nothing here asks for that, and a tower run is a private 1-4 player party, not an open raid queue; a roll that is simply handed over is the GATE's simplicity rule applied to the one place this phase could have copied more machinery than it needed. |
| Split among participants | **Even, across current participants** | P5 built the boss as one shared fight and every prerequisite as parallel solo battles -- there is no raid-style variable attendance window to weight a contribution against. Complicating the split would be solving a problem this mode doesn't have. |
| The grant's own idempotency | **A second, distinct commit key in `committedTransactions`** | The literal thing TDS #30 was left unused for. See §4a for why the transition's own key cannot cover this. |

## 1. Public API — `com.cobbletowers.api.reward`

Per P1's rule of an interface only where it creates a stable extension boundary.

- **`RewardTableView`** — id, display name, the per-`Kind` tiers, the per-floor growth step.
- **`PendingRewardView`** — what a player is holding unclaimed: item, amount, the run and floor it
  came from. Reachable the same way `RunModifiersView` is meant to be from `TowerRunView`: an addon
  reading a run should not need a second entry point to see what it earned.

## 2. Definitions — `RewardTableDefinition`

Data-driven, from `data/<ns>/cobbletowers/reward_tables/*.json`, loaded by the same registry `load()`
helper as the other seven kinds, with the same per-file skip-and-report and `ContentDigest`.

- Weighted item pools per `LedgerEntry.Kind` (`OPPONENT_DEFEATED`, `BOSS_DEFEATED`, `FLOOR_CLEARED`),
  each entry an item id, an amount range and a weight -- the same shape `EncounterPoolDefinition`
  already uses, so there is one weighted-pick idiom in the codebase rather than two.
- A **per-floor growth step**, applied to the tier's amount the way `TowerLevelPolicy.PER_FLOOR_STEP`
  scales level: floor depth multiplies, nothing else does. Keeping this the only place reward value
  scales with depth is the same discipline TDS #45 states for level maths, applied to the one other
  number in this codebase that now grows with the floor.
- `TowerDefinition` gains a required `reward_table` field beside `ruleset`, resolved the same way and
  checked by the same cross-reference pass. `POOL_FORFEITED` entries are never priced (see §4a) so the
  table need not know about them at all.
- `ModifierEffects.rewardPercent()` (P8, built and already summed, never yet consumed) multiplies the
  finished roll once, after the table and the depth step have produced it -- not inside the table,
  for the same reason `ModifierEffects` does no arithmetic of its own anywhere else: one caller,
  applied once.

## 3. Internal — `com.cobbletowers.reward`

Split pure-from-writing the way every other domain here is.

- **`RewardValuation`** (pure) — a list of `LedgerEntry` plus a `RewardTableDefinition`, a floor depth
  and a `ModifierEffects`, in: a list of item grants out. Deterministic from the run seed and its own
  ordinal space (`RewardDraw`'s own counter, `BossDraw`'s `BOSS_ORDINAL` trick again, for the same
  reason -- a crash cannot reroll a grant already computed once).
- **`RewardBankService`** (impure) — decides *whether* a bank point was reached, calls
  `RewardValuation` exactly once per point, and is the only writer of the pending queue and of the
  grant's own commit key. See §4a for why it cannot simply run inline off the transition event.
- **`TowerPendingRewardStore`** — `extends SavedData`, own file id `cobbletowers_pending_rewards`,
  `Map<UUID, List<PendingTowerReward>>`. Directly follows CobbleRaids' `PendingRewardStore`, including
  its reasoning for what is *not* serialized: the item roll is written (there is no reward-config
  schema to keep re-resolvable the way CobbleRaids' choice-based rewards need), but nothing here
  re-derives a snapshot from a definition that might have changed underfoot, because a tower reward
  table entry is a plain item stack, not a policy object.
- **Delivery** — on join, and once at grant time for a player already online, the queue for that
  player is drained straight into their inventory (overflow drops at their feet, the same rule
  Cobblemon and CobbleRaids both use for a full inventory). No claim command exists to run, because
  there is nothing to choose; `/cobbletowers runs reward show` (TDS #60) is diagnostic only.

## 4. Persistence — schema 5

`PersistedRun` gains `lastBankedFloor` (int, default 0): the floor index through which the ledger has
already been priced. `SCHEMA_VERSION` 4 → 5 with one `RunMigrations` entry -- an absent field means
nothing has been banked, which is true of every run written before this build, the same pattern P2
through P8 each used once.

This is what lets `RewardBankService` know which ledger entries are new. Entries are never removed
(the ledger's own doc says "marked, not deleted" and that promise extends to every reader of it, not
only `POOL_FORFEITED`); pricing therefore always means "everything with `floorIndex >
lastBankedFloor`," never "everything," so a second bank point after the first does not re-grant floors
1 through 5 a second time.

The pending reward queue is written at a **forced checkpoint** for the same reason P8's draft is:
granting and then losing the grant to a crash before the next autosave would hand a player a reward
the run's own record says was never paid.

## 4a. Why banking cannot live inside `apply()` alone, and what "banked" gates

**The tension.** P1's transition table has exactly one edge out of `FLOOR_RESOLVING` for a non-final
floor: `REWARDS_BANKED → INTERMISSION`. Every floor clear that is not the last one must fire it, full
stop -- the table has no second, unbanked path to an intermission. But P6 says losing a later boss
"forfeits the unclaimed pool," which only means something if most floors' earnings are still at risk
*after* being "banked." Read literally, the two statements contradict each other.

**The resolution.** `REWARDS_BANKED` is a state-machine event and always fires on a floor clear,
exactly as the table requires. Whether it *also* converts what has accumulated into a real,
un-forfeitable grant is a separate question, answered by `MilestoneDefinition.banksRewards()` (TDS
#24) -- which only exists to be asked on a milestone floor, because `FloorDefinition.milestone()` is
empty everywhere else. An ordinary floor has nothing to consult and grants nothing: `REWARDS_BANKED`
moves the run to `INTERMISSION` and the ledger simply keeps accumulating, still entirely forfeitable
by `LedgerEntry.forfeited` on the next loss, exactly as `TowerEncounters.lose` already writes it for
the *whole* ledger regardless of floor. Floor 5, if its milestone sets `banks_rewards` true, is
therefore the tower's one guaranteed mid-run payout. Floor 10 always finishes through
`FINAL_FLOOR_CLEARED` into `COMPLETED`, a terminal state nothing can forfeit from afterward, so it
always grants whatever remains regardless of the flag. `CASH_OUT_CHOSEN` is, by definition, the
player choosing to grant everything remaining and stop.

**Why the grant cannot simply run inside `apply()`, right after the checkpoint.** `RunTransitionService
.apply` already hangs work off arrival at a state -- `DraftService.open` at `INTERMISSION`,
`ParticipantService.reviveAtIntermission` the same way -- and a bank call could be added there
identically. The difference is what a crash *between* the checkpoint's synchronous flush and that call
costs. `decide()` is explicit that a state machine cannot replay a move: applying `REWARDS_BANKED`
moves the run to `INTERMISSION`, so a retry of the same event finds no transition at all and is
refused as `ILLEGAL_EVENT`. If the process dies in that window, the run's state is durably at
`INTERMISSION` -- correctly -- but nothing durable ever recorded that the grant it implies still needs
to happen, and no event exists that could ask for it again. This is exactly the gap P2 named and left
for P9: *"the same key really can arrive twice against an unchanged state"* -- the run's state does
not move a second time, but the code that grants off it must be safe to run again anyway.

The fix is the one CobbleRaids already proved: a grant is committed under its **own** key
(`run:{run}:floor:{floor}:granted`, distinct from the transition's `...:banked` key), written into the
same `committedTransactions` list `PersistedRun.hasCommitted` already reads. `RewardBankService` checks
that key before doing anything, the same shape `RunTransitionService.decide` already checks
`KEY_REUSED` with. And because a live event can no longer be the only way to trigger it, a small sweep
at server start re-checks every run sitting at `INTERMISSION`, `COMPLETED` or `CASHED_OUT` whose
`lastBankedFloor` is behind its ledger, and bank point applies then instead. Same lesson P8's watchdog
gap taught, in the same phase's own persistence layer this time rather than the participant one.

## 5. Where it runs

- **Live path**: tied to arriving at `INTERMISSION`, `COMPLETED` or `CASHED_OUT` in
  `RunTransitionService.apply`, in the same block that already reacts to reaching a state rather than
  to the event that produced it.
- **Recovery path**: once at `onServerStarted`, over runs already sitting at one of those three states
  with an unbanked tail -- covers the crash window in §4a without needing a live event.
- Both call the same `RewardBankService.bank(server, runId, now)`, which is what makes the recovery
  path a plain retry of the live one rather than a second implementation of it.

## 6. Commands

`/cobbletowers runs cashout`, alongside the existing `leave`, firing `CASH_OUT_CHOSEN` from
`INTERMISSION` -- **permissions on the subcommand itself**, the lesson P7 paid for once already:
Brigadier keeps the first registration's `requires` on a merged literal, so a player command added
under a tree first registered at operator level would be silently unreachable by the players it is
for. `/cobbletowers runs reward show` is diagnostic (TDS #60): what a run has banked, what is still at
risk, and what is queued for each participant.

## 7. Performance impact

- **No new per-tick work.** Banking is event-driven off arrival at three states, at most three times
  in a ten-floor run (floor 5, floor 10, an early cash-out), plus the one-time recovery sweep at start.
- **A valuation is a handful of seed mixes** over the ledger entries newer than `lastBankedFloor` --
  bounded by floor count, never by anything that grows across a run's lifetime.
- **Delivery on join** is a queue lookup keyed by the joining player's UUID, not a scan.
- **Persistence** adds one int to a record already written at forced checkpoints, and one more
  small `SavedData` file, flushed only when a grant actually happens -- not on a timer.

## What the live run changed

One thing this phase's own code got wrong, invisible to the unit tests and found by the smoke test
on a real server.

**`releaseInstance` was saving a stale run and silently erasing what `bank()` had just written.**
`RunTransitionService.apply` calls `RewardBankService.bank` on arrival at a terminal state, then --
because that state is terminal -- calls `releaseInstance(server, move.next(), now)` to give the cell
back. `move.next()` is the record computed *before* `bank()` ran; `bank()` fetches and saves its own,
newer copy independently, carrying the grant's own commit key and the bumped `lastBankedFloor`.
`releaseInstance` ends with its own save, built from whatever `PersistedRun` it was handed -- so
saving the stale `move.next()` overwrote `bank()`'s write a moment later, and a cashed-out run's
`runs show` came back with no grant key at all, as if nothing had been banked. The pending reward
queue was unaffected (a separate file, written by `bank()` itself), so the item still reached the
player -- only the run's own record of having banked it vanished, which is exactly the kind of gap
that looks fine until the next crash asks `hasCommitted` a question the record can no longer answer
correctly. Fixed by re-fetching the run immediately before `releaseInstance`, rather than trusting
the reference `apply` was holding from before `bank()` ran.

Nothing else in `apply`'s arrival block needed this: `DraftService.open`, `ParticipantService
.reviveAtIntermission` and `DraftService.clearIfSettled` all take just the run id and re-fetch for
themselves, which is what makes them safe by construction. `releaseInstance` was the one call site
still trusting an object handed to it, and `bank()` was the first thing ever inserted before it that
could make that object stale.

## 8. Assumptions and constraints

- **No reward GUI.** P11 owns presentation; a grant here is a roll and an inventory insert, with chat
  confirming what arrived, the same fallback CobbleRaids uses when its own GUI backend is unavailable.
- **No player choice in what is granted.** If a future TDS reading calls for it, it is additive on top
  of `RewardTableDefinition` rather than a redesign of it -- the table already separates "what can be
  rolled" from "what was rolled."
- **Regional pools and vendor services stay reserved.** P1 named them alongside modifiers as ids
  parsed and carried until their own phase; P9 spends none of that reservation.
- **A run's economy is entirely local to that run.** Nothing here reads or writes another run's ledger
  or pool; two runs banking at once share nothing but the reward table definition, which is read-only
  content.
