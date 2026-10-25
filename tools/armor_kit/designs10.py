"""Tideforge shop back attachments: a scallop-shell fan with a pearl, and a cresting wave curling over the back."""
import math
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
from sculpt import *
from designs import two
import generate_armor_textures as gt

B = "armorBody"
gt.SPECS["tideforge"].update(metal=(190, 208, 222), trim=(60, 140, 200), cloth=(30, 84, 140), glow=(170, 240, 255))   # as build.py sets them


def _look():
    return gt.Look("tideforge", gt.SPECS["tideforge"])


def both(v, fn):
    """A sheet seen from either side: the same art on the outward face and (mirrored) on the inward face."""
    for n in ("top", "bottom", "left", "right"):
        v[n][:] = 0
    fn(v["back"])
    fn(v["front"])
    v["front"][:] = v["front"][:, ::-1].copy()


def lerp3(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# ---- the shell fan ------------------------------------------------------------------------------------------------------------

def paint_shell(a, base, rim, ribs, seed, glow):
    """A scallop shell standing on its hinge: ribs fan from the bottom, nacre runs from a deep base colour to a pearl rim."""
    h, w = a.shape[:2]
    a[:] = 0
    c = (w - 1) / 2
    apex = (c, h - 1 + h * 0.06)
    yy, xx = np.mgrid[0:h, 0:w]
    dx, dy = xx - apex[0], apex[1] - yy
    ang = np.arctan2(dx, dy)
    rad = np.hypot(dx, dy)
    rmax = apex[1] + 0.2
    hw = 0.5 * w
    spread = math.atan2(hw, h * 0.52)
    scallop = 1.0 - 0.06 * (0.5 - 0.5 * np.cos(ang / max(spread, 0.1) * ribs * math.pi))
    m = (rad <= rmax * scallop) & (np.abs(ang) <= spread) & (np.abs(dx) <= hw - 0.2) & (rad > h * 0.10)
    t = np.clip((rad - h * 0.10) / (rmax - h * 0.10), 0, 1)
    side = np.where(ang > 0, 0.35, -0.3)
    lvl = 2.2 + 1.2 * t + side
    idx = np.clip(np.floor(lvl + (bayer(h, w) - 0.5) * 0.7), 0, 5).astype(int)
    tip = bayer(h, w) < np.clip((t - 0.45) / 0.5, 0, 1)
    for i in range(6):
        put(a, m & (idx == i) & ~tip, base[i])
        put(a, m & (idx == i) & tip, rim[i])
    k = (ang / max(spread, 0.1) * ribs * 0.5 + ribs * 0.5)                              # which rib a texel belongs to
    groove = m & (np.abs(k - np.round(k)) < 0.11) & (t > 0.08)
    put(a, groove, base[1])
    lit = m & (np.abs(k - np.round(k) - 0.18) < 0.07) & (t > 0.1)
    put(a, lit, rim[5])
    put(a, m & ~erode(m), base[0])
    put(a, erode(m) & ~erode(erode(m)) & (yy < h * 0.55), rim[4])                          # a pale lip round the top
    a[int(h * 0.88):][m[int(h * 0.88):]] = (*base[2], 255)                                 # the hinge


def paint_star(a, rp, glow):
    h, w = a.shape[:2]
    a[:] = 0
    c = (w - 1) / 2
    pts = []
    for k in range(10):
        r = (w * 0.5 - 0.3) if k % 2 == 0 else w * 0.22
        ang = k * math.pi / 5
        pts.append((c + r * math.sin(ang), c - r * math.cos(ang)))
    m = poly_mask(w, h, pts)
    shade(a, m, rp, "v", lo=2.4, hi=4.6, edge=True, dith=0.8)
    yy, xx = np.mgrid[0:h, 0:w]
    for k in range(5):                                              # a ridge down each arm, dotted
        ang = k * 2 * math.pi / 5
        for s in np.linspace(0.8, w * 0.42, 5):
            x, y = int(round(c + s * math.sin(ang))), int(round(c - s * math.cos(ang)))
            if 0 <= x < w and 0 <= y < h and m[y, x]:
                a[y, x] = (*rp[5], 255)
    put(a, np.hypot(xx - c, yy - c) <= 1.2, glow[4])


def tide_shell(look):
    look = _look()
    glow, silver = look.glow, ramp((190, 208, 222))
    deepsea = ramp((46, 96, 150))
    nacre = ramp((248, 238, 250))
    nacre2 = ramp((200, 226, 246))
    coral, coral2 = ramp((238, 120, 108)), ramp((250, 170, 120))
    star = ramp((246, 150, 90))
    parts = []
    HX, HY, HZ = 0.0, 24.4, 4.4

    LAYERS = [  # count, first angle, step, hinge radius, w, h, lift, dz, base ramp, rim ramp, ribs
        (9, -100.0, 25.0, 3.0, 11, 13, 12.0, 0.0, deepsea, nacre, 7), (8, -87.5, 25.0, 3.4, 9, 10, 22.0, 0.7, ramp((78, 138, 190)), nacre, 6),
        (7, -90.0, 30.0, 3.8, 7, 7, 34.0, 1.4, ramp((120, 176, 218)), nacre2, 5)]
    for li, (n, a0, step, r0, w, h, lift, dz, base, rim, ribs) in enumerate(LAYERS):
        for i in range(n):
            th = a0 + step * i
            sn, cs = math.sin(math.radians(th)), math.cos(math.radians(th))
            bx, by, zb = HX + r0 * sn, HY + r0 * cs, HZ + dz
            wob = (0, 2, 0, -2, 1)[(i + li) % 5]
            key = f"tshell_{li}_{w}x{h}"
            parts.append(Part(B, [bx - w / 2, by, zb], [w, h, 0], rot=(-lift - wob, 0, th), pivot=[bx, by, zb], group=f"leafring_{li}",
                              gpivot=[HX, HY, HZ], paint=lambda v, l, m, b=base, r=rim, k=ribs, s=li: two(v, lambda a: paint_shell(a, b, r, k, s, glow)),
                              share=key))

    def silver_box(v, l, m):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), silver, "v", lo=1.8, hi=5.0)

    def pearl(v, l, m):
        for n in ("front", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), nacre, "v", lo=2.6, hi=5.0)
        a = v["back"]
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        c = (w - 1) / 2
        d = np.hypot(xx - c, yy - c)
        a[:] = 0
        shade(a, np.ones((h, w), bool), nacre, "v", lo=2.4, hi=4.6, edge=False)
        put(a, d > w * 0.46, nacre2[3])
        put(a, np.hypot(xx - c + w * 0.18, yy - c + h * 0.2) <= w * 0.16, glow[5])               # a bright highlight
        put(a, np.hypot(xx - c - w * 0.15, yy - c - h * 0.18) <= w * 0.2, glow[3])
    parts.append(Part(B, [-2, HY + 1.2, HZ + 1.0], [4, 4, 4], group="gem", gpivot=[0, HY + 3.2, HZ + 3.0], paint=pearl))
    for k in range(10):                                                                 # a setting of silver claws round the pearl
        a = k * 36.0
        cx, cy = 3.3 * math.sin(math.radians(a)), HY + 3.2 + 3.3 * math.cos(math.radians(a))
        parts.append(Part(B, [cx - 0.5, cy - 1, HZ + 1.8], [1, 2, 1], rot=(0, 0, a), pivot=[cx, cy, HZ + 2.3], paint=silver_box, share="tclaw"))
    parts.append(Part(B, [-1, 21.5, 3.0], [2, 4, 2], paint=silver_box, share="tpost"))
    parts.append(Part(B, [-4, 20.4, 2.9], [8, 2, 2], paint=silver_box, share="tcollar"))

    # coral branches growing off the lower edge of the fan, and starfish clinging to the shells
    def coral_box(v, l, m):
        for n in ("front", "back", "left", "right"):
            h, w = v[n].shape[:2]
            shade(v[n], np.ones((h, w), bool), coral, "v" if h > w else "h", lo=2.0, hi=4.8, dith=0.9)
            rng = np.random.default_rng(h * 7 + w)
            for _ in range(max(2, h * w // 14)):
                v[n][int(rng.integers(0, h)), int(rng.integers(0, w))] = (*coral[1], 255)
        for n in ("top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), coral2, "none", edge=True)

    def coral_tip(v, l, m):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            a = v[n]
            h, w = a.shape[:2]
            yy, xx = np.mgrid[0:h, 0:w]
            c = ((w - 1) / 2, (h - 1) / 2)
            mk = np.hypot(xx - c[0], yy - c[1]) <= max(w, h) / 2 - 0.3 if n in ("front", "back") else np.ones((h, w), bool)
            a[:] = 0
            shade(a, mk, coral2, "v", lo=2.4, hi=5.0)
    for sx in (1, -1):
        for bi, (ox, ang, ln) in enumerate(((10.0, -16.0, 9), (12.4, -34.0, 6))):
            x0 = sx * ox
            y0 = 19.0 - bi * 1.5
            a = sx * -ang
            parts.append(Part(B, [x0 - 1, y0, 3.0 + 0.4 * bi], [2, ln, 2], rot=(0, 0, a), pivot=[x0, y0, 4.0 + 0.4 * bi], paint=coral_box, share=f"tcoral_{ln}"))
            tx, ty = x0 + ln * math.sin(math.radians(a)), y0 + ln * math.cos(math.radians(a))
            for tw, (ta, tl) in enumerate(((a - 38.0, 4), (a + 34.0, 4), (a + 4.0, 3))):
                parts.append(Part(B, [tx - 0.5, ty - 0.5, 3.6 + 0.4 * bi], [1, tl, 1], rot=(0, 0, ta), pivot=[tx, ty, 4.1 + 0.4 * bi], paint=coral_box,
                                  share=f"ttwig_{tl}"))
                ex, ey = tx + tl * math.sin(math.radians(ta)), ty + tl * math.cos(math.radians(ta))
                parts.append(Part(B, [ex - 1, ey - 0.5, 3.6 + 0.4 * bi], [2, 2, 1], paint=coral_tip, share="ttip"))
    for x, y, z, s in ((-11.5, 27.0, 5.2, 6), (12.0, 31.0, 5.8, 5), (-3.2, 36.5, 7.6, 5), (6.0, 16.5, 4.4, 5)):
        parts.append(Part(B, [x - s / 2, y - s / 2, z], [s, s, 0], rot=(-10, 0, (x * 7) % 60 - 30), pivot=[x, y, z], group=f"star_{len(parts)}", gpivot=[x, y, z],
                          paint=lambda v, l, m: two(v, lambda a: paint_star(a, star, glow))))
    return parts


# ---- the cresting wave --------------------------------------------------------------------------------------------------------

WAVE = [(5.5, 4.0), (11.0, 4.8), (17.0, 6.0), (23.0, 7.8), (29.0, 10.0), (34.0, 12.0), (38.0, 12.2), (41.0, 10.4), (42.4, 7.0), (42.0, 3.5), (40.0, 0.8)]   # (y, z) of the centre cut


def paint_water(a, t0, t1, col, ncols, seed, glow, front):
    """A strip of wave: deep teal at the base, turquoise higher up, white foam where it curls; ripple lines run across it."""
    h, w = a.shape[:2]
    a[:] = 0
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:h, 0:w]
    v = 1 - yy / max(1, h - 1)                                       # 0 at the bottom edge of the strip, 1 at the top
    t = t0 + (t1 - t0) * v
    deep, mid, foam = np.array((20, 70, 130)), np.array((40, 160, 200)), np.array((236, 250, 255))
    tr = np.clip(t * 1.25, 0, 1)
    base = np.where(tr[..., None] < 0.6, deep + (mid - deep) * (tr[..., None] / 0.6), mid + (foam - mid) * np.clip((tr[..., None] - 0.6) / 0.4, 0, 1) ** 1.5)
    ripple = 0.5 + 0.5 * np.sin((yy * 0.9 + xx * 0.35 + col * 2.1 + seed) * 1.7)
    light = (ripple > 0.82)
    d = bayer(h, w)
    base = np.where(light[..., None], base + (foam - base) * 0.38, base)
    base = base * (0.9 + 0.2 * d[..., None] * 0.6)
    if front:
        base = base * 0.82                                            # the inside of the barrel is darker
    a[..., :3] = np.clip(base, 0, 255).astype(np.uint8)
    a[..., 3] = 255
    crest = (t > 0.8) & (bayer(h, w) < (t - 0.8) * 5.0)
    a[crest, :3] = (240, 252, 255)
    for _ in range(max(1, w * h // 40)):                              # a bubble
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        if t[y, x] > 0.25:
            a[y, x] = (*glow[4], 255)
    a[0:1, :] = np.clip(a[0:1] * 0.8, 0, 255).astype(np.uint8)
    edge = np.zeros((h, w), bool)
    edge[:, 0] = edge[:, -1] = True
    a[edge, :3] = (a[edge, :3] * 0.85).astype(np.uint8)


def tide_wave(look):
    look = _look()
    glow, silver = look.glow, ramp((190, 208, 222))
    foam_r = ramp((220, 240, 255))
    parts = []
    NC, CW = 9, 3
    segs = list(zip(WAVE[:-1], WAVE[1:]))
    total = sum(math.dist(a, b) for a, b in segs)
    layers = [(0.0, 0.0, 0, len(segs), NC, 0), (0.5, 0.9, 0, len(segs) - 3, NC - 1, 100)]    # the swell: a second, shorter sheet half a column over and a touch out
    for xoff, zoff, s_from, s_to, ncol, seedoff in layers:
      for ci in range(ncol):
        u = (ci + xoff - (NC - 1) / 2) / ((NC - 1) / 2)                 # -1 .. 1 across the wave
        x = u * (NC - 1) / 2 * (CW - 0.4)
        scale = 1.0 - 0.3 * u * u + 0.02 * math.sin(ci * 2.3)                                      # the middle is tallest and curls furthest, the edges are lower
        phase = (ci * 0.9) % 2.0 - 1.0
        acc = 0.0
        for si, ((y0, z0), (y1, z1)) in enumerate(segs):
            if not (s_from <= si < s_to):
                acc += math.dist((y0, z0), (y1, z1))
                continue
            yy0, zz0 = 5.5 + (y0 - 5.5) * scale, 4.0 + (z0 - 4.0) * scale
            yy1, zz1 = 5.5 + (y1 - 5.5) * scale, 4.0 + (z1 - 4.0) * scale
            zz0 += 1.1 * u * u * (si / len(segs)) + zoff
            zz1 += 1.1 * u * u * ((si + 1) / len(segs)) + zoff
            yy0 += 0.8 * phase * (si / len(segs))
            yy1 += 0.8 * phase * ((si + 1) / len(segs))
            dy, dz = yy1 - yy0, zz1 - zz0
            ln = max(3, int(round(math.hypot(dy, dz))) + 1)
            phi = math.degrees(math.atan2(dz, dy))
            t0, t1 = acc / total, (acc + math.dist((y0, z0), (y1, z1))) / total
            acc += math.dist((y0, z0), (y1, z1))
            key = f"twave_{seedoff}_{ci}_{si}_{ln}"
            parts.append(Part(B, [x - CW / 2 - 0.2, yy0, zz0], [CW + 0.4, ln, 0], rot=(-phi, 0, 0), pivot=[x, yy0, zz0], group="leafring_0",
                              gpivot=[0, 20.0, 4.0], paint=lambda v, l, m, t0=t0, t1=t1, ci=ci, si=si: both(v, lambda a, f=None: paint_water(a, t0, t1, ci, NC, si * 3 + ci + seedoff, glow, False)),
                              share=key))
    # spray: foam and droplets flying off the lip, and a few bubbles hanging in the barrel
    def foam(v, l, m):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), foam_r, "v", lo=3.2, hi=5.0, dith=1.0, edge=False)

    def drop(v, l, m):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), glow, "v", lo=3.0, hi=5.0, edge=False)
    rng = np.random.default_rng(5)
    for ci in range(NC):                                            # foam riding the whole crest: two clumps per column, on the lip
        u = (ci - (NC - 1) / 2) / ((NC - 1) / 2)
        sc = 1.0 - 0.3 * u * u + 0.02 * math.sin(ci * 2.3)
        x = u * (NC - 1) / 2 * (CW - 0.4)
        for k in (-4, -3, -2):
            y, z = WAVE[k]
            y, z = 5.5 + (y - 5.5) * sc, 4.0 + (z - 4.0) * sc
            r = int(rng.integers(0, 45))
            parts.append(Part(B, [x - 1, y - 0.5, z - 0.5], [2, 2, 2], rot=(r, r * 0.6, r * 0.4), pivot=[x, y + 0.5, z + 0.5], paint=foam, share=f"tfoam_{k}"))
    for x, y, z, r in ((-8, 44.0, 5.0, 20), (-4, 46.0, 2.0, 40), (2, 47.0, 0.0, 10), (6, 45.0, 1.0, 30), (9, 43.0, 4.0, 50), (-1, 44.5, -2.0, 0), (-6, 42.0, -1.5, 15),
                       (4, 43.0, -3.0, 25), (0, 39.0, -4.5, 35), (-9, 40.0, 0.0, 5), (8, 40.0, -1.0, 45)):
        parts.append(Part(B, [x - 0.5, y - 0.5, z - 0.5], [1, 1, 1], rot=(r, r * 0.5, r * 0.3), pivot=[x, y, z], paint=drop, share="tspray"))
    for x, y, z in ((-9, 20.0, 9.0), (8, 24.0, 11.5), (-3, 28.0, 13.5), (4, 17.0, 7.5), (-6, 33.0, 14.5)):
        parts.append(Part(B, [x - 0.5, y - 0.5, z], [1, 1, 1], paint=drop, share="tbubble"))

    # the mount: a silver crest bracket over the spine, with a wave scroll plate
    def plate(v, l, m):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            shade(v[n], np.ones(v[n].shape[:2], bool), silver, "v", lo=1.8, hi=5.0)
    parts.append(Part(B, [-6, 4.0, 2.9], [12, 3, 2], paint=plate, share="tbase"))
    parts.append(Part(B, [-3, 6.5, 2.9], [6, 3, 2], paint=plate, share="tbase2"))
    return parts
