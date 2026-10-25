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
