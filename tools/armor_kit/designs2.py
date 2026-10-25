"""Tideforge dive rig (twin banded tanks, bubbles) and the Rootvale leaf kite shield."""
import math
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *
from designs import B, clear, two, sides, side_one
from armor_art import shingle


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
        side_name = "l" if sgn < 0 else "r"
        parts.append(Part(B, [cx - 2.0, 11.8, 4.0], [4, 11, 4], group=f"tank_{side_name}", gpivot=[cx, 17.3, 6.0],
                          paint=lambda v, l, m: sides(v, tank, enamel)))
        parts.append(Part(B, [cx - 1.5, 22.8, 4.5], [3, 1, 3], group=f"tank_{side_name}", gpivot=[cx, 17.3, 6.0],
                          paint=lambda v, l, m: sides(v, lambda a, n: cyl(a, steel, *LIT[n]), steel)))
        parts.append(Part(B, [cx - 1.0, 23.8, 5.0], [2, 1, 2], group=f"tank_{side_name}", gpivot=[cx, 17.3, 6.0],
                          paint=lambda v, l, m: sides(v, lambda a, n: cyl(a, dark, *LIT[n]), dark)))
        parts.append(Part(B, [cx - 1.5, 24.8, 5.2], [3, 1, 1], group=f"wheel_{side_name}", gpivot=[cx, 25.3, 5.7],
                          paint=lambda v, l, m: sides(v, lambda a, n: cyl(a, cyan, *LIT[n]), cyan)))
        parts.append(Part(B, [cx - 2.5, 9.9, 3.5], [5, 2, 5], group=f"tank_{side_name}", gpivot=[cx, 17.3, 6.0],
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
    parts.append(Part(B, [-1.5, 17.6, 9.0], [3, 3, 1], group="gauge", gpivot=[0, 19.1, 9.5], paint=gauge))

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
        parts.append(Part(B, [x - s / 2, y, z], [s, s, 0], group=f"bubble_{len(parts)}", gpivot=[x, y + s / 2, z],
                          paint=lambda v, l, m: two(v, bubble)))
    # a mane of leaves rising behind the neck from the top edge
    for k, (mx, ang, mh, ci) in enumerate(((-6, 30, 7, 2), (-3, 14, 8, 1), (0, 0, 9, 0), (3, -14, 8, 1), (6, -30, 7, 2))):
        parts.append(Part(B, [mx - 2.5, TOP - 1.0, 5.6], [5, mh, 0], rot=(0, 0, ang), pivot=[mx, TOP - 1.0, 5.6], group=f"mane_{k}",
                          gpivot=[mx, TOP - 1.0, 5.6], paint=lambda v, l, m, rp=rps[ci]: two(v, lambda a: leaf_art(a, rp, glow, False))))
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


def kite_hw(t, cap=0.2, power=0.9):
    """Half the kite's width (as a fraction of its full width) at fraction t of the way down."""
    if t < cap:
        return 0.5 * math.sqrt(max(0.0, 1 - ((cap - t) / cap) ** 2))
    return 0.5 * (1 - (t - cap) / (1 - cap)) ** power


def poly_hw(y, top=24.8, shoulder_y=20.4, tip_y=2.8, top_hw=6.0, shoulder_hw=9.0):
    """Half-width of the shield outline at height y: a flat top, steep shoulders, straight sides to the point."""
    if y > top or y < tip_y:
        return -1.0
    if y >= shoulder_y:
        return top_hw + (shoulder_hw - top_hw) * (top - y) / (top - shoulder_y)
    return shoulder_hw * (y - tip_y) / (shoulder_y - tip_y)


def root(look):
    """A chunky, real-3D kite shield across the back. Its top corners sit at the shoulders (the arm tops are at y 25).

    Real geometry, not painted depth: bark RIM beams (two long diagonals, a flat top, two angled shoulders) give the outline
    thickness; the face is seven shingle rows, each split at the centre into two halves tilted back, so the face is a ridge; raised
    bark cross beams follow the ridge; and the boss is a stack (an octagon from a box and the same box turned 45 degrees, a petal
    dish, a faceted gem). Zero-thickness planes carry the painted leaves; every size is an integer."""
    bark, leather, glow = look.metal, look.trim, look.glow
    deepg = ramp((34, 58, 28))
    TOP, TIP, SHOULDER = 24.8, 2.8, 20.4
    W = 18
    ROWS, PITCH, LH = 7, 2.6, 6
    ANG = 16.0                                   # each half of a row tilts back this far: the face is a ridge down the middle
    LEAF_W, STEP = 16, 8                         # px: a leaf is 16 wide and one is set every 8 (units: every 2)
    row_cols = [(178, 222, 102), (154, 206, 88), (128, 184, 74), (104, 158, 62), (84, 132, 52), (68, 112, 44), (54, 94, 38)]
    rps = [ramp(c) for c in row_cols]            # sunlit at the top, deep green at the point
    zs = [6.0 + 0.2 * (ROWS - 1 - i) for i in range(ROWS)]            # far enough forward that a tilted half never dips behind the backing
    parts = []

    # the backing: a stepped slab (solid from the side) and a dark kite plane so no row ever shows daylight
    def slab(a, n):
        shade(a, full(a), deepg if n == "back" else bark, "v", lo=1.2 if n == "back" else 1.6, hi=3.0 if n == "back" else 4.2)
    for y0, hh in ((1, 3), (4, 4), (8, 4), (12, 4), (16, 4), (20, 2)):
        w = max(1, round(2 * min(poly_hw(TOP - y0, ), poly_hw(TOP - y0 - hh + 0.01)) * 0.85))
        if w >= 1:
            parts.append(Part(B, [-w / 2, TOP - y0 - hh, 3.2], [w, hh, 1], paint=lambda v, l, m: sides(v, slab, bark)))

    def well(a):
        h, w = a.shape[:2]
        a[:] = 0
        for py in range(h):
            for px in range(w):
                if abs((px + 0.5) / 4 - w / 8) <= poly_hw(TOP - (py + 0.5) / 4) - 0.1:
                    a[py, px] = (*deepg[1 if (px + py) % 5 else 0], 255)
    parts.append(Part(B, [-W / 2, TOP - 22, 4.3], [W, 22, 0], paint=lambda v, l, m: two(v, well)))

    # the face: seven shingle rows, each two half-planes hinged at the centre and tilted back (a ridge), clipped to the outline
    def half_paint(a, i, top, x_end):                  # x_end: the plane's +x edge; a south face's u runs toward -x
        h, w = a.shape[:2]
        a[:] = 0
        span = 2 * (poly_hw(TOP - 0.5) + 1)
        n = int(span / (STEP / 4)) + 3
        for k in range(n):
            xk = (k - (n - 1) / 2) * (STEP / 4)                                          # leaf centres in units from the centre line
            rp = rps[min(ROWS - 1, max(0, i - (1 if (k + i) % 3 == 0 else 0)))]
            tile = np.zeros((h, LEAF_W, 4), np.uint8)
            leaf_art(tile, rp, glow, True)
            if (k + i) % 2:
                tile = np.ascontiguousarray(tile[:, ::-1])
            paste(a, tile, int(round((x_end - xk) * 4)) - LEAF_W // 2, 0)
        for py in range(h):
            yy = top - (py + 0.5) / 4
            for px in range(w):
                if abs(x_end - (px + 0.5) / 4) > poly_hw(yy) - 0.1:
                    a[py, px] = 0

    for i in range(ROWS):
        top = TOP - 0.4 - PITCH * i
        hw = max(poly_hw(top - LH * f) for f in (0, 0.25, 0.5, 0.75, 1.0))
        wr = int(math.ceil(hw)) + 1
        z = zs[i]
        right = Part(B, [0, top - LH, z], [wr, LH, 0], rot=(0, ANG, 0), pivot=[0, top - LH / 2, z], group=f"row_{i}", gpivot=[0, top, z],
                     paint=lambda v, l, m, i=i, top=top, wr=wr: two(v, lambda a: half_paint(a, i, top, float(wr))))
        left = Part(B, [-wr, top - LH, z], [wr, LH, 0], rot=(0, -ANG, 0), pivot=[0, top - LH / 2, z], group=f"row_{i}_m", gpivot=[0, top, z],
                    paint=lambda v, l, m, i=i, top=top, wr=wr: two(v, lambda a: half_paint(a, i, top, 0.0)))
        parts += [right, left]

    # the rim: bark beams. Each is a whole box (real thickness and depth), rotated about its centre to lie along the outline.
    def beam_paint(a, n):
        h, w = a.shape[:2]
        shade(a, full(a), bark, "h" if h > w else "v", lo=1.6, hi=4.8)
        if h > w:
            for yy in range(5, h - 3, 14):
                a[yy, w // 2] = (*leather[5], 255); a[min(h - 1, yy + 1), w // 2] = (*leather[3], 255)       # rivets down the beam
            for yy in range(2, h, 4):
                a[yy, 1:w - 1] = np.where(a[yy, 1:w - 1, 3:4] > 0, np.array((*bark[1], 255), np.uint8), a[yy, 1:w - 1])
    def beam(cx, cy, length, thick, ang, dz=0.0):
        return Part(B, [cx - thick / 2, cy - length / 2, 3.5 + dz], [thick, length, 4], rot=(0, 0, ang), pivot=[cx, cy, 5.5 + dz],
                    paint=lambda v, l, m: sides(v, beam_paint, bark))
    tx, ty, bx, by = 8.5, SHOULDER + 0.2, 0.6, TIP + 0.4                                    # from the shoulder to the point
    d_ang = math.degrees(math.atan2(tx - bx, ty - by))
    d_len = round(math.hypot(tx - bx, ty - by))
    parts += [beam((tx + bx) / 2, (ty + by) / 2, d_len, 2, d_ang), beam(-(tx + bx) / 2, (ty + by) / 2, d_len, 2, -d_ang, 0.15)]   # the two long sides
    parts += [beam(0, TOP - 1.0, 12, 2, 90.0)]                                              # the flat top (a beam turned on its side)
    s_ang = math.degrees(math.atan2(3.0, TOP - 0.7 - SHOULDER))
    parts += [beam(7.5, 22.3, 6, 2, -s_ang), beam(-7.5, 22.3, 6, 2, s_ang, 0.1)]            # the shoulders

    # the raised cross: a spine along the ridge and two arms that follow each half's tilt
    cross_y = 17.0
    parts.append(Part(B, [-0.5, 3.6, 6.95], [1, 19, 1], paint=lambda v, l, m: sides(v, beam_paint, bark)))
    for sgn in (1, -1):
        arm = Part(B, [0 if sgn > 0 else -7, cross_y - 0.5, 6.75], [7, 1, 1], rot=(0, sgn * ANG, 0), pivot=[0, cross_y, 7.25],
                   paint=lambda v, l, m: sides(v, beam_paint, bark))
        parts.append(arm)

    # the boss: an octagon (a box and the same box turned 45 degrees), a petal dish, a faceted gem
    def disc(v, l, m):
        for k in ("front", "left", "right", "top", "bottom"):
            shade(v[k], full(v[k]), bark, "v", lo=1.4, hi=4.2)
        a = v["back"]
        shade(a, full(a), bark, "v", lo=1.8, hi=5.0)
        h, w = a.shape[:2]
        for i_ in range(2, h - 2, 3):
            a[i_, 2:w - 2] = (*bark[2], 255)
    parts.append(Part(B, [-3, cross_y - 3, 6.95], [6, 6, 1], paint=disc))
    parts.append(Part(B, [-3, cross_y - 3, 7.0], [6, 6, 1], rot=(0, 0, 45.0), pivot=[0, cross_y, 7.5], paint=disc))

    def dish(a):
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        c = (w - 1) / 2
        d = np.hypot(xx - c, yy - c)
        a[:] = 0
        shade(a, d <= w / 2, bark, "v", lo=2.0, hi=5.0)
        inner = d <= w / 2 - 2.0
        shade(a, inner, rps[5], "v", lo=1.6, hi=3.4, edge=False)
        for k in range(6):
            ang = math.radians(k * 60 + 15)
            tip = (c + 7.0 * math.cos(ang), c + 7.0 * math.sin(ang))
            s1 = (c + 2.8 * math.cos(ang + 0.7), c + 2.8 * math.sin(ang + 0.7))
            s2 = (c + 2.8 * math.cos(ang - 0.7), c + 2.8 * math.sin(ang - 0.7))
            put(a, poly_mask(w, h, [(c, c), s1, tip, s2]) & inner, rps[k % 3][3 if k % 2 else 4])
    parts.append(Part(B, [-2.5, cross_y - 2.5, 8.1], [5, 5, 0], paint=lambda v, l, m: two(v, dish)))

    def gem(v, l, m):
        for k in ("front", "left", "right", "top", "bottom"):
            shade(v[k], full(v[k]), glow, "v", lo=1.4, hi=3.4)
        a = v["back"]
        h, w = a.shape[:2]
        c = (w - 1) / 2
        a[:] = 0
        shade(a, np.ones((h, w), bool), glow, "v", lo=2.4, hi=4.2)
        put(a, poly_mask(w, h, [(0, 0), (w - 1, 0), (c, c)]), glow[5])
        put(a, poly_mask(w, h, [(0, 0), (c, c), (0, h - 1)]), glow[4])
        put(a, poly_mask(w, h, [(w - 1, 0), (c, c), (w - 1, h - 1)]), glow[3])
        put(a, poly_mask(w, h, [(0, h - 1), (c, c), (w - 1, h - 1)]), glow[2])
    parts.append(Part(B, [-1.5, cross_y - 1.5, 8.15], [3, 3, 1], group="gem", gpivot=[0, cross_y, 8.65], paint=gem))
    return parts
