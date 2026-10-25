"""Rootvale shop back attachment: a blossom halo opening behind the head, with two flowering vines winding down the back."""
import math
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *
from designs import two
from designs3 import with_mirror
from designs8 import _look, paint_leaf

B = "armorBody"
HC, HZ = 29.0, 5.2                       # halo centre height and depth: behind the head, clear of the shoulders


def _petal_mask(w, h, notch=True):
    m = np.zeros((h, w), bool)
    c = (w - 1) / 2
    for y in range(h):
        t = 1 - (y + 0.5) / h                                        # 0 at the base, 1 at the tip
        hw = 0.5 * w * max(0.0, math.sin(math.pi * min(1.0, t ** 0.75))) ** 0.6 if t < 1 else 0.0
        if t < 0.12:
            hw = max(hw, 0.5)
        xs = np.arange(w)
        m[y] = np.abs(xs - c) <= hw - 0.2
    if notch:
        m[:max(2, h // 12), int(math.floor((w - 1) / 2)):int(math.ceil((w - 1) / 2)) + 1] = False
    return m


def paint_petal(a, cream, rose, seed, glow):
    """A petal in one plane: pale green-cream at the base, blush to rose toward a notched tip, fine veins fanning out."""
    h, w = a.shape[:2]
    a[:] = 0
    m = _petal_mask(w, h)
    c = (w - 1) / 2
    yy, xx = np.mgrid[0:h, 0:w]
    t = 1 - yy / max(1, h - 1)
    side = np.where(xx > c, 0.45, -0.35)
    lvl = 2.2 + 1.8 * (1 - np.abs(t - 0.55)) + side
    idx = np.clip(np.floor(lvl + (bayer(h, w) - 0.5) * 0.7), 0, 5).astype(int)
    tipmix = bayer(h, w) < np.clip((t - 0.35) / 0.5, 0, 1)
    for i in range(6):
        put(a, m & (idx == i) & ~tipmix, cream[i])
        put(a, m & (idx == i) & tipmix, rose[i])
    for k in (-0.4, 0.0, 0.4):                       # veins fan from the base to the edge
        for y in range(2, int(h * 0.85)):
            x = int(round(c + k * (w * 0.5) * (1 - y / h) ** -0.0 * (t[y, 0] ** 0.8)))
            if 0 <= x < w and m[y, x]:
                a[y, x] = (*(rose[2] if t[y, 0] > 0.5 else cream[2]), 255)
    put(a, m & ~erode(m), rose[1])
    put(a, erode(m) & ~erode(erode(m)) & (xx > c) & (t > 0.4), rose[5])      # the lit edge
    for y in range(h - 2, h):                                        # a pale green sepal at the very base
        a[y][m[y]] = (*cream[2], 255)


def root_blossom(look):
    look = _look("rootvale")
    glow, bark = look.glow, look.metal
    cream, rose = ramp((218, 232, 168)), ramp((240, 150, 184))
    rose_d = ramp((206, 96, 148))
    parts = []

    # petals: three rings, each shorter and steeper than the one outside it, staggered half a step, cupped back off the halo
    PET = [(12, 7.0, 11, 7, 26.0, 0.0, cream, rose), (12, 6.4, 9, 6, 40.0, 0.8, cream, rose_d), (10, 6.0, 6, 4, 56.0, 1.6, cream, rose)]
    for li, (n, r0, ln, w, lift, dz, cr, ro) in enumerate(PET):
        for i in range(n):
            th = 360.0 * (i + 0.5 * (li % 2)) / n
            sn, cs = math.sin(math.radians(th)), math.cos(math.radians(th))
            bx, by, zb = r0 * sn, HC + r0 * cs, HZ + dz
            wob = (0, 1, 0, -1, 0)[(i + li) % 5]
            L = ln + (2 * wob if li == 0 else 0)
            key = f"bpetal_{li}_{w}x{L}"
            parts.append(Part(B, [bx - w / 2, by, zb], [w, L, 0], rot=(-lift - wob * 2, 0, th), pivot=[bx, by, zb], group=f"leafring_{li}",
                              gpivot=[0, HC, HZ], paint=lambda v, l, m, cr=cr, ro=ro, s=li: two(v, lambda a: paint_petal(a, cr, ro, s, glow)), share=key))

    # stamens: a short green filament with a glowing tip between the petals and the ring
    def stamen(a):
        h, w = a.shape[:2]
        a[:] = 0
        a[2:, w // 2 - 1:w // 2 + 1] = (*cream[3], 255)
        a[2:, w // 2 - 1] = (*cream[2], 255)
        a[:3, :] = (*glow[4], 255)
        a[:2, w // 2 - 1:w // 2 + 1] = (*glow[5], 255)
    for i in range(16):
        th = 360.0 * (i + 0.5) / 16
        sn, cs = math.sin(math.radians(th)), math.cos(math.radians(th))
        bx, by = 4.4 * sn, HC + 4.4 * cs
        parts.append(Part(B, [bx - 1, by, HZ + 0.6], [2, 3, 0], rot=(-52, 0, th), pivot=[bx, by, HZ + 0.6], group="leafring_3", gpivot=[0, HC, HZ],
                          paint=lambda v, l, m: two(v, stamen), share="bstamen"))

    # the ring: bark chips round the heart of the flower, a lime stud between each pair
    def chip(v, l, m):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), bark, "v", lo=1.8, hi=5.0)

    def stud(v, l, m):
        for n in ("front", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), glow, "v", lo=1.6, hi=3.8)
        a = v["back"]
        h, w = a.shape[:2]
        shade(a, np.ones((h, w), bool), glow, "v", lo=2.6, hi=4.6, edge=False)
        put(a, np.hypot(*(np.mgrid[0:h, 0:w] - np.array([(h - 1) / 2, (w - 1) / 2])[:, None, None])) <= w * 0.3, glow[5])
    for i in range(12):
        th = 360.0 * i / 12
        sn, cs = math.sin(math.radians(th)), math.cos(math.radians(th))
        cx, cy = 5.4 * sn, HC + 5.4 * cs
        parts.append(Part(B, [cx - 1.5, cy - 1.5, HZ - 0.8], [3, 3, 2], rot=(0, 0, th), pivot=[cx, cy, HZ + 0.2], paint=chip, share="bchip"))
        th2 = th + 15.0
        sn, cs = math.sin(math.radians(th2)), math.cos(math.radians(th2))
        parts.append(Part(B, [5.4 * sn - 1, HC + 5.4 * cs - 1, HZ - 0.4], [2, 2, 3], paint=stud, share="bstud"))
    parts.append(Part(B, [-1, 22.0, 3.0], [2, 6, 2], paint=chip, share="bpost"))                      # the post that holds the halo up
    parts.append(Part(B, [-4, 21.0, 2.9], [8, 2, 2], paint=chip, share="bbar"))

    # two vines wind down the back from the halo, weaving in and out of the surface, with leaves and buds
    def vine_seg(v, l, m):
        for n in ("front", "back", "left", "right"):
            h, w = v[n].shape[:2]
            shade(v[n], np.ones((h, w), bool), bark, "v" if h > w else "h", lo=1.4, hi=4.2)
            if n == "back" and h > w:
                v[n][1:h - 1, w // 2] = (*cream[2], 255) if False else (*bark[4], 255)
        for n in ("top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), bark, "none", edge=True)

    def bud(v, l, m):
        for n in ("front", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), rose, "v", lo=1.8, hi=4.2)
        a = v["back"]
        h, w = a.shape[:2]
        c = (w - 1) / 2
        a[:] = 0
        shade(a, np.ones((h, w), bool), rose, "v", lo=2.2, hi=4.4, edge=False)
        for k in range(5):                                             # five petals round a glowing centre
            ang = k * 2 * math.pi / 5
            x, y = int(round(c + (w * 0.28) * math.sin(ang))), int(round(c - (w * 0.28) * math.cos(ang)))
            if 0 <= x < w and 0 <= y < h:
                a[y, x] = (*rose[5], 255)
        a[int(c):int(c) + 2, int(c):int(c) + 2] = (*glow[5], 255)

    def vine(sign):
        pts = []
        for k in range(15):
            u = k / 14
            x = sign * (6.5 - 2.0 * u + 1.9 * math.sin(u * math.pi * 2.0 + 0.4))
            y = 17.0 - 16.0 * u
            z = 3.2 + 1.0 * math.sin(u * math.pi * 3.0 + 1.0)
            pts.append((x, y, z))
        out = []
        for k in range(len(pts) - 1):
            (ax, ay, az), (bx, by, bz) = pts[k], pts[k + 1]
            ln = max(2, round(math.dist((ax, ay), (bx, by))) + 1)
            psi = math.degrees(math.atan2(bx - ax, by - ay))
            mx, my = (ax + bx) / 2, (ay + by) / 2
            out.append(Part(B, [mx - 1.5, my - ln / 2, az], [3, ln, 2], rot=(0, 0, psi), pivot=[mx, my, az + 1.0], group="leafring_4", gpivot=[0, 22.0, 3.0],
                            paint=vine_seg, share=f"bvine_{ln}"))
        for k in (2, 5, 8, 11):                                         # a leaf off every third joint, alternating sides
            x, y, z = pts[k]
            side = 1 if (k // 3) % 2 == 0 else -1
            th = side * 62.0
            out.append(Part(B, [x - 2.5, y, z + 0.6], [5, 9, 0], rot=(-26, 0, th), pivot=[x, y, z + 0.6], group="leafring_5", gpivot=[0, 22.0, 3.0],
                            paint=lambda v, l, m: two(v, lambda a: paint_leaf(a, ramp((92, 150, 66)), glow, 5)), share="bvleaf"))
        for k in (3, 6, 9, 12):
            x, y, z = pts[k]
            out.append(Part(B, [x - 1, y - 1, z + 1.6], [2, 2, 2], paint=bud, share="bbud"))
        return out
    parts += vine(1) + vine(-1)
    return parts
