"""Sculpt kit: parts are boxes or flat planes whose faces are painted by hand-style painters (silhouettes, shading, glow).

Rule that matters: Blockbench derives box-UV faces from FLOORED cube sizes, so every size here is rounded to an integer and a thin
plane is a zero-thickness plane. A fractional size maps the wrong texels (cropped edges, shifted faces)."""
from __future__ import annotations
import json, math, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))   # tools/
import generate_armor_geo as gg
import generate_armor_textures as gt
from armor_paint import ramp

K = gg.K
TEX_W, TEX_H = gg.TEX_W, gg.TEX_H
DROP_VANILLA = set()                          # bone names whose vanilla cube is left out (set per build)
BAYER = np.array(((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))) / 16.0


def bayer(h, w):
    return np.tile(BAYER, (h // 4 + 1, w // 4 + 1))[:h, :w]


def shift(m, dy, dx):
    out = np.zeros_like(m)
    h, w = m.shape
    ys, yd = (slice(dy, h), slice(0, h - dy)) if dy >= 0 else (slice(0, h + dy), slice(-dy, h))
    xs, xd = (slice(dx, w), slice(0, w - dx)) if dx >= 0 else (slice(0, w + dx), slice(-dx, w))
    out[ys, xs] = m[yd, xd]
    return out


def erode(m):
    return m & shift(m, 1, 0) & shift(m, -1, 0) & shift(m, 0, 1) & shift(m, 0, -1)


def put(img, mask, c, a=255):
    img[mask] = (*c[:3], a)


def shade(img, mask, rp, mode="v", lo=1.8, hi=4.6, edge=True, lit=0.38, inv=False, dith=0.35):
    """Fill `mask` from ramp `rp` (6 colours, dark to light) with a dithered gradient, a dark outline and a light top-left rim."""
    h, w = mask.shape
    if mode == "v":
        t = np.repeat((1 - np.arange(h) / max(1, h - 1))[:, None], w, 1)
    elif mode == "h":
        t = np.repeat((1 - np.arange(w) / max(1, w - 1))[None, :], h, 0)
    elif mode == "cyl":
        x = np.arange(w) / max(1, w - 1)
        t = np.repeat(np.clip(1 - np.abs(x - lit) * 2.0, 0, 1)[None, :], h, 0)
    else:
        t = np.full((h, w), 0.5)
    if inv:
        t = 1 - t
    lvl = lo + (hi - lo) * t
    idx = np.clip(np.floor(lvl + (bayer(h, w) - 0.5) * dith), 0, 5).astype(int)
    for i in range(6):
        put(img, mask & (idx == i), rp[i])
    if edge:
        e1 = erode(mask)
        put(img, mask & ~e1, rp[0])
        put(img, e1 & ~shift(e1, 1, 1), rp[min(5, int(hi) + 1)])
        put(img, e1 & ~shift(e1, -1, -1), rp[1])


def poly_mask(w, h, pts):
    im = Image.new("L", (w, h), 0)
    ImageDraw.Draw(im).polygon([(round(x), round(y)) for x, y in pts], fill=255)
    return np.array(im) > 0


def ribbon(w, h, x0, amp, freq, phase, w0, taper=0.9, tip=0.0):
    """A tendril hanging from the top of a w x h face: a centre line that sways, a width that tapers to a point."""
    L, R = [], []
    for i in range(h + 1):
        t = i / h
        cx = x0 + amp * math.sin(t * math.pi * freq + phase) * (0.25 + 0.75 * t)
        hw = max(tip, w0 * (1 - t) ** taper)
        L.append((cx - hw, i)); R.append((cx + hw, i))
    return poly_mask(w, h, L + R[::-1])


def leaf(w, h, bulge=0.5, wide=0.46):
    """A lanceolate leaf standing on its bottom edge, tip at the top."""
    L, R = [], []
    for i in range(h + 1):
        t = 1 - i / h
        hw = wide * w * math.sin(math.pi * t ** (0.7 + bulge * 0.3)) if t > 0 else 0
        L.append((w / 2 - hw, i)); R.append((w / 2 + hw, i))
    return poly_mask(w, h, L + R[::-1])


def crescent(n, R, r, dx, dy=0):
    yy, xx = np.mgrid[0:n, 0:n]
    c = (n - 1) / 2
    return ((xx - c) ** 2 + (yy - c) ** 2 <= R * R) & ~((xx - c - dx) ** 2 + (yy - c - dy) ** 2 <= r * r)


class Part:
    def __init__(self, bone, origin, size, rot=(0, 0, 0), pivot=None, paint=None, mirror=False, group=None, gpivot=None, **kw):
        # Blockbench derives box-UV faces from FLOORED sizes, so a fractional size maps the wrong texels (cropped edges, shifted
        # faces). Round every size to an integer, keeping the centre; thin planes become true zero-thickness planes.
        new = [math.floor(v + 0.5) for v in size]
        self.bone, self.size = bone, new
        self.origin = [o + (v - n) / 2 for o, v, n in zip(origin, size, new)]
        self.rot, self.pivot, self.paint, self.kw = rot, pivot, paint, kw
        self.mirror = mirror
        self.group, self.gpivot = group, gpivot       # an animation bone nested under `bone`, with its own pivot

    def mirrored(self):
        o, s = self.origin, self.size
        rx, ry, rz = self.rot
        pv = None if self.pivot is None else [-self.pivot[0], self.pivot[1], self.pivot[2]]
        gp = None if self.gpivot is None else [-self.gpivot[0], self.gpivot[1], self.gpivot[2]]
        return Part(self.bone, [-(o[0] + s[0]), o[1], o[2]], s, (rx, -ry, -rz), pv, self.paint, True,
                    None if self.group is None else self.group + "_m", gp, **self.kw)


def build(parts, base_art, look, out: Path, name: str, number: int):
    """Pack the parts into rows 32+ of a copy of the layer art, paint them, return (image, cubes-by-bone, glow mask)."""
    W, H = TEX_W * K, TEX_H * K
    canvas = np.zeros((H, W, 4), np.uint8)
    canvas[: base_art.height, : base_art.width] = np.array(base_art)
    cubes, groups, x, y, row = {}, {}, 0, 33, 0
    shared = {}                                    # parts with the same `share` key (and size) reuse one painted cell
    for p in parts:
        sx, sy, sz = p.size
        key = p.kw.get("share")
        if key and key in shared:
            cx, cy = shared[key]
        else:
            cw, ch = math.ceil(2 * sz + 2 * sx), math.ceil(sz + sy)
            if x + cw > TEX_W:
                x, y, row = 0, y + row, 0
            if y + ch > TEX_H:
                raise ValueError("ornament texture is full")
            lay = {"top": (sz, 0, sx, sz), "bottom": (sz + sx, 0, sx, sz), "left": (0, sz, sz, sy), "front": (sz, sz, sx, sy),
                   "right": (sz + sx, sz, sz, sy), "back": (2 * sz + sx, sz, sx, sy)}
            views = {}
            for fn, (fx, fy, fw, fh) in lay.items():
                x0, y0 = round((x + fx) * K), round((y + fy) * K)
                x1, y1 = max(x0 + 1, round((x + fx + fw) * K)), max(y0 + 1, round((y + fy + fh) * K))
                views[fn] = canvas[y0:y1, x0:x1]
            if p.paint:
                p.paint(views, look, p.mirror)
            cx, cy = x, y
            if key:
                shared[key] = (x, y)
            x, row = x + cw + 1, max(row, ch + 1)
        c = gg.cube(list(p.origin), list(p.size), [cx, cy], 0.0)
        if any(p.rot):
            c["pivot"] = list(p.pivot) if p.pivot else [p.origin[0] + sx / 2, p.origin[1], p.origin[2] + sz / 2]
            c["rotation"] = list(p.rot)
        if p.group:
            groups.setdefault(p.group, {"parent": p.bone, "pivot": list(p.gpivot or [0, 0, 0]), "cubes": []})["cubes"].append(c)
        else:
            cubes.setdefault(p.bone, []).append(c)
    return Image.fromarray(canvas, "RGBA"), cubes, groups


def write(parts, spec_name, layers: Path, out: Path):
    """Write the geo and both layer textures (+ glow masks) for a set whose base art lives in `layers`."""
    (out / "geo").mkdir(parents=True, exist_ok=True)
    (out / "textures/armor").mkdir(parents=True, exist_ok=True)
    look = gt.Look(spec_name, gt.SPECS[spec_name])
    cubes = {}
    for number in (1, 2):
        sheet = Image.open(layers / f"{spec_name}_layer_{number}.png").convert("RGBA")
        half = sheet.width // 2
        art = sheet.crop((0, 0, half, sheet.height))
        mask = sheet.crop((half, 0, sheet.width, sheet.height)).split()[3]
        full, cubes, groups = build(parts, art, look, out, spec_name, number)
        arr = np.array(full)
        gm = np.zeros(arr.shape[:2], bool)
        gm[: mask.height, : mask.width] = np.array(mask) > 0
        for c in look.glow[3:]:
            gm |= (arr[..., 0] == c[0]) & (arr[..., 1] == c[1]) & (arr[..., 2] == c[2]) & (arr[..., 3] > 0)
        glow = np.zeros_like(arr)
        glow[gm] = arr[gm]
        Image.fromarray(arr, "RGBA").save(out / f"textures/armor/{spec_name}_{number}.png")
        Image.fromarray(glow, "RGBA").save(out / f"textures/armor/{spec_name}_{number}_glowmask.png")
    g = gg.geo(spec_name, cubes)
    for bone in g["minecraft:geometry"][0]["bones"]:          # a set may replace a vanilla box entirely with its own sculpted geometry
        if bone["name"] in DROP_VANILLA and bone["cubes"]:
            bone["cubes"] = bone["cubes"][1:]
    for name, grp in groups.items():            # animation bones: any name is allowed when nested under a conventional bone
        g["minecraft:geometry"][0]["bones"].append({"name": name, "parent": grp["parent"], "pivot": grp["pivot"], "cubes": grp["cubes"]})
    for bone in g["minecraft:geometry"][0]["bones"]:
        if bone["name"] == "armorBody":
            bone["cubes"][0]["inflate"] = 1.04      # a hair bigger than the arms (1.0): the faces overlap and would z-fight otherwise
    (out / f"geo/{spec_name}.geo.json").write_text(json.dumps(g, indent=1), encoding="utf-8")


def base_layers(spec_name, layers: Path, **override):
    layers.mkdir(parents=True, exist_ok=True)
    gt.SPECS[spec_name].update(override)
    look = gt.Look(spec_name, gt.SPECS[spec_name])
    l1, l2 = gt.make_layers(look)
    gt.with_emission(l1, look).save(layers / f"{spec_name}_layer_1.png")
    gt.with_emission(l2, look).save(layers / f"{spec_name}_layer_2.png")
    return look
