"""Paragon 3D touches, kept to two: a gold crest fin along the helm and a raised cap on each shoulder. Everything else is painted."""
from sculpt import *
from designs5 import _paint, LIMB


def paragon_touches(look):
    plate, gold = _paint("plate", look.glow), _paint("gold", look.glow)
    fin = Part("armorHead", [-1.0, 32.6, -4.0], [2, 3, 8], paint=gold, share="touch_fin")
    cap = Part("armorRightArm", [-9.5, 24.0, -3.0], [6, 2, 6], paint=plate, share="touch_cap")
    left = cap.mirrored()
    left.bone = LIMB["L"][0]
    return [fin, cap, left]


def _tone(rp, edge):
    def paint(v, look, mirror):
        for n in ("front", "back", "left", "right", "top", "bottom"):
            a = v[n]
            m = np.ones(a.shape[:2], bool)
            shade(a, m, rp, "cyl" if a.shape[0] > a.shape[1] * 1.3 else "v", lo=2.4, hi=5.0, edge=True, lit=0.34)
            put(a, m & ~erode(m), edge)
    return paint


def region_touches(look):
    """The same two touches in a region's own colours: a trim-coloured helm fin and metal shoulder caps."""
    fin = Part("armorHead", [-1.0, 32.6, -4.0], [2, 3, 8], paint=_tone(look.trim, look.dark[1]), share="touch_fin")
    cap = Part("armorRightArm", [-9.5, 24.0, -3.0], [6, 2, 6], paint=_tone(look.metal, look.dark[1]), share="touch_cap")
    left = cap.mirrored()
    left.bone = LIMB["L"][0]
    return [fin, cap, left]
