"""Paragon 3D armor, kept deliberately small: a handful of plates seated on the vanilla cubes, each raised the same one unit off its
surface (every plate is two units thick and sunk one unit in). The detail lives in the painted art (art_paragon); the boxes only give
the silhouette its few strong landmarks: a crest fin, a brow and diamond, pauldron caps with a spike, a chest plate, a skirt of
tassets, knee cops and toe caps."""
import numpy as np
from sculpt import *

H, BD, W = "armorHead", "armorBody", "armorWaist"
LIMB = {"R": ("armorRightArm", "armorRightLeg", "armorRightBoot"), "L": ("armorLeftArm", "armorLeftLeg", "armorLeftBoot")}

ivory, silver = ramp((240, 236, 246)), ramp((196, 204, 228))
gold, goldd = ramp((252, 214, 100)), ramp((190, 142, 52))
slate = ramp((70, 78, 108))


def _full(v):
    return np.ones(v.shape[:2], bool)


def _paint(style, glow):
    rp = {"plate": ivory, "silver": silver, "gold": gold, "gem": glow}[style]
    seam = goldd[3] if style in ("gold", "gem") else slate[1]

    def paint(v, look, mirror):
        for n in ("front", "back", "left", "right"):
            a = v[n]
            m = _full(a)
            shade(a, m, rp, "cyl" if a.shape[0] > a.shape[1] * 1.3 else "v", lo=2.4 if style != "gold" else 3.0, hi=5.0, edge=True, lit=0.34)
            put(a, m & ~erode(m), seam)
            if style in ("plate", "silver") and n in ("front", "back") and min(a.shape[:2]) >= 8:
                e1 = erode(m)
                put(a, e1 & ~erode(e1), gold[3])
            if style == "gem" and n == "front":
                c = a.shape
                put(a, (np.hypot(*(np.mgrid[0:c[0], 0:c[1]] - np.array([(c[0] - 1) / 2, (c[1] - 1) / 2])[:, None, None])) <= min(c) * 0.32), glow[5])
        for n in ("top", "bottom"):
            shade(v[n], _full(v[n]), rp, "none", lo=3.6 if n == "top" else 2.0, hi=4.6, edge=True)
            put(v[n], _full(v[n]) & ~erode(_full(v[n])), seam)
    return paint


def make(look):
    glow = look.glow
    painters = {s: _paint(s, glow) for s in ("plate", "silver", "gold", "gem")}
    parts = []

    def box(bone, x, y, z, sx, sy, sz, style="plate", rot=(0, 0, 0), mirror=True, side=None, pivot=None):
        key = f"arm_{style}_{sx}x{sy}x{sz}"
        p = Part(bone, [x, y, z], [sx, sy, sz], rot=rot, pivot=pivot or [x + sx / 2, y + sy / 2, z + sz / 2], paint=painters[style], share=key)
        parts.append(p)
        if mirror:
            m = p.mirrored()
            if side is not None:
                m.bone = LIMB["L"][side]
            parts.append(m)

    # ---- helm (cube +-5, top y 33): a gold brow with a diamond, a single swept crest fin, cheek plates -------------------------
    box(H, -5.0, 28.4, -6.0, 10, 2, 2, "gold", mirror=False)
    box(H, -1.5, 30.2, -6.0, 3, 3, 2, "gem", rot=(0, 0, 45), mirror=False)
    box(H, -1.0, 32.0, -4.0, 2, 8, 8, "plate", rot=(-10, 0, 0), mirror=False, pivot=[0.0, 32.0, 0.0])
    box(H, -1.0, 32.0, -4.6, 2, 5, 2, "gold", rot=(-10, 0, 0), mirror=False, pivot=[0.0, 32.0, 0.0])
    box(H, -6.0, 24.0, -4.0, 2, 6, 6, "plate")

    # ---- torso (front face z -2.04): one chest plate, a gem, a belt plate -----------------------------------------------------
    box(BD, -4.0, 16.0, -3.0, 8, 7, 2, "plate", mirror=False)
    box(BD, -1.5, 19.0, -4.0, 3, 3, 2, "gem", rot=(0, 0, 45), mirror=False)
    box(BD, -4.0, 12.4, -3.0, 8, 2, 2, "gold", mirror=False)

    # ---- arms (top y 25): a cap over the shoulder, one lame drooping off its outer edge, one spike ------------------------------
    box("armorRightArm", -9.5, 24.0, -3.5, 7, 2, 7, "plate", side=0)
    box("armorRightArm", -14.5, 23.0, -3.5, 5, 2, 7, "gold", rot=(0, 0, -40), side=0, pivot=[-9.5, 24.0, 0.0])
    box("armorRightArm", -9.5, 26.0, -1.0, 2, 5, 2, "gold", rot=(0, 0, -24), side=0, pivot=[-8.5, 26.0, 0.0])

    # ---- waist (front z -2.5): one row of tassets ----------------------------------------------------------------------------
    box(W, -2.0, 5.6, -3.5, 4, 7, 2, "plate", mirror=False)
    box(W, -5.0, 6.0, -3.5, 3, 7, 2, "plate", rot=(0, 0, 10), pivot=[-3.5, 13.0, 0.0])
    box(W, -4.0, 6.0, 1.5, 8, 7, 2, "plate", mirror=False)

    # ---- legs and feet -------------------------------------------------------------------------------------------------------
    box("armorRightLeg", -4.4, 3.4, -3.5, 5, 3, 2, "gold", side=1)
    box("armorRightBoot", -4.0, 0.4, -5.0, 4, 2, 4, "plate", side=2)
    return parts


def paragon_armor(look):
    return make(look)
