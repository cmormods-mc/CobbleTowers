# P50: The route map (three paths between floors)

Status: **Design agreed with the owner (2026-10-17). Nothing built.** Follows P34/P34b/P45 (relics and rooms), P46 (rotating tower, seeded floors), P4 (structures and the warm pool). Written first, per the design gate. Visual direction: the Frontier Desk web UI (`docs/design/` has no doc for it yet; mockups were reviewed in chat).

## Why

Between floors a run today is: a three-card modifier draft, then (about half the time) a two-option room, then ready-up. Both are votes on the same screen, and neither says anything about where the run is going. The owner asked for a Slay the Spire style route: three paths of rooms to take, with the rooms eventually being different physical places in the tower.

The map turns two separate small choices into one meaningful one, shows what scouting buys, and gives the rooms a home that can grow into real structures.

## What a player sees

* After a floor is cleared, the intermission shows **three nodes** for the next step. A node is the **room** visited before the next battle plus the **modifier** carried from now on (plus, from Stage 2, the arena variant the next battle is fought in).
* Nodes are connected in a graph. Each step fans out to up to three nodes; milestone floors (a boss or champion, floors 5 and 10 today) are **one node every route meets**.
* **Far Sight** (and any scouting) reveals node kinds further down the graph. Beyond the scouting range a node shows as fog.
* The party votes on a node, as it votes on a card today. A tie is broken by the run seed. Votes show as party sprites on nodes.
* Whole-tower view: cleared floors, the chosen route, and fog.

## Decisions (owner, 2026-10-17: agreed with all recommendations)

The direction (a map, eventually with physical rooms) and the recommendations below are the owner's. The warm-window timing is the one thing still to measure on a rig.

1. **A node replaces the three-card draft.** Each of the three modifier offers rides on one node, so a floor has one choice, not two. Keeping both would mean two votes between every pair of floors.
2. **Every floor keeps its battle.** The CobbleRaids-based floor boss stays on every floor. A node chooses the room before it, the modifier, and later the arena; it does not skip a fight.
3. **Milestone floors stay fixed** and all routes converge on them. The relic draft there is unchanged.
4. **Node kinds are the rooms that exist.** Shrine, Gambler, Rest, Forge, Library, Vault, Pool and Echo Duel (`IntermissionEvents.Room`), plus Vendor and a plain Battle node (room: none). No new room logic in Stage 1.
5. **Scouting becomes route depth.** Far Sight's "3 floors further" reveals node kinds three steps ahead.
6. **Same map for the whole party and for a trial.** The graph comes from the run seed, so a daily or weekly trial gives every player the same map and a run code reproduces it.

## How it works

### Stage 1: the map (UI and route logic only)

* `route/RouteGraph` is **pure**: `generate(seed, tower, segmentStart, segmentEnd)` returns steps of up to three nodes and the edges between them. It uses its own ordinal space in `EncounterSeed` (like `DraftDraw.DRAFT_ORDINAL_BASE`, `RELIC_ORDINAL_BASE`, `IntermissionEvents.EVENT_ORDINAL_BASE`), so adding it cannot reshuffle any existing draw.
* A node is `(kind, roomOption, modifierId)`. The modifier comes from the same pool and the same eligibility rules as `DraftService.cardsFor` today (held modifiers, lock-in due, risk tiers), drawn per node instead of per card. A lock-in draft keeps working: when `lockInDue`, every node in the step offers a held modifier to lock in.
* A **segment** runs from one milestone to the next. A graph is generated per segment, so an ascending tower (`TowerDefinition.ascensionOf`) simply continues with the next segment.
* `DraftService.open` opens a **route choice** instead of a card draft; the votes, tie-break and settle path are reused. Settling a node applies its modifier and, if it has a room, opens that room exactly as `settle` chains an event room today.
* The chosen node per floor is **persisted inside `RunModifierState`**, beside `draft`, so a restart keeps the route and the screen can draw the past. No `PersistedRun` schema bump.
* Payload: the web UI needs the visible slice of the graph. This is a new action/payload pair in `WebGuiBridge.SUPPORTED`, with an `ActionCoverageTest` branch for the new action kind.

### Stage 2: arena variants

* `FloorDefinition` already has `layout` (structure plus four anchors). A node adds an optional **layout override**, so one floor can be fought in a different existing structure.
* `CellWarmPool` is keyed by structure with a target of 2 per structure. It is asked to `want` only the variants of the **voted** node, not all three, so build cost stays at today's level. (Open question: whether the warm window after a vote is long enough; see risks.)
* Needs a few arena variants per region (data and schematics, not code).

### Stage 3: physical rooms

* Each room kind gets its own structure with the four anchors (entry, presentation, spectator, exit). Players walk in, the vote or trade happens in the place, and they leave by the exit.
* The Vendor NPC and the Forge move into their halls. Echo Duel keeps its presentation point.
* The main cost is schematics (converted by `schem_to_structure.py`, checked in `ci_local.sh`) and build time, not code.

## Constraints that shape it

* **Persistence.** `PersistedRun.SCHEMA_VERSION` is 7 and a mismatch is rejected on load, which is why the route lives inside `RunModifierState` instead of a new run field.
* **Cell cost.** Cell build and release are sliced per tick and still the largest server cost (`cobbletowers-perf-audit`). Never prepare three arenas per step.
* **Fairness.** Scored trials give every player one attempt on the same seed. A route graph is a function of the seed, so it is the same for everyone. Paths with different difficulty need matching payout, which the existing risk tier and payout percentage already express.
* **Four traps** (`mod_code_four_traps`): votes arrive from several players (UUIDs, not player references), all server writes on the main thread, bounded scans, throttled ticks.

## Not built (and not in Stage 1)

* New room logic, new modifier kinds, elite or hidden nodes, shops that change per node.
* A route for the existing fixed Test Tower beyond the generic generator.
* Spectator and Echo recording of the route (Echoes already record a run; adding the route is a later field).
* Any change to milestone rewards, boards or Ascension.

## Tests (planned)

* Pure unit: the same seed gives the same graph twice and in a fresh JVM; every step has one to three nodes; every node's modifier is eligible and legal; milestone floors have one node; a lock-in step offers only held modifiers; no node repeats a modifier within a step.
* Pure unit: scouting depth reveals exactly the configured number of steps.
* Live (dev jar, new smoke test): two players vote different nodes, the tie goes to the seed; the chosen modifier and room apply; a restart keeps the route; a trial gives two players the same map.
* Web: screenshots from fixtures (`tools/shoot.mjs`), plus node tests for the pure view of the graph.

## Resolved and open

1. **Route storage: resolved.** Inside `RunModifierState`, no schema bump. Check that `RunModifierState`'s own serialisation tolerates a missing field so runs written before P50 still load.
2. **Warm window: open, measure first.** The next floor's cell is built after the vote. Measure on the rig whether the intermission is long enough; if not, warm the voted structure as soon as the first vote lands (Stage 2 only; Stage 1 uses one standard arena, which is already warm).
3. **A plain Battle node carries a modifier: resolved, yes.** Every node trades risk for payout, so no route is free.
4. **Room frequency: open.** The generator decides how many room nodes a step shows; the mix needs a first guess and playtest data.
5. **Voters: resolved.** Unchanged: everyone who can still fight (`DraftService.voters`).
6. **Runs in flight at deploy: resolved.** They finish with the card draft; the map applies from the next run.
