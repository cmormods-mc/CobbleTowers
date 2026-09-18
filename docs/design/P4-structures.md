# P4: chunk tickets, real arenas, anchors and the warm pool

Written before the code it describes, as the TDS gate requires.

P3 gave a run a cell, but the cell was empty void and nothing kept it loaded. P4 builds a floor in it,
keeps its chunks loaded for exactly as long as something is using them, and takes both away again.

It closes the two limits P3 left explicit: the cleanup sweep only saw loaded chunks, and the third
verification stage was deliberately absent rather than stubbed.

## Decisions taken into P4

| Decision | Choice | Why |
|---|---|---|
| Schematic format | **Converted offline to vanilla structure NBT** | Minecraft cannot read WorldEdit's Sponge format. The alternative is a hand-written binary parser in the path that builds a floor while players wait, to save a step a person runs twice a year. |
| Chunk loading | **Region tickets, never `setChunkForced`** | A forceload is written into the world and survives a restart, which is exactly what #27 forbids. Tickets live in memory, so a server that comes back from a crash holds none -- the invariant is true by construction. |
| Ticket footprint | **The arena, not the cell** | A cell interior is 16 chunks square; an arena is about four. Loading the interior would tick 289 chunks per run to hold a building that fits in 49. |
| Anchors | **Declared in the floor definition, validated twice** | The schematics carry no marker blocks, so nothing can derive them. They are checked offline against the committed structure and again at runtime against what was actually pasted. |
| Warm pool | **Keyed by structure** | Warming only saves work if the cell handed over is already built *for the floor asking for it*. A pool that did not know which floor would have to paste again on handover. |
| Reset | **Sweep a fixed volume, write only where something is** | Nothing remembers what was pasted into a cell after a restart. Reading is far cheaper than writing, so the sweep looks at ~74k positions and writes the few thousand that are not already air. |

## 1. `validation/schem_to_structure.py`

Sponge v2 in (`Palette`, `BlockData` varints, `Width/Height/Length`), vanilla structure NBT out
(`size`, `palette`, `blocks`). Air is dropped: an arena is a 51x10x51 box that is mostly nothing, and
a structure that placed air would also erase whatever it was pasted over.

**The conversion is checked, not assumed.** `--check` re-reads the output and compares its per-block
histogram against the source; `ci_local.sh` runs it for every committed schematic. The `.schem`
sources are committed beside the converter so that is reproducible, which catches the failure nobody
would otherwise notice: a schematic re-exported without re-running the conversion, leaving the jar
shipping the previous building.

Results: `arena_floor` 51x10x51, 5,437 blocks of 26,010; `boss_arena` 49x9x49, 2,798 of 21,609.

## 2. `instance/CellTickets`

One `TicketType<ChunkPos>`, added when a cell is prepared and removed when it is released, radius 3
chunks around the arena centre -- 49 chunks, which holds a 51-block arena with margin and ticks, so
entities in an arena behave while a run is in it.

## 3. `instance/CellPreparer`

Holds the chunks, then pastes: the order matters, or the placement writes into chunks that are not
loaded. `reset` sweeps the cell and clears what it finds.

## 4. Anchors (TDS section 12)

Four per floor -- entry, presentation, spectator, exit -- declared relative to the structure, because
a run is given whichever cell is free and a world position would be right for exactly one of them.

Validated in two places, on purpose:
- **offline**, by `validate_definitions.py`, against the committed structure: inside it, something
  solid below, nothing in the way, headroom clear. 40 anchors checked today.
- **at runtime**, by `CellAnchors`, against the blocks actually pasted. That is the version that
  still holds when a structure is replaced or rotated.

A floor that builds wrong is **broken content, not a bad cell**, so the cell goes back to the free
pool rather than into quarantine: the next run would fail the same way wherever it was put, and
quarantining would slowly eat the tower over a typo.

## 5. The warm pool

Cells already built, kept per structure, topped up after an allocation and never more than once every
five seconds. Event-driven rather than ticked, so it has no idle cost (TDS section 11 forbids
per-tick sweeps).

The measured reason it exists: **a cold paste took 242 ms** on the rig -- about five ticks, a visible
stutter -- while later pastes took 11 and 84 ms. Handing over a cell that is already built removes
that from the moment a party is waiting.

## 6. Cleanup

Release now: reset the blocks, sweep the contents *while the chunks are still held*, drop the
tickets, then ask whether anything still claims the cell.

That last stage was first written as "are the tickets gone", which was very nearly a tautology --
release drops them on the line above. It asks instead whether **anything in this mod still claims the
cell**: a ticket that was not dropped, or a warm-pool entry that outlived the cell it names. That is
a question a bug can answer wrongly, which is the only kind worth asking. It is proven by a unit
test rather than live, because a live release cannot be talked into the failing state from outside.

## 7. Performance, measured

- **Nothing per tick.** Tickets change on allocate and release; the pool is event-driven.
- **Cold paste 242 ms, warm pastes 11-84 ms**, reset 5,437 writes. The cold figure is why the pool
  exists; if a future structure is much larger, the build wants chunking across ticks before it
  ships. The 127-block Tideforge interior would be roughly seven times the blocks.
- **147 tower chunks held for 3 cells** on the rig -- 49 per cell, as designed. That is the number to
  multiply by expected concurrent runs.
- **Reading beats writing**: reset reads ~74k positions and writes only what is there.

## 8. Assumptions and constraints

- Minecraft 1.21.1, Fabric, Java 21, CobbleRaids >= 0.8.94-encounter-api.
- The reset volume fits this phase's arenas with margin; a tower-sized structure needs it raised.
- Nothing here reaches a client, and no floor logic, encounter or reward drives it yet -- a floor is
  built and torn down by the run lifecycle and by hand, and that is all.
