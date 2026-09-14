# CobbleTowers V1 Gameplay + Technical Specification

## Status

Approved design baseline for the first playable standard-mode implementation.

This document is intentionally specific enough to constrain implementation. Future changes should be explicit design revisions rather than accidental behavior introduced by code.

## Notes

### Exact functionality

This specification defines the standard 10-floor Battle Tower loop, multiplayer rules, private-instance behavior, boss integration, challenge/modifier progression, rewards, transport, failure/rejoin semantics, and production structure strategy.

### Architectural role

This is the contract between the Tower run engine, instance system, CobbleRaids adapter, encounter manager, modifier system, reward service, and client presentation layer. Implementation classes may change; these behavioral rules should not.

### Performance considerations

The design assumes up to 20 concurrent solo runs on a continuously running Fabric server. Runtime systems must therefore avoid global scans, uncontrolled natural Pokémon spawning inside Tower instances, per-instance tick loops that scale with world size, dynamically created dimensions, and permanently force-loaded completed floors.

### Constraints

- Minecraft 1.21.1
- Fabric
- Java 21
- Cobblemon 1.7.3
- CobbleRaids integration only through `com.cobbleraids.api`
- Standard mode: 10 floors
- Party size: 1–4 players
- Infinite-mode-compatible run model from day one
- **No fireworks or firework entities anywhere in CobbleTowers presentation or gameplay**

---

## 1. Production structure strategy

The supplied `Cobblemon_Battle_Tower_v2` datapack/mcfunction assets are source/reference geometry, not the production runtime generator.

Production implementation should convert the structure into reusable structure-template/NBT pieces rather than executing the 1,168 source commands for every run.

Natural source boundaries already exist:

- foundation
- core
- floors 1–4
- floor 5 boss checkpoint
- floors 6–9
- floor 10 champion arena
- details/crown

The full architectural shell may be placed for visual continuity, while gameplay state is activated progressively.

## 2. Shared dimension model

All Tower runs share one dedicated Tower dimension.

Each run receives one private isolated region/cell. Do not create one Minecraft dimension per party.

Approved baseline:

- void world
- fixed daytime for V1
- private cells arranged in a compact 2D grid
- configurable cell spacing
- target default spacing: 192 blocks, subject to final exact template bounds validation
- blocks cannot be placed or broken by players inside active Tower instance bounds
- only current gameplay-relevant floors should require active runtime state/chunk retention

## 3. Physical tower lifecycle

The Tower remains a literal vertical 10-floor structure.

Recommended lifecycle:

1. allocate private region
2. place/ensure architectural shell and floor templates
3. activate floor 1 gameplay state
4. keep completed static blocks but remove their active entities/runtime encounter state
5. activate future floors only as progression reaches them
6. on run termination, remove tracked gameplay entities/state and return the region slot to the allocator

Completed static floors do not need to be destroyed merely because they are complete.

## 4. Floor progression loop

Every floor contains a boss.

Approved loop:

1. floor starts
2. Tower explicitly spawns required ordinary Cobblemon encounters
3. players complete the configured number of ordinary encounters
4. boss becomes available/activates
5. cooperative CobbleRaids external encounter starts
6. boss resolves
7. Tower rewards resolve
8. three-card challenge/buff selection appears
9. multiplayer vote resolves
10. any fifth accepted challenge triggers permanent-modifier promotion selection
11. next-floor portal activates
12. players advance

Floor 5 is a checkpoint/miniboss floor.

Floor 10 is a championship/final-boss floor selected from a dedicated final-boss pool.

## 5. Floor transport

V1 transport is portal-based through the central service/core area.

Implementation should hide the transport mechanism behind a Tower transport abstraction so a later animated elevator can replace the presentation without changing run progression.

The next-floor portal activates only after boss/reward/card resolution is complete.

## 6. Ordinary Pokémon encounters

Tower ordinary encounters are explicitly selected and spawned by CobbleTowers.

Do not rely on uncontrolled natural Cobblemon spawning inside Tower instances.

Ordinary encounters are individually battleable; party members may fight different ordinary encounters simultaneously.

A floor requires a configurable number of ordinary encounters to be completed before the boss activates.

Future encounter selection should support:

- floor-based difficulty bands
- run/floor themes
- deterministic selection from the run seed where appropriate

Tower-owned encounter entities must be tracked directly by UUID. Do not search the dimension globally to rediscover them.

## 7. Boss selection and integration

Boss definitions come from the public CobbleRaids API rather than duplicated Tower configuration.

Boss selection combines:

- floor-based rarity/power progression
- optional run/floor theme filtering
- special floor 5 checkpoint pool behavior
- dedicated floor 10 championship pool behavior

The Tower owns run/floor/reward/modifier state.

CobbleRaids owns cooperative battle execution, shared boss HP, battle participant mechanics, party-state carryover, and battle cleanup.

Tower boss encounters use addon-managed/external CobbleRaids completion mode so ordinary CobbleRaids rewards/catching/progression are not granted.

## 8. Card selection and multiplayer voting

After each floor, present three randomly selected animated cards when the client UI supports them.

Initial server implementation must not depend on reusable CobblemonCards UI until that API has been verified.

Voting rules:

- solo: direct selection
- multiplayer: majority vote
- selection window: 45 seconds
- tie: deterministic random choice among tied winners using the run RNG
- no votes when timer expires: deterministic choice from the three offered cards

The server is authoritative for available cards, votes, timeout, and resolution.

## 9. Temporary challenge semantics

A challenge card selected after floor N applies to the immediately following boss encounter.

The pending temporary modifier is consumed after that boss resolves.

Persist modifier IDs and tier/state data, not live Java object references.

## 10. Permanent challenge promotion

Every fifth accepted challenge selection triggers a permanent-modifier promotion decision.

**Approved rule:** the party chooses one of the previous five accepted challenge modifiers to promote into the run's permanent modifier set.

This promotion choice is separate from choosing the current floor's ordinary card result.

The five-challenge promotion window is then reset for the next group of five accepted challenges.

## 11. Permanent modifier tiers

Permanent modifier duplicates do not create duplicate entries.

**Approved rule:** selecting/promoting a modifier that already exists permanently increases its tier.

Conceptually:

- Fortified I
- Fortified II
- Fortified III

Each modifier definition owns its allowed maximum tier and per-tier effect values.

The run persists one modifier ID -> tier mapping.

Bosses receive:

- every currently active permanent modifier at its stored tier
- the current pending temporary challenge modifier

## 12. Player elimination

If one participant's entire Pokémon party is exhausted, that player is eliminated from the Tower run while remaining party members continue.

Default elimination destination: the player's pre-Tower location, captured safely when entering the run.

Physical Minecraft death inside the Tower is also treated as Tower elimination, while inventory is protected from Tower-specific loss unless the server's global inventory policy overrides behavior intentionally.

## 13. Disconnect/rejoin

Disconnected active participants receive a 5-minute reconnect grace period.

During the grace period, the run may continue for remaining players.

If the participant reconnects within the grace period and the run still exists, the run engine may restore them only when doing so is safe for the current progression/battle state.

If the grace period expires, they are eliminated from the run.

No stale participant reference may keep a run, entity, chunk, or server world alive.

## 14. `/tower leave`

`/tower leave` has a 5-second channel.

The channel is cancelled by:

- player movement
- incoming damage
- entering a battle

If a player is already in an active battle, the run/boss integration must perform canonical participant withdrawal before teleporting the player out.

## 15. Teleport restrictions

Tower escape protection uses two layers:

1. block known direct mechanisms such as ender pearls and configured home/teleport commands/items where integrations are available
2. authoritative Tower-boundary enforcement so an unknown teleport provider cannot move an active participant out of the instance silently

Do not rely on command-name blacklists alone.

## 16. Inventory and healing

Normal player inventory is permitted.

Cobblemon healing items are permitted.

There is no automatic between-floor healing.

Cards/modifiers may grant full-party or partial healing as explicit effects.

## 17. Rewards

Tower rewards are independent of CobbleRaids rewards.

A reward is earned/claimed after every boss.

Once committed, already claimed Tower rewards remain with the player even if the run later fails.

Initial presentation may use GUI + sound. A later dedicated Pokéball/reveal animation is allowed, but it remains a Tower reward implementation rather than invoking CobbleRaids reward state.

**No fireworks/firework entities are permitted for reward feedback.**

## 18. Visual/effect policy

CobbleTowers must not use firework rockets, firework entities, or firework-based celebration mechanics for:

- floor completion
- boss victory
- card selection
- reward reveal
- portal activation
- championship completion
- player elimination
- run completion

Permitted later presentation includes ordinary particles, sounds, shaders/client effects, GUI animation, Pokéball animation, lighting changes, and other non-firework effects that are performance-safe.

## 19. Standard vs infinite mode

The run engine must support arbitrary positive floor numbers.

Standard mode configuration stops at floor 10.

Do not encode business logic around `floor <= 10` except inside the Standard mode definition/template provider.

This preserves a path to infinite mode without rewriting core run state.

## 20. Performance rules

The production implementation must prefer:

- UUID-indexed ownership maps
- explicit event-driven transitions
- deterministic run-scoped RNG
- bounded instance allocation
- direct cleanup from known run-owned objects
- progressive gameplay activation

Avoid:

- `getAllEntities()` in runtime Tower progression
- broad radius entity scans for ownership discovery
- one tick callback per run/player/floor
- dynamic dimension creation per party
- uncontrolled natural Cobblemon spawns in Tower instances
- permanent chunk tickets for all 10 floors
- repeated execution of the source `.mcfunction` command set per run

## 21. Validation target

Before V1 is considered server-ready, test at minimum:

- solo runs
- 2/3/4-player parties
- 20 simultaneous solo instances
- slot allocation/reuse
- player disconnect/reconnect
- one player eliminated while teammates continue
- `/tower leave`
- failed boss encounter
- successful boss encounter
- server restart with active runs
- abandoned runs
- dimension/chunk unload
- repeated create/destroy soak test
- no leaked Tower-owned entities
- no leaked CobbleRaids encounter handles
- no leaked instance allocations
- no persistent chunk tickets after run cleanup

Measure MSPT/TPS, loaded chunks, entity/Pokémon count, active battles, heap, GC behavior, and disk growth under concurrency.
