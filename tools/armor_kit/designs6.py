"""Paragon helmet, designed as one object (it replaces the vanilla head box): a clean ten-unit shell with a hipped roof, a slit visor
band, a gold brow with a diamond, a nose guard, a chin plate, angled cheek guards, a neck guard and one crest. The richness is in the
painted shell (visor, crown band, ear gems, vents, back ridge, a sunburst on the roof); the few extra boxes only shape the silhouette."""
import math
import numpy as np
from sculpt import *
import art_paragon as ap
from armor_art import fill, rect, rrect, poly, ell, px
from designs5 import _paint, ivory, silver, gold, goldd, slate

H = "armorHead"


def make(look):
    glow = look.glow
    base = {s: _paint(s, glow) for s in ("plate", "silver", "gold", "gem")}
    under = ap.under

    def shell(v, l, m):
        """The whole helmet shell: every face painted as armor (front visor and vents, ear gems, back ridge, roof sunburst)."""
        for n in ("front", "left", "right", "back"):
            f = v[n]
            h, w = f.shape[:2]
            ap.plate(f, rect(f, 0, 0, w, h), mode="cyl")
            if n == "front":
                ap.band(f, 3, 6, 2, w - 2)                                         # a crown band set with gems
                for x in (w // 4, w // 2, 3 * w // 4):
                    ap.gem(f, x, 6, glow, 1)
                fill(f, rect(f, 5, 14, w - 10, 11), under, "v", lo=1.2, hi=3.0)    # the visor recess
                for x in range(7, w - 7):
                    px(f, x, 18, glow[3]); px(f, x, 19, glow[5]); px(f, x, 20, glow[4]); px(f, x, 21, glow[3])
                for x in range(8, w - 8, 4):                                       # vents on the lower face
                    for y in range(28, 33):
                        px(f, x, y, under[0])
                ap.corner_rivets(f, rect(f, 0, 10, w, 26))
            elif n in ("left", "right"):
                for ang, ln in ((0.55, 15), (0.95, 14), (1.35, 12)):               # three feathers fanning back and down from the ear gem
                    for t in range(ln):
                        x, y = w * 0.42 + t * math.cos(ang) * 1.3, h * 0.45 + t * math.sin(ang)
                        wd = 1.5 * math.sin(math.pi * t / ln) ** 0.7
                        for dy in range(int(-wd), int(wd) + 1):
                            px(f, int(round(x)), int(round(y)) + dy, gold[5 if dy < 0 else 4 if dy == 0 else 3])
                fill(f, ell(f, int(w * 0.42) - 5, int(h * 0.45) - 5, 10, 10), gold, "v", lo=3.0, hi=5.0)
                ap.gem(f, int(w * 0.42), int(h * 0.45), glow, 2)
                ap.corner_rivets(f, rect(f, 0, 0, w, h))
            else:
                ap.band(f, 3, 3, 2, w - 2)
                f[6:26, w // 2 - 1:w // 2 + 1] = (*gold[4], 255)                   # a gold ridge down the back
                ap.scroll(f, 4, w // 2 - 3, 15, 2.2, 9.0)
                ap.scroll(f, w // 2 + 3, w - 4, 15, 2.2, 9.0)
                fill(f, rect(f, 1, 28, w - 2, h - 29), under, "v", lo=1.6, hi=3.2)
        t = v["top"]
        th, tw = t.shape[:2]
        shade(t, np.ones((th, tw), bool), ivory, "none", lo=3.4, edge=False)       # the roof: a sunburst round a glowing seed
        c = (tw - 1) / 2
        for k in range(12):
            a = k * math.pi / 6
            fill(t, poly(t, [(c + 0.16 * tw * math.cos(a - .24), c + 0.16 * th * math.sin(a - .24)), (c + 0.47 * tw * math.cos(a), c + 0.47 * th * math.sin(a)),
                             (c + 0.16 * tw * math.cos(a + .24), c + 0.16 * th * math.sin(a + .24))]), gold, "v", lo=3.0, hi=5.0, edge=False)
        fill(t, ell(t, int(c - 0.14 * tw), int(c - 0.14 * th), int(0.28 * tw), int(0.28 * th)), gold, "v", lo=3.0, hi=5.0)
        fill(t, ell(t, int(c - 0.08 * tw), int(c - 0.08 * th), int(0.16 * tw), int(0.16 * th)), glow, "v", lo=2.8, hi=5.2, edge=False)
        shade(v["bottom"], np.ones(v["bottom"].shape[:2], bool), under, "none", lo=2.0, edge=False)

    def cheek(v, l, m):
        base["plate"](v, l, m)
        for n in ("left", "right"):
            f = v[n]
            h, w = f.shape[:2]
            for ang, ln in ((0.5, 12), (0.95, 11), (1.4, 9)):
                for t in range(ln):
                    x, y = w * 0.3 + t * math.cos(ang) * 1.2, h * 0.32 + t * math.sin(ang)
                    wd = 1.3 * math.sin(math.pi * t / ln) ** 0.7
                    for dy in range(int(-wd), int(wd) + 1):
                        px(f, int(round(x)), int(round(y)) + dy, gold[5 if dy < 0 else 4 if dy == 0 else 3])
            ap.gem(f, int(w * 0.3), int(h * 0.32), glow, 2)

    painters = dict(base, shell=shell, cheek=cheek)
    parts = []

    def box(x, y, z, sx, sy, sz, style="plate", rot=(0, 0, 0), pivot=None, mirror=False):
        key = f"helm_{style}_{sx}x{sy}x{sz}"
        p = Part(H, [x, y, z], [sx, sy, sz], rot=rot, pivot=pivot or [x + sx / 2, y + sy / 2, z + sz / 2], paint=painters[style], share=key)
        parts.append(p)
        if mirror:
            parts.append(p.mirrored())

    box(-5, 24, -5, 10, 9, 10, "shell")                                              # the shell, y 24..33
    # a hipped roof: a flat ridge plate and a slope down to the front and to the back (measured: -rot x drops the front end)
    box(-5, 33, -1, 10, 2, 2, "plate")
    box(-5, 33, -5, 10, 2, 4, "plate", rot=(28, 0, 0), pivot=[0.0, 34.0, -1.0])
    box(-5, 33, 1, 10, 2, 4, "plate", rot=(-28, 0, 0), pivot=[0.0, 34.0, 1.0])
    box(-1, 35, -1, 2, 3, 2, "gold")                                                 # the crest follows the roof: ridge and both slopes
    box(-1, 35, -5, 2, 3, 4, "gold", rot=(28, 0, 0), pivot=[0.0, 34.0, -1.0])
    box(-1, 35, 1, 2, 3, 4, "gold", rot=(-28, 0, 0), pivot=[0.0, 34.0, 1.0])
    # the face: a gold brow with the diamond, a nose guard and a chin plate
    box(-5, 30.2, -6.5, 10, 2, 2, "gold")
    box(-1.5, 30.8, -8.2, 3, 3, 2, "gem", rot=(0, 0, 45))
    box(-1, 25.5, -7.0, 2, 5, 2, "gold")
    box(-4, 23.0, -6.5, 8, 2, 3, "silver")
    # angled cheek guards and a neck guard
    box(-7, 24.5, -4.0, 2, 6, 7, "cheek", rot=(0, 0, 10), pivot=[-6.0, 30.5, 0.0], mirror=True)
    box(-5, 21.5, 4.0, 10, 3, 2, "plate")
    box(-4, 19.5, 4.5, 8, 2, 2, "silver")
    return parts


def paragon_helmet(look):
    return make(look)
