#!/usr/bin/env python3
"""Concept renders for the P49 armor models: the 12 sets on a blocky player, front-right and back-left.

    python tools/concept_armor_sheet.py <out_dir>     # writes <tower>.png for each of the four towers

A small software renderer (flat-shaded boxes, painter's algorithm) so the worn 3D parts can be judged before any model
JSON exists. Units are model pixels: feet at y=0, legs to 12, body to 24, head to 32; front is +z.

Design rules taken from the reference armor mods and the Blockbench style guide:
  * silhouette first: crests, horns, antlers, wings and halos break the head and shoulder outline;
  * one metal, one cloth, one glow colour per set (cloth and glow are the extras in CLOTH / GLOW below);
  * hung cloth is a 2-face plane (a thin box here), cheap and rigid, so it suits a server-attached part;
  * slants are single-axis rotations in 22.5 degree steps (every rotated element below obeys that);
  * a glow part stands for a full-bright display (brightness override), drawn unshaded with a bloom.
"""

from __future__ import annotations

import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_armor_art import PALETTES  # noqa: E402

SS = 2
SCALE = 10.2 * SS
STEP = 22.5

# cloth and glow colours per set (metal comes from PALETTES)
CLOTH = {
    "recruit": (120, 96, 70), "challenger": (168, 36, 48), "paragon": (236, 232, 244),
    "tidewalker": (30, 110, 120), "tideforge": (16, 70, 130), "leviathan": (10, 36, 92),
    "sprout": (96, 130, 56), "rootvale": (84, 100, 44), "heartwood": (56, 70, 34),
    "duskwanderer": (80, 60, 120), "duskvale": (96, 40, 130), "nightfall": (40, 24, 84),
}
GLOW = {
    "recruit": (230, 200, 120), "challenger": (255, 214, 96), "paragon": (255, 244, 190),
    "tidewalker": (150, 240, 230), "tideforge": (120, 220, 255), "leviathan": (150, 255, 255),
    "sprout": (200, 255, 130), "rootvale": (190, 255, 120), "heartwood": (170, 255, 90),
    "duskwanderer": (230, 170, 255), "duskvale": (255, 140, 220), "nightfall": (255, 200, 90),
}


# ---- scene --------------------------------------------------------------------------------------------------------

def rot_point(p, c, axis, deg):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    x, y, z = (p[0] - c[0], p[1] - c[1], p[2] - c[2])
    if axis == "z":
        x, y = x * ca - y * sa, x * sa + y * ca
    elif axis == "x":
        y, z = y * ca - z * sa, y * sa + z * ca
    else:
        x, z = x * ca + z * sa, -x * sa + z * ca
    return (x + c[0], y + c[1], z + c[2])


def rot_dir(n, axis, deg):
    return rot_point(n, (0, 0, 0), axis, deg)


def snap(deg):
    return round(deg / STEP) * STEP


class Scene:
    def __init__(self):
        self.boxes = []

    def box(self, x0, y0, z0, x1, y1, z1, color, rot=None, glow=False):
        """rot = (axis, degrees) about the box centre, or (axis, degrees, pivot)."""
        lo = (min(x0, x1), min(y0, y1), min(z0, z1))
        hi = (max(x0, x1), max(y0, y1), max(z0, z1))
        self.boxes.append((lo, hi, color, rot, glow))

    def mbox(self, x0, y0, z0, x1, y1, z1, color, rot=None, glow=False):
        self.box(x0, y0, z0, x1, y1, z1, color, rot, glow)
        mrot = None
        if rot:
            axis, deg = rot[0], rot[1]
            pivot = rot[2] if len(rot) > 2 else None
            if axis != "x":
                deg = -deg
            mrot = (axis, deg, (-pivot[0], pivot[1], pivot[2])) if pivot else (axis, deg)
        self.box(-x1, y0, z0, -x0, y1, z1, color, mrot, glow)

    def rod(self, a, b, th, color, plane="xy", depth=None, glow=False, mirror=False):
        """A bar from a to b in a plane, angle snapped to 22.5 degrees. Endpoints are where the bar is centred."""
        depth = depth or th
        (ax, ay, az), (bx, by, bz) = a, b
        mx, my, mz = (ax + bx) / 2, (ay + by) / 2, (az + bz) / 2
        if plane == "xy":
            length = math.hypot(bx - ax, by - ay)
            ang = snap(math.degrees(math.atan2(-(bx - ax), by - ay)))
            self.box(mx - th / 2, my - length / 2, mz - depth / 2, mx + th / 2, my + length / 2, mz + depth / 2, color, ("z", ang), glow)
            if mirror:
                self.box(-mx - th / 2, my - length / 2, mz - depth / 2, -mx + th / 2, my + length / 2, mz + depth / 2, color, ("z", -ang), glow)
        elif plane == "yz":
            length = math.hypot(by - ay, bz - az)
            ang = snap(math.degrees(math.atan2(bz - az, by - ay)))
            self.box(mx - depth / 2, my - length / 2, mz - th / 2, mx + depth / 2, my + length / 2, mz + th / 2, color, ("x", ang), glow)
            if mirror:
                self.box(-mx - depth / 2, my - length / 2, mz - th / 2, -mx + depth / 2, my + length / 2, mz + th / 2, color, ("x", ang), glow)
        else:  # xz
            length = math.hypot(bx - ax, bz - az)
            ang = snap(math.degrees(math.atan2(bx - ax, bz - az)))
            self.box(mx - th / 2, my - depth / 2, mz - length / 2, mx + th / 2, my + depth / 2, mz + length / 2, color, ("y", ang), glow)
            if mirror:
                self.box(-mx - th / 2, my - depth / 2, mz - length / 2, -mx + th / 2, my + depth / 2, mz + length / 2, color, ("y", -ang), glow)

    def cloth(self, x0, x1, ytop, ybot, z, color, th=0.55, flare=0.0, segs=2):
        """A hanging 2-face plane; the lower segments flare out by `flare` degrees (positive = away from the body)."""
        out = 1 if z >= 0 else -1
        h = (ytop - ybot) / segs
        for i in range(segs):
            top = ytop - i * h
            ang = flare * i * (-out)
            shade = tuple(int(c * (1 - 0.06 * i)) for c in color)
            self.box(x0, top - h, z - th / 2, x1, top, z + th / 2, shade,
                     ("x", ang, ((x0 + x1) / 2, top, z)) if ang else None)


def shade(color, f):
    return tuple(max(0, min(255, int(c * f))) for c in color)


def render(scene: Scene, yaw_deg: float, size=(350, 540)) -> Image.Image:
    w, h = size[0] * SS, size[1] * SS
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glow_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw, gdraw = ImageDraw.Draw(img), ImageDraw.Draw(glow_layer)
    yaw, pitch = math.radians(yaw_deg), math.radians(16)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)

    def project(p):
        x, y, z = p
        xr, zr = x * cy + z * sy, -x * sy + z * cy
        return (w / 2 + xr * SCALE, h * 0.86 - (y * cp - zr * sp) * SCALE, zr * cp + y * sp)

    quads = [
        ((0, 0, 1), [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]),
        ((0, 0, -1), [(1, 0, 0), (0, 0, 0), (0, 1, 0), (1, 1, 0)]),
        ((1, 0, 0), [(1, 0, 1), (1, 0, 0), (1, 1, 0), (1, 1, 1)]),
        ((-1, 0, 0), [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)]),
        ((0, 1, 0), [(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)]),
        ((0, -1, 0), [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)]),
    ]
    faces = []
    for lo, hi, color, rot, glow in scene.boxes:
        corner = {(i, j, k): (hi[0] if i else lo[0], hi[1] if j else lo[1], hi[2] if k else lo[2])
                  for i in (0, 1) for j in (0, 1) for k in (0, 1)}
        centre = ((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2)
        axis = deg = None
        pivot = centre
        if rot:
            axis, deg = rot[0], rot[1]
            if len(rot) > 2:
                pivot = rot[2]
        for normal, idx in quads:
            n = normal
            verts = [corner[i] for i in idx]
            if rot:
                n = rot_dir(n, axis, deg)
                verts = [rot_point(v, pivot, axis, deg) for v in verts]
            nxr = n[0] * cy + n[2] * sy
            nzr = -n[0] * sy + n[2] * cy
            if nzr * cp + n[1] * sp <= 0.001:
                continue
            pts = [project(v) for v in verts]
            depth = sum(p[2] for p in pts) / 4
            if glow:
                fill, edge = shade(color, 1.1), shade(color, 0.8)
            else:
                dot = max(0.0, nxr * -0.4 + n[1] * 0.8 + nzr * 0.45)
                fill, edge = shade(color, 0.55 + 0.6 * dot), shade(color, 0.4)
            faces.append((depth, [(p[0], p[1]) for p in pts], fill, edge, glow))
    faces.sort(key=lambda f: f[0])
    for _, pts, fill, edge, glow in faces:
        draw.polygon(pts, fill=fill + (255,), outline=edge + (255,))
        if glow:
            gdraw.polygon(pts, fill=fill + (255,))
    bloom = glow_layer.filter(ImageFilter.GaussianBlur(7 * SS))
    img = Image.alpha_composite(img, Image.eval(bloom, lambda v: v))
    img.alpha_composite(Image.eval(bloom, lambda v: v // 2))
    return img.resize(size, Image.LANCZOS)


# ---- the body every set wears (the 2D layer that stays) ------------------------------------------------------------

SKIN = (224, 180, 142)


def body(pal, tier):
    o, d, m, l, a = pal
    s = Scene()
    s.mbox(0.1, 3.5, -2.2, 4.1, 12, 2.2, d)                  # legs
    s.mbox(0.1, 0, -2.5, 4.1, 3.5, 2.8, m)                   # boots
    s.box(-4.3, 11, -2.4, 4.3, 12.8, 2.4, a)                 # belt
    s.box(-4.3, 12.8, -2.3, 4.3, 24, 2.3, m)                 # torso
    s.box(-1.4, 14.2, 2.3, 1.4, 21.5, 2.45, l)               # chest plate
    s.mbox(4.1, 12.8, -2.3, 8.3, 24, 2.3, m)                 # arms
    s.mbox(4.1, 12.8, -2.4, 8.4, 15.2, 2.4, d)               # gauntlets
    s.box(-4.5, 24, -4.5, 4.5, 32.6, 4.5, m)                 # helmet
    s.box(-3.4, 25.2, 4.45, 3.4, 29.2, 4.7, SKIN)            # face opening
    s.box(-3.4, 27.2, 4.7, 3.4, 28.2, 4.9, (50, 38, 36))     # eye shadow
    s.box(-4.6, 29.6, -4.6, 4.6, 30.8, 4.6, a)               # brow band
    return s


# ---- shared part builders ------------------------------------------------------------------------------------------

def pauldron(s, pal, layers, size, flare=22.5, y0=23.0, colors=None):
    o, d, m, l, a = pal
    for i in range(layers):
        col = (colors or [l, m, a])[i % len(colors or [l, m, a])]
        reach = (5.2 - i * 0.7) * size
        s.mbox(4.0, y0 + i * 1.2, -3.2 * size + i * 0.4, 4.0 + reach, y0 + 1.5 + i * 1.2, 3.2 * size - i * 0.4,
               col, ("z", flare - i * 0.0))


def halo(s, cx, cy, cz, r, color, thick=0.9, depth=0.9, glow=True, a0=0, count=16):
    for k in range(count):
        th = a0 + k * 360 / count
        px, py = cx + r * math.cos(math.radians(th)), cy + r * math.sin(math.radians(th))
        L = 2 * math.pi * r / count * 1.08
        s.box(px - thick / 2, py - L / 2, cz - depth / 2, px + thick / 2, py + L / 2, cz + depth / 2, color,
              ("z", snap(th)), glow)


def arc(s, cx, cy, cz, r, th0, th1, color, thick=1.2, depth=1.0, glow=False, step=22.5):
    th = th0
    while th <= th1 + 0.01:
        px, py = cx + r * math.cos(math.radians(th)), cy + r * math.sin(math.radians(th))
        L = 2 * math.pi * r * step / 360 * 1.12
        s.box(px - thick / 2, py - L / 2, cz - depth / 2, px + thick / 2, py + L / 2, cz + depth / 2, color,
              ("z", th), glow)
        th += step


def crescent(s, cx, cy, cz, r, color, glow=True):
    """A bowl-shaped crescent (a moon lying on its back): a tapered arc of 22.5 degree segments."""
    for k in range(9):
        th = 180 + k * 22.5
        thick = 0.7 + 1.9 * math.sin(math.pi * k / 8)
        px, py = cx + r * math.cos(math.radians(th)), cy + r * math.sin(math.radians(th))
        L = 2 * math.pi * r / 16 * 1.15
        s.box(px - thick / 2, py - L / 2, cz - 0.8, px + thick / 2, py + L / 2, cz + 0.8, color, ("z", snap(th)), glow)


def leaf(s, x, y, z, size, color, tilt=0, glow=False):
    s.box(x - size, y - 0.35, z - size * 0.45, x + size, y + 0.35, z + size * 0.45, color, ("z", tilt) if tilt else None, glow)


def wing(s, pal, cloth, side, span, tall, glow_edge=None):
    """A bat/feather wing: a bone arm, finger bones, and a membrane of thin columns between them."""
    o, d, m, l, a = pal
    z = -3.6
    sx = side
    shoulder = (sx * 1.2, 21.5)
    tip = (sx * (1.2 + span), 21.5 + tall)
    fingers = [(sx * (1.2 + span * 0.96), 21.5 + tall * 0.18), (sx * (1.2 + span * 0.62), 21.5 - tall * 0.25),
               (sx * (1.2 + span * 0.26), 21.5 - tall * 0.40)]
    # membrane: columns between the upper edge (bone) and the lower scalloped edge
    cols = int(span)
    for i in range(cols):
        t = (i + 0.5) / cols
        xx = shoulder[0] + (tip[0] - shoulder[0]) * t
        top = shoulder[1] + (tip[1] - shoulder[1]) * t
        # lower edge: line through the finger tips, scalloped between fingers
        k = t * 3
        seg = min(2, int(k))
        pts = [shoulder] + [fingers[2], fingers[1], fingers[0]] + [tip]
        # simple lower edge: interpolate shoulder -> f3 -> f2 -> f1 -> tip
        idx = min(3, int(t * 4))
        p0, p1 = pts[idx], pts[idx + 1]
        lt = t * 4 - idx
        bottom = p0[1] + (p1[1] - p0[1]) * lt - 1.2 * math.sin(lt * math.pi)
        s.box(xx - 0.55 * sx if sx > 0 else xx - 0.55, bottom, z - 0.2 - i * 0.05, xx + 0.55, top, z + 0.2 - i * 0.05, cloth)
    s.rod((shoulder[0], shoulder[1], z), (tip[0], tip[1], z), 0.9, l, "xy", depth=0.9)
    for f in fingers:
        s.rod((tip[0], tip[1], z), (f[0], f[1], z), 0.7, m, "xy", depth=0.7)
    if glow_edge:
        s.rod((tip[0], tip[1], z + 0.3), (tip[0] + sx * 0.8, tip[1] + 2.6, z + 0.3), 0.8, glow_edge, "xy", glow=True)


# ---- NEUTRAL: the champion's line (knight, tabard, plume, halo) ------------------------------------------------------

def neutral(name, pal, tier):
    o, d, m, l, a = pal
    cl, gl = CLOTH[name], GLOW[name]
    s = body(pal, tier)
    if tier == 1:      # Recruit: kettle brim, short plume, leather strap and a small tabard
        s.box(-5.6, 31.6, -5.6, 5.6, 32.5, 5.6, l)
        s.rod((0, 32.5, -3), (0, 36.5, -1.5), 1.4, a, "yz", depth=1.6)
        s.rod((0, 36.5, -1.5), (0, 36, 1.5), 1.2, a, "yz", depth=1.4)
        s.box(-3.2, 5.5, 2.4, 3.2, 11.6, 2.9, cl)
        s.rod((-4.2, 23.6, 2.6), (4.2, 15.2, 2.6), 0.9, (110, 80, 52), "xy", depth=0.5)
    elif tier == 2:   # Challenger: crested helm, swept plume, winged pauldrons, tabard and short cape
        s.box(-0.9, 32.6, -4.2, 0.9, 35.2, 4.2, l)
        for i in range(5):
            s.rod((0, 35 + i * 0.4, 3.8 - i * 2.0), (0, 36.2 + i * 0.1, 2.6 - i * 2.3), 1.7, a if i % 2 else gl, "yz", depth=1.7)
        pauldron(s, pal, 3, 1.0, 22.5, colors=[l, m, a])
        s.mbox(7.6, 25.8, -1.6, 9.4, 29.8, 1.6, l, ("z", 22.5))       # winged tips
        s.box(-3.4, 5.4, 2.5, 3.4, 15.5, 3.0, cl)                      # tabard front
        s.box(-1.0, 8.4, 3.0, 1.0, 12.8, 3.3, a)                       # emblem
        s.cloth(-3.8, 3.8, 23.4, 9.5, -2.9, cl, flare=11.25)           # cape
    else:              # Paragon: winged helm, halo, great pauldrons, long cape, tassets
        for sx in (1, -1):
            for i in range(4):
                s.rod((sx * 4.6, 29.5 + i * 0.4, -0.5), (sx * (8.2 + i * 1.6), 33.5 + i * 2.0, -0.5), 1.1,
                      l if i % 2 else a, "xy", depth=1.0)
        s.box(-1.0, 32.6, -4.4, 1.0, 36.4, 4.4, a)
        for i in range(7):
            s.rod((0, 36 + i * 0.1, 4.0 - i * 1.5), (0, 38 + i * 0.2 - i * 0.2, 3.0 - i * 1.6), 1.7, gl if i % 2 else a, "yz", depth=1.7)
        halo(s, 0, 31.5, -6.2, 8.2, gl)
        pauldron(s, pal, 4, 1.25, 22.5, y0=22.6, colors=[l, m, a, l])
        s.mbox(9.2, 25.8, -2.2, 11.4, 31.4, 2.2, a, ("z", 22.5))
        s.cloth(-4.0, 4.0, 24.2, 1.0, -3.0, cl, flare=11.25, segs=4)
        s.box(-3.6, 21, -3.3, 3.6, 22.2, -2.6, a)                       # cape trim
        s.box(-3.4, 3.5, 2.5, 3.4, 15.5, 3.0, cl)
        s.box(-1.2, 9, 3.0, 1.2, 14, 3.3, gl, glow=True)
        s.mbox(0.4, 6.5, 2.4, 4.3, 11.2, 3.2, l)                         # tassets
    return s


# ---- TIDEFORGE: shells, fins, spiral crests -------------------------------------------------------------------------

def tide(name, pal, tier):
    o, d, m, l, a = pal
    cl, gl = CLOTH[name], GLOW[name]
    s = body(pal, tier)
    def fin(x, y0, size, col):
        s.rod((x, y0, 0), (x + (3.2 + size) * (1 if x > 0 else -1), y0 + 1.2 + size * 1.3, 0), 1.1, col, "xy", depth=2.6)
    if tier == 1:      # Tidewalker: a single swept fin each side and one scallop shell on the shoulder
        for sx in (1, -1):
            s.rod((sx * 4.7, 29, -1.5), (sx * 7.2, 33, -2.5), 1.0, l, "xy", depth=2.4)
        s.mbox(4.0, 23.2, -2.6, 8.8, 25.4, 2.6, l, ("z", 22.5))
        s.mbox(4.2, 25.4, -1.8, 7.6, 26.8, 1.8, a, ("z", 22.5))
        s.cloth(-2.6, 2.6, 23.4, 14.4, -2.9, cl, flare=11.25)
    elif tier == 2:   # Tideforge: spiral conch crest, fin ears, stacked shell pauldrons, dorsal fin and tail cloth
        for i in range(7):
            r = 3.0 - i * 0.34
            s.box(-r, 32.6 + i * 1.05, -r - 1 + i * 0.1, r, 33.6 + i * 1.05, r - 1 + i * 0.1, l if i % 2 else a)
        s.box(-0.5, 32.6, 2.2, 0.5, 40.6, 3.0, gl, glow=True)
        for sx in (1, -1):
            s.rod((sx * 4.6, 28, 0), (sx * 8.6, 34.4, -1.4), 1.1, l, "xy", depth=3.2)
            s.rod((sx * 4.6, 27, 0), (sx * 8.4, 30.4, -1.0), 0.9, a, "xy", depth=2.6)
        for i in range(4):
            s.mbox(4.0 + i * 0.5, 23.0 + i * 1.1, -3.5 + i * 0.5, 9.6 - i * 0.9, 24.6 + i * 1.1, 3.5 - i * 0.5,
                   l if i % 2 == 0 else a, ("z", 22.5))
        for i in range(5):
            s.rod((0, 24 - i * 0.4, -2.6 - i * 0.8), (0, 29 - i * 1.0, -4.0 - i * 1.7), 1.0, l if i % 2 else a, "yz", depth=1.4)
        s.cloth(-2.6, 2.6, 21.5, 6, -4.8, cl, flare=22.5, segs=3)
        s.box(-3.4, 8, 2.5, 3.4, 14.6, 3.0, cl)
    else:              # Leviathan: trident crest, great fin ears, layered shell mantle, spine ridge, scale tassets, tail cloth
        for sx in (-1, 0, 1):
            s.box(sx * 2.6 - 0.6, 32.6, -0.8, sx * 2.6 + 0.6, 37 + (2.5 if sx == 0 else 0), 0.8, l)
            s.box(sx * 2.6 - 1.0, 37 + (2.5 if sx == 0 else 0), -0.8, sx * 2.6 + 1.0, 39.6 + (2.5 if sx == 0 else 0), 0.8, gl, glow=True)
        s.box(-3.0, 32.6, -0.8, 3.0, 33.8, 0.8, a)
        for sx in (1, -1):
            for i in range(3):
                s.rod((sx * 4.6, 27 + i * 0.5, -0.5 - i * 0.6), (sx * (9 + i * 1.6), 33 + i * 3, -2 - i * 1.0), 1.1, l if i % 2 else a, "xy", depth=3.4 - i * 0.6)
        for i in range(5):
            s.mbox(4.0 + i * 0.4, 22.6 + i * 1.15, -4.2 + i * 0.5, 10.8 - i * 0.9, 24.2 + i * 1.15, 4.2 - i * 0.5,
                   l if i % 2 == 0 else a, ("z", 22.5))
        for i in range(9):
            s.rod((0, 24.5 - i * 0.2, -2.5 - i * 0.55), (0, 30 + (8 - i) * 0.9, -3.4 - i * 0.7), 1.2, l if i % 2 else a, "yz", depth=1.6)
        s.cloth(-3.4, 3.4, 22, 1.5, -5.6, cl, flare=22.5, segs=4)
        s.box(-0.6, 4, -6.3, 0.6, 21, -5.8, gl, glow=True)
        for i in range(4):
            s.mbox(0.3, 3 + i * 2.2, 2.4, 4.3, 5.2 + i * 2.2, 3.3, l if i % 2 else a)   # scale tassets
        s.box(-3.4, 6, 2.5, 3.4, 15.4, 3.0, cl)
    return s


# ---- ROOTVALE: antlers, leaf mantles, bark ---------------------------------------------------------------------------

def antler(s, pal, side, big, glowc=None):
    o, d, m, l, a = pal
    bone = (178, 140, 96)
    def p(x, y, z=0):
        return (side * x, y, z)
    beam = [(3.0, 32.6), (4.2, 36.0), (5.4, 39.6)] if big else [(3.0, 32.6), (3.9, 35.4), (4.6, 38.2)]
    s.rod(p(*beam[0]), p(*beam[1]), 1.1, bone, "xy", depth=1.1)
    s.rod(p(*beam[1]), p(*beam[2]), 0.9, bone, "xy", depth=0.9)
    s.rod(p(*beam[1]), p(beam[1][0] + 2.2, beam[1][1] + 1.6), 0.8, bone, "xy", depth=0.8)
    if big:
        s.rod(p(*beam[2]), p(beam[2][0] + 1.8, beam[2][1] + 3.4), 0.8, bone, "xy", depth=0.8)
        s.rod(p(*beam[2]), p(beam[2][0] - 1.4, beam[2][1] + 3.0), 0.8, bone, "xy", depth=0.8)
        s.rod(p(*beam[0]), p(beam[0][0] + 2.6, beam[0][1] + 2.4, 0), 0.8, bone, "xy", depth=0.8)
        for (px, py) in ((beam[2][0] + 1.8, beam[2][1] + 3.4), (beam[2][0] - 1.4, beam[2][1] + 3.0)):
            s.box(side * px - 0.7, py, -0.7, side * px + 0.7, py + 1.4, 0.7, glowc, glow=True)
    else:
        s.rod(p(*beam[2]), p(beam[2][0] + 0.9, beam[2][1] + 1.8), 0.7, bone, "xy", depth=0.7)


def rootvale(name, pal, tier):
    o, d, m, l, a = pal
    cl, gl = CLOTH[name], GLOW[name]
    s = body(pal, tier)
    if tier == 1:      # Sprout: leaf crown, a vine scarf and one leaf cape
        for k in range(8):
            ang = k * 45
            x, z = 4.3 * math.cos(math.radians(ang)), 4.3 * math.sin(math.radians(ang))
            leaf(s, x, 33.0 + (k % 2) * 0.9, z, 1.5, l if k % 2 else m, 0)
        s.rod((-4.2, 24, 2.7), (4.2, 20, 2.7), 1.0, cl, "xy", depth=0.9)
        s.mbox(4.2, 22.8, -2.2, 7.6, 24.3, 2.2, cl, ("z", 22.5))
        leaf(s, 0, 22, -3.1, 2.2, l, 0)
    elif tier == 2:   # Rootvale: stag antlers, moss mantle, bark chest ring, leaf tabard
        antler(s, pal, 1, False)
        antler(s, pal, -1, False)
        for i in range(3):
            s.mbox(4.0, 23.0 + i * 1.0, -3.0 + i * 0.5, 9.2 - i * 0.7, 24.4 + i * 1.0, 3.0 - i * 0.5,
                   cl if i % 2 == 0 else m, ("z", 22.5))
        for k in range(5):
            leaf(s, 6.5 + (k % 2) * 1.4, 24.4 - k * 0.3, -2.6 + k * 1.3, 1.3, l, 22.5)
            leaf(s, -6.5 - (k % 2) * 1.4, 24.4 - k * 0.3, -2.6 + k * 1.3, 1.3, l, -22.5)
        s.box(-4.5, 17.5, -2.6, 4.5, 20.0, 2.6, (96, 68, 44))
        for k in range(4):
            leaf(s, -3 + k * 2, 11, 2.9, 1.6, l if k % 2 else cl, 0)
        s.cloth(-3.4, 3.4, 22.5, 12, -2.9, cl, flare=11.25)
    else:              # Heartwood: towering antlers with glowing buds, bark pauldrons, trunk back with branches, root tassets
        antler(s, pal, 1, True, gl)
        antler(s, pal, -1, True, gl)
        for i in range(4):
            s.mbox(4.0, 22.5 + i * 1.2, -3.8 + i * 0.5, 11.2 - i * 0.9, 24.0 + i * 1.2, 3.8 - i * 0.5,
                   d if i % 2 == 0 else m, ("z", 22.5))
        for k in range(6):
            leaf(s, 7.4 + (k % 2) * 1.6, 27.4 + k * 0.4, -3 + k * 1.2, 1.6, l, 22.5)
            leaf(s, -7.4 - (k % 2) * 1.6, 27.4 + k * 0.4, -3 + k * 1.2, 1.6, l, -22.5)
        s.box(-4.2, 13, -5.4, 4.2, 25, -2.3, d)
        s.box(-2.6, 15, -6.4, 2.6, 23, -5.4, m)
        s.rod((0, 23, -6.4), (6.5, 29, -7.0), 1.2, d, "xy", depth=1.2)
        s.rod((0, 23, -6.4), (-6.5, 28, -7.0), 1.2, d, "xy", depth=1.2)
        for k in range(8):
            leaf(s, ((k % 4) - 1.5) * 2.6, 14 + k * 1.6, -6.8, 1.5, l, 0)
        s.box(-0.7, 16, -6.9, 0.7, 21, -6.5, gl, glow=True)
        s.mbox(0.3, 0, -4.4, 4.3, 5.5, -2.3, d)
        s.mbox(0.5, 5.5, 2.4, 4.3, 12, 3.4, d)
        for k in range(4):
            s.box(1.2 + (k % 2) * 1.6, 3 + k * 2, 3.4, 1.8 + (k % 2) * 1.6, 5 + k * 2, 3.9, gl, glow=True)
    return s


# ---- DUSKVALE: moon, bat wings, star cloak ----------------------------------------------------------------------------

def duskvale(name, pal, tier):
    o, d, m, l, a = pal
    cl, gl = CLOTH[name], GLOW[name]
    s = body(pal, tier)
    if tier == 1:      # Duskwanderer: a peaked hood with a star charm and a short scarf
        s.box(-4.7, 32.6, -4.7, 4.7, 34.4, 4.7, cl)
        s.box(-3.2, 34.4, -3.2, 3.2, 36.2, 3.2, cl)
        s.rod((0, 35.5, -1), (0, 38.0, -5.2), 2.2, cl, "yz", depth=3.0)
        s.box(-0.8, 31.9, 4.8, 0.8, 33.6, 5.4, gl, glow=True)
        s.box(-1.7, 32.4, 4.8, 1.7, 33.1, 5.3, gl, glow=True)
        s.cloth(-4.4, 4.4, 24.4, 19, 2.7, cl, flare=11.25)
        s.mbox(4.2, 23.0, -2.3, 7.5, 24.4, 2.3, cl, ("z", 22.5))
    elif tier == 2:   # Duskvale: crescent crown, bat wings, spiked pauldrons, star cloak
        crescent(s, 0, 38.0, -0.6, 5.0, gl)
        s.mbox(4.2, 23.0, -3.2, 9.4, 25.4, 3.2, d, ("z", 22.5))
        s.rod((5.6, 25.4, 0), (6.2, 30, 0), 1.5, l, "xy", depth=1.5, mirror=True)
        s.rod((7.6, 25.0, 0), (8.2, 28.2, 0), 1.1, a, "xy", depth=1.1, mirror=True)
        wing(s, pal, cl, 1, 9, 9, gl)
        wing(s, pal, cl, -1, 9, 9, gl)
        s.cloth(-3.4, 3.4, 23, 7, -3.0, cl, flare=11.25, segs=3)
        for k in range(5):
            s.box(-2.6 + (k % 3) * 2.4, 9 + k * 2.4, -3.4, -1.8 + (k % 3) * 2.4, 9.8 + k * 2.4, -3.2, gl, glow=True)
    else:              # Nightfall: moon-and-star crown, horns, great wings, star cloak, pendant tassets
        crescent(s, 0, 38.6, -0.6, 7.0, gl)
        s.box(-0.9, 41.5, -1.0, 0.9, 43.3, 0.2, gl, glow=True)
        s.box(-1.6, 42.0, -1.0, 1.6, 42.8, 0.2, gl, glow=True)
        for sx in (1, -1):
            s.rod((sx * 4.2, 32.6, 0), (sx * 5.6, 35.8, 0), 1.3, l, "xy", depth=1.3)
            s.rod((sx * 5.6, 35.8, 0), (sx * 5.2, 39.0, 0), 1.0, l, "xy", depth=1.0)
        s.mbox(4.2, 22.8, -4.0, 10.6, 26.2, 4.0, d, ("z", 22.5))
        for k in range(3):
            s.rod((5.4 + k * 1.9, 26, 0), (6.0 + k * 1.9, 30 + k * 1.8, 0), 1.3, a if k % 2 else l, "xy", depth=1.3, mirror=True)
        wing(s, pal, cl, 1, 12, 12, gl)
        wing(s, pal, cl, -1, 12, 12, gl)
        s.cloth(-3.8, 3.8, 23.5, 3, -2.8, cl, flare=11.25, segs=4)
        for k in range(9):
            s.box(-3 + (k % 4) * 2.0, 5 + k * 2.1, -3.2 - (k % 2) * 0.1, -2.2 + (k % 4) * 2.0, 5.8 + k * 2.1, -2.9, gl, glow=True)
        for k in range(4):
            s.box(-3.3 + k * 2.2, 5.5 - (k % 2) * 1.8, 2.4, -2.5 + k * 2.2, 11.4, 3.0, cl)
            s.box(-3.3 + k * 2.2, 4.4 - (k % 2) * 1.8, 2.4, -2.5 + k * 2.2, 5.5 - (k % 2) * 1.8, 3.0, gl, glow=True)
    return s


SETS = {
    "Neutral": [("recruit", neutral, 1), ("challenger", neutral, 2), ("paragon", neutral, 3)],
    "Tideforge": [("tidewalker", tide, 1), ("tideforge", tide, 2), ("leviathan", tide, 3)],
    "Rootvale": [("sprout", rootvale, 1), ("rootvale", rootvale, 2), ("heartwood", rootvale, 3)],
    "Duskvale": [("duskwanderer", duskvale, 1), ("duskvale", duskvale, 2), ("nightfall", duskvale, 3)],
}
TIER = {1: "Tier I  utility", 2: "Tier II", 3: "Tier III  defense"}


def tower_sheet(tower: str, sets) -> Image.Image:
    cw, ch = 730, 590
    sheet = Image.new("RGB", (cw * 3, ch + 10), (28, 26, 34))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("arial.ttf", 20)
    except OSError:
        font = ImageFont.load_default()
    for col, (name, fn, tier) in enumerate(sets):
        pal = PALETTES[name]
        x0 = col * cw
        d.rectangle([x0 + 6, 6, x0 + cw - 6, ch], fill=(40, 37, 50), outline=(66, 62, 80))
        scene = fn(name, pal, tier)
        for k, yaw in enumerate((-30, 150)):
            view = render(scene, yaw)
            sheet.paste(view, (x0 + 10 + k * 358, 40), view)
        d.text((x0 + 22, 14), f"{name.title()}  ({TIER[tier]})", fill=(240, 232, 208), font=font)
        for i, c in enumerate((pal[1], pal[2], pal[3], pal[4], CLOTH[name], GLOW[name])):
            d.rectangle([x0 + cw - 190 + i * 28, 14, x0 + cw - 168 + i * 28, 32], fill=c)
    return sheet


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    out.mkdir(parents=True, exist_ok=True)
    for tower, sets in SETS.items():
        img = tower_sheet(tower, sets)
        path = out / f"{tower.lower()}.png"
        img.save(path)
        print(path, img.size)


if __name__ == "__main__":
    main()
