# CobbleTowers

CobbleTowers is a Fabric 1.21.1 / Cobblemon 1.7.3 Battle Tower framework designed for private solo or party tower runs and integration with CobbleRaids bosses.

## Project constraints

- Java 21
- Fabric / Minecraft 1.21.1
- Mojang mappings
- Cobblemon 1.7.3
- 1–4 players per run
- 10-floor standard mode; architecture must permit a future infinite mode
- One boss per floor
- One shared Battle Tower dimension with isolated private run instances
- Event-driven state; no global per-tick entity scans
- Tower rewards are independent of CobbleRaids rewards
- Claimed rewards survive run failure
- Challenge cards affect the next boss; every fifth accepted challenge also creates a permanent modifier stack for later bosses
- No automatic party healing outside explicit buffs
- Ender pearls and external teleportation are blocked during an active run
- `/tower leave` exits after a five-second delay

## Engineering rules

CobbleTowers must depend on stable public integration contracts, never CobbleRaids internal implementation packages. APIs are kept intentionally small and are added only when verified against the actual upstream implementation.

Major implementation phases are not considered complete merely because they compile. Each phase is validated against source/API behavior, then dedicated-server behavior, with multiplayer and performance testing added as soon as executable systems exist.

Code should favor simple indexed state, explicit lifecycle ownership, deterministic behavior where useful for debugging, and explicit cleanup over broad polling or world/entity scans.

## Status

Phase 0: upstream CobbleRaids architecture and integration audit in progress.
