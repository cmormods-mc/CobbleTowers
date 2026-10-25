"""Paragon worn armor: bevelled ivory plate with gold edging over dark padded mail, engraved panel lines, a sun on the chest, gold
filigree and glowing inlay. Every plate is drawn the same way: shaded body, a seam line, a gold inner edge, rivets at the corners."""
from armor_art import *

ivory, silver = ramp((240, 236, 246)), ramp((196, 204, 228))
under, mail = ramp((46, 54, 86)), ramp((108, 120, 160))
gold, goldd = ramp((252, 214, 100)), ramp((190, 142, 52))
SEAM = under[0]


def plate(v, mask, rp=ivory, groove=True, trim=True, lo=2.6, hi=5.0, mode="v"):
    """One armor plate: shaded body, dark seam, gold inner edge, an engraved groove just inside it."""
    shade(v, mask, rp, mode, lo, hi, edge=True)
    put(v, mask & ~erode(mask), SEAM)
    e1 = erode(mask)
    if trim:
        put(v, e1 & ~erode(e1), gold[3])
        e2 = erode(e1)
    else:
        e2 = e1
    if groove:
        g = erode(e2)
        put(v, e2 & ~g, rp[1])
        put(v, shift(e2 & ~g, 1, 0) & g, rp[5])                       # a lit lip under the groove


def corner_rivets(v, mask, rp=gold):
    ys, xs = np.nonzero(mask)
    if not len(ys):
        return
    for (x, y) in ((xs.min() + 3, ys.min() + 3), (xs.max() - 4, ys.min() + 3), (xs.min() + 3, ys.max() - 4), (xs.max() - 4, ys.max() - 4)):
        px(v, x, y, rp[5]); px(v, x + 1, y, rp[4]); px(v, x, y + 1, rp[3]); px(v, x + 1, y + 1, rp[1])


def band(v, y, h, x0=0, x1=None):
    """A gold band with a lit top row and a shaded bottom row."""
    x1 = v.shape[1] if x1 is None else x1
    m = rect(v, x0, y, x1 - x0, h)
    shade(v, m, gold, "v", lo=3.0, hi=5.0, edge=False)
    v[y, x0:x1] = (*gold[5], 255)
    v[y + h - 1, x0:x1] = (*goldd[2], 255)


def gem(v, cx, cy, glow, r=2):
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if abs(dx) + abs(dy) <= r:
                px(v, cx + dx, cy + dy, glow[5] if abs(dx) + abs(dy) <= r - 2 else glow[4] if abs(dx) + abs(dy) < r else gold[3])


def scroll(v, x0, x1, y, amp=1.6, period=7.0):
    """A line of gold filigree: a sine curl with a dot in every loop."""
    for x in range(x0, x1):
        yy = int(round(y + amp * math.sin((x - x0) * 2 * math.pi / period)))
        px(v, x, yy, gold[4])
        if (x - x0) % int(period) == int(period) // 2:
            px(v, x, int(round(y - amp - 1.5)), gold[5])


def sun(f, cx, cy, r, glow):
    """A gold-ringed disc with a glowing core and eight rays."""
    yy, xx = np.mgrid[0:f.shape[0], 0:f.shape[1]]
    d = np.hypot(xx - cx, yy - cy)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        for t in range(int(r) + 1, int(r) + 5):
            px(f, int(round(cx + t * math.cos(a))), int(round(cy + t * math.sin(a))), gold[4 if t < r + 3 else 3])
    shade(f, d <= r, gold, "v", lo=3.0, hi=5.0, edge=True)
    shade(f, d <= r - 2.5, ivory, "v", lo=3.2, hi=5.0, edge=False)
    shade(f, d <= r - 4.5, glow, "v", lo=2.8, hi=5.2, edge=False)


def mailbase(st):
    """The padded under-layer: dark quilting, a soft horizontal gradient with stitched rows and staggered seams."""
    fill(st, ones(st), under, "v", lo=2.0, hi=3.4, edge=False)
    h, w = st.shape[:2]
    for y in range(3, h, 6):
        st[y, :] = (*under[1], 255)
        for x in range((y // 6 % 2) * 4, w, 8):
            st[y + 1:y + 5, x] = (*under[1], 255)
    for y in range(4, h, 6):
        st[y, :] = (*under[3], 255)
    st[:, ::32] = (*under[1], 255)


def head(b, glow):
    st = b.strip
    mailbase(st)
    for n in ("right", "front", "left", "back"):
        f = b.face(n)
        h, w = f.shape[:2]
        if n == "front":
            plate(f, rect(f, 1, 1, w - 2, h - 2), mode="cyl")
            band(f, 2, 6, 3, w - 3)                                           # a crown band set with gems
            for x in (8, 15, 22):
                gem(f, x, 5, glow, 1)
            fill(f, rect(f, 4, 14, w - 8, 9), under, "v", lo=1.2, hi=3.0)       # the visor, with a glowing slit
            for x in range(6, w - 6):
                px(f, x, 17, glow[3]); px(f, x, 18, glow[5]); px(f, x, 19, glow[4])
            band(f, 8, 22, 14, 18)                                             # the nose guard, gold, with a lit ridge
            for x in range(8, 25, 4):                                          # breathing slits on the chin plate
                for y in range(25, 29):
                    px(f, x, y, under[0])
            corner_rivets(f, rect(f, 1, 9, w - 2, 21))
        elif n in ("right", "left"):
            plate(f, rrect(f, 2, 3, w - 4, h - 6, 3), mode="cyl")
            for k, c in enumerate((gold[5], gold[4], gold[3])):               # three swept feather strokes
                for t in range(15):
                    px(f, 8 + t, 22 - t + k * 2, c)
            gem(f, 18, 12, glow, 2)
            corner_rivets(f, rrect(f, 2, 3, w - 4, h - 6, 3))
        else:
            plate(f, rect(f, 1, 1, w - 2, 22), mode="cyl")
            band(f, 2, 3, 3, w - 3)
            f[3:22, w // 2 - 1:w // 2 + 1] = (*gold[4], 255)                  # a gold ridge down the back
            scroll(f, 4, w // 2 - 3, 12)
            scroll(f, w // 2 + 3, w - 4, 12)
            fill(f, rect(f, 2, 24, w - 4, 7), under, "v", lo=1.4, hi=3.2)     # a mail neck guard
    t = b.top
    shade(t, ones(t), ivory, "none", lo=3.4, edge=False)                       # a sunburst with a glowing seed
    c = 15.5
    for k in range(12):
        a = k * math.pi / 6
        fill(t, poly(t, [(c + 5 * math.cos(a - .22), c + 5 * math.sin(a - .22)), (c + 14.5 * math.cos(a), c + 14.5 * math.sin(a)),
                         (c + 5 * math.cos(a + .22), c + 5 * math.sin(a + .22))]), gold, "v", lo=3.0, hi=5.0, edge=False)
    fill(t, ell(t, 11, 11, 10, 10), gold, "v", lo=3.0, hi=5.0)
    fill(t, ell(t, 13, 13, 6, 6), glow, "v", lo=2.8, hi=5.2, edge=False)
    fill(b.bottom, ones(b.bottom), under, "none", lo=2.0, edge=False)


def chest(b, glow):
    st = b.strip
    mailbase(st)
    for n in ("right", "front", "left", "back"):
        f = b.face(n)
        h, w = f.shape[:2]
        if n == "front":
            band(f, 0, 5, 0, w)                                                  # a gold gorget
            for x in (7, 24):
                gem(f, x, 2, glow, 1)
            plate(f, poly(f, [(3, 6), (28, 6), (28, 21), (16, 31), (3, 21)]), mode="cyl")
            f[8:27, w // 2 - 1:w // 2 + 1] = (*gold[4], 255)                       # a keel down the centre
            sun(f, 16, 13, 7, glow)
            for k in range(3):                                                    # three lames below, each with a pointed lower edge
                y = 30 + k * 5
                plate(f, poly(f, [(2, y), (29, y), (29, y + 4), (16, y + 6), (2, y + 4)]), groove=False)
            band(f, 44, 4, 0, w)
            gem(f, 16, 46, glow, 1)
        elif n in ("right", "left"):
            fill(f, rect(f, 5, 0, 4, h), gold, "h", lo=3.0, hi=5.0)
            rivets(f, (6,), 8, gold); rivets(f, (6,), 24, gold)
        else:
            plate(f, rrect(f, 2, 2, w - 4, 38, 5), mode="cyl")                    # the root plate the wings grow from
            for pts in ([(3, 4), (8, 4), (29, 36), (24, 36)], [(29, 4), (24, 4), (3, 36), (8, 36)]):
                fill(f, poly(f, pts), gold, "h", lo=3.0, hi=5.0)
            fill(f, ell(f, 9, 6, 14, 14), goldd, "v", lo=2.4, hi=4.4)             # the halo socket
            fill(f, ell(f, 12, 9, 8, 8), glow, "v", lo=2.8, hi=5.2, edge=False)
            f[2:38, w // 2 - 1:w // 2 + 1] = (*gold[4], 255)
            band(f, 41, 4, 0, w)
    fill(b.top, ones(b.top), ivory, "none", lo=3.4, edge=False)
    fill(b.bottom, ones(b.bottom), under, "none", lo=2.0, edge=False)


def arm(b, glow):
    st = b.strip
    mailbase(st)
    for n in ("right", "front", "left", "back"):
        f = b.face(n)
        h, w = f.shape[:2]
        plate(f, rect(f, 0, 0, w, 14), mode="cyl")                               # the pauldron shell
        for x in range(0, w, 4):                                                 # a scalloped gold lower edge
            y = 13 + (1 if (x // 4) % 2 else 0)
            f[y - 1:y + 1, x:x + 4] = (*gold[4], 255)
        rivets(f, (w // 2 - 1,), 5, gold)
        band(f, 17, 3, 0, w)                                                     # a gold ring below it
        plate(f, rrect(f, 1, 24, w - 2, 8, 2), mode="cyl", groove=False)         # an elbow cop
        plate(f, rect(f, 0, 34, w, 10), mode="cyl")                              # the bracer
        band(f, 34, 2, 0, w)
        band(f, 42, 2, 0, w)
        for x in range(2, w - 2, 5):
            px(f, x, 39, gold[3]); px(f, x, 40, gold[2])
        band(f, 44, 4, 0, w)
    t = b.top
    shade(t, ones(t), ivory, "none", lo=3.6, edge=False)
    fill(t, ell(t, 3, 3, 10, 10), gold, "v", lo=3.0, hi=5.0)
    fill(t, ell(t, 6, 6, 4, 4), glow, "v", lo=2.8, hi=5.2, edge=False)
    fill(b.bottom, ones(b.bottom), under, "none", lo=2.0, edge=False)


def boots(b, glow):
    st = b.strip
    fill(b.top, ones(b.top), ivory, "none", lo=2.8, edge=False)
    fill(b.bottom, ones(b.bottom), under, "none", lo=2.0, edge=False)
    sub = st[32:48]
    mailbase(sub)
    for n in ("right", "front", "left", "back"):
        f = b.face(n)[32:48]
        h, w = f.shape[:2]
        band(f, 0, 3, 0, w)                                                      # a gold cuff
        plate(f, rect(f, 0, 3, w, 10), mode="cyl", groove=False)
        for x in range(2, w - 2, 5):
            px(f, x, 8, gold[3])
        fill(f, rect(f, 0, 13, w, 3), under, "v", lo=1.4, hi=3.0, edge=False)   # the sole


def legs(b, glow):
    st = b.strip
    mailbase(st)
    for n in ("right", "front", "left", "back"):
        f = b.face(n)
        h, w = f.shape[:2]
        plate(f, rect(f, 0, 1, w, 19), mode="cyl")                               # a cuisse
        scroll(f, 2, w - 2, 10, 1.2, 6.0)
        band(f, 21, 2, 0, w)
        plate(f, rect(f, 0, 23, w, 9), gold, mode="cyl", groove=False, trim=False)    # a gold knee cop with a glowing gem
        gem(f, w // 2, 27, glow, 2)
        plate(f, rect(f, 0, 33, w, 12), mode="cyl")                              # a greave with a centre ridge
        f[34:44, w // 2 - 1:w // 2 + 1] = (*gold[4], 255)
        band(f, 44, 4, 0, w)
    fill(b.top, ones(b.top), ivory, "none", lo=3.0, edge=False)
    fill(b.bottom, ones(b.bottom), under, "none", lo=2.0, edge=False)


def waist(b, glow):
    st = b.strip
    sub = st[32:48]
    mailbase(sub)
    for n in ("right", "front", "left", "back"):
        f = b.face(n)[32:48]
        h, w = f.shape[:2]
        band(f, 0, 5, 0, w)                                                      # a gold belt
        rivets(f, range(3, w - 2, 8), 1, gold)
        for x in range(0, w, 8):                                                 # pointed ivory lames hanging below
            plate(f, poly(f, [(x, 5), (x + 8, 5), (x + 8, 11), (x + 4, 15), (x, 11)]), groove=False)
    gem(b.face("front")[32:48], b.face("front").shape[1] // 2, 2, glow, 2)
    fill(b.top, ones(b.top), ivory, "none", lo=3.4, edge=False)


def paint(l1, l2, look):
    glow = look.glow
    head(Box(l1, 0, 0, 8, 8, 8), glow)
    chest(Box(l1, 16, 16, 8, 12, 4), glow)
    arm(Box(l1, 40, 16, 4, 12, 4), glow)
    boots(Box(l1, 0, 16, 4, 12, 4), glow)
    waist(Box(l2, 16, 16, 8, 12, 4), glow)
    legs(Box(l2, 0, 16, 4, 12, 4), glow)
