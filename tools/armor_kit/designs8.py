"""Duskvale wings and the Rootvale leaf shield, built to the Paragon wing standard: hundreds of real planes in overlapping rows,
each painted with its own shaded barbs or veins, bowed off the back so the silhouette has depth from every angle."""
import math
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *
from designs import two, sides
from designs2 import poly_hw, serrate
from designs3 import with_mirror
from wingart import feather
import generate_armor_textures as gt

B = "armorBody"


def _look(name):
    return gt.Look(name, gt.SPECS[name])


def _along(poly, s):
    seg = [math.dist(poly[i], poly[i + 1]) for i in range(len(poly) - 1)]
    d = s * sum(seg)
    for i, L in enumerate(seg):
        if d <= L or i == len(seg) - 1:
            t = min(1.0, d / L)
            return (poly[i][0] + (poly[i + 1][0] - poly[i][0]) * t, poly[i][1] + (poly[i + 1][1] - poly[i][1]) * t)
        d -= L


# ---- Duskvale: night wings ------------------------------------------------------------------------------------------------

DUSK_ROWS = [  # base colour, tip colour: the back row is deep indigo and every row in front of it is lighter and pinker
    ((30, 18, 66), (92, 60, 160)), ((44, 28, 90), (116, 80, 188)), ((62, 42, 120), (148, 104, 214)),
    ((90, 62, 160), (196, 126, 214)), ((120, 88, 192), (240, 160, 236))]


def dusk_feather(a, base, tip, seed, glow, spark):
    """One feather: shaded barbs that run from a deep base colour to a pale tip colour (dithered between), a glinting point."""
    h, w = a.shape[:2]
    A, Bt = np.zeros_like(a), np.zeros_like(a)
    for img, rp in ((A, base), (Bt, tip)):
        feather(img, (w - 1) / 2, h - 1.0, -math.pi / 2, h - 2.0, w - 0.5, rp, np.random.default_rng(seed))
    t = 1.0 - np.repeat((np.arange(h) / max(1, h - 1))[:, None], w, 1)            # 0 at the base, 1 at the tip
    use_tip = bayer(h, w) < np.clip((t - 0.30) / 0.55, 0, 1)
    a[:] = np.where(use_tip[..., None], Bt, A)
    a[..., 3] = A[..., 3]
    live = a[..., 3] > 0
    a[:3][live[:3]] = (*glow[4], 255)                                              # the point catches the light
    if spark:
        ys, xs = np.nonzero(live[: int(h * 0.30)])
        for y, x in list(zip(ys, xs))[::5]:
            a[y, x] = (*glow[5], 255)


def dusk_wings(look):
    look = _look("duskvale")
    glow = look.glow
    metal, trim = look.metal, look.trim
    parts = []
    z0 = 3.0

    # the bars of the wing: dark violet metal with a lit pink inlay
    def bar_paint(v, l, m):
        for n in ("front", "back", "left", "right"):
            h, w = v[n].shape[:2]
            shade(v[n], np.ones((h, w), bool), metal, "h" if w > h else "v", lo=1.8, hi=4.6)
            if n == "back" and h > w:
                v[n][2:h - 2, w // 2] = (*trim[3], 255)
        for n in ("top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), metal, "none", edge=True)

    def gem_paint(v, l, m):
        for n in ("front", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), metal, "v", lo=1.6, hi=4.0)
        a = v["back"]
        h, w = a.shape[:2]
        c = (w - 1) / 2
        a[:] = 0
        shade(a, np.ones((h, w), bool), glow, "v", lo=2.0, hi=4.2, edge=False)
        put(a, poly_mask(w, h, [(0, 0), (w - 1, 0), (c, c)]), glow[5])
        put(a, poly_mask(w, h, [(0, 0), (c, c), (0, h - 1)]), glow[4])
        put(a, poly_mask(w, h, [(0, h - 1), (c, c), (w - 1, h - 1)]), glow[2])

    ARM = {"upper": [(4.0, 23.0), (10.5, 34.5), (21.5, 42.0), (33.0, 41.0)],
           "lower": [(4.0, 19.5), (10.0, 10.5), (17.0, 4.0), (23.0, -1.5)]}
    ROWS = {  # feathers, arm span, fan from, fan to (radians below the wing axis), longest, lift (deg), z, width
        "upper": [(20, (0.26, 1.00), 1.05, 0.02, 34, 7.0, 3.00, 6), (17, (0.22, 0.96), 1.12, 0.07, 28, 12.0, 3.40, 6),
                  (14, (0.17, 0.92), 1.20, 0.13, 21, 17.0, 3.80, 5), (12, (0.12, 0.88), 1.28, 0.20, 14, 23.0, 4.20, 5),
                  (10, (0.06, 0.82), 1.36, 0.28, 9, 30.0, 4.60, 4)],
        "lower": [(14, (0.28, 1.00), 1.00, 0.04, 26, 7.0, 3.35, 6), (12, (0.22, 0.95), 1.10, 0.10, 20, 12.0, 3.75, 6),
                  (10, (0.16, 0.90), 1.18, 0.16, 14, 18.0, 4.15, 5), (8, (0.10, 0.84), 1.26, 0.24, 9, 25.0, 4.55, 4)],
    }
    for kind, poly in ARM.items():
        axis = math.atan2(poly[-1][1] - poly[0][1], poly[-1][0] - poly[0][0])
        wk = 0 if kind == "upper" else 1
        for ri, (n, (s0, s1), a0, a1, maxlen, lift, zr, w) in enumerate(ROWS[kind]):
            base, tip = (ramp(c) for c in DUSK_ROWS[ri])
            for i in range(n):
                u = i / (n - 1)
                px, py = _along(poly, s0 + (s1 - s0) * u)
                fan = a0 + (a1 - a0) * u ** 0.85
                ang = axis - fan
                span = 0.42 + 0.58 * math.sin(math.pi * (0.12 + 0.8 * u))
                if ri < 2 and i % 3 == 0:
                    span = min(1.0, span * 1.22)                                    # long pointed fingers break the trailing edge
                ln = max(6, int(round(maxlen * span / 4.0)) * 4)
                psi = math.degrees(math.atan2(math.cos(ang), math.sin(ang)))
                tone = (i + ri) % 2
                spark = ri >= 3 and i % 2 == 0
                key = f"dfeather_{kind[0]}{ri}_{w}x{ln}_{tone}_{int(spark)}"
                z = zr + 0.05 * (i % 3)
                bt = (base, tip) if tone == 0 else (tip_shift(base), tip_shift(tip))
                parts += with_mirror(Part(B, [px - w / 2, py, z], [w, ln, 0.3], rot=(-lift - 2 * (i % 3), 0, psi), pivot=[px, py, z + 0.15],
                                          group=f"wing_{wk * 5 + ri}", gpivot=[poly[0][0], poly[0][1], z0],
                                          paint=feather_painter(bt, i + 31 * ri, glow, spark), share=key))
        for si in range(len(poly) - 1):                                              # a bar per segment, a gem at each joint, over every row
            (ax, ay), (bx, by) = poly[si], poly[si + 1]
            ln = max(2, int(round(math.dist((ax, ay), (bx, by)))))
            psi = math.degrees(math.atan2(bx - ax, by - ay))
            parts += with_mirror(Part(B, [ax - 1, ay, 5.0], [2, ln, 2], rot=(0, 0, psi), pivot=[ax, ay, 6.0], group=f"wing_{wk * 5}",
                                      gpivot=[poly[0][0], poly[0][1], z0], paint=bar_paint, share=f"dbar_{ln}"))
        for jx, jy in poly[:-1]:
            parts += with_mirror(Part(B, [jx - 1.5, jy - 1.5, 4.4], [3, 3, 3], group=f"wing_{wk * 5}", gpivot=[poly[0][0], poly[0][1], z0],
                                      paint=gem_paint, share="dgem"))
    # a claw at each upper wrist, curving up out of the arm
    wx, wy = ARM["upper"][2]
    parts += with_mirror(Part(B, [wx - 1, wy + 1, 5.0], [2, 7, 2], rot=(0, 0, -18), pivot=[wx, wy + 1, 6.0], group="wing_0",
                              gpivot=[ARM["upper"][0][0], ARM["upper"][0][1], z0], paint=bar_paint, share="dclaw"))

    # the shoulder mount: a bracket across the spine with a plate over each wing root
    def mount(v, l, m):
        for n in ("front", "back", "left", "right"):
            h, w = v[n].shape[:2]
            shade(v[n], np.ones((h, w), bool), metal, "v", lo=1.4, hi=4.2)
        for n in ("top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), metal, "none", edge=True)
    parts.append(Part(B, [-5, 21.0, 2.9], [10, 3, 2], paint=mount, share="dmount"))
    parts.append(Part(B, [-1.5, 18.0, 3.0], [3, 4, 2], paint=mount, share="dspine"))

    # the moon behind the head: a deep crescent under a bright one, so it has a thickness, with stars drifting in the opening
    def moon_paint(rp, rim, stars):
        def draw(a):
            h, w = a.shape[:2]
            m = crescent(w, w * 0.50, w * 0.40, w * 0.27, -w * 0.04)
            shade(a, m, rp, "h", lo=1.6 if not rim else 2.6, hi=3.4 if not rim else 5.0, edge=False, dith=0.9)
            if rim:
                put(a, m & ~erode(m), glow[4])
                put(a, erode(m) & ~erode(erode(m)), rp[4])
            if stars:
                rnd = np.random.default_rng(7)
                for _ in range(7):
                    x, y = int(rnd.integers(w // 2, w - 3)), int(rnd.integers(3, h - 3))
                    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                        if not m[y + dy, x + dx]:
                            a[y + dy, x + dx] = (*(glow[5] if dx == dy == 0 else glow[3]), 255)
        return lambda v, l, mi: two(v, draw)
    deep, pale = ramp((60, 36, 116)), ramp((214, 184, 255))
    parts.append(Part(B, [-8.5, 20.5, 5.0], [17, 17, 0.3], rot=(0, 0, 22.5), pivot=[0, 29.0, 5.0], group="moon", gpivot=[0, 29.0, 5.5],
                      paint=moon_paint(deep, False, False)))
    parts.append(Part(B, [-7.5, 21.5, 5.6], [15, 15, 0.3], rot=(0, 0, 22.5), pivot=[0, 29.0, 5.75], group="moon", gpivot=[0, 29.0, 5.5],
                      paint=moon_paint(pale, True, True)))
    return parts


def tip_shift(rp):
    """A slightly warmer twin of a ramp, so neighbouring feathers do not all read the same."""
    return [(min(255, int(r * 1.06) + 4), g, min(255, int(b * 1.05))) for r, g, b in rp]


def feather_painter(bt, seed, glow, spark):
    base, tip = bt
    return lambda v, l, m: two(v, lambda a: dusk_feather(a, base, tip, seed, glow, spark))


# ---- Rootvale: the heartwood shield ---------------------------------------------------------------------------------------

LEAF_RINGS = [  # base ramp colour pairs, outermost layer first (dark, low, long) to the innermost (bright, steep, short)
    ((34, 66, 36), (44, 82, 44)), ((58, 100, 44), (70, 118, 50)), ((84, 134, 58), (98, 150, 64)),
    ((116, 168, 76), (132, 186, 84)), ((158, 208, 100), (178, 228, 116))]


def paint_leaf(a, rp, glow, seed, tipglow=True):
    """A serrated leaf in one plane: base at the bottom edge, tip at the top; the right half lit, side veins rising to a bright midrib."""
    h, w = a.shape[:2]
    rng = np.random.default_rng(seed)
    a[:] = 0
    m = serrate(leaf(w, h, bulge=0.5, wide=0.5), period=3 + seed % 2)
    c = (w - 1) / 2
    yy, xx = np.mgrid[0:h, 0:w]
    t = 1 - yy / max(1, h - 1)                                                      # 0 at the stem, 1 at the tip
    side = np.where(xx > c, 0.8, -0.5) * np.clip(1 - np.abs(xx - c) / (w / 2 + 0.01) * 0.3, 0, 1)
    lvl = 1.7 + 2.7 * t + side + 0.25 * np.sin(yy * 0.9 + rng.random() * 5)
    idx = np.clip(np.floor(lvl + (bayer(h, w) - 0.5) * 0.7), 0, 5).astype(int)
    for i in range(6):
        put(a, m & (idx == i), rp[i])
    for y in range(int(h * 0.18), h - 3, 3):                                        # side veins, each rising toward the tip
        for k in range(1, int(w / 2) + 1):
            for sg in (-1, 1):
                x, yv = int(round(c + sg * k)), y - k
                if 0 <= x < w and 0 <= yv < h and m[yv, x]:
                    a[yv, x] = (*rp[1 if sg < 0 else 2], 255)
    for y in range(2, h - 1):
        x = int(c)
        if m[y, x]:
            a[y, x] = (*rp[5], 255)                                                  # the midrib
            if w >= 5 and m[y, x + 1]:
                a[y, x + 1] = (*rp[4], 255)
    put(a, m & ~erode(m), rp[0])
    put(a, erode(m) & ~erode(erode(m)) & (xx > c) & (yy < h * 0.7), rp[4])        # a light inner rim down the lit side
    if tipglow:
        for y in range(0, 3):
            for x in range(w):
                if m[y, x]:
                    a[y, x] = (*glow[4], 255)


def root_shield(look):
    look = _look("rootvale")
    glow, bark = look.glow, look.metal
    parts = []
    TOP, TIP, SHOULDER = 24.8, 2.8, 20.4
    CY, ZF = 16.5, 7.4
    deepg = ramp((26, 44, 24))
    rps = [(ramp(a), ramp(b)) for a, b in LEAF_RINGS]

    # the slab behind the face: stepped boxes with grain, built right up to the face so it is solid from the side
    def slab(v, l, m):
        def grain(a, n):
            h, w = a.shape[:2]
            shade(a, np.ones((h, w), bool), deepg if n == "back" else bark, "v", lo=1.2, hi=3.0 if n == "back" else 4.2)
            for x in range(1, w, 4):
                a[:, x][a[:, x, 3] > 0] = (*bark[1], 255)
        sides(v, grain, bark)
    for y0, hh in ((1, 3), (4, 4), (8, 4), (12, 4), (16, 4), (20, 2)):
        w = max(1, round(2 * min(poly_hw(TOP - y0), poly_hw(TOP - y0 - hh + 0.01)) * 0.85))
        parts.append(Part(B, [-w / 2, TOP - y0 - hh, 3.2], [w, hh, 4], paint=slab))

    # the face between the leaves: dark moss with specks
    def moss(a):
        h, w = a.shape[:2]
        a[:] = 0
        rng = np.random.default_rng(3)
        for py in range(h):
            y = TOP - (py + 0.5) / 4
            for px in range(w):
                x = 9.0 - (px + 0.5) / 4
                if abs(x) <= poly_hw(y) - 0.1:
                    a[py, px] = (*deepg[1 + int(rng.random() < 0.28) + int(rng.random() < 0.08)], 255)
    parts.append(Part(B, [-9, TOP - 22, ZF], [18, 22, 0], paint=lambda v, l, m: two(v, moss)))

    # the rim: slim bark beams with a lit lime inlay, knots where they meet
    def beam_paint(a, n):
        h, w = a.shape[:2]
        shade(a, np.ones((h, w), bool), bark, "h" if h > w else "v", lo=1.4, hi=4.4)
        if n in ("front", "back") and h > w:
            a[2:h - 2, w // 2] = (*glow[3], 255)
            a[2:h - 2:6, w // 2] = (*glow[4], 255)

    def beam(cx, cy, length, thick, ang, dz=0.0):
        return Part(B, [cx - thick / 2, cy - length / 2, 4.8 + dz], [thick, length, 3], rot=(0, 0, ang), pivot=[cx, cy, 6.3 + dz],
                    paint=lambda v, l, m: sides(v, beam_paint, bark), share=f"rbeam_{length}x{thick}")
    tx, ty, bx, by = 8.5, SHOULDER + 0.2, 0.6, TIP + 0.4
    d_ang = math.degrees(math.atan2(tx - bx, ty - by))
    d_len = round(math.hypot(tx - bx, ty - by))
    parts += [beam((tx + bx) / 2, (ty + by) / 2, d_len, 2, d_ang), beam(-(tx + bx) / 2, (ty + by) / 2, d_len, 2, -d_ang, 0.1)]
    parts += [beam(0, TOP - 0.5, 14, 2, 90.0)]
    s_ang = math.degrees(math.atan2(3.0, TOP - 0.7 - SHOULDER))
    parts += [beam(7.5, 22.3, 6, 2, -s_ang), beam(-7.5, 22.3, 6, 2, s_ang, 0.1)]

    def knot(v, l, m):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), bark, "v", lo=1.6, hi=4.6)
        h, w = v["back"].shape[:2]
        put(v["back"], np.hypot(*(np.mgrid[0:h, 0:w] - np.array([(h - 1) / 2, (w - 1) / 2])[:, None, None])) <= w * 0.27, glow[3])
    for kx, ky in ((8.8, SHOULDER + 0.6), (-8.8, SHOULDER + 0.6), (6.4, TOP - 0.8), (-6.4, TOP - 0.8), (0.0, TIP + 0.8)):
        parts.append(Part(B, [kx - 1, ky - 1, 5.8], [2, 2, 2], paint=knot, share="rknot"))

    # the leaves: a layer of dark broad leaves lying under the rest and spilling over the rim, then five layers up from the seed, each
    # shorter, steeper and brighter than the one outside it and staggered half a step, so every leaf overlaps two below it
    def outline_dist(theta):
        sn, cs = math.sin(math.radians(theta)), math.cos(math.radians(theta))
        t = 0.0
        while t < 24.0:
            if abs(t * sn) > poly_hw(CY + t * cs) - 0.2:
                return t
            t += 0.05
        return 24.0
    BASE_R = 2.9

    def leaf_part(theta, reach, lift, group, rp, ci, vi, cap=99, dz=0.0, wid=None, snap=2):
        sn, cs = math.sin(math.radians(theta)), math.cos(math.radians(theta))
        bxp, byp = BASE_R * sn, CY + BASE_R * cs
        zb = ZF + 0.5 + dz
        length = min(cap, max(3, round((outline_dist(theta) + reach - BASE_R) / math.cos(math.radians(lift)) / snap) * snap))
        width = wid or (4 if length < 6 else 6 if length < 11 else 8)
        key = f"rleaf_{ci}{vi}_{width}x{length}"
        return Part(B, [bxp - width / 2, byp, zb], [width, length, 0], rot=(-lift, 0, theta), pivot=[bxp, byp, zb], group=group,
                    gpivot=[0, CY, ZF], paint=lambda v, l, m: two(v, lambda a: paint_leaf(a, rp, glow, ci * 7 + vi)), share=key)
    for i in range(14):                                                              # under layer, spilling past the rim
        th = 360.0 * (i + 0.25) / 14 - 180.0
        parts.append(leaf_part(th, 0.4, 5.0, "leafring_0", rps[0][i % 2], 0, i % 2, dz=-0.6, wid=9, snap=3))
    LAYERS = [(16, 13.0, -1.0, 99, 0.4, 1), (15, 22.0, -1.0, 16, 1.0, 2), (12, 32.0, -2.0, 13, 1.7, 3), (10, 43.0, -3.0, 10, 2.4, 4)]
    for li, (n, lift, reach, cap, dz, ci) in enumerate(LAYERS):
        for i in range(n):
            th = 360.0 * (i + 0.5 * (li % 2)) / n - 180.0
            wob = (3, 0, 2, -1, 1)[(i + li) % 5]
            parts.append(leaf_part(th, reach - 0.4 * wob, lift + wob, f"leafring_{li + 1}", rps[ci][i % 2], ci, i % 2, cap=cap, dz=dz))
    for i in range(8):                                                               # the heart bud: a short steep crown
        th = 360.0 * (i + 0.25) / 8 - 180.0
        parts.append(leaf_part(th, -4.0, 56.0, "leafring_5", rps[4][i % 2], 4, 2 + i % 2, cap=7, dz=3.0, wid=4, snap=2))

    # the seed: a socket of bark chips around a faceted gem
    def chip(v, l, m):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), bark, "v", lo=1.8, hi=5.0)
    for k in range(8):
        a = k * 45.0
        cx, cy = 3.9 * math.sin(math.radians(a)), CY + 3.9 * math.cos(math.radians(a))
        parts.append(Part(B, [cx - 1, cy - 1, ZF + 3.4], [2, 2, 1], rot=(0, 0, a), pivot=[cx, cy, ZF + 3.9], paint=chip, share="rchip"))

    def gem(v, l, m):
        for n in ("front", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), glow, "v", lo=1.4, hi=3.4)
        a = v["back"]
        h, w = a.shape[:2]
        c = (w - 1) / 2
        a[:] = 0
        shade(a, np.ones((h, w), bool), glow, "v", lo=2.4, hi=4.2)
        put(a, poly_mask(w, h, [(0, 0), (w - 1, 0), (c, c)]), glow[5])
        put(a, poly_mask(w, h, [(0, 0), (c, c), (0, h - 1)]), glow[4])
        put(a, poly_mask(w, h, [(w - 1, 0), (c, c), (w - 1, h - 1)]), glow[3])
        put(a, poly_mask(w, h, [(0, h - 1), (c, c), (w - 1, h - 1)]), glow[2])
    parts.append(Part(B, [-1.5, CY - 1.5, ZF + 3.6], [3, 3, 2], group="gem", gpivot=[0, CY, ZF + 4.6], paint=gem))
    return parts
