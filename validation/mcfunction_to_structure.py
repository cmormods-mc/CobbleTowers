#!/usr/bin/env python3
"""Converts a BlockArchitect-style .mcfunction (a list of `setblock ~x ~y ~z block` commands) into a structure .nbt.

BlockArchitect (and similar) export a build as a datapack function. Minecraft cannot paste that as a template, and
running 15,000 setblock commands to build a floor while players wait would be a poor way to make one, so it is converted
once, here, and the result is committed -- the same reasoning as schem_to_structure.py, whose writer this reuses.

    mcfunction in : `setblock ~x ~y ~z minecraft:block[props]` lines; `#` comments and blank lines ignored
    structure out : size (from the extent, origin at the minimum corner), palette, blocks

Only `setblock` with relative (`~`) coordinates is understood. Anything else -- `fill`, `summon`, absolute coordinates --
is an error rather than something to skip, because a skipped line is a hole in a floor found weeks later.

**Lighting.** A sealed build has only the light its artist put in it, and a tower floor is fought in it. Rather than edit
the artist's blocks, an optional sidecar `<name>.options.json` beside the source adds invisible `minecraft:light` blocks:

    {"lights": [9, 27, 35, 53]}        # standing levels (y); five lights per level, three blocks above the floor

Light blocks have no collision and cannot be seen, so the build looks exactly as drawn.

**The conversion is checked, not assumed.** --check re-reads the output and compares its per-block histogram with the
source plus any lights.

    python validation/mcfunction_to_structure.py <input.mcfunction> <output.nbt>
    python validation/mcfunction_to_structure.py --check <input.mcfunction> <output.nbt>
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from schem_to_structure import histogram_of_structure, split_state, write_structure  # noqa: E402

DATA_VERSION = 3955          # Minecraft 1.21.1
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
SETBLOCK = re.compile(r"^setblock\s+~(-?\d+)\s+~(-?\d+)\s+~(-?\d+)\s+(\S+)(?:\s+(?:replace|keep|destroy))?\s*$")
LIGHT_STATE = "minecraft:light[level=15]"
LIGHT_OFFSETS = [(0, 0), (-7, 0), (7, 0), (0, -7), (0, 7)]   # from the build's centre column
LIGHT_HEIGHT = 3


def parse(path: Path) -> list[tuple[tuple[int, int, int], str]]:
    placed: dict[tuple[int, int, int], str] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        match = SETBLOCK.match(line)
        if not match:
            raise SystemExit(f"mcfunction_to_structure: FAIL -- {path.name}:{number}: not a relative setblock: {line[:80]}")
        position = (int(match[1]), int(match[2]), int(match[3]))
        state = match[4]
        if split_state(state)[0] in AIR:
            continue
        if position in placed:
            raise SystemExit(f"mcfunction_to_structure: FAIL -- {path.name}:{number}: {position} is set twice")
        placed[position] = state
    return list(placed.items())


def options_for(source: Path) -> dict:
    sidecar = source.with_suffix(".options.json")
    return json.loads(sidecar.read_text(encoding="utf-8")) if sidecar.exists() else {}


def build(source: Path):
    """The blocks to write, as (position, state), with the origin moved to the minimum corner, and the size."""
    blocks = parse(source)
    if not blocks:
        raise SystemExit(f"mcfunction_to_structure: FAIL -- {source.name} places no blocks")
    low = tuple(min(p[axis] for p, _ in blocks) for axis in range(3))
    high = tuple(max(p[axis] for p, _ in blocks) for axis in range(3))
    size = tuple(high[axis] - low[axis] + 1 for axis in range(3))
    placed = [((p[0] - low[0], p[1] - low[1], p[2] - low[2]), state) for p, state in blocks]

    occupied = {position for position, _ in placed}
    lights = 0
    centre = (size[0] // 2, size[2] // 2)
    for level in options_for(source).get("lights", []):
        for dx, dz in LIGHT_OFFSETS:
            position = (centre[0] + dx, level + LIGHT_HEIGHT, centre[1] + dz)
            if position in occupied:
                print(f"  note: no light at {position}: that is a block of the build", file=sys.stderr)
                continue
            if not all(0 <= position[axis] < size[axis] for axis in range(3)):
                raise SystemExit(f"mcfunction_to_structure: FAIL -- light at {position} is outside the {size} build")
            placed.append((position, LIGHT_STATE))
            occupied.add(position)
            lights += 1
    return size, placed, lights


def histogram_of(placed) -> Counter:
    def normalise(state: str) -> str:
        name, properties = split_state(state)
        if not properties:
            return name
        return f"{name}[" + ",".join(f"{key}={value}" for key, value in sorted(properties.items())) + "]"
    return Counter(normalise(state) for _, state in placed)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("--check", action="store_true", help="verify an existing target instead of writing it")
    args = parser.parse_args()

    size, placed, lights = build(args.source)
    expected = histogram_of(placed)
    if not args.check:
        write_structure(args.target, size, sorted(placed, key=lambda item: (item[0][1], item[0][2], item[0][0])), DATA_VERSION)
    if not args.target.exists():
        print(f"mcfunction_to_structure: FAIL -- {args.target} does not exist")
        sys.exit(1)

    written = histogram_of_structure(args.target)
    if written != expected:
        print(f"mcfunction_to_structure: FAIL -- {args.target.name} does not match {args.source.name}")
        for state in sorted(set(written) | set(expected)):
            if written[state] != expected[state]:
                print(f"  {state}: source {expected[state]}, structure {written[state]}")
        sys.exit(1)

    total = sum(expected.values())
    print(f"mcfunction_to_structure: {args.source.name} -> {args.target.name}  {size[0]}x{size[1]}x{size[2]}  "
          f"{total:,} blocks ({lights} invisible lights), histogram matches")


if __name__ == "__main__":
    main()
