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
    """A sleek kite shield that grows from a seed at its centre. Its top corners sit at the shoulders (arm tops are y 25).

    A slim bark RIM (one-unit beams, a lit lime inlay down each) holds a smooth, gently ridged FACE; the face is painted as rings of
    petals radiating from the seed, each ring overlapping the one outside it, the petal tips glowing. Real leaves start under the seed
    disc and lift off the face, fanning out and over the rim. Zero-thickness planes carry the painted art; every size is an integer."""
    bark, leather, glow = look.metal, look.trim, look.glow
    deepg = ramp((30, 52, 26))
    TOP, TIP, SHOULDER = 24.8, 2.8, 20.4
    CY = 17.0                                    # the seed
    ANG = 10.0                                   # each half of the face tilts back this far: a gentle ridge down the middle
    ZF = 6.0                                     # the ridge's depth
    rps = [ramp(c) for c in ((196, 238, 118), (166, 218, 100), (136, 194, 84), (108, 166, 70), (84, 138, 58), (64, 112, 48), (48, 90, 40))]
    parts = []

    # behind the face: a stepped slab (solid from the side) and a dark kite plane so nothing shows daylight
    def slab(a, n):
        shade(a, full(a), deepg if n == "back" else bark, "v", lo=1.2 if n == "back" else 1.6, hi=3.0 if n == "back" else 4.2)
    for y0, hh in ((1, 3), (4, 4), (8, 4), (12, 4), (16, 4), (20, 2)):
        w = max(1, round(2 * min(poly_hw(TOP - y0), poly_hw(TOP - y0 - hh + 0.01)) * 0.85))
        parts.append(Part(B, [-w / 2, TOP - y0 - hh, 3.2], [w, hh, 1], paint=lambda v, l, m: sides(v, slab, bark)))

    def well(a):
        h, w = a.shape[:2]
        a[:] = 0
        for py in range(h):
            for px in range(w):
                if abs((px + 0.5) / 4 - w / 8) <= poly_hw(TOP - (py + 0.5) / 4) - 0.1:
                    a[py, px] = (*deepg[1 if (px + py) % 5 else 0], 255)
    parts.append(Part(B, [-9, TOP - 22, 4.3], [18, 22, 0], paint=lambda v, l, m: two(v, well)))

    # the face: rings of petals radiating from the seed, outer rings painted first so each inner ring overlaps the one outside it
    RINGS = [(3.0, 7.4, 8, 0.0), (6.0, 10.6, 12, 0.5), (9.0, 13.8, 16, 0.0), (11.8, 16.6, 22, 0.5)]    # r0, r1, petals, angular offset
    ring_cols = [((158, 214, 96), (136, 196, 84)), ((124, 184, 78), (104, 164, 68)), ((96, 154, 64), (80, 134, 56)), ((72, 124, 52), (60, 106, 46))]
    ring_rps = [[ramp(c) for c in pair] for pair in ring_cols]
    def face_paint(a, x_end):
        h, w = a.shape[:2]
        a[:] = 0
        pid = np.full((h, w), -1, np.int32)
        col = np.zeros((h, w, 4), np.uint8)
        for py in range(h):
            y = TOP - (py + 0.5) / 4
            for px in range(w):
                x = x_end - (px + 0.5) / 4
                if abs(x) > poly_hw(y) - 0.1:
                    continue
                col[py, px] = (*deepg[2 if (px + py) % 4 else 1], 255)                      # the bare face between petals
                r = math.hypot(x, y - CY)
                th = math.atan2(x, y - CY)
                for ri in range(len(RINGS) - 1, -1, -1):                                    # outer ring first
                    r0, r1, n, off = RINGS[ri]
                    if r < r0 - 0.4 or r > r1 + 0.9:
                        continue
                    step = 2 * math.pi / n
                    k = int(math.floor(th / step + 0.5 - off))
                    phi = th - (k + off) * step
                    u = (r - r0) / (r1 - r0)
                    half = 0.5 * step * (r0 + r1) / 2 * 1.0 * max(0.0, math.sin(math.pi * min(1.0, max(0.0, u)) ** 0.75)) ** 0.8
                    if abs(phi) * r <= half and 0.0 <= u <= 1.05:
                        rp = ring_rps[ri][k % 2]
                        c = rp[2 + (1 if phi < 0 else 0) + (1 if u > 0.55 else 0)]                # lit on one side, brighter toward the tip
                        if abs(phi) * r < 0.5 and 0.1 < u < 0.97:
                            c = rp[5]                                                          # the midrib
                        if u > 0.9 and abs(phi) * r < 0.65:
                            c = glow[4]                                                        # a glowing tip
                        col[py, px] = (*c, 255)
                        pid[py, px] = ri * 100 + (k % n)
                        break
        a[:] = col
        for py in range(h):                                                                    # outline every petal where its neighbour differs
            for px in range(w):
                if pid[py, px] < 0:
                    continue
                for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                    qy, qx = py + dy, px + dx
                    if 0 <= qy < h and 0 <= qx < w and pid[qy, qx] != pid[py, px] and col[py, px, 3] and not (a[py, px, :3] == np.array(glow[4])).all():
                        a[py, px] = (*ring_rps[3][1][0], 255)
                        break
    for sgn, x0 in ((1, 0), (-1, -9)):
        parts.append(Part(B, [x0, TOP - 22, ZF], [9, 22, 0], rot=(0, sgn * ANG, 0), pivot=[0, TOP - 11, ZF],
                          paint=lambda v, l, m, xe=float(x0 + 9): two(v, lambda a: face_paint(a, xe))))

    # the rim: slim bark beams, a thin lit inlay down each; whole boxes rotated about their centres to lie on the outline
    def beam_paint(a, n):
        h, w = a.shape[:2]
        shade(a, full(a), bark, "h" if h > w else "v", lo=1.4, hi=4.2)
        if n in ("front", "back") and h > w:
            a[2:h - 2, w // 2] = (*glow[3], 255)                                              # the lit inlay
    def beam(cx, cy, length, thick, ang, dz=0.0):
        return Part(B, [cx - thick / 2, cy - length / 2, 4.8 + dz], [thick, length, 3], rot=(0, 0, ang), pivot=[cx, cy, 6.3 + dz],
                    paint=lambda v, l, m: sides(v, beam_paint, bark))
    tx, ty, bx, by = 8.5, SHOULDER + 0.2, 0.6, TIP + 0.4
    d_ang = math.degrees(math.atan2(tx - bx, ty - by))
    d_len = round(math.hypot(tx - bx, ty - by))
    parts += [beam((tx + bx) / 2, (ty + by) / 2, d_len, 1, d_ang), beam(-(tx + bx) / 2, (ty + by) / 2, d_len, 1, -d_ang, 0.1)]
    parts += [beam(0, TOP - 0.5, 12, 1, 90.0)]
    s_ang = math.degrees(math.atan2(3.0, TOP - 0.7 - SHOULDER))
    parts += [beam(7.5, 22.3, 6, 1, -s_ang), beam(-7.5, 22.3, 6, 1, s_ang, 0.1)]

    # leaves that grow out of the seed: each starts under the seed disc and stands up off the flat front face as 3D relief. They stay
    # inside the outline (never past the sides) and their tips stand well in front of the rim; the length is measured from the real outline.
    def outline_dist(theta):
        sn, cs = math.sin(math.radians(theta)), math.cos(math.radians(theta))
        t = 0.0
        while t < 22.0:
            if abs(t * sn) > poly_hw(CY + t * cs) - 0.2:
                return t
            t += 0.05
        return 22.0
    BASE_R = 2.7
    def sprout(theta, reach, lift, k, rp, cap=99):
        sn, cs = math.sin(math.radians(theta)), math.cos(math.radians(theta))
        bxp, byp = BASE_R * sn, CY + BASE_R * cs
        zb = ZF - abs(bxp) * math.sin(math.radians(ANG)) + 0.55
        length = min(cap, max(3, round((outline_dist(theta) + reach - BASE_R) / math.cos(math.radians(lift)))))
        width = 3 if length < 6 else 4
        return Part(B, [bxp - width / 2, byp, zb], [width, length, 0], rot=(-lift, 0, theta), pivot=[bxp, byp, zb], group=f"sprout_{k}",
                    gpivot=[bxp, byp, zb], paint=lambda v, l, m: two(v, lambda a: leaf_art(a, rp, glow, False)))
    k = 0
    for theta in (0, 50, -50, 100, -100, 150, -150):                                                # each ends 0.6 inside the outline
        parts.append(sprout(theta, -0.6, 48.0, k, ring_rps[k % 2][0], cap=11))
        k += 1

    # the seed: a bark disc with a lime ring over the leaf bases, and a faceted gem
    def disc(a):
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        c = (w - 1) / 2
        d = np.hypot(xx - c, yy - c)
        a[:] = 0
        shade(a, d <= w / 2, bark, "v", lo=1.8, hi=5.0)
        put(a, (d <= w / 2 - 1.5) & (d > w / 2 - 3.0), glow[3])
        put(a, d <= w / 2 - 3.2, rps[6][3])
    parts.append(Part(B, [-3, CY - 3, ZF + 0.9], [6, 6, 0], paint=lambda v, l, m: two(v, disc)))

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
    parts.append(Part(B, [-1.5, CY - 1.5, ZF + 0.95], [3, 3, 1], group="gem", gpivot=[0, CY, ZF + 1.4], paint=gem))
    return parts
