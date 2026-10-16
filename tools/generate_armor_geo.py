#!/usr/bin/env python3
"""Geo armor models for Armor Model API (P49): a Bedrock .geo.json per set plus its textures, built from the worn layer art.

    python tools/generate_armor_geo.py [--sets tideforge,...] [--out <assets/cobbletowers dir>]

Clients that have Armor Model API render these (limbs swing, real 3D ornaments); other clients keep the shader layers. The
server tags each piece with `minecraft:custom_data` {"armor_model_api": {model, texture, glowmask}}.

The base cubes use the vanilla armor UV layout, so the existing 64x32-layout layer art maps onto them at any resolution (UV is
divided by the declared texture size, not the file size). Armor layer 1 art goes to `textures/armor/<set>_1.png`, layer 2
(leggings) to `<set>_2.png`, each with a `_glowmask` sibling cut from the emission half of the layer image.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import math

import numpy as np
from PIL import Image

import armor_paint
from armor_paint import Cv
from generate_armor_textures import SPECS, Look

TEX_W, TEX_H = 64, 128  # rows 0-31 the layer art in the vanilla layout, rows 32-127 the ornament cubes
K = 4  # texels per unit in the written PNGs, matching the layer art


def cube(origin, size, uv, inflate, mirror=False):
    c = {"origin": origin, "size": size, "uv": uv, "inflate": inflate}
    if mirror:
        c["mirror"] = True
    return c


def bones(extra: dict | None = None) -> list[dict]:
    """The nine conventional armor bones with the vanilla cubes; `extra` maps a bone name to more cubes (ornaments)."""
    extra = extra or {}

    def bone(name, pivot, cubes):
        return {"name": name, "pivot": pivot, "cubes": cubes + extra.get(name, [])}

    return [
        bone("armorHead", [0, 24, 0], [cube([-4, 24, -4], [8, 8, 8], [0, 0], 1.0)]),
        bone("armorBody", [0, 24, 0], [cube([-4, 12, -2], [8, 12, 4], [16, 16], 1.0)]),
        bone("armorRightArm", [-5, 22, 0], [cube([-8, 12, -2], [4, 12, 4], [40, 16], 1.0)]),
        bone("armorLeftArm", [5, 22, 0], [cube([4, 12, -2], [4, 12, 4], [40, 16], 1.0, True)]),
        bone("armorWaist", [0, 24, 0], [cube([-4, 12, -2], [8, 4, 4], [16, 24], 0.5)]),
        bone("armorRightLeg", [-1.9, 12, 0], [cube([-3.9, 0, -2], [4, 12, 4], [0, 16], 0.5)]),
        bone("armorLeftLeg", [1.9, 12, 0], [cube([-0.1, 0, -2], [4, 12, 4], [0, 16], 0.5, True)]),
        bone("armorRightBoot", [-1.9, 12, 0], [cube([-3.9, 0, -2], [4, 4, 4], [0, 24], 1.0)]),
        bone("armorLeftBoot", [1.9, 12, 0], [cube([-0.1, 0, -2], [4, 4, 4], [0, 24], 1.0, True)]),
    ]


def geo(name: str, extra: dict | None = None) -> dict:
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{name}", "texture_width": TEX_W, "texture_height": TEX_H,
                            "visible_bounds_width": 3, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
            "bones": bones(extra),
        }],
    }


def tideforge_ornaments():
    """bone -> [(origin, size, material)] in geo coordinates (y up, the front at -z), unrotated boxes."""
    o = {"armorHead": [], "armorBody": [], "armorRightArm": [], "armorLeftArm": [], "armorRightLeg": [], "armorLeftLeg": [],
         "armorRightBoot": [], "armorLeftBoot": []}
    # head: a swept comb crest, a nape fin, flared temple fins
    o["armorHead"] += [([-1, 33, 1.5], [2, 6, 3.5], "metal"), ([-1, 33, -2], [2, 9, 3.5], "metal"), ([-1, 33, -5], [2, 4.5, 3], "trim"),
                       ([-0.6, 38, -1.4], [1.2, 2.5, 1.4], "glow"), ([-1.2, 24, 5], [2.4, 9, 1.8], "metal")]
    for sx in (1, -1):
        x = 5 if sx == 1 else -7
        o["armorHead"] += [([x, 26, -3], [2, 8, 7], "metal"), ([x + (1.5 if sx == 1 else -1), 28, -1], [1, 3.5, 3.5], "trim")]
    # body: a carapace down the back with a dorsal fin
    o["armorBody"] += [([-4.5, 12, 3], [9, 13, 1.6], "metal"), ([-3.5, 15, 4.6], [7, 9, 1.6], "metal"), ([-2, 18, 6.2], [4, 5, 1.6], "trim"),
                       ([-0.8, 25, 3.2], [1.6, 5.5, 4.5], "metal"), ([-0.5, 20, 7.8], [1, 2, 1], "glow")]
    # arms: stacked shell pauldrons that swing with the arm
    for bone, sx in (("armorRightArm", -1), ("armorLeftArm", 1)):
        x0, x1 = (-10.5, -2.5) if sx < 0 else (2.5, 10.5)
        o[bone] += [([x0, 23.2, -4], [x1 - x0, 1.8, 8], "metal"), ([x0 + (0 if sx < 0 else 1), 25, -3], [x1 - x0 - 1, 1.6, 6], "metal"),
                    ([-10.5 if sx < 0 else 7, 26.6, -2], [3.5, 1.2, 4], "trim")]
    # legs: knee guards and side fins; boots: cuff fins and toe caps
    for bone, bb, sx in (("armorRightLeg", "armorRightBoot", -1), ("armorLeftLeg", "armorLeftBoot", 1)):
        x = -3.9 if sx < 0 else -0.1
        o[bone] += [([x - 0.3, 4, -3.5], [4.6, 4, 1], "trim"), ([x - 1.5 if sx < 0 else x + 4.5, 2, -2], [1, 8, 4.5], "metal")]
        o[bb] += [([x - 1.6 if sx < 0 else x + 4.6, 1, -1.5], [1, 5, 3.5], "metal"), ([x - 0.2, 0.2, -3.6], [4.4, 2, 1.2], "trim")]
    return o


def pack_ornaments(ornaments, look, art):
    """Paints every ornament box into rows 32+ of a copy of `art` (an RGBA image of the layer art) and returns (image, cubes)."""
    armor_paint.BEVEL = 1
    cv = Cv(TEX_W * K, TEX_H * K)
    h, w = art.height, art.width
    cv.a[:h, :w] = np.array(art)
    ramps = {"metal": look.metal, "trim": look.trim, "glow": look.glow}
    tones = {"top": 0.55, "front": 0.2, "back": 0.2, "left": -0.1, "right": -0.1, "bottom": -0.6}
    cubes = {}
    x, y, row = 0, 32, 0
    for bone, items in ornaments.items():
        for origin, size, material in items:
            sx, sy, sz = size
            cw, ch = math.ceil(2 * sz + 2 * sx), math.ceil(sz + sy)
            if x + cw > TEX_W:
                x, y, row = 0, y + row, 0
            if y + ch > TEX_H:
                raise ValueError("ornament texture is full")
            faces = {"top": (sz, 0, sx, sz), "bottom": (sz + sx, 0, sx, sz), "left": (0, sz, sz, sy), "front": (sz, sz, sx, sy),
                     "right": (sz + sx, sz, sz, sy), "back": (2 * sz + sx, sz, sx, sy)}
            for name, (fx, fy, fw, fh) in faces.items():
                x0, y0 = round((x + fx) * K), round((y + fy) * K)
                x1, y1 = round((x + fx + fw) * K), round((y + fy + fh) * K)
                mask = np.zeros((TEX_H * K, TEX_W * K), bool)
                mask[y0:y1, x0:x1] = True
                cv.plate(mask, ramps[material], outline=(x1 - x0) >= 5 and (y1 - y0) >= 5, split=(x1 - x0) > 8 and (y1 - y0) > 8,
                         tone=tones[name], hot=name == "top")
            cubes.setdefault(bone, []).append(cube(list(origin), list(size), [x, y], 0.0))
            x, row = x + cw, max(row, ch)
    return Image.fromarray(cv.a, "RGBA"), cubes


def write_set(name: str, layers: Path, out: Path) -> None:
    (out / "geo").mkdir(parents=True, exist_ok=True)
    (out / "textures/armor").mkdir(parents=True, exist_ok=True)
    look = Look(name, SPECS[name])
    ornaments = {"tideforge": tideforge_ornaments}.get(name, lambda: {})()
    cubes = {}
    for number in (1, 2):
        sheet = Image.open(layers / f"{name}_layer_{number}.png").convert("RGBA")
        half = sheet.width // 2
        art = sheet.crop((0, 0, half, sheet.height))
        mask = sheet.crop((half, 0, sheet.width, sheet.height)).split()[3]
        full, cubes = pack_ornaments(ornaments, look, art)
        full.save(out / f"textures/armor/{name}_{number}.png")
        # glow: the art where the layer's mask says so (ornament glow texels are the glow ramp colours)
        arr = np.array(full)
        glow_mask = np.zeros(arr.shape[:2], bool)
        glow_mask[: mask.height, : mask.width] = np.array(mask) > 0
        for c in look.glow[3:]:
            glow_mask |= (arr[..., 0] == c[0]) & (arr[..., 1] == c[1]) & (arr[..., 2] == c[2]) & (arr[..., 3] > 0)
        glow = np.zeros_like(arr)
        glow[glow_mask] = arr[glow_mask]
        Image.fromarray(glow, "RGBA").save(out / f"textures/armor/{name}_{number}_glowmask.png")
    (out / f"geo/{name}.geo.json").write_text(json.dumps(geo(name, cubes), indent=1), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sets", default="tideforge")
    parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers")
    args = parser.parse_args()
    for name in args.sets.split(","):
        write_set(name, args.out / "textures/models/armor", args.out)
        print("wrote geo armor", name)


if __name__ == "__main__":
    main()
