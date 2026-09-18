# P5: the encounter core and the Cobblemon adapter

Written before the code it describes, as the TDS gate requires.

P4 built a floor to stand in and nothing happened on it. P5 is the first phase where a floor is
played: opponents appear, players fight them, and the floor resolves.

## The shape a floor takes, and why

Settled with the user during planning, and it differs from the TDS's original reading:

> Ordinary floors are small normal Cobblemon encounters as a prerequisite, then that floor's
> CobbleRaids boss. Every opponent defeated and every floor cleared adds to an unclaimed pool, cashed
> out at the end of the tower.

Floors 5 and 10 stay special through **content** rather than a second code path — handpicked bosses,
higher tiers, larger contributions — so the milestone flag already in the floor schema decides which
boss is drawn (TDS #52, #53 satisfied by what is drawn, not by how).

This solves a problem rather than creating one. `BattleBuilder.pve` takes **one** player; several
players against one opponent is what forced CobbleRaids into custom battle actors and mixins. As
**parallel solo battles** — one per player, at the same time — the prerequisite needs no Cobblemon
internals at all, and the shared fight is the boss, which CobbleRaids' encounter API already runs for
one to four players. It also avoids the four-player Showdown stall that cost CobbleRaids several
sessions, because no single battle ever holds four players.

**P5 builds the prerequisite half only.** The boss handoff is P6, where the CobbleRaids adapter
belongs. Turning the ledger into loot is P9.

## Decisions taken into P5

| Decision | Choice | Why |
|---|---|---|
| What an opponent is | **Derived from a seed, never stored** | TDS #29: a crash cannot reroll an encounter. Arithmetic over run seed, floor and ordinal gives the same opponents after any restart, and costs nothing to recover. |
| Level | **One policy class, snapshot per floor** | TDS #45 says in as many words not to scatter level maths. `TowerLevelSnapshot` already owns the mean; the policy adds the ruleset's bounds and the floor step, once. |
| Parties | **Not cloned, not healed first** | TDS #16: no free healing. Damage and PP carry between floors because the battle uses the real party. |
| Opponents | **Uncatchable** | TDS #78: tower opponents are never captured. |
| Battle events | **Subscribed once, routed by battle id** | Cobblemon's events are global. An index from battle id to encounter is the whole correctness of the adapter. |
| The pool | **A ledger, with no values in it** | P9 owns the economy. A pool that guessed at worth now would be rewritten then. |

## 1. `encounter/` — the logical core

- **`EncounterSeed`** — `(run seed, floor index, ordinal)` to a seed. Pure.
- **`EncounterDraw`** — weighted pick from `EncounterPoolDefinition`, which already carries weights
  and a `totalWeight()` for the denominator.
- **`EncounterSnapshot`** — species, aspects and level, immutable once taken.
- **`TowerLevelPolicy`** — the only place a tower level is decided: the party mean from
  `TowerLevelSnapshot`, plus the entry's offset, clamped to the ruleset's bounds. Taken **once per
  floor**, so fainting, disconnecting or spectating cannot lower the difficulty mid-floor (#45).

All four are free of Minecraft types and tested without a server.

## 2. `battle/cobblemon/` — the adapter (TDS #31)

The only code in the mod that names a Cobblemon battle type.

- Spawns the opponent through `PokemonProperties` and `sendOut`, marked uncatchable.
- One `BattleBuilder.pve` per player, cloning off and healing off.
- Subscribes once to `BATTLE_VICTORY`, `BATTLE_FAINTED` and `BATTLE_FLED`, and routes each to the
  encounter that owns that battle id.
- Removes its opponent entity on **every** path out, including the ones nobody plans for: a logout
  mid-battle, an abandoned run, a server stop. An entity left standing in a cell is what P3's
  quarantine catches, and quarantining a cell because this phase forgot to tidy up is a poor way to
  find out.

## 3. `TowerEncounter` — one floor's round

Which players still have an opponent up, and which have cleared theirs. The last one clearing raises
`ENCOUNTER_RESOLVED_CLEARED`; everyone being out raises `ENCOUNTER_RESOLVED_WIPED`. Both have been in
P1's table since the first week; this is the first code to raise them.

## 4. The ledger

An entry per defeated opponent and per cleared floor: what, which floor, which player. No worth is
decided. It takes `PersistedRun` to schema v3 — the migration framework's second real step, and the
first one that carries data rather than stamping a version.

## 5. Performance

- **No per-tick work.** The adapter subscribes once at startup; everything else is event-driven.
- **Up to four simple battles at once** rather than one battle with four players. More battles, far
  simpler ones, and it avoids the stall that shape is known to cause.
- **One entity per player per floor**, removed on every exit path. The cell's chunks are already held
  by P4's ticket, so nothing new stays loaded.
- **The ledger is a few fields per opponent**, written with the checkpoint the floor already takes.
- Recovering an encounter is arithmetic, not a read.

## 6. What the first live floor changed

Three things were wrong in ways no unit test could have reached, because each needed a real
Cobblemon, a real world, or both.

- **`BattleBuilder.pve` takes a non-null party.** Passing null for it -- expecting Cobblemon to
  default it, as the generated `pve$default` overload would -- threw
  "Parameter specified as non-null is null" on the first floor ever played. It gets the player's own
  party store now, which is also the correct answer for TDS #16: the battle is fought with what the
  party actually has.
- **Players were never put in the arena.** The floor was built, the opponent spawned in it, and the
  party stayed in the overworld while their Pokemon fought in another dimension. P4 built and
  validated an entry anchor for exactly this; P5 now teleports each fighter onto it before a single
  battle starts.
- **The anchors were being measured from the wrong origin.** A floor is centred in its cell, so the
  paste origin depends on the structure's size -- and computing it a second time here, without the
  size, put the entry anchor twenty-five blocks from the arena. The party arrived in the void beside
  their own floor, which the bot's log reported as "fell out of the world". There is one calculation
  now, {@code CellPreparer.originFor}, and everything that turns an anchor into a world position asks
  it rather than working it out again.

A fourth was in the harness rather than the mod, and is worth the same attention: Minecraft only
prints a command's stack trace when it is running in an IDE, so on a real server the failure arrived
as "An unexpected error occurred" with nothing in the log. The dev command reports its own throwable
now, which is what turned a day of guessing into one line.

## 7. A gap this phase found and did not close

**Nothing ends a floor that a player stops answering.** The two-player run stalled once because one
bot's lead had been replaced by a level 1 Magikarp that could not cast the move it kept choosing, so
Showdown asked, the bot answered "Invalid action choice", and the floor stayed open. The cause was a
test-party mistake, but the behaviour it exposed is real: a player who never chooses -- disconnected,
AFK, or holding a Pokemon with no legal move -- leaves the round open forever, and with it the cell,
its chunk tickets and the run.

TDS #59 already calls for this: a decision timeout, an encounter watchdog, and telling a slow player
apart from a stalled battle engine. It belongs with P7's disconnect and reconnect handling rather
than here, and it is written down so it is not rediscovered as a mystery.

## 8. Assumptions and constraints

- Minecraft 1.21.1, Fabric, Java 21, Cobblemon 1.7.3, CobbleRaids >= 0.8.94-encounter-api.
- CobbleRaids is still used only through `com.cobbleraids.api`; the architecture check enforces it.
- No GUI: a floor is driven by command until P11.
- The boss half of a floor does not exist yet, so a cleared prerequisite resolves the floor outright.
  P6 inserts the boss between them.
