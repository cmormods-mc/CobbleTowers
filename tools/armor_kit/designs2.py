"""Tideforge dive rig (twin banded tanks, bubbles) and the Rootvale leaf kite shield."""
import math
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *
from designs import B, clear, two, sides, side_one


def full(v):
    return np.ones(v.shape[:2], bool)


def paste(a, tmp, x, y):
    """Composite a small RGBA tile onto face array `a` at (x, y), clipped."""
    h, w = tmp.shape[:2]
    y0, x0 = max(0, y), max(0, x)
    y1, x1 = min(a.shape[0], y + h), min(a.shape[1], x + w)
    if y1 <= y0 or x1 <= x0:
        return
    sub = tmp[y0 - y:y1 - y, x0 - x:x1 - x]
    live = sub[..., 3] > 0
    a[y0:y1, x0:x1][live] = sub[live]


def cyl(v, rp, lit=0.3, shift=0):
    """A cylinder read across a flat face: dark edges, a shaded body in clean bands, a narrow bright streak, a rim light on the far side."""
    h, w = v.shape[:2]
    for x in range(w):
        t = x / max(1, w - 1)
        d = abs(t - lit)
        i = 4 if d < 0.13 else 3 if d < 0.30 else 2 if d < 0.55 else 1
        if d < 0.045:
            i = 5
        if t > 0.9:
            i = max(i, 2)
        i = max(1, min(5, i + shift))
        if x == 0 or x == w - 1:
            i = 0
        v[:, x] = (*rp[i], 255)
    v[0, :] = (*rp[0], 255)
    v[-1, :] = (*rp[0], 255)


LIT = {"front": (0.28, 0), "back": (0.70, 0), "left": (0.50, -1), "right": (0.50, -1)}


# ---- Tideforge: a big twin-tank dive rig with rising bubbles ------------------------------------------------------------

def tide(look):
    steel, dark = ramp((118, 132, 152)), ramp((52, 62, 80))
    enamel, band, glow = look.trim, ramp((232, 241, 250)), look.glow
    cyan = ramp((70, 200, 235))
    parts = []

    def plate(v, n):
        h, w = v.shape[:2]
        shade(v, full(v), dark, "v", lo=1.4, hi=3.8)
        if n in ("front", "back"):
            for yy in (int(h * 0.25), int(h * 0.75)):
                m = np.zeros((h, w), bool); m[yy - 1:yy + 2, 1:w - 1] = True
                shade(v, m, steel, "v", lo=1.6, hi=4.0)
                for rx in range(3, w - 2, 5):
                    v[yy, rx] = (*steel[5], 255)
    parts.append(Part(B, [-5.0, 11.8, 3.0], [10, 11, 1], paint=lambda v, l, m: sides(v, plate, dark)))

    navy = ramp((24, 60, 120))

    def tank(v, n):
        h, w = v.shape[:2]
        lit, sh = LIT[n]
        cyl(v, enamel, lit, sh)                                                   # the blue enamel body
        for f0, f1, rp in ((0.27, 0.31, cyan), (0.46, 0.64, band)):               # a thin cyan stripe and the wide white band
            b0, b1 = int(h * f0), int(h * f1)
            cyl(v[b0:b1], rp, lit, sh)
            v[b0 - 1, :] = (*navy[0], 255); v[b1, :] = (*navy[0], 255)
        mid = (int(h * 0.46) + int(h * 0.64)) // 2
        if n in ("front", "back"):
            for x in range(2, w - 2):                                             # a wave mark across the band
                y = mid + int(round(1.4 * math.sin(x * 0.85)))
                v[y, x] = (*cyan[2], 255)
            v[mid, 2] = (*glow[4], 255); v[mid, w - 3] = (*glow[4], 255)
        low = int(h * 0.86)
        cyl(v[low:], navy, lit, sh)                                               # the foot ring
        v[low - 1, :] = (*navy[0], 255)
        rivets_row = (2, w - 4) if w > 8 else (1,)
        for x in rivets_row:                                                      # shoulder rivets
            v[4, x] = (*steel[5], 255); v[4, x + 1] = (*steel[3], 255); v[5, x] = (*steel[2], 255)
    for sgn in (-1, 1):
        cx = 2.9 * sgn
        parts.append(Part(B, [cx - 2.0, 11.8, 4.0], [4, 11, 4], paint=lambda v, l, m: sides(v, tank, enamel)))
        parts.append(Part(B, [cx - 1.5, 22.8, 4.5], [3, 1, 3],
                          paint=lambda v, l, m: sides(v, lambda a, n: cyl(a, steel, *LIT[n]), steel)))
        parts.append(Part(B, [cx - 1.0, 23.8, 5.0], [2, 1, 2],
                          paint=lambda v, l, m: sides(v, lambda a, n: cyl(a, dark, *LIT[n]), dark)))
        parts.append(Part(B, [cx - 1.5, 24.8, 5.2], [3, 1, 1],
                          paint=lambda v, l, m: sides(v, lambda a, n: cyl(a, cyan, *LIT[n]), cyan)))
        parts.append(Part(B, [cx - 2.5, 9.9, 3.5], [5, 2, 5],
                          paint=lambda v, l, m: sides(v, lambda a, n: cyl(a, dark, *LIT[n]), dark)))
    parts.append(Part(B, [-4.5, 18.4, 8.0], [9, 1, 1],
                      paint=lambda v, l, m: sides(v, lambda a, n: cyl(a, steel, *LIT[n]), steel)))

    def gauge(v, *_):
        for n in ("front", "left", "right", "top", "bottom"):
            shade(v[n], full(v[n]), dark, "v")
        a = v["back"]
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        c = (w - 1) / 2
        ring = (xx - c) ** 2 + (yy - c) ** 2 <= (w / 2) ** 2
        face = (xx - c) ** 2 + (yy - c) ** 2 <= (w / 2 - 1.8) ** 2
        clear(a)
        shade(a, ring, steel, "v", lo=2.4, hi=5.0)
        put(a, face, band[2])
        for ang in range(-60, 61, 30):                              # tick marks round the dial
            tx, ty = int(round(c + (w / 2 - 2.6) * math.sin(math.radians(ang)))), int(round(c - (w / 2 - 2.6) * math.cos(math.radians(ang))))
            a[ty, tx] = (*dark[0], 255)
        for k in range(1, 4):                                       # the needle
            a[int(c) - k, int(c) + k // 2] = (*cyan[1], 255)
        a[int(c), int(c)] = (*dark[0], 255)
    parts.append(Part(B, [-1.5, 17.6, 9.0], [3, 3, 1], paint=gauge))

    def bubble(a):
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        c = (w - 1) / 2
        r2 = (xx - c) ** 2 + (yy - c) ** 2
        disc = r2 <= (w / 2) ** 2
        inner = r2 <= (w / 2 - 1.3) ** 2
        put(a, disc & ~inner, cyan[4])
        put(a, inner & (BAYER[yy % 4, xx % 4] > 0.55) & (yy + xx > c), cyan[2])     # a faint shaded fill
        put(a, disc & ~inner & (yy < c) & (xx < c), band[5])                          # a bright rim top left
        hx = max(1, int(w * 0.28))
        if w >= 5:
            a[hx, hx] = (*band[5], 255); a[hx, hx + 1] = (*band[5], 255); a[hx + 1, hx] = (*band[5], 255)
    for x, y, z, s in ((-3.2, 28.2, 6.4, 2), (-2.2, 31.4, 6.9, 2), (-3.8, 34.2, 6.3, 1), (3.0, 28.8, 6.4, 2),
                       (3.9, 32.0, 6.9, 2), (2.6, 35.2, 6.3, 1)):
        parts.append(Part(B, [x - s / 2, y, z], [s, s, 0], paint=lambda v, l, m: two(v, bubble)))
    return parts


# ---- Rootvale: a kite shield made of leaves, with leaves drifting off it ----------------------------------------------------

def kite(w, h, cap=0.2, power=0.9):
    m = np.zeros((h, w), bool)
    c = (w - 1) / 2
    for i in range(h):
        t = (i + 0.5) / h
        hw = w / 2 * math.sqrt(max(0.0, 1 - ((cap - t) / cap) ** 2)) if t < cap else w / 2 * (1 - (t - cap) / (1 - cap)) ** power
        m[i, int(round(c - hw + 0.4)):int(round(c + hw - 0.4)) + 1] = True
    return m


def serrate(m, period=4):
    """Notch the edge of a leaf mask with tiny teeth."""
    out = m.copy()
    h = m.shape[0]
    ys, xs = np.nonzero(m & ~erode(m))
    for y, x in zip(ys, xs):
        if 0.2 * h < y < 0.86 * h and y % period == 1:
            out[y, x] = False
    return out


def leaf_art(a, rp, glow, down=False):
    """A pointed, serrated, veined leaf: dark half, lit half, a bright streak, a midrib, a glowing tip."""
    h, w = a.shape[:2]
    t = np.zeros_like(a)
    m = serrate(leaf(w, h, bulge=0.5, wide=0.5))
    c = w // 2
    left = m.copy(); left[:, c:] = False
    right = m & ~left
    put(t, left, rp[2]); put(t, right, rp[3])
    streak = right.copy(); streak[:, :c + 1] = False; streak[:, c + 3:] = False
    put(t, streak, rp[4])
    low = m.copy(); low[:int(h * 0.80)] = False
    put(t, low, rp[1])
    for yy in range(6, h - 4, 4):                                                   # side veins, rising toward the tip
        for i in range(1, 4):
            for sg in (-1, 1):
                x, y = c + sg * i, yy - i
                if 0 <= x < w and 0 <= y < h and m[y, x]:
                    t[y, x] = (*rp[1 if sg < 0 else 2], 255)
    for yy in range(3, h - 1):
        t[yy, c] = (*rp[5], 255)                                                    # the midrib
    put(t, m & ~erode(m), rp[0])
    for yy in range(0, 3):
        for xx in range(w):
            if m[yy, xx]:
                t[yy, xx] = (*glow[4], 255)
    a[:] = np.flipud(t) if down else t


def root(look):
    bark, leather, glow = look.metal, look.trim, look.glow
    deepg = ramp((34, 58, 28))
    row_cols = [(172, 216, 98), (142, 196, 82), (114, 172, 68), (90, 142, 56), (68, 114, 46), (54, 94, 38)]   # sunlit top -> shaded point
    rps = [ramp(c) for c in row_cols]
    parts = []
    KW, KH = 12, 16

    # the backing: a stepped slab (solid from the side) behind a smooth kite plane (the real outline from behind)
    def slab(a, n):
        shade(a, full(a), deepg if n == "back" else bark, "v", lo=1.2 if n == "back" else 1.6, hi=3.0 if n == "back" else 4.2)
    for ytop, hh, w in ((23, 2, 10), (21, 2, 8), (19, 2, 7), (17, 2, 5), (15, 2, 3), (13, 6, 1)):
        parts.append(Part(B, [-w / 2, ytop - hh, 3.2], [w, hh, 1.2], paint=lambda v, l, m: sides(v, slab, bark)))

    def well(a):
        h, w = a.shape[:2]
        m = kite(w, h)
        shade(a, m, deepg, "v", lo=1.0, hi=2.6, edge=False)
        ys, xs = np.nonzero(m)
        for y, x in zip(ys, xs):
            if (x + y) % 6 == 0:
                a[y, x] = (*deepg[0], 255)                                           # a woven under-layer
        put(a, m & ~erode(m), bark[1])
    parts.append(Part(B, [-KW / 2, 7.3, 4.5], [KW, KH, 0], paint=lambda v, l, m: two(v, well)))

    # six rows of leaves, tip-down, each row proud of the one below and all tilted alike; they shade from sunlit to deep
    rows = [(22.4, (-3.6, -1.8, 0.0, 1.8, 3.6), 4), (20.4, (-2.7, -0.9, 0.9, 2.7), 4), (18.4, (-1.8, 0.0, 1.8), 4),
            (16.4, (-0.9, 0.9), 4), (14.4, (0.0,), 4), (12.4, (0.0,), 3)]
    for ri, (yt, xs, w) in enumerate(rows):
        z = 4.8 + 0.2 * (5 - ri)
        for x in xs:
            parts.append(Part(B, [x - w / 2, yt - 5.0, z], [w, 5, 0], rot=(14, 0, 0), pivot=[x, yt, z],
                              paint=lambda v, l, m, rp=rps[ri]: two(v, lambda a: leaf_art(a, rp, glow, True))))

    # the frame: a smooth kite ring of bark with a cross, hollow so the leaves show through
    def frame(a):
        h, w = a.shape[:2]
        m = kite(w, h)
        inner = m
        for _ in range(5):
            inner = erode(inner)
        a[:] = 0
        ring = m & ~inner
        shade(a, ring, bark, "v", lo=2.0, hi=4.8)
        c = w // 2
        sp = np.zeros((h, w), bool); sp[:, c - 2:c + 2] = True
        hy = int(h * 0.36)
        hb = np.zeros((h, w), bool); hb[hy - 2:hy + 2, :] = True
        shade(a, sp & m, bark, "h", lo=2.0, hi=4.8)
        shade(a, hb & m, bark, "v", lo=2.0, hi=4.8)
        e1 = erode(m) & ~erode(erode(m))
        ys, xs = np.nonzero(e1)
        for y, x in zip(ys, xs):
            if (x * 3 + y * 2) % 11 == 0:
                a[y, x] = (*leather[5], 255); a[min(h - 1, y + 1), x] = (*leather[2], 255)   # rivets round the rim
        for x, y in ((c, hy), (c, 5), (5, hy), (w - 6, hy)):
            for dx, dy in ((0, 0), (1, 0), (0, 1)):
                a[y + dy, x + dx] = (*leather[5 if dx + dy == 0 else 3], 255)
    parts.append(Part(B, [-KW / 2, 7.3, 6.9], [KW, KH, 0], paint=lambda v, l, m: two(v, frame)))
    def post(a, n):
        shade(a, full(a), bark, "v", lo=1.6, hi=4.6)
    for px_, py_ in ((-5.5, 20.5), (4.5, 20.5), (-4.0, 16.8), (3.0, 16.8), (-0.5, 9.0)):          # bark posts tie the frame to the backing
        parts.append(Part(B, [px_, py_, 4.6], [1, 1, 3], paint=lambda v, l, m: sides(v, post, bark)))

    def boss(a):
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        c = (w - 1) / 2
        d = np.hypot(xx - c, yy - c)
        disc = d <= w / 2
        a[:] = 0
        shade(a, disc, bark, "v", lo=2.0, hi=5.0)
        put(a, d <= w / 2 - 2.2, deepg[1])
        for k in range(6):                                                           # six petals round the seed
            ang = math.radians(k * 60 + 15)
            tip = (c + 7.2 * math.cos(ang), c + 7.2 * math.sin(ang))
            s1 = (c + 3.4 * math.cos(ang + 0.6), c + 3.4 * math.sin(ang + 0.6))
            s2 = (c + 3.4 * math.cos(ang - 0.6), c + 3.4 * math.sin(ang - 0.6))
            pm = poly_mask(w, h, [(c, c), s1, tip, s2]) & (d <= w / 2 - 2.2)
            put(a, pm, rps[k % 3][3 if k % 2 else 4])
        shade(a, d <= 2.7, glow, "v", lo=2.4, hi=5.2)
    parts.append(Part(B, [-2.5, 15.0, 7.3], [5, 5, 0], paint=lambda v, l, m: two(v, boss)))
    return parts
