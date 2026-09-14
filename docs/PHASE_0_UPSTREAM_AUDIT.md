# Phase 0 — Verified CobbleRaids Integration Audit

## Purpose

This document records only integration facts verified against the current `cmormods-mc/SnobblemonRaids` `main` branch. It exists to prevent CobbleTowers from being designed around assumptions about CobbleRaids internals.

## Verified runtime baseline

The current CobbleRaids build targets:

- Minecraft 1.21.1
- Mojang mappings
- Java 21
- Fabric Loader 0.17.2 in the build
- Fabric API 0.116.6+1.21.1
- Cobblemon 1.7.3

CobbleTowers should initially match this runtime baseline unless a later compatibility decision is explicitly validated.

## Verified four-player support

`CobbleRaidsConfig.VALIDATED_MAX_HUMAN_PLAYERS` is 4, and `RaidDefinition.Recruitment` rejects definitions outside `1..4`.

`RaidBattleRegistryMixin` rewrites cooperative raid Showdown actors into contiguous ids: player actors become `p1..pN`, followed by the single boss actor. The mixin also injects `playerCount` into the raid start format for the paired Showdown patch.

**Integration consequence:** CobbleTowers may target 1–4 participants, but four-player tower battles still require dedicated regression testing because this behavior depends on the CobbleRaids Showdown integration, not stock Cobblemon behavior.

## Boss battle construction boundary

`RaidFactory.startFromWildBoss(...)` is the current battle-construction choke point. It:

1. requires a marked CobbleRaids boss entity;
2. rejects removed or already-battling bosses;
3. validates participant dimension and battle availability;
4. builds each player's Cobblemon battle team;
5. constructs the raid boss actor;
6. starts the Cobblemon battle;
7. constructs and binds `RaidSession`;
8. activates the session.

**Integration consequence:** CobbleTowers must not reproduce this implementation. CobbleRaids should eventually expose a small public boss-battle service/adapter contract that owns these invariants.

## Raid runtime ownership

`RaidSession` is the server-authoritative identity/state object for one raid. It owns references to the Cobblemon battle, physical boss entity, boss actor id, definition id and run progress.

`RaidRegistry` indexes sessions by Cobblemon battle UUID. It explicitly clears on server shutdown because a retained session can retain the battle, entity, level and therefore an entire world.

**Integration consequence:** CobbleTowers needs the same explicit lifecycle discipline for its own run registry, but must not retain `RaidSession` or Cobblemon entity references as persisted tower state. Persist stable ids and reconstruct transient references.

## Terminal-state authority

`RaidLifecycleCoordinator` is the single authority for victory, defeat, withdrawal, disconnect, timeout, abort, boss cleanup and reward finalization.

Important verified behavior:

- a participant can withdraw without ending the shared raid for remaining participants;
- disconnect is treated as participant withdrawal;
- a raid fails when no active participants remain;
- victory finalization is guarded against duplicate execution;
- normal CobbleRaids victory invokes progression, state carryover, catch handling and `RaidRewardService`;
- cleanup order matters because Cobblemon still owns battle actors until battle termination.

**Integration consequence:** Tower player elimination must use a supported CobbleRaids withdrawal path rather than independently deleting actors or closing the battle. Tower boss completion should be observed through an integration callback/event instead of duplicating CobbleRaids terminal logic.

## Rewards boundary

`RaidRewardService` is specifically a CobbleRaids reward queue. It resolves CobbleRaids definitions/policies, tracks pending raid rewards, persists unclaimed claims, delays GUI opening until battle UI cleanup, and grants CobbleRaids reward choices.

**Integration consequence:** CobbleTowers must not call `RaidRewardService` for tower loot. Tower rewards require an independent persistent claim ledger and grant service. CobbleRaids integration should expose battle outcome information, not its reward implementation.

## Performance findings relevant to CobbleTowers

CobbleRaids already has server-tick services. Its reward service demonstrates the desired idle behavior by returning immediately when its delayed-open map is empty.

CobbleTowers must follow the same principle:

- no global entity scan per tick;
- no scan of all tower regions per tick;
- active runs indexed directly by run/player/instance ids;
- event-driven transitions wherever Fabric/Cobblemon events exist;
- any unavoidable countdown/timer service must return immediately when no timer is active;
- entity UUIDs should be owned by the relevant run/floor rather than rediscovered through world scans.

No numeric MSPT budget is asserted in Phase 0 because no CobbleTowers executable workload exists yet. Numeric performance claims begin only after an instrumentable implementation exists.

## Initial public integration surface

The audit supports a deliberately small future CobbleRaids-facing adapter. The exact names are not frozen yet, but CobbleTowers needs only capabilities equivalent to:

- resolve/select a CobbleRaids boss definition;
- spawn/prepare a tower-owned CobbleRaids boss;
- start a raid battle for a frozen 1–4 player participant snapshot;
- withdraw one participant safely;
- observe terminal battle outcome and surviving participants;
- apply tower-specific boss modifiers through a supported pre-battle customization hook.

Reward APIs are intentionally excluded.

## Explicit non-goals

CobbleTowers will not:

- import CobbleRaids `lifecycle`, `raid`, `spawn`, `reward`, or mixin implementation packages as its public dependency contract;
- duplicate CobbleRaids Showdown actor rewriting;
- manipulate Cobblemon battle actors directly to remove tower participants;
- use CobbleRaids reward queues for tower loot;
- add broad per-tick world/entity discovery to find its own state.

## Next validation gate

Before implementing the adapter API, verify the boss-definition registry, boss-spawn customization path, battle event coordinator, participant faint/whiteout behavior, and whether the existing public/API package already exposes any suitable contracts. Then define the minimum interface set and add compile-level tests around contracts that are free of Minecraft runtime types.
