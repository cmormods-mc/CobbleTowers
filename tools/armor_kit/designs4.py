"""Paragon back attachment: a gold halo behind the head and two wings of overlapping feathers; a laurel on the helm."""
import math
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *
from designs import two
from designs3 import HEAD, leaf_down, with_mirror

B = "armorBody"
ivory, silver = ramp((238, 234, 246)), ramp((190, 200, 226))
gold, goldd = ramp((252, 214, 100)), ramp((190, 142, 52))


def paragon(look):
    glow = look.glow
    parts = []

    # the halo: a gold ring set with glowing gems and short rays, standing behind the head on a bracket from the root plate
    def ring(a):
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        c = (w - 1) / 2
        d = np.hypot(xx - c, yy - c)
        a[:] = 0
        R, r = w * 0.36, w * 0.26
        shade(a, (d <= R) & (d >= r), gold, "v", lo=2.2, hi=5.0)
        put(a, (d <= r + 1.2) & (d >= r), goldd[1])
        for k in range(12):                                                   # a gem in each twelfth
            ang = k * math.pi / 6
            gx, gy = c + (R + r) / 2 * math.cos(ang), c + (R + r) / 2 * math.sin(ang)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                x, y = int(round(gx)) + dx - 1, int(round(gy)) + dy - 1
                if 0 <= x < w and 0 <= y < h:
                    a[y, x] = (*(glow[5] if dx == dy == 0 else glow[4]), 255)
            tx, ty = c + (R + 3) * math.cos(ang + math.pi / 12), c + (R + 3) * math.sin(ang + math.pi / 12)   # a ray between gems
            for s in range(3):
                x, y = int(round(tx + s * math.cos(ang + math.pi / 12))), int(round(ty + s * math.sin(ang + math.pi / 12)))
                if 0 <= x < w and 0 <= y < h:
                    a[y, x] = (*gold[4 - s], 255)
    parts.append(Part(B, [-8, 23.0, 5.0], [16, 16, 0.3], group="ring", gpivot=[0, 31.0, 5.15], paint=lambda v, l, m: two(v, ring)))

    def post(v, *_):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), goldd, "v", lo=1.6, hi=4.0)
    parts.append(Part(B, [-1.0, 22.0, 2.9], [2, 5, 2], paint=post))

    # two wings: the right wing is built tips toward +x and mirrored; three rows of feathers, the longest at the back, the gold
    # coverts nearest the root and nearest the viewer, every row at its own depth so no two planes share one
    ROOT = (3.4, 20.6)
    ROWS = [  # (feathers, first angle, last angle, length first, length last, width, depth, ramp)
        (7, 24, 118, 17, 9, 4, 3.0, silver),
        (6, 34, 112, 12, 8, 4, 3.45, ivory),
        (5, 44, 104, 7, 5, 3, 3.9, gold),
    ]
    k = 0
    for n, a0, a1, l0, l1, w, z, rp in ROWS:
        for i in range(n):
            t = i / (n - 1)
            ang = a0 + (a1 - a0) * t
            length = round(l0 + (l1 - l0) * t)
            parts += with_mirror(Part(B, [ROOT[0] - w / 2, ROOT[1], z], [w, length, 0.3], rot=(0, 0, ang), pivot=[ROOT[0], ROOT[1], z + 0.15],
                                      group=f"wing_{k}", gpivot=[ROOT[0], ROOT[1], z + 0.15],
                                      paint=lambda v, l, m, rp=rp: two(v, leaf_down(rp, glow))))
            k += 1
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
