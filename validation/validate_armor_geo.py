#!/usr/bin/env python3
"""The shipped geo armor (Armor Model API) must be well formed.

A broken .geo.json or a missing texture renders nothing on a Fabric client that has Armor Model API, and nothing in the build
notices: the server only sends the paths. So this checks, at build time:

- ArmorSetItems.GEO_SETS, the geo files and the textures name the same sets
- each geo parses, has one geometry `geometry.<set>` with a positive texture size and the nine armor bones; any other bone is a
  decorative (animation) bone, which Armor Model API allows only when it is nested, through `parent`, under an armor bone, and
  which may not set a bone-level `mirror` or `inflate` (the API refuses them; set them per cube)
- every cube has a numeric origin, a non-negative size, an whole uv, and its box-UV footprint fits the texture
- every set has textures/armor/<set>_{1,2}.png and a _glowmask sibling, PNG, all one size that is a whole multiple of the
  declared texture size

    python validation/validate_armor_geo.py
"""

from __future__ import annotations

import json
import math
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "cobbletowers"
ITEMS = ROOT / "src" / "main" / "java" / "com" / "cobbletowers" / "armor" / "ArmorSetItems.java"
BONES = {"armorHead", "armorBody", "armorRightArm", "armorLeftArm", "armorWaist", "armorRightLeg", "armorLeftLeg",
         "armorRightBoot", "armorLeftBoot"}
PNG_SIG = b"\x89PNG\r\n\x1a\n"

errors: list[str] = []
fractional: set[str] = set()


def fail(msg: str) -> None:
    errors.append(msg)


def num(v) -> bool:
    return isinstance(v, (int, float)) and not isinstance(v, bool) and v == v and abs(v) < 1e6


def vec(v, n: int) -> bool:
    return isinstance(v, list) and len(v) == n and all(num(x) for x in v)


def png_size(path: Path):
    head = path.read_bytes()[:24]
    if head[:8] != PNG_SIG or head[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", head[16:24])


def check_geo(name: str, path: Path) -> tuple[int, int] | None:
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
        geometry = doc["minecraft:geometry"]
    except (ValueError, KeyError) as e:
        fail(f"{name}: geo does not parse: {e!r}")
        return None
    if len(geometry) != 1:
        fail(f"{name}: expected one geometry, found {len(geometry)}")
        return None
    desc = geometry[0].get("description", {})
    if desc.get("identifier") != f"geometry.{name}":
        fail(f"{name}: identifier is {desc.get('identifier')!r}, want geometry.{name}")
    w, h = desc.get("texture_width"), desc.get("texture_height")
    if not (isinstance(w, int) and isinstance(h, int) and w > 0 and h > 0):
        fail(f"{name}: texture_width/texture_height must be positive integers")
        return None
    seen = set()
    all_bones = {b.get("name"): b for b in geometry[0].get("bones", [])}
    for bone in geometry[0].get("bones", []):
        bn = bone.get("name")
        if bn not in BONES:
            chain, cur = 0, bone
            while cur is not None and cur.get("name") not in BONES and chain < 8:
                cur = all_bones.get(cur.get("parent"))
                chain += 1
            if cur is None or cur.get("name") not in BONES:
                fail(f"{name}: unknown bone {bn!r} (a decorative bone must sit under an armor bone through 'parent')")
        if "mirror" in bone or "inflate" in bone:
            fail(f"{name}/{bn}: bone-level mirror/inflate is refused by Armor Model API; set it per cube")
        seen.add(bn)
        if not vec(bone.get("pivot"), 3):
            fail(f"{name}/{bn}: bad pivot")
        for i, c in enumerate(bone.get("cubes", [])):
            where = f"{name}/{bn}#{i}"
            if not (vec(c.get("origin"), 3) and vec(c.get("size"), 3) and vec(c.get("uv"), 2)):
                fail(f"{where}: origin, size, uv must be numeric vectors")
                continue
            if any(s < 0 for s in c["size"]):
                fail(f"{where}: negative size {c['size']}")
            if not all(float(x).is_integer() for x in c["uv"]):
                fail(f"{where}: uv must be whole, got {c['uv']}")
                continue
            if "inflate" in c and not num(c["inflate"]):
                fail(f"{where}: inflate not numeric")
            if "rotation" in c and not vec(c["rotation"], 3):
                fail(f"{where}: bad rotation")
            sx, sy, sz = c["size"]
            if any(not float(x).is_integer() for x in c["size"]):
                fractional.add(name)
            u, v = c["uv"]
            if u < 0 or v < 0 or u + math.ceil(2 * sz + 2 * sx) > w or v + math.ceil(sz + sy) > h:
                fail(f"{where}: uv footprint ({u},{v}) + box {c['size']} leaves the {w}x{h} texture")
    for missing in sorted(BONES - seen):
        fail(f"{name}: missing bone {missing}")
    return w, h


def main() -> int:
    declared = set()
    m = re.search(r"GEO_SETS\s*=\s*java\.util\.Set\.of\(([^)]*)\)", ITEMS.read_text(encoding="utf-8"))
    if m:
        declared = set(re.findall(r'"([a-z0-9_]+)"', m.group(1)))
    else:
        fail("could not find GEO_SETS in ArmorSetItems.java")
    geos = {p.name[: -len(".geo.json")]: p for p in (ASSETS / "geo").glob("*.geo.json")}
    if declared != set(geos):
        fail(f"GEO_SETS {sorted(declared)} and geo files {sorted(geos)} differ")
    for name in sorted(declared | set(geos)):
        size = check_geo(name, geos[name]) if name in geos else None
        factors = set()
        for layer in ("1", "2"):
            for suffix in ("", "_glowmask"):
                tex = ASSETS / "textures" / "armor" / f"{name}_{layer}{suffix}.png"
                if not tex.is_file():
                    fail(f"{name}: missing {tex.relative_to(ROOT)}")
                    continue
                got = png_size(tex)
                if got is None:
                    fail(f"{tex.relative_to(ROOT)}: not a PNG")
                    continue
                if size:
                    k = got[0] // size[0]
                    if k < 1 or got != (size[0] * k, size[1] * k):
                        fail(f"{tex.relative_to(ROOT)}: {got[0]}x{got[1]} is not a whole multiple of {size[0]}x{size[1]}")
                    factors.add(k)
        if len(factors) > 1:
            fail(f"{name}: textures differ in scale ({sorted(factors)})")
    if errors:
        print("validate_armor_geo: FAILED", file=sys.stderr)
        for e in errors:
            print(f"  {e}", file=sys.stderr)
        return 1
    if fractional:
        print(f"validate_armor_geo: note: {len(fractional)} sets still use fractional cube sizes ({', '.join(sorted(fractional))}); "
              "Blockbench floors them for box UV, so regenerate with tools/armor_kit when they are redone")
    print(f"validate_armor_geo: {len(geos)} geo armor sets ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
