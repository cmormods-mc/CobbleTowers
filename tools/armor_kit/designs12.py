"""Tideforge shop back attachment: a kraken crown. Six thick tentacles grow from a carapace between the shoulder blades and curl over the
head, round the shoulders and down behind the hips. Each one is a chain of tapering, mottled segments with silver cuffs, rows of
suckers on its inner curve and barnacles on its back."""
import math
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *
import generate_armor_textures as gt

B = "armorBody"
gt.SPECS["tideforge"].update(metal=(190, 208, 222), trim=(60, 140, 200), cloth=(30, 84, 140), glow=(170, 240, 255))


def _look():
    return gt.Look("tideforge", gt.SPECS["tideforge"])


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def orient(d):
    """Rotation (x, 0, z) that turns a box's +y axis onto direction d. A box tilts toward +z by x = -lift, then swings about z by theta."""
    d = unit(d)
    lift = math.degrees(math.asin(max(-1.0, min(1.0, d[2]))))
    theta = math.degrees(math.atan2(d[0], d[1]))
    return (-lift, 0, theta)


def box_along(bone, centre, d, w, length, **kw):
    """A w x length x w box centred on `centre`, its long axis along d."""
    cx, cy, cz = centre
    return Part(bone, [cx - w / 2, cy - length / 2, cz - w / 2], [w, length, w], rot=orient(d), pivot=[cx, cy, cz], **kw)


def paint_skin(skin, spot, glow, seed):
    def paint(v, l, m):
        rng = np.random.default_rng(seed)
        for n, a in v.items():
            h, w = a.shape[:2]
            full = np.ones((h, w), bool)
            shade(a, full, skin, "cyl" if n in ("front", "back", "left", "right") else "none", lo=1.8, hi=4.2, lit=0.34, edge=False, dith=0.6)
            for _ in range(max(2, h * w // 16)):                       # mottled lighter blotches
                y, x = int(rng.integers(0, h)), int(rng.integers(0, w))
                a[y, x] = (*spot[int(rng.integers(2, 5))], 255)
                if w > 3 and x + 1 < w and rng.random() < 0.5:
                    a[y, x + 1] = (*spot[2], 255)
            if n in ("front", "back", "left", "right") and h > 2:      # a dark seam where each segment meets the next
                a[0, :] = (*skin[1], 255)
                a[h - 1, :] = (*skin[0], 255)
            if rng.random() < 0.22:                                    # a bioluminescent speck
                a[int(rng.integers(1, max(2, h - 1))), int(rng.integers(0, w))] = (*glow[4], 255)
    return paint


def tentacle(parts, bone, p0, u, v, a0, k1, k2, power, length, w0, w1, nseg, seed, look, suckers=True):
    glow = look.glow
    skin = ramp((26, 62, 118))
    spot = ramp((70, 140, 190))
    pale = ramp((236, 214, 200))
    silver = ramp((190, 208, 222))
    u, v = unit(u), unit(v)
    v = unit(v - u * float(np.dot(u, v)))
    step = length / nseg
    p = np.asarray(p0, float)
    rng = np.random.default_rng(seed)
    for i in range(nseg):
        s = (i + 0.5) / nseg
        alpha = a0 + k1 * s + k2 * s ** power + 0.30 * math.sin(2 * math.pi * 1.25 * s + seed)      # a lean, a curl at the tip, and a sway between
        bn = np.cross(u, v)
        T = unit(math.cos(alpha) * u + math.sin(alpha) * v + 0.28 * math.sin(2 * math.pi * 1.6 * s + seed * 1.7) * bn)
        N0 = -math.sin(alpha) * u + math.cos(alpha) * v
        N = unit(N0 - T * float(np.dot(N0, T)))                          # toward the inside of the curl, exactly square to the arm
        w = max(1, int(round(w0 + (w1 - w0) * s ** 0.8)))
        L = max(2, int(math.ceil(step * 1.35)))
        c = p + T * step / 2
        parts.append(box_along(bone, c, T, w, L, group=f"leafring_{seed}", gpivot=[float(p0[0]), float(p0[1]), float(p0[2])],
                               paint=paint_skin(skin, spot, glow, (w * 31 + L) % 7 + (i % 2) * 7), share=f"kseg_{w}x{L}_{(w * 31 + L) % 7 + (i % 2) * 7}"))
        if suckers and s > 0.14 and w >= 3:                              # cups sit on the inner curve: a pair every other segment on the thick part, a zigzag on the thin
            thick = w >= 5
            if (not thick) or i % 2 == 0:
                sw = 3 if thick else 2
                B3 = np.cross(T, N)
                for off in ((-0.27, 0.27) if thick else ((0.2 if i % 2 == 0 else -0.2),)):
                    sc = c + N * (w / 2 + 0.2) + B3 * off * w
                    parts.append(Part(bone, [sc[0] - sw / 2, sc[1] - 0.5, sc[2] - sw / 2], [sw, 1, sw], rot=orient(N), pivot=[float(sc[0]), float(sc[1]), float(sc[2])],
                                      paint=paint_sucker(pale, glow), share=f"ksucker_{sw}"))
        if i == int(nseg * 0.12):                    # a silver cuff round the arm
            parts.append(box_along(bone, c, T, w + 1, 2, paint=paint_cuff(silver, glow), share=f"kcuff_{w}"))
        if 0.1 < s < 0.6 and rng.random() < 0.2:                        # barnacles on the outer curve
            bc = c - N * (w / 2 - 0.2)
            parts.append(Part(bone, [bc[0] - 1, bc[1] - 1, bc[2] - 1], [2, 2, 2], paint=paint_barnacle(), share="kbarnacle"))
        p = p + T * step
    return p


def paint_sucker(pale, glow):
    def paint(v, l, m):
        for n in ("front", "back", "left", "right", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), pale, "v", lo=2.4, hi=4.4)
        a = v["top"]
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        d = np.hypot(xx - (w - 1) / 2, yy - (h - 1) / 2)
        a[:] = 0
        shade(a, d <= w / 2 + 0.2, pale, "v", lo=2.8, hi=4.8, edge=True)
        put(a, d <= w * 0.28, (90, 40, 66))
        put(a, d <= w * 0.12, glow[3])
    return paint


def paint_cuff(silver, glow):
    def paint(v, l, m):
        for n, a in v.items():
            h, w = a.shape[:2]
            shade(a, np.ones((h, w), bool), silver, "v", lo=1.8, hi=5.0)
            if n in ("front", "back") and w >= 4:
                a[h // 2, 1:w - 1:3] = (*silver[1], 255)               # rivets
    return paint


def paint_barnacle():
    rp = ramp((150, 158, 164))

    def paint(v, l, m):
        for n, a in v.items():
            shade(a, np.ones(a.shape[:2], bool), rp, "v", lo=1.6, hi=4.4, dith=0.9)
    return paint


def kraken_crown(look):
    look = _look()
    glow, metal = look.glow, look.metal
    parts = []
    # the three pairs of tentacles: (name, root, rising direction, curl direction, heading start, heading end, power, length, w0, w1, segments)
    SPEC = [
        ("crown", (5.4, 22.5, 3.8), (0.25, 1.0, 0.5), (-0.45, 0.0, -0.9), -0.10, 1.5, 1.8, 3.0, 40, 7, 1, 21),
        ("sweep", (7.4, 19.0, 3.4), (0.9, 0.4, 0.3), (0.1, 0.9, -0.2), -0.30, 0.7, 2.7, 3.0, 34, 6, 1, 18),
        ("coil", (4.2, 14.0, 3.5), (0.6, -0.55, 0.45), (-0.15, 0.55, 0.55), -0.20, 0.4, 3.0, 2.6, 30, 6, 1, 16),
    ]
    seed = 0
    for sx in (1, -1):
        for k, (name, p0, u, v, a0, k1, k2, pw, ln, w0, w1, n) in enumerate(SPEC):
            tentacle(parts, B, (sx * p0[0], p0[1], p0[2]), (sx * u[0], u[1], u[2]), (sx * v[0], v[1], v[2]), a0, k1, k2, pw, ln, w0, w1, n, seed + k, look)
        seed += 3

    # the carapace the tentacles grow from: a ribbed silver shell over the spine with a glowing eye, and barnacled rock round its base
    def plate(v, l, m):
        for n, a in v.items():
            shade(a, np.ones(a.shape[:2], bool), metal, "v", lo=1.6, hi=5.0)

    def eye(v, l, m):
        for n in ("front", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), glow, "v", lo=1.4, hi=3.8)
        a = v["back"]
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        c = (w - 1) / 2
        d = np.hypot(xx - c, (yy - c) * 1.2)
        a[:] = 0
        shade(a, np.ones((h, w), bool), glow, "v", lo=2.0, hi=4.4, edge=False)
        put(a, d <= w * 0.34, (14, 30, 66))                                    # a dark vertical pupil in a bright iris
        put(a, (np.abs(xx - c) <= 0.8) & (np.abs(yy - c) <= h * 0.34), (14, 30, 66))
        put(a, d <= w * 0.14, glow[5])
    parts.append(Part(B, [-6, 20.0, 2.9], [12, 4, 3], paint=plate, share="kshell1"))
    parts.append(Part(B, [-4.5, 17.0, 3.0], [9, 3, 3], paint=plate, share="kshell2"))
    parts.append(Part(B, [-3, 14.0, 3.0], [6, 3, 3], paint=plate, share="kshell3"))
    parts.append(Part(B, [-2, 17.4, 6.0], [4, 4, 2], group="gem", gpivot=[0, 19.4, 7.0], paint=eye))
    for x, y, z in ((-7, 19.0, 3.4), (7, 19.0, 3.4), (-5, 13.5, 3.3), (5, 13.5, 3.3), (0, 12.0, 3.6), (-3.5, 22.8, 3.8), (3.5, 22.8, 3.8)):
        parts.append(Part(B, [x - 1, y - 1, z - 0.4], [2, 2, 2], paint=paint_barnacle(), share="kbarnacle"))
    return parts
