"""Duskvale worn armor: night-violet robes, a pale crescent moon, pink glow, stars, and hems that dissolve into wisps."""
from armor_art import *
from sculpt import crescent

night, deep, mid = ramp((34, 22, 68)), ramp((70, 40, 120)), ramp((108, 72, 164))
pale, pink = ramp((206, 176, 255)), ramp((242, 122, 192))


def moon(v, x, y, n, glow, rp=pale):
    """A crescent painted at (x, y), n texels across, with a lit rim."""
    m = np.zeros(v.shape[:2], bool)
    c = crescent(n, n * 0.5, n * 0.42, n * 0.24, -n * 0.05)
    h, w = v.shape[:2]
    m[max(0, y):min(h, y + n), max(0, x):min(w, x + n)] = c[:min(n, h - y), :min(n, w - x)]
    fill(v, m, rp, "h", lo=2.4, hi=5.0, edge=False)
    put(v, m & ~erode(m), glow[4])
    return m


def folds(st, rp, period=6, strength=1):
    """Vertical cloth folds: darker and lighter streaks, dithered."""
    h, w = st.shape[:2]
    for x in range(w):
        s = math.sin(x * 2 * math.pi / period)
        if s > 0.55:
            for y in range(0, h, 2):
                if st[y, x, 3]:
                    st[y, x] = (*rp[1], 255)
        elif s < -0.7:
            for y in range(1, h, 2):
                if st[y, x, 3]:
                    st[y, x] = (*rp[4], 255)


def head(b, glow):
    st = b.strip
    fill(st, ones(st), deep, "v", lo=1.4, hi=3.8, edge=False)
    folds(st, deep, 7)
    for x in (0, 32, 64, 96):
        st[:, x] = (*night[0], 255)
    stars(st, rect(st, 0, 0, 128, 28), 16, 3, pale[5], pale[3])
    f = b.face("front")
    op = ell(f, 5, 11, 22, 19)
    fill(f, op, night, "v", lo=0.4, hi=2.0, edge=False)                                  # the hood's dark opening
    put(f, op & ~erode(op), pale[4])
    put(f, erode(op) & ~erode(erode(op)) & (BAYER[np.arange(32)[:, None] % 4, np.arange(32)[None, :] % 4] > 0.5), pale[2])
    for x0 in (9, 18):                                                                    # two glowing eyes
        for y in (21, 22, 23):
            for x in range(x0, x0 + 5):
                px(f, x, y, glow[5] if y == 22 else glow[4])
    moon(f, 10, 2, 12, glow)                                                              # a small moon on the brow
    for n in ("right", "left"):
        g = b.face(n)
        put(g, line(g, 1, 12, 8, 30) & ones(g), pale[3])
    g = b.face("back")
    fill(g, rect(g, 0, 8, 32, 24), night, "v", lo=0.8, hi=2.8, edge=False)
    for k in range(5):
        x = 4 + k * 6
        put(g, line(g, x, 10, x + (k % 2) * 2 - 1, 30), pale[2])
    stars(g, rect(g, 0, 8, 32, 20), 6, 9, pale[5], pale[3])
    fade_alpha(g, 24, 32, 1)
    t = b.top
    fill(t, ones(t), night, "none", lo=1.4, edge=False)
    stars(t, ones(t), 14, 4, pale[5], pale[3])
    moon(t, 8, 8, 16, glow)
    fill(b.bottom, ones(b.bottom), night, "none", lo=1.0, edge=False)


def chest(b, glow):
    st = b.strip
    fill(st, ones(st), deep, "v", lo=1.2, hi=3.8, edge=False)
    folds(st, deep, 8)
    fill(st, rect(st, 0, 0, 96, 6), pale, "v", lo=2.0, hi=4.8, edge=False)               # a pale high collar
    st[6, :] = (*night[0], 255)
    for x in range(3, 94, 8):
        px(st, x, 3, glow[4])
    f = b.face("front")
    moon(f, 7, 8, 19, glow)                                                                # the crescent, kept clear of the arms' overlap
    stars(f, rect(f, 8, 8, 17, 24), 10, 5, pale[5], pale[3])
    for x, y in ((24, 12), (25, 20), (22, 27)):
        px(f, x, y, glow[5])
    fill(st, rect(st, 0, 33, 96, 4), pink, "v", lo=2.2, hi=4.8)                            # a pink sash
    fill(f, ell(f, 12, 31, 8, 8), pale, "v", lo=2.4, hi=5.0)
    fill(f, ell(f, 14, 33, 4, 4), pink, "v", lo=3.0, hi=5.2)
    for n in ("right", "left"):
        g = b.face(n)
        put(g, line(g, 8, 7, 8, 32), pale[2])
        stars(g, rect(g, 0, 8, 16, 24), 3, 7, pale[5], pale[3])
    g = b.face("back")
    fill(g, rect(g, 0, 7, 32, 28), night, "v", lo=0.6, hi=2.6, edge=False)               # a constellation across the back
    pts = [(6, 12), (13, 18), (21, 13), (25, 24), (15, 29), (8, 25)]
    for a, c in zip(pts, pts[1:]):
        put(g, line(g, *a, *c) & ones(g), pale[2])
    for x, y in pts:
        px(g, x, y, pale[5]); px(g, x + 1, y, pale[3]); px(g, x, y + 1, pale[3])
    st[38:, :] = (*deep[2], 255)                                                           # ragged ghost hem: strands that fade out
    for x in range(1, 96, 4):
        st[38:46, x] = (*pale[3], 255)
    fade_alpha(st, 39, 48, 2)
    fill(b.top, ones(b.top), pale, "none", lo=3.0, edge=False)
    fill(b.bottom, ones(b.bottom), night, "none", lo=1.0, edge=False)


def arm(b, glow):
    st = b.strip
    for n in ("right", "front", "left", "back"):
        f = b.face(n)
        fill(f, ones(f), deep, "cyl", lo=1.2, hi=4.0, edge=False)
    folds(st, deep, 8)
    fill(st, rect(st, 0, 0, 64, 9), pale, "v", lo=2.2, hi=5.0)                             # a pale shoulder mantle
    st[9, :] = (*night[0], 255)
    for x in range(2, 64, 4):
        px(st, x, 6, glow[4])
    stars(st, rect(st, 0, 10, 64, 22), 8, 6, pale[5], pale[3])
    fill(st, rect(st, 0, 33, 64, 4), pale, "v", lo=2.2, hi=4.8, edge=False)                 # a pale cuff that dissolves into wisps
    for x in range(1, 64, 3):
        st[37:46, x] = (*pale[3], 255)
    fade_alpha(st, 37, 48, 4)
    fill(b.top, ones(b.top), pale, "none", lo=3.4, edge=False)
    fill(b.bottom, ones(b.bottom), night, "none", lo=1.0, edge=False)


def boots(b, glow):
    st = b.strip
    fill(st[16:32, 16:32], np.ones((16, 16), bool), pale, "none", lo=2.6, edge=False)
    fill(st[16:32, 32:48], np.ones((16, 16), bool), night, "none", lo=1.0, edge=False)
    sub = st[32:48]
    fill(sub, ones(sub), night, "v", lo=0.8, hi=3.0, edge=False)
    fill(sub, rect(sub, 0, 0, 64, 4), pale, "v", lo=2.4, hi=5.0, edge=False)               # a pale cuff
    sub[4, :] = (*deep[0], 255)
    f = b.face("front")[32:48]
    fill(f, rrect(f, 2, 7, 12, 6, 2), deep, "v", lo=1.4, hi=3.6)
    put(f, line(f, 3, 12, 13, 8), pale[3])                                                  # a ribbon tie
    px(f, 8, 8, glow[5]); px(f, 7, 8, glow[4]); px(f, 9, 8, glow[4])
    for x in range(64):
        px(sub, x, 14, deep[1]); px(sub, x, 15, night[0])


def legs(b, glow):
    st = b.strip
    for n in ("right", "front", "left", "back"):
        f = b.face(n)
        fill(f, ones(f), deep, "v", lo=1.2, hi=3.8, edge=False)
    folds(st, deep, 8)
    fill(st, rect(st, 0, 0, 64, 4), pale, "v", lo=2.4, hi=5.0, edge=False)
    f = b.face("front")
    sl = rect(f, 6, 4, 4, 38)
    fill(f, sl, night, "h", lo=0.6, hi=2.2, edge=False)                                    # a slit showing the night beneath
    put(f, line(f, 6, 4, 6, 41), pale[3]); put(f, line(f, 9, 4, 9, 41), pale[3])
    moon(f, 2, 19, 11, glow)
    stars(st, rect(st, 0, 5, 64, 30), 7, 8, pale[5], pale[3])
    for x in range(1, 64, 4):
        st[36:48, x] = (*pale[3], 255)
    fade_alpha(st, 38, 48, 5)
    fill(b.top, ones(b.top), deep, "none", lo=3.0, edge=False)
    fill(b.bottom, ones(b.bottom), night, "none", lo=1.0, edge=False)


def waist(b, glow):
    st = b.strip
    fill(st, rect(st, 0, 33, 96, 4), pink, "v", lo=2.2, hi=4.8)
    st[37:, :] = (*deep[2], 255)
    for x in range(1, 96, 4):
        st[37:46, x] = (*pale[3], 255)
    fade_alpha(st, 39, 48, 2)
    fill(b.top, ones(b.top), pale, "none", lo=3.0, edge=False)


def paint(l1, l2, look):
    glow = look.glow
    head(Box(l1, 0, 0, 8, 8, 8), glow)
    chest(Box(l1, 16, 16, 8, 12, 4), glow)
    arm(Box(l1, 40, 16, 4, 12, 4), glow)
    boots(Box(l1, 0, 16, 4, 12, 4), glow)
    waist(Box(l2, 16, 16, 8, 12, 4), glow)
    legs(Box(l2, 0, 16, 4, 12, 4), glow)
