"""Tideforge worn armor: pearl shell plates, blue enamel, steel harness, a white depth band with glowing ticks."""
from armor_art import *

pearl, enamel, navy = ramp((222, 232, 244)), ramp((50, 130, 196)), ramp((24, 60, 120))
steel, dark, cyan = ramp((118, 132, 152)), ramp((52, 62, 80)), ramp((70, 200, 235))
bandr = ramp((238, 244, 251))


def lower(st, glow, y_band=34):
    """The depth band and the scalloped enamel hem shared by the chestplate and the leggings' waist."""
    m = rect(st, 0, y_band, st.shape[1], 8)
    fill(st, m, bandr, "v", lo=2.6, hi=5.2, edge=False)
    st[y_band - 1, :] = (*navy[0], 255)
    st[y_band + 8, :] = (*navy[0], 255)
    for x in range(2, st.shape[1], 4):
        px(st, x, y_band + 3, glow[4]); px(st, x, y_band + 4, glow[3])
    hem = rect(st, 0, y_band + 9, st.shape[1], 5) & ~wave(st, y_band + 11, 2, 8, below=True) | rect(st, 0, y_band + 9, st.shape[1], 2)
    st[y_band + 9:, :] = 0
    fill(st, hem, enamel, "v", lo=1.6, hi=4.6)
    for x in range(1, st.shape[1], 8):
        px(st, x + 3, y_band + 10, cyan[5])


def head(b, glow):
    st = b.strip
    fill(st, ones(st), pearl, "v", lo=2.2, hi=4.9, edge=False)
    for x in (0, 32, 64, 96):
        st[:, x] = (*pearl[1], 255)
    fill(st, rect(st, 0, 10, 128, 6), enamel, "v", lo=2.0, hi=4.8, edge=False)           # the enamel band round the helm
    st[9, :] = (*navy[0], 255); st[16, :] = (*navy[0], 255)
    rivets(st, range(4, 126, 8), 12, steel)
    f = b.face("front")
    fill(f, poly(f, [(12, 2), (20, 2), (16, 8)]), enamel, "v", lo=2.0, hi=4.8)           # a chevron on the brow
    px(f, 16, 4, glow[4])
    fill(f, rrect(f, 3, 17, 26, 11, 3), steel, "v", lo=2.0, hi=4.6)                      # visor frame and dark glass
    fill(f, rrect(f, 5, 19, 22, 7, 2), navy, "v", lo=1.0, hi=3.4)
    for k in (8, 14, 20):
        put(f, line(f, k, 25, k + 4, 19) & rrect(f, 5, 19, 22, 7, 2), navy[4])
    for x in range(7, 25):
        px(f, x, 22, glow[4]); px(f, x, 23, glow[3]) if x % 2 else None                  # the glowing slit
    px(f, 4, 18, glow[5]); px(f, 27, 18, glow[5])
    fill(f, rect(f, 6, 28, 20, 4), pearl, "v", lo=2.0, hi=4.4)                           # chin guard with vents
    for x in (11, 16, 21):
        f[29:32, x] = (*dark[1], 255)
    for n in ("right", "left"):
        g = b.face(n)
        fill(g, ell(g, 8, 15, 16, 16), steel, "v", lo=2.2, hi=5.0)                       # a porthole on each cheek
        fill(g, ell(g, 11, 18, 10, 10), cyan, "v", lo=1.4, hi=5.0)
        px(g, 13, 20, glow[5]); px(g, 14, 20, glow[5]); px(g, 13, 21, glow[5])
        for x, y in ((9, 22), (22, 22), (15, 16), (15, 29)):
            px(g, x, y, steel[5])
    g = b.face("back")
    fill(g, rrect(g, 5, 17, 22, 13, 2), steel, "v", lo=1.8, hi=4.6)                      # mounting plate for the tanks
    for cx in (8, 17):
        fill(g, ell(g, cx, 20, 7, 7), dark, "v", lo=1.0, hi=3.0)
        put(g, ell(g, cx, 20, 7, 7) & ~erode(ell(g, cx, 20, 7, 7)), cyan[4])
    rivets(g, (7, 23), 18, steel); rivets(g, (7, 23), 28, steel)
    t = b.top
    fill(t, ones(t), pearl, "none", lo=3.4, edge=False)
    fill(t, ell(t, 4, 4, 24, 24), pearl, "none", lo=4.4, edge=False)
    fill(t, rect(t, 13, 0, 6, 32), enamel, "h", lo=2.0, hi=4.6, edge=False)
    rivets(t, (14,), 6, steel); rivets(t, (14,), 24, steel)
    fill(t, ell(t, 13, 13, 6, 6), cyan, "v", lo=3.0, hi=5.0)
    fill(b.bottom, ones(b.bottom), dark, "none", lo=2.0, edge=False)


def chest(b, glow):
    st = b.strip
    fill(st, ones(st), pearl, "v", lo=2.2, hi=4.8, edge=False)
    fill(st, rect(st, 0, 0, 96, 6), steel, "v", lo=2.0, hi=4.8, edge=False)              # collar ring
    st[6, :] = (*navy[0], 255)
    rivets(st, range(3, 94, 8), 2, steel)
    f = b.face("front")
    for x in (1, 17):
        fill(f, rrect(f, x, 8, 14, 24, 4), pearl, "cyl", lo=2.4, hi=5.0)                 # the two breastplates
    fill(f, rect(f, 14, 6, 4, 28), enamel, "h", lo=2.0, hi=4.8)                          # an enamel keel between them
    for y in range(9, 32, 5):
        px(f, 15, y, glow[4]); px(f, 16, y, glow[3])
    for pts in ([(1, 7), (6, 7), (16, 28), (11, 28)], [(31, 7), (26, 7), (16, 28), (21, 28)]):   # harness straps
        fill(f, poly(f, pts), steel, "h", lo=1.8, hi=4.6)
    fill(f, ell(f, 11, 15, 10, 10), steel, "v", lo=2.0, hi=5.0)                          # a round buckle with a lit core
    fill(f, ell(f, 13, 17, 6, 6), cyan, "v", lo=2.6, hi=5.2)
    px(f, 15, 19, glow[5]); px(f, 16, 19, glow[5])
    for n in ("right", "left"):
        g = b.face(n)
        fill(g, rect(g, 2, 8, 12, 24), enamel, "cyl", lo=1.8, hi=4.8)
        fill(g, rect(g, 5, 15, 6, 3), steel, "v"); rivets(g, (6, 9), 16, steel)
    g = b.face("back")
    fill(g, rrect(g, 2, 8, 28, 24, 3), steel, "v", lo=1.6, hi=4.4)                      # back plate behind the tanks
    for y in (13, 24):
        fill(g, rect(g, 3, y, 26, 4), dark, "v", lo=2.0, hi=4.4)
        rivets(g, (5, 25), y + 1, steel)
    fill(g, rect(g, 15, 8, 2, 24), dark, "h", lo=1.4, hi=3.4, edge=False)
    lower(st, glow)
    fill(b.top, ones(b.top), pearl, "none", lo=3.8, edge=False)
    fill(b.bottom, ones(b.bottom), dark, "none", lo=2.0, edge=False)


def arm(b, glow):
    st = b.strip
    for n in ("right", "front", "left", "back"):
        f = b.face(n)
        fill(f, ones(f), enamel, "cyl", lo=1.6, hi=4.9, edge=False)
    shell = rect(st, 0, 0, 64, 9) | (wave(st, 9, 2.5, 8, below=True) & rect(st, 0, 0, 64, 16))
    fill(st, shell, pearl, "v", lo=2.2, hi=5.0)                                          # a scalloped pearl pauldron
    for x in range(4, 64, 8):
        st[2:12, x] = (*pearl[2], 255)
    rivets(st, (6, 22, 38, 54), 4, steel)
    fill(st, rect(st, 0, 33, 64, 6), bandr, "v", lo=2.6, hi=5.2, edge=False)             # a white cuff band
    st[32, :] = (*navy[0], 255); st[39, :] = (*navy[0], 255)
    for x in range(2, 64, 4):
        px(st, x, 36, glow[4])
    fill(st, rect(st, 0, 40, 64, 8), steel, "v", lo=1.8, hi=4.6)                         # steel gauntlet
    for x in range(3, 64, 4):
        st[41:46, x] = (*dark[1], 255)
    fill(b.top, ones(b.top), pearl, "none", lo=3.8, edge=False)
    fill(b.bottom, ones(b.bottom), steel, "none", lo=2.0, edge=False)


def boots(b, glow):
    st = b.strip
    for x0 in (16, 32):                                                                    # the boot's top and bottom faces live in rows 16..32
        fill(st[16:32, x0:x0 + 16], np.ones((16, 16), bool), enamel if x0 == 16 else dark, "none", lo=2.4, edge=False)
    fill(st, rect(st, 0, 32, 64, 4), enamel, "v", lo=2.0, hi=4.8, edge=False)           # an enamel cuff
    fill(st, rect(st, 0, 36, 64, 2), bandr, "v", lo=3.0, hi=5.2, edge=False)             # a white band
    st[35, :] = (*navy[0], 255)
    fill(st, rect(st, 0, 38, 64, 8), dark, "cyl", lo=1.4, hi=3.8, edge=False)            # a dark steel shaft
    fill(st, rect(st, 0, 46, 64, 2), dark, "none", lo=1.4, edge=False)
    for x in range(64):
        px(st, x, 45, glow[4])                                                            # a lit sole line
    f = b.face("front")
    fill(f, rrect(f, 3, 39, 10, 6, 2), pearl, "v", lo=2.4, hi=5.0)                      # a pearl toe cap
    for x in (2, 7, 12):
        px(st, x, 33, steel[5])


def legs(b, glow):
    st = b.strip
    for n in ("right", "front", "left", "back"):
        f = b.face(n)
        fill(f, ones(f), enamel, "cyl", lo=1.6, hi=4.9, edge=False)
    fill(st, rect(st, 0, 0, 64, 6), bandr, "v", lo=2.6, hi=5.2, edge=False)             # the hip band
    st[6, :] = (*navy[0], 255)
    for x in range(2, 64, 4):
        px(st, x, 3, glow[4])
    f = b.face("front")
    kn = rrect(f, 2, 19, 12, 12, 4) | (wave(f, 18, 1.5, 6, below=True) & rect(f, 2, 17, 12, 8))
    fill(f, kn & rect(f, 2, 17, 12, 15), pearl, "cyl", lo=2.4, hi=5.0)                  # a pearl knee shell
    for ex in (3, 8, 12):                                                                # ribs fanning out like a scallop shell
        put(f, line(f, 8, 19, ex, 30) & kn, pearl[1])
    for x in (4, 8, 11):
        px(f, x, 28, glow[4])
    for n in ("right", "left"):
        g = b.face(n)
        fill(g, rect(g, 6, 8, 4, 34), steel, "h", lo=1.8, hi=4.4, edge=False)
        rivets(g, (7,), 12, steel); rivets(g, (7,), 34, steel)
    for y in (34, 38):
        fill(st, rect(st, 0, y, 64, 2), dark, "v", lo=1.6, hi=3.8, edge=False)
    fill(b.top, ones(b.top), enamel, "none", lo=3.0, edge=False)
    fill(b.bottom, ones(b.bottom), dark, "none", lo=2.0, edge=False)


def waist(b, glow):
    st = b.strip
    lower(st, glow)
    fill(b.top, ones(b.top), pearl, "none", lo=3.8, edge=False)


def paint(l1, l2, look):
    glow = look.glow
    head(Box(l1, 0, 0, 8, 8, 8), glow)
    chest(Box(l1, 16, 16, 8, 12, 4), glow)
    arm(Box(l1, 40, 16, 4, 12, 4), glow)
    boots(Box(l1, 0, 16, 4, 12, 4), glow)
    waist(Box(l2, 16, 16, 8, 12, 4), glow)
    legs(Box(l2, 0, 16, 4, 12, 4), glow)
