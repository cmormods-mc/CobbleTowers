"""Paragon 3D armor: sculpted plates standing off the vanilla cubes - a crested helm, stacked pauldrons, a keeled breastplate,
a skirt of hanging tassets, knee horns and finned boots. Every plate is a real box (two units thick at least) in ivory, silver or
gold over a dark under-layer; equal boxes share one painted cell."""
import numpy as np
from sculpt import *
from designs import sides

H, BD, W = "armorHead", "armorBody", "armorWaist"
LIMB = {"R": ("armorRightArm", "armorRightLeg", "armorRightBoot"), "L": ("armorLeftArm", "armorLeftLeg", "armorLeftBoot")}

ivory, silver = ramp((240, 236, 246)), ramp((196, 204, 228))
gold, goldd = ramp((252, 214, 100)), ramp((190, 142, 52))
slate = ramp((70, 78, 108))


def _full(v):
    return np.ones(v.shape[:2], bool)


def _paint(style, glow):
    """One painter per style: shaded faces with a dark outline, a gold inner trim on the big faces, a lit top."""
    rp = {"plate": ivory, "silver": silver, "gold": gold, "dark": slate, "gem": glow}[style]

    def paint(v, look, mirror):
        for n in ("front", "back", "left", "right"):
            a = v[n]
            m = _full(a)
            shade(a, m, rp, "cyl" if a.shape[0] > a.shape[1] * 1.3 else "v", lo=2.2 if style != "gold" else 3.0, hi=5.0, edge=True, lit=0.34)
            put(a, m & ~erode(m), goldd[3] if style in ("gold", "gem") else slate[1])          # a soft seam line, not a black outline
            if style in ("plate", "silver") and n in ("front", "back") and min(a.shape[:2]) >= 8:
                e1 = erode(m)
                put(a, e1 & ~erode(e1), gold[3])                                  # a thin gold border inside the dark outline
            if style == "gem" and n == "front":
                c = a.shape
                put(a, (np.hypot(*(np.mgrid[0:c[0], 0:c[1]] - np.array([(c[0] - 1) / 2, (c[1] - 1) / 2])[:, None, None])) <= min(c) * 0.32), glow[5])
        for n in ("top", "bottom"):
            shade(v[n], _full(v[n]), rp, "none", lo=3.6 if n == "top" else 2.0, hi=4.6, edge=True)
            put(v[n], _full(v[n]) & ~erode(_full(v[n])), goldd[3] if style in ("gold", "gem") else slate[1])
    return paint


def make(look):
    glow = look.glow
    painters = {s: _paint(s, glow) for s in ("plate", "silver", "gold", "dark", "gem")}
    parts = []

    def box(bone, x, y, z, sx, sy, sz, style="plate", rot=(0, 0, 0), mirror=True, side=None, pivot=None):
        """A plate on `bone` (right side at -x). With mirror, the left twin goes on the matching left bone."""
        key = f"arm_{style}_{sx}x{sy}x{sz}"
        p = Part(bone, [x, y, z], [sx, sy, sz], rot=rot, pivot=pivot or [x + sx / 2, y + sy / 2, z + sz / 2], paint=painters[style], share=key)
        parts.append(p)
        if mirror:
            m = p.mirrored()
            if side is not None:
                m.bone = LIMB["L"][side] if bone in LIMB["R"] else bone
            parts.append(m)

    # ---- helm: a diamond on the brow, a gold brow bar, flared cheeks, a swept centre fin flanked by two side fins ----------------
    box(H, -5.5, 28.4, -6.4, 11, 2, 2, "gold", mirror=False)
    box(H, -1.5, 30.0, -6.6, 3, 3, 2, "gem", rot=(0, 0, 45), mirror=False)
    box(H, -7.4, 23.0, -4.8, 2, 7, 8, "plate", rot=(0, 0, 8), pivot=[-7.0, 30.0, 0.0])
    box(H, -8.2, 23.2, 0.6, 2, 3, 3, "gold", rot=(0, 0, 22), pivot=[-6.2, 24.0, 2.1])
    box(H, -3.5, 21.8, -6.2, 7, 2, 2, "silver", mirror=False)
    box(H, -1.0, 33.0, -5.0, 2, 8, 10, "plate", rot=(-14, 0, 0), mirror=False, pivot=[0.0, 33.0, 0.0])
    box(H, -1.0, 40.0, -3.5, 2, 2, 5, "gold", rot=(-14, 0, 0), mirror=False, pivot=[0.0, 33.0, 0.0])
    box(H, -4.6, 33.0, -2.0, 2, 7, 6, "silver", rot=(-10, 0, -26), pivot=[-3.6, 33.0, 1.0])
    box(H, -4.5, 21.4, 4.6, 9, 3, 2, "silver", rot=(-16, 0, 0), mirror=False, pivot=[0.0, 24.0, 5.0])

    # ---- torso: a broad gorget, a chest plate over a V of two angled plates, abdomen bands, a back plate ----------------------
    box(BD, -5.0, 23.2, -4.2, 10, 2, 3, "gold", mirror=False)
    box(BD, -4.0, 18.4, -4.2, 8, 5, 2, "plate", mirror=False)
    box(BD, -4.2, 13.2, -4.2, 4, 5, 2, "plate", rot=(0, 0, 14), pivot=[-2.2, 18.2, 0.0])
    box(BD, -1.5, 19.6, -5.6, 3, 3, 2, "gem", rot=(0, 0, 45), mirror=False)
    box(BD, -3.5, 12.4, -4.0, 7, 2, 2, "gold", mirror=False)
    box(BD, -3.0, 13.0, 2.2, 6, 9, 2, "plate", mirror=False)
    box(BD, -1.0, 12.0, 4.2, 2, 11, 2, "silver", mirror=False)

    # ---- arms: three lames fanned round the shoulder joint, a spike, an elbow fin, a bracer and a flared cuff -----------------
    box("armorRightArm", -9.0, 25.0, -3.0, 6, 2, 6, "plate", side=0)                                       # the flat cap over the joint
    for tier, (ln, dp, rz) in enumerate(((5, 7, -26), (5, 8, -54))):                                       # two lames drooping off its outer edge
        box("armorRightArm", -6.0 - ln - 3.0, 25.0, -dp / 2, ln, 2, dp, "gold" if tier == 0 else "plate", rot=(0, 0, rz), side=0,
            pivot=[-9.0, 26.0, 0.0])
    box("armorRightArm", -8.4, 27.2, -1.0, 2, 5, 2, "gold", rot=(0, 0, -28), side=0, pivot=[-7.4, 27.2, 0.0])
    box("armorRightArm", -10.2, 15.4, -1.6, 2, 5, 3, "gold", rot=(0, 0, -16), side=0, pivot=[-9.2, 16.0, 0.0])
    box("armorRightArm", -9.8, 11.8, -3.2, 2, 5, 6, "plate", side=0)
    box("armorRightArm", -9.2, 11.4, -3.4, 6, 2, 6, "silver", side=0)

    # ---- waist: a gem buckle and a skirt of hanging tassets -----------------------------------------------------------------
    box(W, -1.5, 12.4, -4.6, 3, 3, 2, "gem", rot=(0, 0, 45), mirror=False)
    box(W, -2.0, 5.6, -4.0, 4, 7, 2, "plate", mirror=False)
    box(W, -5.4, 6.0, -3.6, 3, 7, 2, "plate", rot=(0, 0, 11), pivot=[-4.0, 13.0, 0.0])
    box(W, -7.0, 7.0, -1.8, 2, 6, 4, "silver", rot=(0, 0, 7), pivot=[-6.0, 13.0, 0.0])
    box(W, -4.0, 6.0, 2.2, 8, 7, 2, "plate", mirror=False)

    # ---- legs: a thigh plate, a gold knee cop with a forward horn, a broad shin plate ---------------------------------------------
    box("armorRightLeg", -3.9, 7.4, -3.4, 4, 4, 2, "plate", side=1)
    box("armorRightLeg", -4.4, 3.6, -3.8, 5, 3, 2, "gold", side=1)
    box("armorRightLeg", -2.9, 4.1, -6.8, 2, 2, 3, "silver", side=1)
    box("armorRightLeg", -4.0, 0.2, -3.4, 4, 4, 2, "plate", side=1)

    # ---- boots: a toe cap with a spike, a fin at the ankle and a heel spike ------------------------------------------------
    box("armorRightBoot", -3.9, 0.4, -6.2, 4, 2, 4, "plate", side=2)
    box("armorRightBoot", -2.9, 0.8, -8.2, 2, 2, 2, "gold", side=2)
    box("armorRightBoot", -6.0, 0.8, -1.5, 2, 4, 3, "gold", rot=(0, 0, -20), side=2, pivot=[-4.9, 0.8, 0.0])
    box("armorRightBoot", -2.9, 0.6, 2.4, 2, 3, 2, "silver", side=2)
    return parts


def paragon_armor(look):
    return make(look)
