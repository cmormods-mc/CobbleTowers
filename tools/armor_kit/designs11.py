"""Tideforge shop back attachment: leviathan fin-wings. Bone-and-silver spars carry rows of fin blades; every blade is a bony ray with a
scalloped membrane either side of it, painted from deep navy at the root to bright teal at the tip, like a manta or flying-fish fin."""
import math
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *
from designs import two
from designs3 import with_mirror
import generate_armor_textures as gt

B = "armorBody"
gt.SPECS["tideforge"].update(metal=(190, 208, 222), trim=(60, 140, 200), cloth=(30, 84, 140), glow=(170, 240, 255))


def _look():
    return gt.Look("tideforge", gt.SPECS["tideforge"])


def along(poly, s):
    seg = [math.dist(poly[i], poly[i + 1]) for i in range(len(poly) - 1)]
    d = s * sum(seg)
    for i, L in enumerate(seg):
        if d <= L or i == len(seg) - 1:
            t = min(1.0, d / L)
            return (poly[i][0] + (poly[i + 1][0] - poly[i][0]) * t, poly[i][1] + (poly[i + 1][1] - poly[i][1]) * t)
        d -= L


def paint_fin(a, deep, bright, bone, seed, glow, spark):
    """One fin blade: a bony ray down the middle that runs past the membrane as a spike, fine side rays, and a membrane whose far edge
    scoops inward between the rays. Navy at the root, teal and pearl toward the edge, dithered between the two ramps."""
    h, w = a.shape[:2]
    a[:] = 0
    c = (w - 1) / 2
    yy, xx = np.mgrid[0:h, 0:w]
    u = (xx - c) / max(c, 0.5)                                       # -1 .. 1 across the blade
    t = 1.0 - yy / max(1, h - 1)                                     # 0 at the root, 1 at the tip
    edge_t = 0.70 + 0.29 * (1.0 - np.abs(u) ** 0.55)                 # the membrane ends here: lowest at the sides, at the ray at the middle
    spread = np.clip(0.62 + 2.2 * t, 0, 1.0)                         # the blade narrows toward the root
    mem = (t <= edge_t) & (np.abs(u) <= spread) & (t > 0.0)
    ray = (np.abs(xx - c) < 0.8 + 0.4 * (1 - t)) & (t > 0.0) & (t <= 1.0)
    # membrane shading: darker toward the root and down the right side, ripples that follow the side rays
    lvl = 1.4 + 2.6 * t + np.where(u > 0, -0.45, 0.35) * (1 - t * 0.4) + 0.3 * np.sin(yy * 0.7 + seed)
    idx = np.clip(np.floor(lvl + (bayer(h, w) - 0.5) * 0.45), 0, 5).astype(int)
    toward = bayer(h, w) < np.clip((t - 0.38) / 0.58, 0, 1)
    for i in range(6):
        put(a, mem & (idx == i) & ~toward, deep[i])
        put(a, mem & (idx == i) & toward, bright[i])
    for k in (-0.62, -0.31, 0.31, 0.62):                              # fine side rays, curving out from the root
        for y in range(h):
            tt = 1.0 - y / max(1, h - 1)
            x = int(round(c + k * c * min(1.0, 0.62 + 2.2 * tt) * (0.7 + 0.3 * tt)))
            if 0 <= x < w and mem[y, x]:
                a[y, x] = (*bone[2 if tt < 0.5 else 3], 255)
    put(a, mem & ~erode(mem), deep[0])                                 # a dark outline round the membrane
    put(a, erode(mem) & ~erode(erode(mem)) & (u < 0) & (t > 0.5), bright[5])   # a light rim down the lit side
    shade(a, ray, bone, "v", lo=2.4, hi=5.0, edge=False, dith=0.5)     # the bony ray over it, brightening to the tip
    put(a, ray & ((yy % 4) == 0) & (t < 0.9), bone[1])                 # joints along the ray
    if spark:
        live = a[..., 3] > 0
        ys, xs = np.nonzero(live[: int(h * 0.3)])
        for y, x in list(zip(ys, xs))[::5]:
            a[y, x] = (*glow[5], 255)
    a[:2][ray[:2]] = (*glow[4], 255)                                   # the ray's tip glints


def leviathan_wings(look):
    look = _look()
    glow, metal = look.glow, look.metal
    bone = ramp((228, 236, 242))
    parts = []
    z0 = 3.0

    def spar(v, l, m):
        for n in ("front", "back", "left", "right"):
            h, w = v[n].shape[:2]
            shade(v[n], np.ones((h, w), bool), bone, "h" if w > h else "v", lo=1.8, hi=4.8)
            if n == "back" and h > w:
                v[n][2:h - 2, w // 2] = (*glow[3], 255)                 # a teal vein of light down the spar
                v[n][3:h - 2:5, w // 2] = (*glow[5], 255)
                for y in range(1, h - 1, 6):                            # and a ring round it at each joint of bone
                    v[n][y, :] = (*bone[1], 255)
        for n in ("top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), bone, "none", edge=True)

    def knuckle(v, l, m):
        for n in ("front", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), bone, "v", lo=1.8, hi=4.6)
        a = v["back"]
        h, w = a.shape[:2]
        c = (w - 1) / 2
        a[:] = 0
        shade(a, np.ones((h, w), bool), bone, "v", lo=2.0, hi=4.6, edge=True)
        yy, xx = np.mgrid[0:h, 0:w]
        put(a, np.hypot(xx - c, yy - c) <= w * 0.3, glow[4])
        put(a, np.hypot(xx - c + 0.6, yy - c + 0.6) <= w * 0.14, glow[5])

    def spine(v, l, m):
        for n in ("front", "back", "left", "right"):
            shade(v[n], np.ones(v[n].shape[:2], bool), bone, "v", lo=2.4, hi=5.0)
        for n in ("top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), bone, "none", edge=True)

    ARM = {"upper": [(4.0, 23.0), (11.0, 32.0), (24.0, 37.0), (38.0, 33.0)],
           "lower": [(4.0, 19.5), (10.0, 11.0), (15.5, 4.5), (21.0, -1.5)]}
    ROWS = {  # blades, arm span, fan from, fan to (radians below the wing axis), longest, lift (deg), z, width
        "upper": [(18, (0.20, 1.00), 1.10, 0.05, 36, 6.0, 3.00, 7), (16, (0.18, 0.97), 1.18, 0.10, 30, 11.0, 3.45, 7),
                  (14, (0.14, 0.93), 1.26, 0.16, 23, 16.0, 3.90, 6), (12, (0.10, 0.90), 1.34, 0.24, 16, 22.0, 4.35, 6),
                  (10, (0.06, 0.85), 1.42, 0.32, 10, 28.0, 4.80, 5)],
        "lower": [(14, (0.24, 1.00), 1.06, 0.06, 28, 6.0, 3.30, 7), (12, (0.20, 0.96), 1.14, 0.12, 22, 11.0, 3.75, 6),
                  (10, (0.16, 0.92), 1.22, 0.20, 15, 17.0, 4.20, 6), (8, (0.10, 0.86), 1.30, 0.28, 10, 24.0, 4.65, 5)],
    }
    TONES = [(((8, 28, 66), (40, 126, 178)), ((12, 36, 80), (54, 144, 192))), (((10, 36, 84), (52, 146, 196)), ((16, 46, 100), (68, 166, 210))),
             (((14, 46, 104), (74, 178, 216)), ((20, 58, 120), (92, 194, 228))), (((22, 66, 128), (112, 214, 236)), ((28, 80, 146), (134, 228, 244))),
             (((34, 90, 150), (170, 242, 250)), ((44, 106, 166), (198, 248, 254)))]
    for kind, poly in ARM.items():
        axis = math.atan2(poly[-1][1] - poly[0][1], poly[-1][0] - poly[0][0])
        wk = 0 if kind == "upper" else 1
        for ri, (n, (s0, s1), a0, a1, maxlen, lift, zr, w) in enumerate(ROWS[kind]):
            for i in range(n):
                u = i / (n - 1)
                px, py = along(poly, s0 + (s1 - s0) * u)
                fan = a0 + (a1 - a0) * u ** 0.85
                ang = axis - fan
                span = 0.45 + 0.55 * math.sin(math.pi * (0.10 + 0.82 * u))
                if ri < 2 and i % 3 == 1:
                    span = min(1.0, span * 1.18)                        # a few long blades break the trailing edge
                ln = max(6, int(round(maxlen * span / 4.0)) * 4)
                psi = math.degrees(math.atan2(math.cos(ang), math.sin(ang)))
                tone = (i + ri) % 2
                deep_c, bright_c = TONES[ri][tone]
                spark = ri >= 3 and i % 2 == 0
                key = f"fin_{kind[0]}{ri}_{w}x{ln}_{tone}_{int(spark)}"
                z = zr + 0.05 * (i % 3)
                parts += with_mirror(Part(B, [px - w / 2, py, z], [w, ln, 0.3], rot=(-lift - 2 * (i % 3), 0, psi), pivot=[px, py, z + 0.15],
                                          group=f"wing_{wk * 5 + ri}", gpivot=[poly[0][0], poly[0][1], z0],
                                          paint=lambda v, l, m, d=ramp(deep_c), b=ramp(bright_c), s=i + 17 * ri, sp=spark: two(v, lambda a: paint_fin(a, d, b, bone, s, glow, sp)),
                                          share=key))
        for si in range(len(poly) - 1):                                  # a bone spar per segment, a knuckle at each joint, and spines along its upper edge
            (ax, ay), (bx, by) = poly[si], poly[si + 1]
            ln = max(2, int(round(math.dist((ax, ay), (bx, by)))))
            psi = math.degrees(math.atan2(bx - ax, by - ay))
            parts += with_mirror(Part(B, [ax - 1.5, ay, 5.4], [3, ln, 2], rot=(0, 0, psi), pivot=[ax, ay, 6.4], group=f"wing_{wk * 5}",
                                      gpivot=[poly[0][0], poly[0][1], z0], paint=spar, share=f"fspar_{ln}"))
            if kind == "upper":
                for k in range(1, 4):
                    t = k / 4.0
                    sx, sy = ax + (bx - ax) * t, ay + (by - ay) * t
                    up = psi + 90.0
                    parts += with_mirror(Part(B, [sx - 0.5, sy, 5.9], [1, 4, 1], rot=(0, 0, psi + 24.0 * (1 if si < 2 else -1)), pivot=[sx, sy, 6.4],
                                              group=f"wing_{wk * 5}", gpivot=[poly[0][0], poly[0][1], z0], paint=spine, share="fspine"))
        for jx, jy in poly[:-1]:
            parts += with_mirror(Part(B, [jx - 1.5, jy - 1.5, 4.9], [3, 3, 3], group=f"wing_{wk * 5}", gpivot=[poly[0][0], poly[0][1], z0], paint=knuckle, share="fknuckle"))
    # a great claw spike at each upper wrist, and a long bone barb at each wing tip
    wx, wy = ARM["upper"][2]
    tx, ty = ARM["upper"][3]
    parts += with_mirror(Part(B, [wx - 1, wy, 5.6], [2, 9, 2], rot=(0, 0, -22), pivot=[wx, wy, 6.6], group="wing_0", gpivot=[4.0, 23.0, z0], paint=spine, share="fclaw"))
    parts += with_mirror(Part(B, [tx - 1, ty - 1, 5.6], [2, 8, 2], rot=(0, 0, 70), pivot=[tx, ty, 6.6], group="wing_0", gpivot=[4.0, 23.0, z0], paint=spine, share="fbarb"))

    # the mount: a ribbed silver carapace over the spine, with a pearl set where the wings meet
    def plate(v, l, m):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), metal, "v", lo=1.8, hi=5.0)

    def pearl(v, l, m):
        for n in ("front", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), glow, "v", lo=1.8, hi=4.2)
        a = v["back"]
        h, w = a.shape[:2]
        c = (w - 1) / 2
        a[:] = 0
        shade(a, np.ones((h, w), bool), glow, "v", lo=2.4, hi=4.6, edge=False)
        put(a, poly_mask(w, h, [(0, 0), (w - 1, 0), (c, c)]), glow[5])
        put(a, poly_mask(w, h, [(0, 0), (c, c), (0, h - 1)]), glow[4])
        put(a, poly_mask(w, h, [(0, h - 1), (c, c), (w - 1, h - 1)]), glow[2])
    parts.append(Part(B, [-6, 21.0, 2.9], [12, 3, 2], paint=plate, share="fplate"))
    parts.append(Part(B, [-4, 18.4, 3.0], [8, 3, 2], paint=plate, share="fplate2"))
    parts.append(Part(B, [-2, 14.6, 3.0], [4, 4, 2], paint=plate, share="fplate3"))
    parts.append(Part(B, [-2, 20.2, 4.4], [4, 4, 2], group="gem", gpivot=[0, 22.2, 5.4], paint=pearl))
    return parts
