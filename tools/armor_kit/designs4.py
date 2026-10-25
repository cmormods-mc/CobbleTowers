"""Paragon back attachment: a gold halo behind the head and two wings of overlapping feathers; a laurel on the helm."""
import math
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *
from designs import two
from designs3 import HEAD, leaf_down, with_mirror
from wingart import paint_feather

B = "armorBody"
ivory, silver = ramp((238, 234, 246)), ramp((190, 200, 226))
gold, goldd = ramp((252, 214, 100)), ramp((190, 142, 52))


def bar_paint(v, l, m):
    for n in ("front", "back", "left", "right"):
        shade(v[n], np.ones(v[n].shape[:2], bool), gold, "h", lo=2.6, hi=5.0, edge=False)
    for n in ("top", "bottom"):
        shade(v[n], np.ones(v[n].shape[:2], bool), goldd, "none", edge=False)


def gem_paint(v, l, m):
    glow = l.glow
    for n in ("front", "left", "right", "top", "bottom"):
        shade(v[n], np.ones(v[n].shape[:2], bool), gold, "v", lo=2.4, hi=5.0, edge=False)
    a = v["back"]
    h, w = a.shape[:2]
    a[:] = 0
    shade(a, np.ones((h, w), bool), gold, "v", lo=2.4, hi=5.0, edge=False)
    put(a, (np.hypot(*np.mgrid[0:h, 0:w] - np.array([(h - 1) / 2, (w - 1) / 2])[:, None, None]) <= w * 0.28), glow[5])


def paragon(look):
    glow = look.glow
    parts = []

    # the halo: a thin gold ring with a tall spike over it, two four-point sparkles and gems, standing behind the head
    def ring(a):
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        c = (w - 1) / 2
        cy = c + 6
        a[:] = 0
        d = np.hypot(xx - c, yy - cy)
        R = w * 0.33
        shade(a, (d <= R) & (d >= R - 2.6), gold, "v", lo=2.6, hi=5.0, edge=False)
        put(a, (d <= R - 2.0) & (d >= R - 2.6), goldd[2])
        for k in range(12):                                                   # a gem in each twelfth
            ang = k * math.pi / 6
            gx, gy = c + (R - 1.3) * math.cos(ang), cy + (R - 1.3) * math.sin(ang)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                x, y = int(round(gx)) + dx - 1, int(round(gy)) + dy - 1
                if 0 <= x < w and 0 <= y < h:
                    a[y, x] = (*(glow[5] if dx == dy == 0 else glow[4]), 255)
        top = cy - R
        spike = poly_mask(w, h, [(c, 0), (c + 3.5, top - 9), (c + 1.5, top + 3), (c - 1.5, top + 3), (c - 3.5, top - 9)])
        shade(a, spike, gold, "h", lo=2.8, hi=5.0, edge=False)
        a[2:int(top) + 2, int(c)] = (*glow[5], 255)
        for sx in (-1, 1):                                                    # a four-point sparkle up each side
            qx, qy = c + sx * w * 0.34, cy - R * 0.95
            for t in range(-6, 7):
                for x, y, lv in ((qx + t, qy, 4 - abs(t) // 2), (qx, qy + t, 4 - abs(t) // 2)):
                    xi, yi = int(round(x)), int(round(y))
                    if 0 <= xi < w and 0 <= yi < h:
                        a[yi, xi] = (*gold[max(1, min(5, lv))], 255)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
                xi, yi = int(round(qx)) + dx, int(round(qy)) + dy
                if 0 <= xi < w and 0 <= yi < h:
                    a[yi, xi] = (*glow[5], 255)
        for k in range(24):                                                   # a dotted outer arc
            ang = math.pi * 1.08 + k * (math.pi * 0.84) / 23
            x, y = int(round(c + (R + 4.5) * math.cos(ang))), int(round(cy + (R + 4.5) * math.sin(ang)))
            if 0 <= x < w and 0 <= y < h and k % 2 == 0:
                a[y, x] = (*goldd[3], 255)
    parts.append(Part(B, [-9, 22.0, 5.0], [18, 18, 0.3], group="ring", gpivot=[0, 30.0, 5.15], paint=lambda v, l, m: two(v, ring)))

    def post(v, *_):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), goldd, "v", lo=1.6, hi=4.0)
    parts.append(Part(B, [-1.0, 22.0, 2.9], [2, 5, 2], paint=post))

    # four wings built from real feathers: each one a painted plane fanned out from the arm, tilted up off the back by row so the
    # wing bows, over a gold arm of beams and joint gems. Equal feathers share one painted cell, so there can be hundreds.
    z0 = 3.0
    ARM = {  # shoulder, elbow, wrist, tip (x, y), z of the arm
        "upper": [(4.2, 22.2), (9.0, 32.5), (20.5, 38.2), (28.0, 39.0)],
        "lower": [(4.2, 19.0), (10.5, 11.0), (18.0, 5.5), (23.5, 0.6)],
    }
    ROWS = {  # feathers, arm span (from, to), fan from, fan to (radians below the wing axis), max length, lift (deg), z, tints, width
        "upper": [(18, (0.30, 1.00), 1.00, 0.04, 30, 8.0, 3.00, 0, 6), (15, (0.24, 0.95), 1.10, 0.10, 23, 13.0, 3.40, 1, 6),
                  (12, (0.18, 0.90), 1.18, 0.16, 16, 19.0, 3.80, 0, 5), (10, (0.12, 0.84), 1.25, 0.24, 10, 26.0, 4.20, 1, 5)],
        "lower": [(16, (0.30, 1.00), 1.00, 0.04, 28, 8.0, 3.35, 0, 6), (13, (0.24, 0.95), 1.10, 0.10, 21, 13.0, 3.75, 1, 6),
                  (10, (0.18, 0.90), 1.18, 0.16, 14, 19.0, 4.15, 0, 5), (8, (0.12, 0.84), 1.25, 0.24, 9, 26.0, 4.55, 1, 5)],
    }
    TINT = (ivory, ramp((236, 226, 204)), ramp((240, 240, 252)))

    def along(poly, s):
        seg = [math.dist(poly[i], poly[i + 1]) for i in range(len(poly) - 1)]
        d = s * sum(seg)
        for i, L in enumerate(seg):
            if d <= L or i == len(seg) - 1:
                t = min(1.0, d / L)
                return (poly[i][0] + (poly[i + 1][0] - poly[i][0]) * t, poly[i][1] + (poly[i + 1][1] - poly[i][1]) * t)
            d -= L

    def feather_part(name, w, ln, tint, gilt, seed):
        def paint(v, l, m):
            two(v, lambda a: paint_feather(a, tint, seed, gold if gilt else None, glow if gilt else None))
        return paint

    for kind, poly in ARM.items():
        axis = math.atan2(poly[-1][1] - poly[0][1], poly[-1][0] - poly[0][0])
        wk = 0 if kind == "upper" else 1
        for ri, (n, (s0, s1), a0, a1, maxlen, lift, zr, toff, w) in enumerate(ROWS[kind]):
            for i in range(n):
                u = i / (n - 1)
                px, py = along(poly, s0 + (s1 - s0) * u)
                fan = a0 + (a1 - a0) * u ** 0.85
                ang = axis - fan                                               # world direction of the strand
                span = 0.42 + 0.58 * math.sin(math.pi * (0.12 + 0.8 * u))      # short at the shoulder, longest mid-wing
                ln = max(5, int(round(maxlen * span / 2.0)) * 2)              # lengths snap to even steps so cells are shared
                psi = math.degrees(math.atan2(math.cos(ang), math.sin(ang)))   # tilt of +y toward +x
                tint = TINT[(i + toff) % 2]
                gilt = ri == 3 and i % 2 == 0
                key = f"feather_{w}x{ln}_{(i + toff) % 2}_{int(gilt)}"
                z = zr + 0.05 * (i % 3)
                parts += with_mirror(Part(B, [px - w / 2, py, z], [w, ln, 0.3], rot=(-lift - 2 * (i % 3), 0, psi), pivot=[px, py, z + 0.15],
                                          group=f"wing_{wk * 4 + ri}", gpivot=[poly[0][0], poly[0][1], z0],
                                          paint=feather_part(key, w, ln, tint, gilt, i), share=key))

        # the gold arm: a beam per segment and a gem at each joint, in front of every row
        for si in range(len(poly) - 1):
            (ax, ay), (bx, by) = poly[si], poly[si + 1]
            ln = max(2, int(round(math.dist((ax, ay), (bx, by)))))
            psi = math.degrees(math.atan2(bx - ax, by - ay))
            parts += with_mirror(Part(B, [ax - 1, ay, 4.6], [2, ln, 2], rot=(0, 0, psi), pivot=[ax, ay, 5.6], group=f"wing_{wk * 4 + 0}",
                                      gpivot=[poly[0][0], poly[0][1], z0], paint=bar_paint, share=f"bar_{ln}"))
        for ji, (jx, jy) in enumerate(poly[:-1]):
            parts += with_mirror(Part(B, [jx - 1.5, jy - 1.5, 4.1], [3, 3, 3], group=f"wing_{wk * 4 + 0}", gpivot=[poly[0][0], poly[0][1], z0],
                                      paint=gem_paint, share="joint_gem"))
    return parts


def paragon_accents(look):
    glow = look.glow
    parts = []
    # a laurel of gold leaves sweeping back from each temple over the helm
    for i, (ang, z, x) in enumerate(((20, -2.6, 3.6), (46, -1.2, 3.9), (72, 0.2, 4.0))):
        parts += with_mirror(Part(HEAD, [x - 1.5, 32.1, z], [3, 6, 0.3], rot=(0, 0, ang), pivot=[x, 32.1, z + 0.15],
                                  group=f"crest_{2 * i}", gpivot=[x, 32.1, z + 0.15],
                                  paint=lambda v, l, m: two(v, leaf_down(gold, glow))))
    # one feather on each shoulder
    p = Part("armorRightArm", [-6.5 - 2.0, 23.6, 0.4], [4, 8, 0.3], rot=(0, 0, -50), pivot=[-6.5, 23.6, 0.55],
             group="shoulder_0", gpivot=[-6.5, 23.6, 0.55], paint=lambda v, l, m: two(v, leaf_down(ivory, glow)))
    parts += [p, Part("armorLeftArm", [6.5 - 2.0, 23.6, 0.4], [4, 8, 0.3], rot=(0, 0, 50), pivot=[6.5, 23.6, 0.55],
                      group="shoulder_0_m", gpivot=[6.5, 23.6, 0.55], paint=lambda v, l, m: two(v, leaf_down(ivory, glow)))]
    return parts
