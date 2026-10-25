"""Silhouette accents for the helmets and shoulders: a few painted planes per set (swept fins, crests, drifting wisps)."""
import math
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *
from designs import two, side_one

HEAD, RARM = "armorHead", "armorRightArm"


def full(v):
    return np.ones(v.shape[:2], bool)


def leaf_down(rp, glow, down=False):
    def f(a):
        h, w = a.shape[:2]
        t = np.zeros_like(a)
        m = leaf(w, h, bulge=0.6, wide=0.48)
        shade(t, m, rp, "cyl", lo=1.8, hi=4.8, lit=0.32)
        c = w // 2
        for yy in range(3, h - 2):
            t[yy, c] = (*rp[5], 255)
        for yy in range(6, h - 3, 4):
            for i in range(1, 4):
                for sg in (-1, 1):
                    if 0 <= c + sg * i < w and m[yy - i, c + sg * i]:
                        t[yy - i, c + sg * i] = (*rp[4], 255)
        for yy in range(0, 3):
            for xx in range(w):
                if m[yy, xx]:
                    t[yy, xx] = (*glow[4], 255)
        a[:] = np.flipud(t) if down else t
    return f


def with_mirror(p):
    return [p, p.mirrored()]


# ---- Tideforge: swept ear-fins growing out of the temples and trailing back -------------------------------------------------

def tide_accents(look):
    pearl, enamel = ramp((222, 232, 244)), ramp((50, 130, 196))
    glow = look.glow

    def fin(a):                                                   # drawn with the tip at the LEFT (toward the back of the head)
        h, w = a.shape[:2]
        pts = [(w - 1, h * 0.22), (w * 0.6, h * 0.08), (w * 0.22, h * 0.04), (1, 0)]
        for k in range(7):                                        # a scalloped trailing edge sweeping back to the root
            t = k / 6
            pts.append((1 + (w * 0.58) * t, h * (0.18 + 0.22 * t) + 1.6 * math.sin(k * 2.4) + h * 0.48 * t ** 1.3))
        pts += [(w - 1, h * 0.98)]
        m = poly_mask(w, h, pts)
        shade(a, m, pearl, "h", lo=2.2, hi=5.0, inv=True)
        blue = m.copy(); blue[:, :int(w * 0.62)] = False                # a clean blue enamel band toward the root
        shade(a, blue, enamel, "h", lo=2.2, hi=4.6, edge=False, inv=True)
        put(a, m & ~erode(m), enamel[1])
        for x in range(int(w * 0.25), int(w * 0.85), 4):          # glowing rib ticks along the fin
            col = np.where(m[:, x])[0]
            if len(col):
                a[(col[0] + col[-1]) // 2, x] = (*glow[4], 255)
    f = Part(HEAD, [5.05, 25.5, -1.5], [0.3, 7.0, 7.5], rot=(0, 0, 10), pivot=[5.2, 25.5, 0.0], group="fin", gpivot=[5.2, 25.5, 0.0],
             paint=lambda v, l, m: side_one(v, fin, m))
    return [f, f.mirrored()]


# ---- Rootvale: a crest of leaves on the helm and leaves sprouting from the shoulders -----------------------------------------

def root_accents(look):
    leafA, leafB, glow = look.cloth, ramp((126, 176, 72)), look.glow
    parts = []
    for rz, w, hh, rp in ((-38, 3.2, 6.0, leafA), (0, 3.8, 7.6, leafB), (38, 3.2, 6.0, leafA)):
        parts.append(Part(HEAD, [-w / 2, 33.0, -1.0], [w, hh, 0.3], rot=(0, 0, rz), pivot=[0, 33.0, -0.85],
                          group=f"crest_{len(parts)}", gpivot=[0, 33.0, -0.85],
                          paint=lambda v, l, m, rp=rp: two(v, leaf_down(rp, glow))))
    for rz, w, hh, rp, z in ((58, 3.0, 6.4, leafB, -0.8), (28, 3.4, 7.2, leafA, 0.4)):
        k = len(parts) // 2
        p = Part("armorRightArm", [-6.5 - w / 2, 23.6, z], [w, hh, 0.3], rot=(0, 0, -rz), pivot=[-6.5, 23.6, z + 0.15],
                 group=f"shoulder_{k}", gpivot=[-6.5, 23.6, z + 0.15],
                 paint=lambda v, l, m, rp=rp: two(v, leaf_down(rp, glow)))
        parts += [p, Part("armorLeftArm", [6.5 - w / 2, 23.6, z], [w, hh, 0.3], rot=(0, 0, rz), pivot=[6.5, 23.6, z + 0.15],
                          group=f"shoulder_{k}_m", gpivot=[6.5, 23.6, z + 0.15],
                          paint=lambda v, l, m, rp=rp: two(v, leaf_down(rp, glow)))]
    return parts


# ---- Duskvale: a crescent crest and wisps trailing from the shoulders ------------------------------------------------------

def dusk_accents(look):
    pale = ramp((206, 176, 255))
    glow = look.glow
    parts = []

    def crest(a):
        h, w = a.shape[:2]
        m = crescent(w, w * 0.5, w * 0.42, 0.0, -w * 0.22)       # horns pointing up, like a bowl
        shade(a, m, pale, "v", lo=2.4, hi=5.0, edge=False, inv=True)
        put(a, m & ~erode(m), glow[4])
    # (a small crest crescent was tried here and cut: it read as a second moon next to the big one)

    def wisp(a):
        h, w = a.shape[:2]
        m = ribbon(w, h, w / 2, w * 0.18, 1.3, 0.8, w * 0.42, taper=0.7)
        shade(a, m, pale, "v", lo=1.6, hi=4.8, inv=True, edge=False, dith=1.0)
        put(a, m & ~erode(m), ramp((110, 70, 190))[1])
        for y in range(int(h * 0.15), int(h * 0.65)):
            xs = np.where(m[y])[0]
            if len(xs):
                a[y, (xs[0] + xs[-1]) // 2] = (*glow[4], 255)
        for y in range(int(h * 0.45), h):
            for x in range(w):
                if BAYER[y % 4, x % 4] < (y / h - 0.45) * 1.9:
                    a[y, x] = 0
    for z in (-0.6, 1.0):
        w = Part("armorRightArm", [-7.6, 12.0, z], [2.6, 11.0, 0.3], rot=(0, 0, -14), pivot=[-6.3, 23.0, z + 0.15],
                 group=f"wisp_{len(parts) // 2}", gpivot=[-6.3, 23.0, z + 0.15], paint=lambda v, l, m: two(v, wisp))
        parts += [w, w.mirrored()]
        parts[-1].bone = "armorLeftArm"
    return parts


ACCENTS = {"tideforge": tide_accents, "rootvale": root_accents, "duskvale": dusk_accents}
