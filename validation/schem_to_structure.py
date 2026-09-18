#!/usr/bin/env python3
"""Converts a WorldEdit Sponge .schem into a vanilla Minecraft structure .nbt.

Minecraft cannot read WorldEdit's format. Its StructureTemplateManager reads the vanilla structure
layout from data/<namespace>/structure/<name>.nbt, so the arenas are converted once, here, and the
result is committed. The alternative -- parsing Sponge at runtime -- would put a hand-written binary
parser in the path that builds a floor while players wait, to save a step that a person runs twice a
year.

    Sponge v2 in : Palette (name -> id), BlockData (varint stream, x + z*W + y*W*L), Width/Height/Length
    vanilla out  : size, palette (Name + Properties), blocks ({pos, state})

Air is dropped. An arena is a 51x10x51 box that is mostly nothing, and a structure that places air
would also erase whatever it is pasted over, which is not what a floor should do.

**The conversion is checked, not assumed.** --check re-reads the output and compares its per-block
histogram against the source. A converter that quietly drops or shifts blocks fails as a hole in an
arena floor weeks later, by which time nobody is looking at this script.

    python validation/schem_to_structure.py <input.schem> <output.nbt>
    python validation/schem_to_structure.py --check <input.schem> <output.nbt>
"""

from __future__ import annotations

import argparse
import gzip
import struct
import sys
from collections import Counter
from pathlib import Path

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 7, 8, 9, 10, 11, 12


class Reader:
    """Just enough NBT to read a schematic. Big-endian, as the format is."""

    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def take(self, count: int) -> bytes:
        chunk = self.data[self.pos:self.pos + count]
        if len(chunk) != count:
            raise ValueError(f"truncated NBT at {self.pos}: wanted {count} bytes, had {len(chunk)}")
        self.pos += count
        return chunk

    def u1(self) -> int:
        return self.take(1)[0]

    def i2(self) -> int:
        return struct.unpack(">h", self.take(2))[0]

    def i4(self) -> int:
        return struct.unpack(">i", self.take(4))[0]

    def string(self) -> str:
        return self.take(struct.unpack(">H", self.take(2))[0]).decode("utf-8")

    def value(self, tag: int):
        if tag == TAG_BYTE:
            return struct.unpack(">b", self.take(1))[0]
        if tag == TAG_SHORT:
            return self.i2()
        if tag == TAG_INT:
            return self.i4()
        if tag == TAG_LONG:
            return struct.unpack(">q", self.take(8))[0]
        if tag == TAG_FLOAT:
            return struct.unpack(">f", self.take(4))[0]
        if tag == TAG_DOUBLE:
            return struct.unpack(">d", self.take(8))[0]
        if tag == TAG_BYTE_ARRAY:
            return self.take(self.i4())
        if tag == TAG_STRING:
            return self.string()
        if tag == TAG_LIST:
            element, count = self.u1(), self.i4()
            return [self.value(element) for _ in range(max(count, 0))]
        if tag == TAG_COMPOUND:
            out = {}
            while True:
                child = self.u1()
                if child == TAG_END:
                    return out
                # The name is read into a variable on purpose: in `d[self.string()] = self.value(t)`
                # Python evaluates the right-hand side first, so the value would be read out of the
                # stream before the name that precedes it.
                name = self.string()
                out[name] = self.value(child)
        if tag == TAG_INT_ARRAY:
            return [self.i4() for _ in range(self.i4())]
        if tag == TAG_LONG_ARRAY:
            count = self.i4()
            return [struct.unpack(">q", self.take(8))[0] for _ in range(count)]
        raise ValueError(f"unknown NBT tag {tag} at {self.pos}")

    def root(self) -> tuple[str, dict]:
        tag = self.u1()
        if tag != TAG_COMPOUND:
            raise ValueError(f"expected a compound root, got tag {tag}")
        return self.string(), self.value(TAG_COMPOUND)


class Writer:
    """The matching half. Only the tags a structure file needs."""

    def __init__(self):
        self.out = bytearray()

    def u1(self, value: int) -> None:
        self.out.append(value)

    def i4(self, value: int) -> None:
        self.out += struct.pack(">i", value)

    def string(self, value: str) -> None:
        encoded = value.encode("utf-8")
        self.out += struct.pack(">H", len(encoded)) + encoded

    def named(self, tag: int, name: str) -> None:
        self.u1(tag)
        self.string(name)

    def int_list(self, name: str, values: list[int]) -> None:
        self.named(TAG_LIST, name)
        self.u1(TAG_INT)
        self.i4(len(values))
        for value in values:
            self.i4(value)

    def compound_list(self, name: str, writers) -> None:
        self.named(TAG_LIST, name)
        self.u1(TAG_COMPOUND)
        self.i4(len(writers))
        for write in writers:
            write(self)
        # Each element writes its own TAG_End.

    def end(self) -> None:
        self.u1(TAG_END)


def varints(data: bytes):
    """Sponge stores one varint per block, in x + z*Width + y*Width*Length order."""
    value = 0
    shift = 0
    for byte in data:
        value |= (byte & 0x7F) << shift
        if byte & 0x80:
            shift += 7
            continue
        yield value
        value = 0
        shift = 0
    if shift:
        raise ValueError("BlockData ended mid-varint")


def split_state(state: str) -> tuple[str, dict[str, str]]:
    """'minecraft:red_banner[rotation=10]' -> ('minecraft:red_banner', {'rotation': '10'})."""
    if "[" not in state:
        return state, {}
    name, _, rest = state.partition("[")
    properties = {}
    for pair in rest.rstrip("]").split(","):
        if not pair:
            continue
        key, _, value = pair.partition("=")
        properties[key.strip()] = value.strip()
    return name, properties


def read_schematic(path: Path) -> dict:
    raw = path.read_bytes()
    data = gzip.decompress(raw) if raw[:2] == b"\x1f\x8b" else raw
    _, root = Reader(data).root()
    # Sponge v3 moves everything under a "Schematic" compound; v2 keeps it at the root.
    return root.get("Schematic", root)


def blocks_from(schematic: dict) -> tuple[tuple[int, int, int], list[tuple[tuple[int, int, int], str]]]:
    width, height, length = schematic["Width"], schematic["Height"], schematic["Length"]
    palette = schematic["Palette"]
    by_id = {index: name for name, index in palette.items()}
    block_data = schematic["BlockData"]

    placed = []
    for i, state_id in enumerate(varints(bytes(block_data))):
        state = by_id.get(state_id)
        if state is None:
            raise ValueError(f"block {i} refers to palette id {state_id}, which the palette does not have")
        if state in AIR:
            continue
        x = i % width
        z = (i // width) % length
        y = i // (width * length)
        placed.append(((x, y, z), state))
    expected = width * height * length
    if i + 1 != expected:
        raise ValueError(f"BlockData held {i + 1} blocks, but {width}x{height}x{length} is {expected}")
    return (width, height, length), placed


def write_structure(path: Path, size: tuple[int, int, int], placed, data_version: int) -> None:
    states = sorted({state for _, state in placed})
    state_ids = {state: index for index, state in enumerate(states)}

    writer = Writer()
    writer.u1(TAG_COMPOUND)
    writer.string("")
    writer.named(TAG_INT, "DataVersion")
    writer.i4(data_version)
    writer.int_list("size", list(size))

    def palette_entry(state: str):
        def write(w: Writer) -> None:
            name, properties = split_state(state)
            if properties:
                w.named(TAG_COMPOUND, "Properties")
                for key, value in properties.items():
                    w.named(TAG_STRING, key)
                    w.string(value)
                w.end()
            w.named(TAG_STRING, "Name")
            w.string(name)
            w.end()
        return write

    writer.compound_list("palette", [palette_entry(state) for state in states])

    def block_entry(position, state: str):
        def write(w: Writer) -> None:
            w.int_list("pos", list(position))
            w.named(TAG_INT, "state")
            w.i4(state_ids[state])
            w.end()
        return write

    writer.compound_list("blocks", [block_entry(position, state) for position, state in placed])
    writer.compound_list("entities", [])
    writer.end()

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(bytes(writer.out)))


def histogram_of_structure(path: Path) -> Counter:
    raw = path.read_bytes()
    data = gzip.decompress(raw) if raw[:2] == b"\x1f\x8b" else raw
    _, root = Reader(data).root()
    palette = []
    for entry in root["palette"]:
        name = entry["Name"]
        properties = entry.get("Properties", {})
        if properties:
            joined = ",".join(f"{key}={value}" for key, value in sorted(properties.items()))
            palette.append(f"{name}[{joined}]")
        else:
            palette.append(name)
    return Counter(palette[block["state"]] for block in root["blocks"])


def histogram_of_schematic(placed) -> Counter:
    def normalise(state: str) -> str:
        name, properties = split_state(state)
        if not properties:
            return name
        joined = ",".join(f"{key}={value}" for key, value in sorted(properties.items()))
        return f"{name}[{joined}]"
    return Counter(normalise(state) for _, state in placed)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", type=Path, help="the .schem to read")
    parser.add_argument("target", type=Path, help="the structure .nbt to write")
    parser.add_argument("--check", action="store_true",
                        help="verify an existing target against the source instead of writing it")
    args = parser.parse_args()

    schematic = read_schematic(args.source)
    size, placed = blocks_from(schematic)
    data_version = schematic.get("DataVersion", 3955)
    source_histogram = histogram_of_schematic(placed)

    if not args.check:
        write_structure(args.target, size, placed, data_version)

    if not args.target.exists():
        print(f"schem_to_structure: FAIL -- {args.target} does not exist")
        sys.exit(1)

    written = histogram_of_structure(args.target)
    if written != source_histogram:
        print(f"schem_to_structure: FAIL -- {args.target.name} does not match {args.source.name}")
        for state in sorted(set(written) | set(source_histogram)):
            if written[state] != source_histogram[state]:
                print(f"  {state}: schematic {source_histogram[state]}, structure {written[state]}")
        sys.exit(1)

    total = sum(source_histogram.values())
    box = size[0] * size[1] * size[2]
    print(f"schem_to_structure: {args.source.name} -> {args.target.name}  "
          f"{size[0]}x{size[1]}x{size[2]}  {total:,} blocks placed of {box:,} "
          f"({len(source_histogram)} states, air dropped), histogram matches")


if __name__ == "__main__":
    main()
