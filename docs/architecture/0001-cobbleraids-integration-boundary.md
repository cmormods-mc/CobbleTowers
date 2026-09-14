# ADR 0001 — CobbleRaids Integration Boundary

## Status

Accepted for Phase 0.

## Context

CobbleTowers needs CobbleRaids to provide the boss entity preparation and cooperative battle engine used by each tower floor. The audited CobbleRaids implementation currently keeps those responsibilities in internal packages:

- `config.RaidDefinitionRegistry` owns immutable, datapack-reloaded boss definitions.
- `spawn.RaidBossSpawner` owns creation and preparation of a physical boss entity.
- `raid.RaidFactory` owns cooperative battle construction and participant validation.
- `raid.RaidSession` and `raid.RaidRegistry` own active battle state.
- `lifecycle.RaidLifecycleCoordinator` owns terminal transitions, withdrawal and cleanup.
- `lifecycle.RaidRewardService` owns CobbleRaids-specific rewards and must not be reused by tower runs.

There is currently no `com.cobbleraids.api` package on the audited upstream `main` branch.

## Decision

CobbleTowers will use an anti-corruption boundary and will not import CobbleRaids implementation packages.

The desired upstream public contract is intentionally capability-oriented rather than exposing internal objects. CobbleTowers needs only these capabilities:

1. enumerate or resolve an immutable boss descriptor by resource id;
2. spawn/prepare a boss through CobbleRaids' canonical preparation path;
3. start a cooperative encounter for a frozen snapshot of 1–4 players;
4. withdraw one participant through CobbleRaids' canonical lifecycle path;
5. observe one terminal encounter result exactly once;
6. provide a supported pre-battle customization hook for tower difficulty modifiers.

CobbleTowers does **not** need access to `RaidSession`, `RaidRegistry`, Showdown actor ids, reward queues, spawn scheduler state, lobbies, or CobbleRaids' natural-spawn machinery.

## Contract shape

The upstream API should prefer immutable request/result records and opaque handles. An encounter handle should contain stable identity only (for example encounter UUID, battle UUID and definition id) and must not expose `PokemonBattle`, `PokemonEntity` or `RaidSession` as mutable lifecycle authorities.

Any API event/listener invoked by CobbleRaids must be fault-isolated. An addon listener throwing an exception must not prevent CobbleRaids cleanup, battle termination or other listeners from running.

The public boss descriptor should expose only values needed to make Tower decisions (id, species, rarity/tier, level and relevant combat metadata). It should not expose the complete internal `RaidDefinition` record because fields such as natural-spawn policy and CobbleRaids rewards are unrelated to Tower and would unnecessarily couple the projects.

## Tower-owned state

CobbleTowers remains authoritative for:

- run id, owner/party membership and instance id;
- floor number and floor seed;
- room generation state;
- temporary and permanent challenge modifiers;
- tower reward multiplier;
- pending/claimed tower rewards;
- run failure/abandon state;
- teleport restrictions and delayed `/tower leave` state.

Persisted tower state stores stable ids and serializable values, never live world/entity/battle references.

## Reward isolation

Tower victory rewards are generated and persisted by CobbleTowers. CobbleRaids reward services must not be invoked as the Tower reward implementation. The integration result may report combat outcome, elapsed time, surviving participant ids and contribution information if useful, but reward policy remains a Tower concern.

## Performance rules

The integration may register lifecycle events, but it must not add global world/entity discovery loops. Active encounters and runs are indexed directly. Timer services must have an O(1) idle fast path and iterate only active timers/runs. Boss references are tracked by stable UUID while live and discarded at lifecycle completion.

## Consequences

This adds a small amount of adapter code, but prevents CobbleTowers from breaking whenever CobbleRaids reorganizes internal packages. It also keeps the Tower independently testable: run progression, challenge stacking, reward calculation and instance allocation can be unit-tested without starting a Cobblemon battle.

The first gameplay integration commit is blocked until the public CobbleRaids contract exists and compiles. This is deliberate rather than a missing implementation.
