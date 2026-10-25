"""Rootvale worn armor: bark plates, layered leaf shingles, leather straps, lime glow."""
from armor_art import *

bark, barkd = ramp((112, 82, 52)), ramp((66, 46, 30))
leafA, leafB = ramp((72, 104, 46)), ramp((126, 176, 72))
leather = ramp((176, 134, 72))


def planks(st, seam=6, tone=1):
    fill(st, ones(st), bark, "v", lo=1.8, hi=4.4, edge=False)
    for x in range(seam - 1, st.shape[1], seam):
        st[:, x] = (*bark[1], 255)
        st[::7, x] = (*bark[0], 255)
    rnd = random.Random(5)
    for _ in range(max(2, st.shape[1] // 18)):                                            # a few knots
        x, y = rnd.randrange(2, st.shape[1] - 3), rnd.randrange(3, st.shape[0] - 3)
        fill(st, ell(st, x, y, 3, 2), barkd, "v", lo=1.0, hi=3.0)


def hem(st, glow, y0=38):
    """Leather belt then a fringe of leaves with a ragged transparent bottom edge."""
    fill(st, rect(st, 0, y0 - 6, st.shape[1], 5), leather, "v", lo=2.0, hi=4.8)
    st[y0 - 7, :] = (*barkd[0], 255); st[y0 - 1, :] = (*barkd[0], 255)
    rivets(st, range(3, st.shape[1] - 2, 8), y0 - 5, leather)
    st[y0:, :] = 0
    shingle(st, rect(st, 0, y0, st.shape[1], st.shape[0] - y0), y0, st.shape[0], leafA, leafB, 9, 13, 5, glow)


def head(b, glow):
    st = b.strip
    planks(st)
    shingle(st, rect(st, 0, 3, 128, 13), 3, 16, leafA, leafB, 10, 13, 5, glow)           # a leafy brow
    fill(st, rect(st, 0, 16, 128, 3), leather, "v", lo=2.0, hi=4.8)
    st[15, :] = (*barkd[0], 255); st[19, :] = (*barkd[0], 255)
    rivets(st, range(4, 126, 9), 17, leather)
    f = b.face("front")
    fill(f, rect(f, 4, 21, 24, 9), barkd, "v", lo=1.2, hi=3.2)                           # a bark visor with glowing slits
    for x0 in (6, 19):
        for y in range(23, 26):
            for x in range(x0, x0 + 7):
                px(f, x, y, glow[4] if y < 25 else glow[3])
    fill(f, rect(f, 14, 20, 4, 10), bark, "h", lo=2.0, hi=4.6)                          # a nose guard
    for n in ("right", "left"):
        g = b.face(n)
        shingle(g, rrect(g, 3, 19, 26, 12, 4), 19, 31, leafB, leafA, 9, 11, 4, glow)     # cheek leaves
    g = b.face("back")
    shingle(g, rect(g, 0, 19, 32, 13), 19, 32, leafA, leafB, 10, 13, 5, glow)
    t = b.top
    fill(t, ones(t), leafA, "none", lo=2.0, edge=False)                                  # a rosette of eight leaves
    for k in range(8):
        a = k * math.pi / 4
        c = 15.5
        tip = (c + 14 * math.cos(a), c + 14 * math.sin(a))
        s1 = (c + 7 * math.cos(a + 0.5), c + 7 * math.sin(a + 0.5))
        s2 = (c + 7 * math.cos(a - 0.5), c + 7 * math.sin(a - 0.5))
        fill(t, poly(t, [(c, c), s1, tip, s2]), leafA if k % 2 else leafB, "v", lo=1.8, hi=4.8)
    fill(t, ell(t, 12, 12, 8, 8), glow, "v", lo=2.6, hi=5.2)
    fill(b.bottom, ones(b.bottom), barkd, "none", lo=2.0, edge=False)


def chest(b, glow):
    st = b.strip
    planks(st, 6)
    f = b.face("front")
    shingle(f, poly(f, [(5, 6), (27, 6), (27, 15), (16, 29), (5, 15)]), 6, 29, leafA, leafB, 9, 12, 6, glow)   # a leaf bib
    for x in (4, 24):                                                                     # leather straps with rivets
        fill(f, rect(f, x, 0, 4, 38), leather, "h", lo=2.0, hi=4.6)
        rivets(f, (x + 1,), 6, leather); rivets(f, (x + 1,), 20, leather)
    fill(f, rrect(f, 11, 12, 10, 12, 3), leather, "v", lo=2.0, hi=4.8)                  # a plate with a glowing leaf
    em = np.flipud(leaf(7, 10, bulge=0.5, wide=0.5))
    tile = np.zeros((10, 7, 4), np.uint8)
    shade(tile, em, glow, "v", lo=2.6, hi=5.2)
    for yy in range(2, 8):
        tile[yy, 3] = (*glow[5], 255)
    live = tile[..., 3] > 0
    f[13:23, 12:19][live] = tile[live]
    fill(st, rect(st, 0, 0, 96, 5), leather, "v", lo=2.0, hi=4.8)                         # a leather collar
    st[5, :] = (*barkd[0], 255)
    shingle(st, rect(st, 0, 5, 96, 6), 5, 11, leafB, leafA, 8, 8, 3, None)
    for n in ("right", "left"):
        g = b.face(n)
        fill(g, rect(g, 6, 8, 4, 28), leather, "h", lo=2.0, hi=4.4)
        rivets(g, (7,), 14, leather)
    g = b.face("back")
    for mx in range(3, 30, 5):                                                              # moss specks on the dark back
        px(g, mx, 34, leafA[3]); px(g, mx + 2, 33, leafA[4])
    for pts in ([(2, 6), (7, 6), (30, 30), (25, 30)], [(30, 6), (25, 6), (2, 30), (7, 30)]):   # crossed straps for the shield
        fill(g, poly(g, pts), leather, "h", lo=2.0, hi=4.6)
    fill(g, ell(g, 12, 13, 8, 8), barkd, "v", lo=1.4, hi=3.4)
    fill(g, ell(g, 14, 15, 4, 4), glow, "v", lo=2.6, hi=5.2)
    hem(st, glow, 38)
    fill(b.top, ones(b.top), leafB, "none", lo=3.4, edge=False)
    fill(b.bottom, ones(b.bottom), barkd, "none", lo=2.0, edge=False)


def arm(b, glow):
    st = b.strip
    planks(st, 5)
    shingle(st, rect(st, 0, 0, 64, 16), 0, 16, leafB, leafA, 14, 16, 7, glow)             # big leaves over the shoulder only
    fill(st, rect(st, 0, 33, 64, 8), leather, "v", lo=2.0, hi=4.6)                         # a leather bracer
    st[32, :] = (*barkd[0], 255); st[41, :] = (*barkd[0], 255)
    rivets(st, range(3, 62, 8), 35, leather)
    for x in range(0, 64, 8):
        st[34:40, x] = (*leather[1], 255)
    fill(st, rect(st, 0, 42, 64, 6), barkd, "v", lo=1.6, hi=3.8)                          # a wrapped hand
    for x in range(0, 64, 5):
        px(st, x, 44, leather[3]); px(st, x + 2, 46, leather[3])
    fill(b.top, ones(b.top), leafB, "none", lo=3.6, edge=False)
    fill(b.bottom, ones(b.bottom), barkd, "none", lo=2.0, edge=False)


def boots(b, glow):
    st = b.strip
    fill(st[16:32, 16:32], np.ones((16, 16), bool), leafA, "none", lo=2.4, edge=False)
    fill(st[16:32, 32:48], np.ones((16, 16), bool), barkd, "none", lo=2.0, edge=False)
    sub = st[32:48]
    fill(sub, ones(sub), bark, "v", lo=1.6, hi=4.0, edge=False)
    for x in range(5, 64, 6):
        sub[:, x] = (*bark[1], 255)
    shingle(sub, rect(sub, 0, 0, 64, 7), 0, 7, leafB, leafA, 8, 8, 3, None)              # a leaf cuff
    fill(sub, rect(sub, 0, 7, 64, 2), leather, "v", lo=2.0, hi=4.6, edge=False)
    f = b.face("front")[32:48]
    for k in range(3):                                                                    # crossed laces
        put(f, line(f, 6, 10 + k * 2, 12, 10 + k * 2 + 1), leather[4])
    fill(sub, rect(sub, 0, 13, 64, 3), barkd, "v", lo=1.2, hi=2.8, edge=False)
    for x in range(1, 64, 5):
        px(sub, x, 12, leafA[3]); px(sub, x + 2, 11, leafB[3])                           # moss


def legs(b, glow):
    st = b.strip
    fill(st, ones(st), barkd, "v", lo=1.6, hi=3.8, edge=False)
    for x in range(5, 64, 6):
        st[:, x] = (*barkd[0], 255)
    shingle(st, rect(st, 0, 0, 64, 19), 0, 19, leafA, leafB, 9, 11, 5, glow)                # leaves over the thighs only
    f = b.face("front")
    fill(f, rrect(f, 3, 19, 10, 11, 3), bark, "cyl", lo=1.8, hi=4.8)                     # a bark knee plate
    rivets(f, (5, 10), 21, leather)
    fill(st, rect(st, 0, 30, 64, 4), leather, "v", lo=2.0, hi=4.6)                        # a garter
    st[29, :] = (*barkd[0], 255); st[34, :] = (*barkd[0], 255)
    fill(st, rect(st, 0, 35, 64, 13), barkd, "v", lo=1.4, hi=3.4)
    fill(b.top, ones(b.top), leafA, "none", lo=3.0, edge=False)
    fill(b.bottom, ones(b.bottom), barkd, "none", lo=2.0, edge=False)


def waist(b, glow):
    st = b.strip
    hem(st, glow, 38)
    fill(b.top, ones(b.top), leafB, "none", lo=3.4, edge=False)


def paint(l1, l2, look):
    glow = look.glow
    head(Box(l1, 0, 0, 8, 8, 8), glow)
    chest(Box(l1, 16, 16, 8, 12, 4), glow)
    arm(Box(l1, 40, 16, 4, 12, 4), glow)
    boots(Box(l1, 0, 16, 4, 12, 4), glow)
    waist(Box(l2, 16, 16, 8, 12, 4), glow)
    legs(Box(l2, 0, 16, 4, 12, 4), glow)
