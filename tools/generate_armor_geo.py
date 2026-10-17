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
import random

import numpy as np
from PIL import Image

import armor_paint
from armor_paint import Cv
from generate_armor_textures import SPECS, Look

TEX_W, TEX_H = 64, 192  # rows 0-31 the layer art in the vanilla layout, rows 32-191 the ornament cubes
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


def _mx(items):
    """Mirror boxes across x = 0 (right side to left side); a box is (origin, size, material[, pattern])."""
    out = []
    for o, sz, *rest in items:
        if len(rest) >= 3 and rest[2]:  # a rotated box flips its y and z angles in the mirror
            rx, ry, rz = rest[2]
            rest = [rest[0], rest[1], (rx, -ry, -rz)]
        out.append(((-(o[0] + sz[0]), o[1], o[2]), sz, *rest))
    return out


def tideforge_ornaments():
    """bone -> [(origin, size, material[, pattern])] in geo coordinates (y up, the front at -z), unrotated boxes."""
    # head: a stepped dorsal fin, swept cheek wings, a tail fin down the nape
    crest = [([-1, 33, -2.5], [2, 5.5, 2], "metal"), ([-1, 33, -0.5], [2, 8, 2], "metal"),
             ([-1, 33, 1.5], [2, 5, 2], "metal"), ([-1, 33, 3.5], [2, 2, 2], "metal"), ([-0.7, 41, 0], [1.4, 1.6, 1], "glow"),
             ([1, 36.5, 0], [0.3, 2.2, 1.2], "glow"), ([-1.3, 36.5, 0], [0.3, 2.2, 1.2], "glow")]
    wing = [([5, 28, -2.5], [1.4, 5.5, 4.5], "metal", "scales"), ([5.2, 27, 2], [1.4, 4, 4.5], "metal"),
            ([5.4, 26, 6], [1.4, 3, 3.5], "metal"), ([5.6, 25.4, 9.5], [0.8, 2, 1.6], "trim")]
    nape = [([-1.2, 27, 5], [2.4, 6, 1.2], "metal"), ([-1.2, 24.5, 6.2], [2.4, 5.5, 1.2], "metal"), ([-1, 23, 7.4], [2, 4, 1], "trim")]
    # the face: a trident crown across the front, a heavy brow with a pearl, a slim nose guard, gill-slat cheeks, a chin fin
    crown = [([-5.2, 33, -5.4], [10.4, 1.4, 1.6], "trim"), ([-1.1, 34.4, -5], [2.2, 8, 1.8], "metal"), ([-0.7, 42.2, -4.8], [1.4, 1.2, 1.4], "glow"),
             ([3.3, 34.4, -5], [1.9, 5.5, 1.8], "metal", None, (0, 0, -20)), ([3.7, 39.2, -5.2], [1.1, 1.1, 1.4], "glow", None, (0, 0, -20))]
    brow = [([-5.4, 29.6, -5.9], [10.8, 1.6, 1.1], "metal", "scales"), ([-1.1, 29.7, -6.8], [2.2, 2.2, 1], "glow"),
            ([-1.8, 29.1, -6.3], [3.6, 0.7, 0.6], "trim"), ([-0.5, 25.4, -5.6], [1, 4.2, 0.7], "metal")]
    brow_fin = [([4.2, 30.6, -5.8], [1.6, 2.4, 1.2], "trim")]
    gills = [([3.4, 25.2 + 1.5 * i, -5.7 + 0.3 * i], [3, 0.9, 1.2], "metal") for i in range(3)]
    chin = [([-1.4, 21.6, -5.9], [2.8, 2.6, 1], "metal"), ([-0.8, 20.2, -5.7], [1.6, 1.6, 0.8], "trim")]
    crown = crown[:3] + crown[3:5] + _mx(crown[3:5])
    head = crest + wing + _mx(wing) + nape + crown + brow + brow_fin + _mx(brow_fin) + gills + _mx(gills) + chin
    # body: a scallop shell on the chest, a layered carapace and swept spine fin on the back
    ribs = [(-2.4, 2.4), (-1.2, 3.6), (0, 4.8), (1.2, 3.6), (2.4, 2.4)]
    body = [([cx - 0.55, 17, -3.9], [1.1, h, 0.9], "metal") for cx, h in ribs]
    body += [([-3, 16.2, -4], [6, 0.8, 1], "trim"), ([-0.7, 18.2, -4.4], [1.4, 1.4, 0.5], "glow")]
    body += [([-4.8, 12, 3], [9.6, 12.5, 1.4], "metal", "scales"), ([-4, 14.5, 4.4], [8, 9, 1.4], "metal", "scales"),
             ([-3, 17, 5.8], [6, 5.5, 1.2], "metal", "scales"), ([-0.8, 24.5, 3.2], [1.6, 2.5, 1.8], "metal"),
             ([-0.8, 24.5, 5], [1.6, 5.5, 2.2], "metal"), ([-0.8, 22, 7.2], [1.6, 5.5, 2], "metal"), ([-0.6, 18.5, 7], [1.2, 1.7, 0.8], "glow")]
    # arms: stacked shell pauldrons, hanging lames and a swept forearm fin
    arm = [([-10.2, 23.2, -4.4], [7.4, 2, 8.8], "metal", "scales"), ([-10.8, 25.2, -3.6], [6.8, 1.6, 7.2], "metal", "scales"),
           ([-10.4, 26.8, -2.4], [4, 1.2, 4.8], "trim"), ([-9, 28, -0.6], [1, 1.2, 1.2], "glow"),
           ([-10.8, 19, -3.2], [1.2, 4.2, 6.4], "metal"), ([-10.4, 15.5, -2.6], [1, 3.9, 5.2], "metal"), ([-10.2, 12, 1], [1, 4, 3.5], "trim")]
    # waist and legs: hip fins, shell knee guards, thigh fins
    waist = [([4.4, 12.5, -1.5], [1.6, 4.5, 4.5], "metal"), ([-6, 12.5, -1.5], [1.6, 4.5, 4.5], "metal"), ([-1.5, 12.5, -3.4], [3, 3, 0.9], "metal")]
    leg = [([-4.2, 5.5, -3.8], [4.6, 3, 1.3], "metal", "scales"), ([-3.4, 8.5, -3.5], [3, 1, 1], "trim"),
           ([-5.6, 3, -2], [1.2, 7, 4.4], "metal"), ([-6.6, 5, 0], [1, 3, 2.4], "metal")]
    boot = [([-5.7, 1.5, -1], [1, 4.5, 4.5], "metal"), ([-6.4, 2.5, 2], [0.7, 2, 2.5], "metal"), ([-4.3, 0.2, -3.6], [4.8, 2, 1.2], "metal"),
            ([-3.8, 2.2, -3.4], [3.8, 0.8, 0.9], "trim")]
    return {"armorHead": head, "armorBody": body, "armorRightArm": arm, "armorLeftArm": _mx(arm), "armorWaist": waist,
            "armorRightLeg": leg, "armorLeftLeg": _mx(leg), "armorRightBoot": boot, "armorLeftBoot": _mx(boot)}


def shell_ramp(look):
    """Ornaments are a lighter, more saturated shell than the base plates, so they stand out from the body."""
    from armor_paint import ramp
    base = look.s["metal"]
    return ramp(tuple(min(255, int(c * 1.3) + 10) for c in base))


def finish_face(cv, rect, rp, face, pattern, seed):
    """Depth on a painted face: scales on big faces, a dithered shadow toward the bottom, a few light specks and worn edge nicks."""
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    if w < 6 or h < 6:
        return
    if pattern == "scales" and face in ("top", "front", "back") and w >= 12 and h >= 12:
        sub = Cv(cv.w, cv.h)
        sub.scales(x0 + 1, y0 + 1, w - 2, h - 2, rp, max(6, min(w, h) // 3), tone_top=0.25)
        live = sub.a[..., 3] > 0
        cv.a[live] = sub.a[live]
    rnd = random.Random(seed)
    bayer = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))
    for yy in range(y0 + 1, y1 - 1):
        t = (yy - y0) / h
        if t < 0.62 or face == "top":
            continue
        for xx in range(x0 + 1, x1 - 1):
            if bayer[yy % 4][xx % 4] / 16 < (t - 0.62) * 1.9 and cv.a[yy, xx, 3]:
                cv.a[yy, xx] = (*rp[2], 255)
    for _ in range(max(1, (w * h) // 180)):
        xx, yy = rnd.randrange(x0 + 1, x1 - 1), rnd.randrange(y0 + 1, y1 - 1)
        cv.a[yy, xx] = (*rp[4], 255)


def pack_ornaments(ornaments, look, art):
    """Paints every ornament box into rows 32+ of a copy of `art` (an RGBA image of the layer art) and returns (image, cubes)."""
    armor_paint.BEVEL = 1
    cv = Cv(TEX_W * K, TEX_H * K)
    h, w = art.height, art.width
    cv.a[:h, :w] = np.array(art)
    ramps = {"metal": shell_ramp(look), "trim": look.trim, "glow": look.glow}
    tones = {"top": 0.55, "front": 0.2, "back": 0.2, "left": -0.1, "right": -0.1, "bottom": -0.6}
    cubes = {}
    x, y, row = 0, 32, 0
    for bone, items in ornaments.items():
        for origin, size, material, *extra in items:
            pattern = extra[0] if extra else None
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
                if material != "glow":
                    finish_face(cv, (x0, y0, x1, y1), ramps[material], name, pattern, seed=x0 * 7 + y0)
            c = cube(list(origin), list(size), [x, y], 0.0)
            if len(extra) >= 2 and extra[1]:  # rotation about the box's base centre
                c["pivot"] = [origin[0] + sx / 2, origin[1], origin[2] + sz / 2]
                c["rotation"] = list(extra[1])
            cubes.setdefault(bone, []).append(c)
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
    repo = Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers"
    parser.add_argument("--out", type=Path, default=repo)
    parser.add_argument("--dev-pack", type=Path, help="write a loose resource pack here instead (for a client to try without restarting the server)")
    args = parser.parse_args()
    out = repo
    if args.dev_pack:
        out = args.dev_pack / "assets/cobbletowers"
        (args.dev_pack).mkdir(parents=True, exist_ok=True)
        (args.dev_pack / "pack.mcmeta").write_text('{"pack": {"pack_format": 34, "description": "geo dev"}}', encoding="utf-8")
    else:
        out = args.out
    for name in args.sets.split(","):
        write_set(name, repo / "textures/models/armor", out)
        print("wrote geo armor", name)


if __name__ == "__main__":
    main()
