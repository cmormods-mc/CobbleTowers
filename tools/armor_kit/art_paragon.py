"""Paragon worn armor: bevelled ivory plate with gold edging over dark padded mail, engraved panel lines, a sun on the chest, gold
filigree and glowing inlay. Every plate is drawn the same way: shaded body, a seam line, a gold inner edge, rivets at the corners."""
from armor_art import *

ivory, silver = ramp((240, 236, 246)), ramp((196, 204, 228))
under, mail = ramp((46, 54, 86)), ramp((108, 120, 160))
gold, goldd = ramp((252, 214, 100)), ramp((190, 142, 52))
SEAM = under[0]


def plate(v, mask, rp=None, groove=True, trim=True, lo=2.6, hi=5.0, mode="v"):
    """One armor plate: shaded body, dark seam, gold inner edge, an engraved groove just inside it."""
    rp = ivory if rp is None else rp
    SEAM = under[0]
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


def corner_rivets(v, mask, rp=None):
    rp = gold if rp is None else rp
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


EMBLEM = sun


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
    """A knight's great helm: domed plate, a cross of gold (vertical ridge, horizontal eye slit), breath holes, rivets, a gold rim."""
    st = b.strip
    mailbase(st)
    for n in ("right", "front", "left", "back"):
        f = b.face(n)
        h, w = f.shape[:2]
        plate(f, rect(f, 1, 1, w - 2, h - 2), mode="cyl")
        band(f, h - 4, 3, 1, w - 1)                                            # a gold rim round the base
        band(f, 1, 2, 1, w - 1)                                                # and a thin one over the brow
        if n == "front":
            fill(f, rect(f, 4, 13, w - 8, 5), under, "v", lo=1.2, hi=3.0)       # the eye slit
            for x in range(6, w - 6):
                if abs(x - w // 2) > 1:
                    px(f, x, 15, glow[4]); px(f, x, 16, glow[2])
            f[3:h - 4, w // 2 - 1:w // 2 + 1] = (*gold[4], 255)                 # the vertical ridge, crossing the slit
            f[3:h - 4, w // 2 - 1] = (*gold[5], 255)
            for x in (5, 8, 11, 20, 23, 26):                                    # breath holes either side
                for y in (21, 24, 27):
                    px(f, x, y, under[0]); px(f, x, y + 1, under[1])
            corner_rivets(f, rect(f, 1, 3, w - 2, h - 8))
        elif n in ("right", "left"):
            f[7:h - 5, w // 2 - 1:w // 2 + 1] = (*gold[4], 255)                 # a gold strap down the side
            gem(f, w // 2, 14, glow, 2)
            for y in (6, 22):
                rivets(f, (w // 2 - 6, w // 2 + 4), y, gold)
        else:
            f[3:h - 4, w // 2 - 1:w // 2 + 1] = (*gold[4], 255)                  # a gold ridge down the back
            for y in (8, 16, 24):
                rivets(f, (w // 2 - 7, w // 2 + 5), y, gold)
    t = b.top
    shade(t, ones(t), ivory, "cyl", lo=3.4, edge=False)                        # the dome, a gold ridge running front to back
    t[:, 15:17] = (*gold[4], 255)
    fill(t, ell(t, 12, 12, 8, 8), gold, "v", lo=3.0, hi=5.0)
    fill(t, ell(t, 14, 14, 4, 4), glow, "v", lo=2.8, hi=5.2, edge=False)
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
            EMBLEM(f, 16, 13, 7, glow)
            for k in range(3):                                                    # three separate lames, each with a pointed lower edge
                y = 29 + k * 5
                plate(f, poly(f, [(3, y), (28, y), (28, y + 3), (16, y + 5), (3, y + 3)]), groove=False)
            band(f, 44, 4, 0, w)
            gem(f, 16, 46, glow, 1)
        elif n in ("right", "left"):
            fill(f, rect(f, 6, 0, 4, h), gold, "h", lo=3.2, hi=5.0, edge=False)
            f[:, 6] = (*gold[5], 255); f[:, 9] = (*goldd[2], 255)
            rivets(f, (7,), 8, gold); rivets(f, (7,), 24, gold)
        else:
            plate(f, rrect(f, 2, 2, w - 4, 38, 5), mode="cyl")                    # the root plate the wings grow from
            for pts in ([(4, 5), (8, 5), (28, 36), (24, 36)], [(28, 5), (24, 5), (4, 36), (8, 36)]):
                fill(f, poly(f, pts), gold, "h", lo=3.2, hi=5.0, edge=False)
            f[2:38, w // 2 - 1:w // 2 + 1] = (*gold[4], 255)
            fill(f, ell(f, 8, 5, 16, 16), goldd, "v", lo=2.6, hi=4.6)             # the halo socket, over the straps
            fill(f, ell(f, 11, 8, 10, 10), gold, "v", lo=3.0, hi=5.0)
            fill(f, ell(f, 13, 10, 6, 6), glow, "v", lo=2.8, hi=5.2, edge=False)
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


def themed(look_paint, l1, l2, look, plate_c, silver_c, under_c, mail_c, gold_c, goldd_c, emblem):
    """Paints the whole set with another palette and chest emblem, then restores Paragon's."""
    g = globals()
    keep = {k: g[k] for k in ("ivory", "silver", "under", "mail", "gold", "goldd", "SEAM", "EMBLEM")}
    try:
        g.update(ivory=ramp(plate_c), silver=ramp(silver_c), under=ramp(under_c), mail=ramp(mail_c), gold=ramp(gold_c), goldd=ramp(goldd_c), EMBLEM=emblem)
        g["SEAM"] = g["under"][0]
        look_paint(l1, l2, look)
    finally:
        g.update(keep)


def paint(l1, l2, look):
    glow = look.glow
    head(Box(l1, 0, 0, 8, 8, 8), glow)
    chest(Box(l1, 16, 16, 8, 12, 4), glow)
    arm(Box(l1, 40, 16, 4, 12, 4), glow)
    boots(Box(l1, 0, 16, 4, 12, 4), glow)
    waist(Box(l2, 16, 16, 8, 12, 4), glow)
    legs(Box(l2, 0, 16, 4, 12, 4), glow)
