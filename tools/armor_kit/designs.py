"""Plane helpers (two, side_one, sides) and the Duskvale back attachment: a crescent moon, five wisp tendrils and star sparkles."""
import math, random, sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *

B = "armorBody"


def clear(v):
    v[:] = 0


def two(views, fn):
    """Paint a plane's outward face only. Armor draws without backface culling, so a second painted face would peek out at the
    silhouette edges and shift as the camera moves (a phasing look)."""
    for n in ("front", "top", "bottom", "left", "right"):
        clear(views[n])
    clear(views["back"]); fn(views["back"])


def side_one(views, fn, mirror=False):
    """Same for a plane thin in x: paint the outward side face. `fn` draws with the tip at the LEFT; the west face is flipped."""
    for n in ("front", "back", "top", "bottom", "left", "right"):
        clear(views[n])
    face = views["left"] if mirror else views["right"]
    fn(face)
    if mirror:
        face[:] = face[:, ::-1].copy()


def sides(views, fn, cap):
    for n in ("front", "back", "left", "right"):
        fn(views[n], n)
    for n in ("top", "bottom"):
        v = views[n]
        shade(v, np.ones(v.shape[:2], bool), cap, "none", edge=False)


# ---- Duskvale: a crescent moon behind the head and wisps drifting from the waist ------------------------------------------

def dusk(look):
    pale = ramp((206, 176, 255))       # moon and wisps: a pale violet that reads as light
    deep = ramp((70, 40, 120))
    glow = look.glow
    parts = []

    def moon(v, *_):
        h, w = v.shape[:2]
        m = crescent(w, w * 0.50, w * 0.40, w * 0.27, -w * 0.04)
        shade(v, m, pale, "h", lo=2.4, hi=5.0, edge=False, dith=1.0)
        put(v, m & ~erode(m), glow[4])           # a lit rim
        rnd = random.Random(7)
        for _ in range(6):                       # stars drifting in the opening, each a small plus
            x, y = rnd.randrange(w // 2, w - 3), rnd.randrange(3, h - 3)
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                if not m[y + dy, x + dx]:
                    v[y + dy, x + dx] = (*(glow[5] if dx == dy == 0 else glow[3]), 255)

    parts.append(Part(B, [-7.5, 21.5, 5.4], [15, 15, 0.3], rot=(0, 0, 22.5), pivot=[0, 29.0, 5.55], group="moon", gpivot=[0, 29.0, 5.55],
                        paint=lambda v, l, mi: two(v, lambda a: moon(a))))

    def yoke(v, *_):
        for n in ("front", "back", "left", "right"):
            h, w = v[n].shape[:2]
            shade(v[n], np.ones((h, w), bool), deep, "v", lo=1.4, hi=3.6)
        for n in ("top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), deep, "none", edge=False)
    parts.append(Part(B, [-3.6, 21.0, 2.9], [7.2, 2.6, 2.2], paint=yoke))
    parts.append(Part(B, [-0.7, 22.4, 4.9], [1.4, 4.8, 0.8], paint=yoke))     # a bracket to the moon

    # tendrils: pivot at the waist, fanned in 22.5 degree steps; the long ones in the middle
    spec = [(-52, 4.2, 15, 0.6, -4.2), (-28, 4.8, 20, 1.9, -2.1), (0, 5.4, 22, 3.1, 0), (28, 4.8, 20, 4.4, 2.1), (52, 4.2, 15, 5.2, 4.2)]
    for k, (ang, w, hh, ph, px) in enumerate(spec):
        def tendril(a, w=w, hh=hh, ph=ph, k=k):
            h, ww = a.shape[:2]
            m = ribbon(ww, h, ww / 2, ww * 0.16, 1.5, ph, ww * 0.42, taper=0.75)
            rp = pale if k % 2 == 0 else ramp((150, 110, 220))
            shade(a, m, rp, "v", lo=1.6, hi=4.8, inv=True, edge=False, dith=1.0)
            e = m & ~erode(m)
            put(a, e, ramp((110, 70, 190))[1])
            # fade the tail out with a dither, then a glow core down the middle
            ys = np.arange(h)[:, None] / max(1, h - 1)
            keep = ~((ys > 0.5) & (BAYER[np.arange(h)[:, None] % 4, np.arange(ww)[None, :] % 4] < (ys - 0.5) * 2.0))
            a[~keep[..., 0] if keep.ndim == 3 else ~keep] = 0
            for y in range(int(h * 0.12), int(h * 0.62)):
                xs = np.where(m[y])[0]
                if len(xs):
                    a[y, (xs[0] + xs[-1]) // 2] = (*glow[4], 255)
        parts.append(Part(B, [px - w / 2, 22.4 - hh, 3.5 + 0.05 * k], [w, hh, 0.3], rot=(0, 0, -ang), pivot=[px, 22.4, 3.65 + 0.05 * k], group=f"tendril_{k}", gpivot=[px, 22.4, 3.65 + 0.05 * k],
                          paint=lambda v, l, mi, t=tendril: two(v, t)))

    def star(a):
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        c = (w - 1) / 2
        dx, dy = np.abs(xx - c) / max(c, 1), np.abs(yy - c) / max(c, 1)
        m = (dx ** 0.55 + dy ** 0.55) <= 1.0                      # a four-pointed sparkle
        put(a, m, glow[4])
        put(a, m & (dx + dy < 0.5), glow[5])
    for x, y, z, s in ((-9.2, 31.0, 6.6, 2.6), (8.4, 24.5, 6.2, 2.0), (-7.0, 18.5, 5.6, 1.6), (7.8, 13.0, 5.4, 2.2), (-3.4, 6.0, 5.6, 1.8),
                       (3.8, 4.0, 5.4, 1.5), (0.6, 36.8, 6.6, 2.0), (9.6, 34.0, 6.8, 1.5), (-5.4, 27.0, 6.4, 1.4)):
        parts.append(Part(B, [x - s / 2, y, z], [s, s, 0.3], group=f"star_{len(parts)}", gpivot=[x, y + s / 2, z],
                          paint=lambda v, l, mi: two(v, star)))
    return parts
