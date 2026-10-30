#!/usr/bin/env python3
"""Builds Grizelda, the goblin witch merchant (the Halloween tower's vendor): an original cuboid model, painted atlas and Blockbench source.

    python tools/goblin/goblin_build.py [--previews]

Writes (deterministic):
  src/main/resources/assets/cobbletowers/models/halloween/goblin_merchant/goblin.json       1.21.1 item model, one piece
  src/main/resources/assets/cobbletowers/textures/item/halloween/goblin_merchant/goblin.png  128x128 atlas
  art/goblin_merchant/goblin_merchant.bbmodel (+ goblin.png, previews/)                    open in Blockbench (Java Block/Item)

Space: units (16 per block), x east, y up, z south; the goblin's FRONT is the north face (-z), feet at y = 0, centred on x = z = 8.
Rotated cubes turn about ONE axis by 0 / +-22.5 / +-45 only (vanilla rule). Reference for the look only: the silhouette of a hooded, teal
goblin shaman with a bone-fang necklace and a skull staff; every cube and pixel here is our own.
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import math
import random
import uuid
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MODEL_OUT = ROOT / "src/main/resources/assets/cobbletowers/models/halloween/goblin_merchant/goblin.json"
TEX_OUT = ROOT / "src/main/resources/assets/cobbletowers/textures/item/halloween/goblin_merchant/goblin.png"
ART = ROOT / "art/goblin_merchant"
TEX_ID = "cobbletowers:item/halloween/goblin_merchant/goblin"
FACES = ("north", "east", "south", "west", "up", "down")
SIZE = 128
TILE = 16

# ---- materials: base colour, noise, optional pattern ---------------------------------------------------------------------------------
MATS = {
    "skin": ((62, 150, 140), 10, None), "skin_dark": ((40, 112, 108), 8, None), "skin_light": ((96, 182, 166), 8, None),
    "cloak": ((124, 74, 48), 9, "grain"), "cloak_dark": ((86, 52, 38), 7, "grain"), "leather": ((100, 66, 44), 8, "grain"),
    "gold": ((236, 192, 40), 12, "shine"), "gold_dark": ((184, 132, 22), 8, None),
    "bone": ((228, 222, 200), 10, "speck"), "bone_dark": ((170, 160, 138), 8, "speck"),
    "purple": ((124, 92, 206), 12, "grain"), "purple_dark": ((72, 52, 134), 8, "grain"),
    "red": ((196, 44, 52), 10, None), "red_dark": ((130, 26, 38), 8, None),
    "wood": ((112, 68, 44), 8, "grain"), "wood_dark": ((78, 46, 32), 7, "grain"),
    "cloth": ((178, 152, 112), 9, "weave"), "cloth_dark": ((140, 116, 84), 8, "weave"),
    "black": ((30, 26, 34), 4, None), "glass": ((96, 206, 122), 12, "shine"), "cork": ((172, 122, 72), 8, None),
    "rope": ((206, 176, 112), 10, "weave"),
}
MAT_ORDER = list(MATS)
assert len(MAT_ORDER) <= 40


def paint_tile(name: str, seed: int) -> Image.Image:
    base, amp, pattern = MATS[name]
    rng = random.Random(seed)
    im = Image.new("RGBA", (TILE, TILE))
    px = im.load()
    for y in range(TILE):
        for x in range(TILE):
            d = rng.randint(-amp, amp)
            if pattern == "grain":
                d += -6 if x % 4 == 0 else (3 if x % 4 == 2 else 0)
            elif pattern == "weave":
                d += -7 if (x + y) % 4 == 0 else 0
            elif pattern == "shine":
                d += 18 if (x + y) in (3, 4, 5) and x < 8 else 0
            elif pattern == "speck":
                d += -22 if rng.random() < 0.08 else 0
            px[x, y] = tuple(max(0, min(255, c + d)) for c in base) + (255,)
    return im


# ---- hand-painted faces (own regions in the atlas) -----------------------------------------------------------------------------------
def px_image(rows: list[str], key: dict[str, tuple]) -> Image.Image:
    im = Image.new("RGBA", (len(rows[0]), len(rows)))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            im.putpixel((x, y), key[ch] + (255,))
    return im


K = {"s": MATS["skin"][0], "d": MATS["skin_dark"][0], "l": MATS["skin_light"][0], "r": (230, 40, 48), "R": MATS["red_dark"][0],
     "w": (250, 240, 220), "k": MATS["black"][0], "b": MATS["bone"][0], "B": MATS["bone_dark"][0], "g": MATS["gold"][0], "n": MATS["leather"][0]}
CUSTOM = {
    # the goblin's face: heavy brow, glowing red eyes, a wide mouth with two tusks
    "face": px_image(["ddddddd", "dkkdkkd", "srwsrws", "ssdsdss", "sdsssds", "skbkbks", "sskkkss"], K),
    "skull_front": px_image(["bbb", "rbr", "BbB"], K),
    "pendant": px_image(["bb", "kk"], K),
    "skull_top": px_image(["bbb", "bbb", "bbb"], K),
    "gem": px_image(["rr", "Rr"], K),
    "buckle": px_image(["gg", "gk"], K),
    "ear_ring": px_image(["g", "g"], K),
}
CUSTOM_POS: dict[str, tuple[int, int]] = {}


# ---- model -------------------------------------------------------------------------------------------------------------------------
@dataclass
class El:
    name: str
    group: str
    frm: tuple
    to: tuple
    mat: str
    rot: tuple | None = None        # (axis, angle, origin)
    custom: dict = field(default_factory=dict)   # face -> CUSTOM key
    top: str | None = None          # a different material for the up face
    skip: set = field(default_factory=set)


ELS: list[El] = []


def add(name, group, frm, to, mat, rot=None, custom=None, top=None, skip=()):
    ELS.append(El(name, group, tuple(frm), tuple(to), mat, rot, custom or {}, top, set(skip)))


def mirror(name, group, frm, to, mat, rot=None, custom=None, top=None):
    """The same cube on the other side of the aisle (x -> 16 - x), a rotation mirrored with it."""
    mf, mt = (16 - frm[0], frm[1], frm[2]), (16 - to[0], to[1], to[2])
    lo = (min(mf[0], mt[0]), mf[1], mf[2]); hi = (max(mf[0], mt[0]), mt[1], mt[2])
    mr = None
    if rot:
        axis, ang, org = rot
        mr = (axis, -ang if axis in ("y", "z") else ang, (16 - org[0], org[1], org[2]))
    add(name, group, lo, hi, mat, mr, custom, top)


def build_goblin():
    ELS.clear()
    # legs and feet (toes point north)
    for side, x0 in (("r", 8.6), ("l", 5.0)):
        add(f"leg_{side}", "legs", (x0, 2, 6.6), (x0 + 2.4, 8.2, 9.4), "skin")
        add(f"knee_{side}", "legs", (x0 - 0.2, 4.4, 6.4), (x0 + 2.6, 5.6, 9.6), "skin_dark")
        add(f"foot_{side}", "legs", (x0 - 0.4, 0, 4.8), (x0 + 2.8, 2, 9.6), "skin_dark", top="skin")
        add(f"claws_{side}", "legs", (x0 - 0.2, 0, 4.0), (x0 + 2.6, 1, 4.8), "bone")
    # loincloth, belt, buckle, pouch and gem
    add("cloth_front", "waist", (5.2, 5.6, 5.8), (10.8, 10, 6.5), "cloak", top="cloak_dark")
    for i, (x0, w, drop) in enumerate(((5.2, 1.8, 2.4), (7.2, 1.6, 1.4), (9.0, 1.8, 2.0))):
        add(f"rag_{i}", "waist", (x0, 5.6 - drop, 5.6), (x0 + w, 5.6, 6.0), "cloak_dark")
    add("cloth_back", "waist", (5.2, 5.0, 9.6), (10.8, 10, 10.2), "cloak_dark")
    add("belt", "waist", (4.6, 9.2, 5.9), (11.4, 10.6, 10.1), "leather")
    add("buckle", "waist", (7.2, 9.1, 5.7), (8.8, 10.7, 5.95), "gold", custom={"north": "buckle"})
    add("pouch", "waist", (10.9, 6.6, 6.2), (13.1, 9.4, 8.6), "leather", top="cloak_dark")
    add("pouch_flap", "waist", (10.8, 8.6, 6.1), (13.2, 9.8, 8.7), "cloak_dark")
    add("gem", "waist", (11.6, 7.4, 5.9), (12.4, 8.4, 6.2), "red", custom={"north": "gem"})
    # torso: skin under a leather vest, bone-fang necklace and skull pendant
    add("torso", "torso", (5.0, 10.4, 6.6), (11.0, 17.2, 9.6), "skin")
    add("vest_front", "torso", (5.0, 10.6, 6.1), (11.0, 16.6, 6.7), "leather", top="cloak_dark")
    add("vest_back", "torso", (5.0, 10.6, 9.5), (11.0, 16.6, 10.1), "cloak_dark")
    add("collar", "torso", (4.8, 16.2, 6.0), (11.2, 17.4, 9.9), "cloak_dark")
    for i, x in enumerate((5.6, 6.5, 7.4, 8.6, 9.5, 10.4)):
        drop = 1.3 if i in (2, 3) else 2.0 if i in (1, 4) else 2.6
        add(f"fang_{i}", "necklace", (x, 16.0 - drop - 0.6, 5.7), (x + 0.75, 16.0 - 0.6, 6.2), "bone")
    add("pendant", "necklace", (7.2, 11.4, 5.4), (8.8, 13.0, 5.95), "bone", custom={"north": "pendant"})
    add("necklace_cord", "necklace", (5.3, 15.5, 5.9), (10.7, 16.0, 6.2), "rope")
    # head: skin box, long hooked nose, tusks, big pointed ears with gold rings
    add("head", "head", (4.6, 17.4, 4.8), (11.4, 24.2, 11.2), "skin", custom={"north": "face"}, top="skin_dark")
    add("nose", "head", (7.0, 19.0, 3.6), (9.0, 21.0, 4.8), "skin_light")
    add("nose_tip", "head", (7.3, 18.0, 3.4), (8.7, 19.4, 4.2), "skin_light")
    add("tusk_l", "head", (5.9, 17.6, 4.2), (6.7, 19.4, 4.8), "bone", rot=("z", 22.5, (6.3, 17.6, 4.5)))
    mirror("tusk_r", "head", (5.9, 17.6, 4.2), (6.7, 19.4, 4.8), "bone", rot=("z", 22.5, (6.3, 17.6, 4.5)))
    # ears: two layered boards fanned about the same root so the tip stays attached (a separate rotated tip floats away)
    add("ear_l", "head", (1.0, 20.4, 7.0), (4.8, 22.2, 9.4), "skin", rot=("z", 22.5, (4.8, 21.3, 8.2)), top="skin_light")
    mirror("ear_r", "head", (1.0, 20.4, 7.0), (4.8, 22.2, 9.4), "skin", rot=("z", 22.5, (4.8, 21.3, 8.2)), top="skin_light")
    add("ear_up_l", "head", (2.4, 21.2, 7.5), (4.8, 22.6, 8.9), "skin_light", rot=("z", 45, (4.8, 21.9, 8.2)))
    mirror("ear_up_r", "head", (2.4, 21.2, 7.5), (4.8, 22.6, 8.9), "skin_light", rot=("z", 45, (4.8, 21.9, 8.2)))
    add("ring_l", "head", (1.9, 19.2, 7.4), (2.7, 20.8, 8.4), "gold", custom={"north": "ear_ring"})
    mirror("ring_r", "head", (1.9, 19.2, 7.4), (2.7, 20.8, 8.4), "gold", custom={"north": "ear_ring"})
    # the hood: back, sides, top, gold trim along the front edge, a floppy peak
    add("hood_back", "hood", (3.8, 16.4, 10.6), (12.2, 25.6, 12.0), "cloak", top="cloak_dark")
    add("hood_side_l", "hood", (3.6, 16.4, 4.2), (4.6, 25.6, 11.0), "cloak")
    mirror("hood_side_r", "hood", (3.6, 16.4, 4.2), (4.6, 25.6, 11.0), "cloak")
    add("hood_top", "hood", (3.6, 24.4, 4.0), (12.4, 25.8, 11.8), "cloak_dark", top="cloak")
    add("trim_l", "hood", (3.6, 16.4, 3.6), (4.8, 25.8, 4.4), "gold")
    mirror("trim_r", "hood", (3.6, 16.4, 3.6), (4.8, 25.8, 4.4), "gold")
    add("trim_top", "hood", (3.6, 24.4, 3.6), (12.4, 25.8, 4.4), "gold")
    add("peak_1", "hood", (6.6, 25.6, 7.6), (9.4, 27.4, 10.4), "cloak")
    add("peak_2", "hood", (7.1, 27.0, 8.8), (8.9, 28.8, 11.2), "cloak_dark")
    add("peak_3", "hood", (7.4, 28.2, 10.4), (8.6, 29.6, 12.4), "cloak")
    add("peak_bell", "hood", (7.5, 27.4, 12.2), (8.5, 28.6, 13.2), "gold")
    # character-left arm (-x): bone pauldron with a horn, gold bracer, an open hand offering a potion
    add("shoulder_l", "arm_l", (2.4, 15.2, 6.4), (5.4, 17.0, 9.8), "bone", top="bone_dark")
    add("pauldron_ridge", "arm_l", (2.8, 17.0, 6.8), (5.0, 17.8, 9.4), "bone_dark")
    add("horn_a", "arm_l", (2.6, 17.4, 7.6), (4.4, 20.4, 9.0), "bone_dark", rot=("z", -22.5, (3.5, 17.4, 8.3)))
    add("horn_b", "arm_l", (2.0, 20.0, 7.8), (3.4, 22.2, 8.8), "bone", rot=("z", -45, (2.7, 20.0, 8.3)))
    add("arm_l", "arm_l", (3.2, 11.2, 7.0), (5.4, 15.4, 9.4), "skin")
    add("forearm_l", "arm_l", (3.2, 9.8, 4.6), (5.4, 12.2, 8.0), "skin_dark")
    add("bracer_l", "arm_l", (3.0, 9.6, 5.0), (5.6, 12.4, 6.6), "gold")
    add("hand_l", "arm_l", (3.6, 9.6, 2.8), (5.2, 11.6, 4.8), "skin")
    add("fingers_l", "arm_l", (3.4, 11.4, 2.4), (5.4, 12.0, 4.6), "skin_dark")
    add("potion_l", "arm_l", (3.9, 11.9, 3.0), (4.9, 14.1, 4.0), "glass", top="glass")
    add("potion_neck", "arm_l", (4.2, 14.1, 3.3), (4.6, 14.9, 3.7), "glass")
    add("potion_cork", "arm_l", (4.1, 14.9, 3.2), (4.7, 15.5, 3.8), "cork")
    # character-right arm (+x): feather tuft, arm, gold bracer, hand around the staff
    add("shoulder_r", "arm_r", (10.6, 15.4, 6.6), (13.6, 17.0, 9.8), "purple", top="purple_dark")
    add("arm_r", "arm_r", (10.8, 11.2, 7.0), (13.0, 15.6, 9.4), "skin")
    add("forearm_r", "arm_r", (10.8, 9.8, 4.6), (13.0, 12.2, 8.0), "skin_dark")
    add("bracer_r", "arm_r", (10.6, 9.6, 5.0), (13.2, 12.4, 6.6), "gold")
    add("hand_r", "arm_r", (11.0, 9.6, 2.8), (13.0, 11.8, 4.6), "skin")
    # the staff: wood pole, gold wraps, bone rings, a skull with glowing eyes, three feathers
    add("staff", "staff", (11.5, 0, 3.0), (12.5, 24.0, 4.0), "wood", top="wood_dark")
    add("staff_foot", "staff", (11.3, 0, 2.8), (12.7, 1.4, 4.2), "wood_dark")
    add("wrap_a", "staff", (11.3, 12.4, 2.8), (12.7, 13.6, 4.2), "gold")
    add("wrap_b", "staff", (11.3, 7.0, 2.8), (12.7, 7.8, 4.2), "gold_dark")
    add("ring_a", "staff", (11.2, 17.2, 2.7), (12.8, 18.2, 4.3), "bone")
    add("ring_b", "staff", (11.2, 19.4, 2.7), (12.8, 20.4, 4.3), "bone_dark")
    add("skull", "staff", (10.4, 23.4, 2.2), (13.6, 26.8, 5.4), "bone", custom={"north": "skull_front", "up": "skull_top"})
    add("skull_jaw", "staff", (10.9, 22.6, 2.5), (13.1, 23.6, 4.8), "bone_dark")
    add("feather_a", "staff", (11.5, 26.4, 3.5), (12.5, 30.4, 4.1), "purple", rot=("z", 22.5, (12, 26.4, 3.8)))
    add("feather_b", "staff", (11.6, 26.4, 3.7), (12.4, 31.0, 4.3), "purple_dark")
    add("feather_c", "staff", (11.5, 26.4, 3.5), (12.5, 30.0, 4.1), "purple", rot=("z", -22.5, (12, 26.4, 3.8)))
    # the merchant's pack: a bulging sack, bedroll, rope straps and hanging wares
    add("pack", "pack", (4.4, 8.6, 10.2), (11.6, 18.4, 14.6), "cloth", top="cloth_dark")
    add("pack_bulge", "pack", (5.0, 10.0, 14.4), (11.0, 16.8, 15.6), "cloth_dark")
    add("pack_flap", "pack", (4.2, 16.4, 10.0), (11.8, 18.8, 14.8), "cloak_dark")
    add("bedroll", "pack", (3.8, 18.6, 10.4), (12.2, 21.2, 14.0), "purple", top="purple_dark")
    add("strap_l", "pack", (5.6, 9.0, 9.9), (6.4, 18.8, 10.3), "rope")
    add("strap_r", "pack", (9.6, 9.0, 9.9), (10.4, 18.8, 10.3), "rope")
    add("rope_band", "pack", (4.0, 19.4, 10.2), (12.0, 20.0, 14.2), "rope")
    add("hang_skull", "pack", (11.8, 9.4, 11.0), (13.4, 11.2, 12.6), "bone")
    add("hang_bottle", "pack", (2.6, 11.0, 11.4), (4.4, 13.2, 12.8), "glass")
    add("hang_bottle_cork", "pack", (3.1, 13.2, 11.7), (3.9, 13.9, 12.5), "cork")
    add("hang_cord_a", "pack", (3.4, 13.0, 12.0), (3.6, 17.8, 12.2), "rope")
    add("hang_cord_b", "pack", (12.5, 11.0, 11.7), (12.7, 17.8, 11.9), "rope")


# ---- atlas and UVs -----------------------------------------------------------------------------------------------------------------
def face_dims(e: El, f: str) -> tuple[float, float]:
    sx, sy, sz = (e.to[i] - e.frm[i] for i in range(3))
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[f]


def build_atlas():
    atlas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    tiles = {}
    for i, name in enumerate(MAT_ORDER):
        tx, ty = (i % 8) * TILE, (i // 8) * TILE
        atlas.paste(paint_tile(name, 1000 + i), (tx, ty))
        tiles[name] = (tx, ty)
    cx, cy, row_h = 0, 96, 0
    for key, im in CUSTOM.items():
        if cx + im.width > SIZE:
            cx, cy, row_h = 0, cy + row_h + 1, 0
        atlas.paste(im, (cx, cy))
        CUSTOM_POS[key] = (cx, cy)
        cx += im.width + 1
        row_h = max(row_h, im.height)
    return atlas, tiles


def face_rects(tiles):
    rects = {}
    for i, e in enumerate(ELS):
        for f in FACES:
            if f in e.skip:
                continue
            if f in e.custom:
                im = CUSTOM[e.custom[f]]
                x, y = CUSTOM_POS[e.custom[f]]
                rects[(i, f)] = (x, y, im.width, im.height)
                continue
            mat = e.top if (f == "up" and e.top) else e.mat
            w, h = face_dims(e, f)
            pw, ph = max(1, min(TILE, round(w))), max(1, min(TILE, round(h)))
            tx, ty = tiles[mat]
            ox, oy = (i * 5 + FACES.index(f) * 3) % (TILE - pw + 1), (i * 3 + FACES.index(f) * 7) % (TILE - ph + 1)
            rects[(i, f)] = (tx + ox, ty + oy, pw, ph)
    return rects


def num(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def uv_json(rect):
    x, y, w, h = rect
    k = 16.0 / SIZE
    return [round(x * k, 5), round(y * k, 5), round((x + w) * k, 5), round((y + h) * k, 5)]


def model_json(rects):
    els = []
    for i, e in enumerate(ELS):
        d = {"name": e.name, "from": [num(v) for v in e.frm], "to": [num(v) for v in e.to]}
        if e.rot:
            axis, ang, org = e.rot
            d["rotation"] = {"angle": ang, "axis": axis, "origin": [num(v) for v in org]}
        d["faces"] = {f: {"uv": uv_json(rects[(i, f)]), "texture": "#0"} for f in FACES if f not in e.skip}
        els.append(d)
    return {"credit": "Grizelda the goblin witch merchant, generated by tools/goblin (original work for CobbleTowers)",
            "texture_size": [SIZE, SIZE], "textures": {"0": TEX_ID, "particle": TEX_ID}, "elements": els}


NS = uuid.UUID("0c6a1d2e-77b0-4c36-9b0f-6f5b5d8b0a11")


def _u(s):
    return str(uuid.uuid5(NS, s))


def bbmodel(rects, png_bytes):
    elements, groups = [], {}
    for i, e in enumerate(ELS):
        faces = {}
        for f in FACES:
            if f in e.skip:
                faces[f] = {"uv": [0, 0, 0, 0], "texture": None}
                continue
            x, y, w, h = rects[(i, f)]
            faces[f] = {"uv": [x, y, x + w, y + h], "texture": 0}
        d = {"name": e.name, "box_uv": False, "rescale": False, "locked": False, "light_emission": 0, "render_order": "default",
             "allow_mirror_modeling": True, "from": list(e.frm), "to": list(e.to), "autouv": 0, "color": i % 12, "origin": [8, 0, 8],
             "faces": faces, "type": "cube", "uuid": _u(f"el:{i}:{e.name}")}
        if e.rot:
            axis, ang, org = e.rot
            d["origin"] = list(org)
            d["rotation"] = [ang if axis == "x" else 0, ang if axis == "y" else 0, ang if axis == "z" else 0]
        elements.append(d)
        groups.setdefault(e.group, []).append(d["uuid"])
    outliner = [{"name": g, "origin": [8, 0, 8], "color": 0, "uuid": _u("grp:" + g), "export": True, "isOpen": False, "locked": False,
                 "visibility": True, "autouv": 0, "children": kids} for g, kids in groups.items()]
    tex = {"path": "goblin.png", "name": "goblin.png", "folder": "", "namespace": "cobbletowers", "id": "0", "particle": True, "render_mode": "default",
           "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
           "internal": True, "saved": True, "uuid": _u("texture"), "source": "data:image/png;base64," + base64.b64encode(png_bytes).decode()}
    return {"meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False}, "name": "goblin_merchant", "model_identifier": "",
            "visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
            "unhandled_root_fields": {}, "resolution": {"width": SIZE, "height": SIZE}, "elements": elements, "outliner": outliner,
            "textures": [tex], "animations": []}


# ---- a small software preview (vanilla face shading, nearest sampling) ---------------------------------------------------------------
SHADE = {"up": 1.0, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6, "down": 0.5}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}


def rot_matrix(axis, deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]), "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
            "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]


def quad(e, f):
    x0, y0, z0 = e.frm; x1, y1, z1 = e.to
    return {"north": [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)], "south": [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
            "east": [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)], "west": [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
            "up": [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)], "down": [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)]}[f]


def render(atlas, rects, az, el=14, px=15.0, out=(520, 640), bg=(52, 44, 58, 255), ss=2):
    W, H = out[0] * ss, out[1] * ss
    zbuf = np.full((H, W), -1e9)
    img = np.zeros((H, W, 4), np.uint8); img[:] = bg
    a, p = math.radians(az), math.radians(el)
    cam = np.array([math.sin(a) * math.cos(p), math.sin(p), -math.cos(a) * math.cos(p)])
    right = np.cross(-cam, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, -cam)
    at = np.asarray(atlas)
    centre = np.array([8, 16, 8.0])
    for i, e in enumerate(ELS):
        for f in FACES:
            if f in e.skip:
                continue
            q = np.array(quad(e, f), float)
            n = np.array(NORMAL[f], float)
            if e.rot:
                ax, ang, org = e.rot
                q = (q - org) @ rot_matrix(ax, ang).T + org
                n = rot_matrix(ax, ang) @ n
            if np.dot(n, cam) <= 1e-6:
                continue
            rx, ry, rw, rh = rects[(i, f)]
            rel = q - centre
            sx = (rel @ right) * px * ss + W / 2
            sy = -(rel @ up) * px * ss + H / 2
            depth = rel @ cam
            uv = [(0, 0), (0, 1), (1, 1), (1, 0)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                _tri(img, zbuf, at, (rx, ry, rw, rh), [sx[k] for k in tri], [sy[k] for k in tri], [depth[k] for k in tri], [uv[k] for k in tri], SHADE[f])
    return Image.fromarray(img, "RGBA").resize(out, Image.LANCZOS)


def _tri(img, zbuf, at, rect, xs, ys, zs, uvs, shade):
    rx, ry, rw, rh = rect
    H, W = zbuf.shape
    x0, x1 = int(max(0, math.floor(min(xs)))), int(min(W - 1, math.ceil(max(xs))))
    y0, y1 = int(max(0, math.floor(min(ys)))), int(min(H - 1, math.ceil(max(ys))))
    if x1 < x0 or y1 < y0:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
    if abs(d) < 1e-9:
        return
    l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
    l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
    l2 = 1 - l0 - l1
    m = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
    if not m.any():
        return
    z = l0 * zs[0] + l1 * zs[1] + l2 * zs[2]
    u = l0 * uvs[0][0] + l1 * uvs[1][0] + l2 * uvs[2][0]
    v = l0 * uvs[0][1] + l1 * uvs[1][1] + l2 * uvs[2][1]
    sub = zbuf[y0:y1 + 1, x0:x1 + 1]
    m &= z > sub
    if not m.any():
        return
    tx = np.clip(rx + (u * rw).astype(int), rx, rx + rw - 1)
    ty = np.clip(ry + (v * rh).astype(int), ry, ry + rh - 1)
    col = at[ty, tx].astype(float)
    col[..., :3] *= shade
    region = img[y0:y1 + 1, x0:x1 + 1]
    region[m] = np.clip(col[m], 0, 255).astype(np.uint8)
    sub[m] = z[m]


def check_vanilla_limits():
    """Every cube inside -16..32, rotations one axis and 0/+-22.5/+-45, so 1.21.1 renders it as authored."""
    for e in ELS:
        for v in (*e.frm, *e.to):
            assert -16 <= v <= 32, (e.name, v)
        for a, b in zip(e.frm, e.to):
            assert b > a, (e.name, "empty or inverted")
        if e.rot:
            assert e.rot[0] in "xyz" and e.rot[1] in (-45, -22.5, 22.5, 45), (e.name, e.rot)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--previews", action="store_true")
    args = ap.parse_args()
    build_goblin()
    check_vanilla_limits()
    atlas, tiles = build_atlas()
    rects = face_rects(tiles)
    buf = io.BytesIO(); atlas.save(buf, "PNG"); png = buf.getvalue()
    MODEL_OUT.parent.mkdir(parents=True, exist_ok=True); TEX_OUT.parent.mkdir(parents=True, exist_ok=True); ART.mkdir(parents=True, exist_ok=True)
    MODEL_OUT.write_text(json.dumps(model_json(rects), indent=1) + "\n")
    TEX_OUT.write_bytes(png)
    (ART / "goblin.png").write_bytes(png)
    (ART / "goblin_merchant.bbmodel").write_text(json.dumps(bbmodel(rects, png)))
    print(f"{len(ELS)} cubes; model {MODEL_OUT.relative_to(ROOT)}; texture {TEX_OUT.relative_to(ROOT)}; bbmodel {ART.relative_to(ROOT)}")
    if args.previews:
        (ART / "previews").mkdir(exist_ok=True)
        sheet = Image.new("RGBA", (520 * 4, 640))
        for k, az in enumerate((-35, 35, 145, 215)):
            im = render(atlas, rects, az)
            im.save(ART / "previews" / f"az{az}.png")
            sheet.paste(im, (520 * k, 0))
        sheet.save(ART / "previews" / "sheet.png")
        print("previews:", ART / "previews" / "sheet.png")


if __name__ == "__main__":
    main()
