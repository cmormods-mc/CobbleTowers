"""Paragon worn armor: ivory feather plate, gold trim, a glowing sun on the chest, a laurel ring on the helm."""
from armor_art import *

ivory, silver = ramp((238, 234, 246)), ramp((190, 200, 226))
gold, goldd = ramp((252, 214, 100)), ramp((190, 142, 52))
crim = ramp((164, 36, 48))


def plate(st, tone=ivory):
    fill(st, ones(st), tone, "v", lo=2.4, hi=4.8, edge=False)
    for x in range(7, st.shape[1], 8):
        st[:, x] = (*tone[1], 255)


def trim(st, y, h=3):
    fill(st, rect(st, 0, y, st.shape[1], h), gold, "v", lo=3.2, hi=5.0, edge=False)
    st[y - 1, :] = (*gold[2], 255)
    st[y + h, :] = (*gold[2], 255)


def hem(st, glow, y0=38):
    """A gold belt, then a fringe of ivory feathers with a ragged transparent bottom edge."""
    trim(st, y0 - 6, 4)
    rivets(st, range(3, st.shape[1] - 2, 8), y0 - 5, gold)
    st[y0:, :] = 0
    shingle(st, rect(st, 0, y0, st.shape[1], st.shape[0] - y0), y0, st.shape[0], ivory, silver, 9, 13, 5, glow)


def sun(f, cx, cy, r, glow):
    """A gold-ringed disc with a glowing core and eight short rays."""
    yy, xx = np.mgrid[0:f.shape[0], 0:f.shape[1]]
    d = np.hypot(xx - cx, yy - cy)
    fill(f, d <= r, gold, "v", lo=3.0, hi=5.0)
    fill(f, d <= r - 3, ivory, "v", lo=3.0, hi=5.0)
    fill(f, d <= r - 5, glow, "v", lo=2.8, hi=5.2)
    for k in range(8):
        a = k * math.pi / 4
        for t in range(int(r) + 1, int(r) + 4):
            x, y = int(round(cx + t * math.cos(a))), int(round(cy + t * math.sin(a)))
            if 0 <= x < f.shape[1] and 0 <= y < f.shape[0]:
                f[y, x] = (*gold[4], 255)


def head(b, glow):
    st = b.strip
    plate(st)
    shingle(st, rect(st, 0, 3, 128, 13), 3, 16, ivory, silver, 10, 13, 5, glow)         # a feathered brow
    trim(st, 16, 3)
    rivets(st, range(4, 126, 9), 17, gold)
    f = b.face("front")
    fill(f, rect(f, 4, 21, 24, 9), goldd, "v", lo=2.4, hi=4.2, edge=False)                           # a gold visor with a glowing slit
    for y in range(24, 27):
        for x in range(6, 26):
            px(f, x, y, glow[4] if y < 26 else glow[3])
    fill(f, rect(f, 14, 20, 4, 10), gold, "h", lo=3.0, hi=5.0)                          # a nose guard
    for n in ("right", "left"):
        g = b.face(n)
        fill(g, rrect(g, 3, 19, 26, 12, 4), silver, "v", lo=2.0, hi=4.6)                 # cheek plates with a gold edge
        put_edge = rrect(g, 3, 19, 26, 12, 4) & ~erode(rrect(g, 3, 19, 26, 12, 4))
        g[put_edge] = (*gold[3], 255)
    g = b.face("back")
    shingle(g, rect(g, 0, 19, 32, 13), 19, 32, ivory, silver, 10, 13, 5, glow)
    t = b.top
    fill(t, ones(t), ivory, "none", lo=3.4, edge=False)                                    # a laurel ring round a glowing seed
    c = 15.5
    for k in range(12):
        a = k * math.pi / 6
        tip = (c + 13 * math.cos(a), c + 13 * math.sin(a))
        s1 = (c + 7 * math.cos(a + 0.35), c + 7 * math.sin(a + 0.35))
        s2 = (c + 7 * math.cos(a - 0.35), c + 7 * math.sin(a - 0.35))
        fill(t, poly(t, [s1, tip, s2]), gold, "v", lo=3.0, hi=5.0)
    fill(t, ell(t, 11, 11, 10, 10), glow, "v", lo=2.6, hi=5.2)
    fill(b.bottom, ones(b.bottom), silver, "none", lo=2.0, edge=False)


def chest(b, glow):
    st = b.strip
    plate(st)
    f = b.face("front")
    shingle(f, poly(f, [(4, 12), (28, 12), (28, 22), (16, 38), (4, 22)]), 12, 38, ivory, silver, 9, 12, 6, glow)   # a feathered bib
    for x in (3, 25):
        fill(f, rect(f, x, 0, 4, 40), gold, "h", lo=3.0, hi=5.0)                          # gold shoulder straps
        rivets(f, (x + 1,), 6, gold)
    sun(f, 16, 18, 8, glow)
    fill(st, rect(st, 0, 0, 96, 5), gold, "v", lo=3.0, hi=5.0)                            # a gold collar
    st[5, :] = (*gold[2], 255)
    for n in ("right", "left"):
        g = b.face(n)
        fill(g, rect(g, 6, 8, 4, 28), gold, "h", lo=3.0, hi=5.0)
    g = b.face("back")
    fill(g, rrect(g, 4, 4, 24, 30, 5), silver, "v", lo=2.0, hi=4.4)                      # the root plate the wings grow from
    for pts in ([(2, 6), (7, 6), (30, 30), (25, 30)], [(30, 6), (25, 6), (2, 30), (7, 30)]):
        fill(g, poly(g, pts), gold, "h", lo=3.0, hi=5.0)
    fill(g, ell(g, 10, 11, 12, 12), goldd, "v", lo=1.4, hi=3.4)                          # the halo socket
    fill(g, ell(g, 13, 14, 6, 6), glow, "v", lo=2.6, hi=5.2)
    hem(st, glow, 38)
    fill(b.top, ones(b.top), ivory, "none", lo=3.4, edge=False)
    fill(b.bottom, ones(b.bottom), silver, "none", lo=2.0, edge=False)


def arm(b, glow):
    st = b.strip
    plate(st, silver)
    shingle(st, rect(st, 0, 0, 64, 18), 0, 18, ivory, silver, 14, 16, 7, glow)            # big feathers over the shoulder only
    trim(st, 33, 7)
    rivets(st, range(3, 62, 8), 35, gold)
    for x in range(0, 64, 8):
        st[34:39, x] = (*gold[1], 255)
    fill(b.top, ones(b.top), ivory, "none", lo=3.4, edge=False)
    fill(b.bottom, ones(b.bottom), silver, "none", lo=2.0, edge=False)


def boots(b, glow):
    st = b.strip
    fill(b.top, ones(b.top), ivory, "none", lo=2.8, edge=False)
    fill(b.bottom, ones(b.bottom), silver, "none", lo=2.0, edge=False)
    sub = st[32:48]
    plate(sub, silver)
    shingle(sub, rect(sub, 0, 0, 64, 8), 0, 8, ivory, silver, 8, 8, 3, None)              # a feathered cuff
    trim(sub, 8, 3)
    f = b.face("front")[32:48]
    fill(f, rect(f, 4, 12, 8, 3), gold, "v", lo=3.0, hi=5.0, edge=False)                              # a gold toe cap
    for x in range(1, 64, 6):
        px(sub, x, 14, gold[3])


def legs(b, glow):
    st = b.strip
    plate(st)
    shingle(st, rect(st, 0, 0, 64, 20), 0, 20, ivory, silver, 9, 11, 5, glow)             # feathers over the thighs only
    f = b.face("front")
    fill(f, rrect(f, 3, 20, 10, 11, 3), gold, "cyl", lo=2.8, hi=5.0)                     # a gold knee plate
    fill(f, rrect(f, 5, 22, 6, 7, 2), ivory, "v", lo=3.0, hi=5.0)
    trim(st, 31, 3)
    fill(st, rect(st, 0, 35, 64, 13), silver, "v", lo=1.8, hi=3.8)
    fill(b.top, ones(b.top), ivory, "none", lo=3.0, edge=False)
    fill(b.bottom, ones(b.bottom), silver, "none", lo=2.0, edge=False)


def waist(b, glow):
    st = b.strip
    hem(st, glow, 38)
    fill(b.top, ones(b.top), ivory, "none", lo=3.4, edge=False)


def paint(l1, l2, look):
    glow = look.glow
    head(Box(l1, 0, 0, 8, 8, 8), glow)
    chest(Box(l1, 16, 16, 8, 12, 4), glow)
    arm(Box(l1, 40, 16, 4, 12, 4), glow)
    boots(Box(l1, 0, 16, 4, 12, 4), glow)
    waist(Box(l2, 16, 16, 8, 12, 4), glow)
    legs(Box(l2, 0, 16, 4, 12, 4), glow)
