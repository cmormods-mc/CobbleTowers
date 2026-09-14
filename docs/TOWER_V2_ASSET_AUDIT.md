# Battle Tower v2 Asset Audit

## Notes

### Exact functionality

This audit records the measured block-space envelope and source decomposition of the supplied Battle Tower v2 assets so production instance spacing and template conversion can be based on actual geometry.

### Architectural role

These measurements constrain `TowerInstanceAllocator`, future instance bounds, structure-template conversion, chunk preparation, and teardown logic.

### Performance considerations

The source assets contain 1,168 function lines in the all-in-one form. They are retained as development/reference geometry; production runtime should place optimized structure templates rather than execute the full command stream for every run.

### Source assets inspected

- `Cobblemon_Battle_Tower_v2_all_in_one.mcfunction`
- `Cobblemon_Battle_Tower_v2_datapack.zip`

## Measured maximum relative block envelope

Parsing every relative-coordinate `fill` and `setblock` command in the all-in-one source gives:

- X: `-24 .. 24`
- Y: `0 .. 130`
- Z: `-30 .. 24`

Inclusive maximum dimensions:

- width X: 49 blocks
- height Y: 131 blocks
- depth Z: 55 blocks

The entrance projection is responsible for the larger negative-Z reach.

## V1 instance spacing decision

Default private-cell spacing is **192 blocks** on both X and Z.

With the measured 49x55 horizontal tower footprint, adjacent origins separated by 192 blocks leave well over 100 blocks of horizontal separation between physical tower envelopes. This is sufficient for the V1 private-instance grid while keeping coordinates compact for the 20-solo-run concurrency target.

Spacing remains configurable, but values below a future validated minimum must be rejected rather than allowed to create overlapping instance regions.

## Datapack decomposition

The supplied datapack is already divided into useful conversion units:

- `foundation.mcfunction`
- `core.mcfunction`
- `floor_01.mcfunction`
- `floor_02.mcfunction`
- `floor_03.mcfunction`
- `floor_04.mcfunction`
- `floor_05_boss.mcfunction`
- `floor_06.mcfunction`
- `floor_07.mcfunction`
- `floor_08.mcfunction`
- `floor_09.mcfunction`
- `floor_10_champion.mcfunction`
- `details.mcfunction`
- `clear_volume.mcfunction`
- `build.mcfunction`
- `build_clean.mcfunction`

These should guide the structure-template/NBT split instead of producing one giant monolithic template.

## Production conversion target

Recommended template grouping:

```text
tower/
  foundation
  core
  floor_01
  floor_02
  floor_03
  floor_04
  floor_05_checkpoint
  floor_06
  floor_07
  floor_08
  floor_09
  floor_10_championship
  details_crown
```

This preserves the authored tower appearance while allowing floor-aware placement, validation, and future replacement of individual sections.

## Runtime rule

The original `.mcfunction` files are reference/development assets only. Normal Tower run creation should not parse or execute the 1,168-command all-in-one source at runtime.
