#!/usr/bin/env python3
"""Generates the 2x worn-armor layer textures (128x64) for the twelve sets, plus 32x32 icons rendered from them.

    python tools/generate_armor_textures.py [--out <dir>] [--only set,set]

Output (default: src/main/resources/assets/cobbletowers/textures):
  models/armor/<set>_layer_1.png   256x64: left half = helmet, chestplate, boots (vanilla UV layout at 2x),
                                   right half = emission mask (white where the armor glows)
  models/armor/<set>_layer_2.png   256x64: the same for leggings
  models/armor/<set>_layer_N.polymer.json   {"scale": 2, "emissivity": 1}: tells Polymer's armor shader the above
  item/<set>_<slot>.png            32x32 icons, rendered from the layer art by tools/armor_preview3d.py

The art is painted in "unfolded box" strips so trims wrap around a part, with bevelled plates and material patterns from
tools/armor_paint.py. Each set is a SPEC below; the painter functions read it. Replacing a texture by hand is fine: the
file layout is the contract.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from armor_paint import Cv, Shape, emblem_mask, mix, ramp  # noqa: E402
import armor_icons  # noqa: E402

K = 2  # texels per vanilla pixel


def T(v):
    return int(round(v * K))


# ---- the twelve sets -----------------------------------------------------------------------------------------------

SPECS = {
    # metal, trim, cloth, glow; pattern; helm style, cheek, top; shoulder; emblem; tabard
    "recruit": dict(tier=1, metal=(150, 132, 104), trim=(196, 150, 72), cloth=(120, 86, 60), glow=(236, 204, 120),
                    pattern="mail", helm="open", cheek="plate", top="ridge", shoulder="layered", emblem=None, tabard=True),
    "challenger": dict(tier=2, metal=(150, 160, 180), trim=(228, 180, 62), cloth=(164, 36, 48), glow=(255, 216, 100),
                       pattern="lames", helm="open", cheek="plate", top="ridge", shoulder="layered", emblem="crest", tabard=True),
    "paragon": dict(tier=3, metal=(200, 208, 230), trim=(242, 198, 72), cloth=(236, 232, 244), glow=(255, 246, 196),
                    pattern="lames", helm="visor", cheek="plate", top="ridge", shoulder="layered", emblem="crest", tabard=True),
    "tidewalker": dict(tier=1, metal=(40, 144, 146), trim=(236, 214, 160), cloth=(30, 100, 112), glow=(140, 240, 230),
                       pattern="scales", helm="open", cheek="fin", top="ridge", shoulder="shell", emblem=None, tabard=False),
    "tideforge": dict(tier=2, metal=(44, 118, 180), trim=(232, 238, 246), cloth=(16, 66, 122), glow=(120, 220, 255),
                      pattern="scales", helm="open", cheek="fin", top="spiral", shoulder="shell", emblem="shell", tabard=False),
    "leviathan": dict(tier=3, metal=(26, 66, 144), trim=(242, 202, 72), cloth=(10, 36, 90), glow=(150, 255, 255),
                      pattern="scales", helm="visor", cheek="fin", top="spiral", shoulder="shell", emblem="shell", tabard=False),
    "sprout": dict(tier=1, metal=(150, 120, 76), trim=(130, 172, 70), cloth=(96, 140, 54), glow=(204, 255, 134),
                   pattern="planks", helm="open", cheek="leaf", top="leaf", shoulder="leaf", emblem=None, tabard=False),
    "rootvale": dict(tier=2, metal=(112, 82, 52), trim=(176, 134, 72), cloth=(72, 104, 46), glow=(194, 255, 124),
                     pattern="planks", helm="open", cheek="leaf", top="leaf", shoulder="leaf", emblem="leaf", tabard=True),
    "heartwood": dict(tier=3, metal=(92, 64, 42), trim=(122, 156, 74), cloth=(56, 74, 36), glow=(172, 255, 92),
                      pattern="planks", helm="visor", cheek="leaf", top="leaf", shoulder="leaf", emblem="leaf", tabard=True),
    "duskwanderer": dict(tier=1, metal=(92, 86, 132), trim=(204, 134, 214), cloth=(68, 52, 104), glow=(232, 172, 255),
                         pattern="cloth", helm="hood", cheek="none", top="star", shoulder="none", emblem=None, tabard=False),
    "duskvale": dict(tier=2, metal=(108, 72, 164), trim=(242, 122, 192), cloth=(86, 36, 122), glow=(255, 142, 222),
                     pattern="cloth", helm="hood", cheek="none", top="star", shoulder="spike", emblem="moon", tabard=False),
    "nightfall": dict(tier=3, metal=(66, 50, 124), trim=(255, 192, 72), cloth=(30, 22, 68), glow=(255, 204, 92),
                      pattern="cloth", helm="hood", cheek="none", top="star", shoulder="spike", emblem="moon", tabard=False),
}


class Look:
    def __init__(self, name, spec):
        self.name, self.s = name, spec
        self.tier = spec["tier"]
        self.metal, self.trim = ramp(spec["metal"]), ramp(spec["trim"])
        self.cloth, self.glow = ramp(spec["cloth"]), ramp(spec["glow"])
        self.dark = ramp(tuple(int(c * 0.55) for c in spec["metal"]))
        self.seed = sum(ord(c) for c in name)


def material(cv, x, y, w, h, look, tone=0.0):
    p = look.s["pattern"]
    if p == "lames":
        cv.lames(x, y, w, h, look.metal, max(2, h // T(2.6)))
    elif p == "scales":
        cv.scales(x, y, w, h, look.metal, T(4.4), tone_top=0.18)
    elif p == "planks":
        cv.planks(x, y, w, h, look.metal, T(1.6), seed=look.seed + x)
    elif p == "mail":
        cv.mail(x, y, w, h, look.metal)
    else:  # cloth: a dark robe with folds
        cv.cloth(x, y, w, h, look.cloth if p == "cloth" else look.metal, period=T(2.6), phase=x * 0.3)


def trim_band(cv, x, y, w, h, look, rivets=True):
    m = cv.shape().rect(x, y, w, h).mask()
    cv.plate(m, look.trim, outline=False, split=False, hot=False)
    if rivets and h >= 3:
        for rx in range(x + 2, x + w - 1, T(2)):
            cv.px(rx, y + h // 2, look.trim[5])


def stamp(cv, mask, ox, oy, rp):
    ys, xs = np.nonzero(mask)
    if not len(ys):
        return
    sub = Cv(cv.w, cv.h)
    big = np.zeros((cv.h, cv.w), bool)
    h, w = mask.shape
    big[oy:oy + h, ox:ox + w] = mask[: cv.h - oy, : cv.w - ox]
    sub.plate(big, rp, tone=0.15)
    live = sub.a[..., 3] > 0
    cv.a[live] = sub.a[live]


# ---- parts: each returns (strip, top, bottom) ----------------------------------------------------------------------

def head(look):
    s = look.s
    W, H, D = T(8), T(8), T(8)
    st, top, bot = Cv(2 * D + 2 * W, H), Cv(W, D), Cv(W, D)
    for x0 in (0, D, D + W, 2 * D + W):
        material(st, x0, 0, W if x0 in (D, 2 * D + W) else D, H, look)
    for fx in (0, D, D + W, 2 * D + W):
        pass
    # brow band wraps the helmet
    trim_band(st, 0, T(3), 2 * D + 2 * W, T(1), look)
    fx0, fx1 = D, D + W
    if s["helm"] == "open":
        hole = st.shape().rect(fx0 + T(1.5), T(4.5), W - T(3), H - T(4.5)).mask()
        frame = st.shape().rect(fx0 + T(1), T(4), W - T(2), H - T(4)).mask() & ~hole
        st.plate(frame, look.trim, split=False, hot=False)
        st.a[hole] = (0, 0, 0, 0)
        nasal = st.shape().rect(fx0 + W // 2 - 1, T(4), 2, T(3.2)).mask()
        st.plate(nasal, look.metal, bevel=0, split=False, hot=False)
    elif s["helm"] == "visor":
        face = st.shape().rrect(fx0 + T(1), T(4), W - T(2), T(3.6), 2).mask()
        st.plate(face, look.dark, tone=-0.1)
        st.fill(fx0 + T(1.6), T(5.4), W - T(3.2), 2, look.glow[4])
        st.fill(fx0 + T(1.6), T(5.4), W - T(3.2), 1, look.glow[5])
        chin = st.shape().rect(fx0 + T(2), T(7.2), W - T(4), H - T(7.2)).mask()
        st.plate(chin, look.metal, split=False, hot=False)
    else:  # hood: a cowl with a shadowed face
        cowl = st.shape().ellipse(fx0 + T(0.6), T(2.6), W - T(1.2), H - T(2.2)).mask()
        st.plate(cowl, look.cloth, tone=-0.25)
        face = st.shape().ellipse(fx0 + T(2), T(4.4), W - T(4), T(3.2)).mask()
        st.a[face] = (0, 0, 0, 0)
        for dx in (T(2.4), W - T(3.4)):
            st.px(fx0 + dx, T(5.4), look.glow[4])
    # cheeks on the sides
    for sx in (0, D + W):
        if s["cheek"] == "plate":
            m = st.shape().rrect(sx + T(1.2), T(4), T(5.2), T(3.6), 2).mask()
            st.plate(m, look.metal, tone=0.2)
            st.rivets([sx + T(2), sx + T(5)], T(5), look.metal)
        elif s["cheek"] == "fin":
            m = st.shape().poly([(sx + T(1), T(2.4)), (sx + T(7.4), T(0.6)), (sx + T(6.2), T(4.4)), (sx + T(1.6), T(5.6))]).mask()
            st.plate(m, look.trim, tone=0.05)
            for i in range(3):
                st.line = None
                st.hline(sx + T(2.4) + i * 2, T(3.3) + i, T(4.2) - i * 2, look.trim[1])
        elif s["cheek"] == "leaf":
            m = st.shape().poly([(sx + T(1), T(6.6)), (sx + T(3), T(3.4)), (sx + T(7), T(2.8)), (sx + T(6.2), T(6.2))]).mask()
            st.plate(m, look.cloth, tone=0.3)
            st.hline(sx + T(2.2), T(5.5), T(4), look.cloth[5])
    # neck guard and back
    bx = 2 * D + W
    neck = st.shape().rect(bx + T(0.5), T(6), W - T(1), T(2)).mask()
    st.plate(neck, look.dark, tone=0.1)
    st.rivets([bx + T(2), bx + T(4), bx + T(6)], T(6.6), look.metal)
    if s["emblem"] and look.tier >= 2:
        st.fill(bx + W // 2 - 1, T(1.2), 2, T(1.6), look.glow[4])
    # top
    material(top, 0, 0, W, D, look)
    if s["top"] == "spiral":
        for i, r in enumerate(range(0, W // 2 - 1, 2)):
            c = look.trim if i % 2 == 0 else look.metal
            top.plate(top.shape().rrect(r, r, W - 2 * r, D - 2 * r, 2).mask(), c, outline=True, split=False, hot=False)
    elif s["top"] == "star":
        top.fill(0, 0, W, D, look.cloth[1])
        top.stars(0, 0, W, D, look.glow[5], 6 + look.tier * 2, seed=look.seed)
    elif s["top"] == "leaf":
        for i in range(3):
            top.plate(top.shape().ellipse(1 + i * 4, 2 + (i % 2) * 5, 6, 4).mask(), look.cloth, tone=0.1 * i)
    else:
        top.plate(top.shape().rect(W // 2 - 2, 0, 4, D).mask(), look.trim, split=False)
    # a socket where the worn 3D crest sits
    if look.tier >= 2:
        top.plate(top.shape().rect(W // 2 - 2, D // 2 - 3, 4, 6).mask(), look.dark, split=False, hot=False)
    bot.fill(0, 0, W, D, look.metal[1])
    return st, top, bot


def chest(look):
    s = look.s
    W, H, D = T(8), T(12), T(4)
    st, top, bot = Cv(2 * D + 2 * W, H), Cv(W, D), Cv(W, D)
    fx0, bx0 = D, 2 * D + W
    for x0, w in ((0, D), (D, W), (D + W, D), (2 * D + W, W)):
        material(st, x0, 0, w, H, look)
    # collar
    trim_band(st, 0, 0, 2 * D + 2 * W, T(1.3), look)
    # front breastplate halves and a centre ridge
    if s["pattern"] in ("lames", "mail"):
        for ox in (fx0 + T(0.4), fx0 + W // 2 + 1):
            m = st.shape().rrect(ox, T(1.6), W // 2 - T(0.4) - 1, T(5.6), 3).mask()
            st.plate(m, look.metal, tone=0.25)
        st.rivets([fx0 + T(1.2), fx0 + W - T(1.6)], T(2.2), look.metal)
    st.plate(st.shape().rect(fx0 + W // 2 - 1, T(1.4), 2, T(6)).mask(), look.trim, split=False, hot=False)
    # emblem
    if s["emblem"]:
        n = T(3.4)
        stamp(st, emblem_mask(s["emblem"], n), fx0 + (W - n) // 2, T(2.6), look.trim)
        if look.tier == 3:
            st.px(fx0 + W // 2, T(4), look.glow[5])
    # belt and buckle
    trim_band(st, 0, T(8.6), 2 * D + 2 * W, T(1.2), look)
    gem = st.shape().ellipse(fx0 + W // 2 - 2, T(8.3), 4, T(1.8)).mask()
    st.plate(gem, look.glow, split=False)
    # tabard or hem
    if s["tabard"]:
        tw = T(4)
        tx = fx0 + (W - tw) // 2
        st.cloth(tx, T(9.8), tw, H - T(9.8), look.cloth, period=T(2), phase=1)
        trim_band(st, tx, H - T(1), tw, T(1), look, rivets=False)
        st.vline(tx, T(9.8), H - T(9.8), look.cloth[0])
        st.vline(tx + tw - 1, T(9.8), H - T(9.8), look.cloth[0])
    else:
        for x0 in range(0, 2 * D + 2 * W, T(1.2)):
            st.plate(st.shape().rrect(x0, T(10.3), T(1.3), T(1.9), 1).mask(), look.metal, tone=0.05, split=False, hot=False)
    # sides
    for sx in (0, D + W):
        st.fill(sx + D // 2 - 1, T(2), 2, T(6), look.metal[1])
        st.rivets([sx + D // 2 - 1], T(3), look.metal)
        st.rivets([sx + D // 2 - 1], T(6.5), look.metal)
    # back
    st.plate(st.shape().rect(bx0 + W // 2 - 1, T(1.4), 2, T(7.2)).mask(), look.trim, split=False, hot=False)
    for ox in (bx0 + T(0.4), bx0 + W // 2 + 1):
        st.plate(st.shape().rrect(ox, T(1.8), W // 2 - T(0.4) - 1, T(4.2), 3).mask(), look.metal, tone=0.1)
    clasp = st.shape().ellipse(bx0 + W // 2 - 2, T(1.7), 4, 4).mask()
    st.plate(clasp, look.glow, split=False)
    material(top, 0, 0, W, D, look)
    trim_band(top, 0, 0, W, T(1), look, rivets=False)
    bot.fill(0, 0, W, D, look.metal[1])
    return st, top, bot


def arm(look):
    s = look.s
    W, H, D = T(4), T(12), T(4)
    st, top, bot = Cv(2 * D + 2 * W, H), Cv(W, D), Cv(W, D)
    for x0 in (0, W, 2 * W, 3 * W):
        material(st, x0, 0, W, H, look)
    sh = s["shoulder"]
    if sh == "layered":
        for i in range(3):
            st.plate(st.shape().rrect(0, i * T(1.3), 4 * W, T(2.4), 2).mask(), look.metal if i != 1 else look.trim, tone=0.35 - i * 0.2)
    elif sh == "shell":
        st.scales(0, 0, 4 * W, T(4.8), look.metal, T(3.2), tone_top=0.4)
        trim_band(st, 0, T(4.3), 4 * W, T(1.1), look, rivets=False)
    elif sh == "leaf":
        for i in range(3):
            st.plate(st.shape().poly([(0, i * T(1.5) + T(2.4)), (T(2), i * T(1.5) - 1), (4 * W, i * T(1.5) + 1), (4 * W, i * T(1.5) + T(3.2))]).mask(),
                     look.cloth, tone=0.3 - i * 0.15, bevel=0)
    elif sh == "spike":
        st.plate(st.shape().rect(0, 0, 4 * W, T(3.4)).mask(), look.metal, tone=0.1)
        st.plate(st.shape().poly([(W // 2, T(3.4)), (W // 2 + 3, 0), (W // 2 + 6, T(3.4))]).mask(), look.trim)
    # vambrace and elbow
    trim_band(st, 0, T(5.6), 4 * W, T(1.1), look)
    st.plate(st.shape().rrect(W + 1, T(6.2), W - 2, T(2.4), 2).mask(), look.metal, tone=0.2)
    # gauntlet cuff
    st.plate(st.shape().rect(0, T(9), 4 * W, T(3)).mask(), look.dark, tone=0.05, split=False)
    trim_band(st, 0, T(9), 4 * W, T(1), look, rivets=False)
    top.plate(top.shape().rect(0, 0, W, D).mask(), look.metal, tone=0.2)
    bot.fill(0, 0, W, D, look.dark[1])
    return st, top, bot


def legs_inner(look):
    """Leggings (layer 2): belt skirt on the body region, thigh / knee / greave on the legs."""
    s = look.s
    # body waist
    W, H, D = T(8), T(12), T(4)
    bst, btop, bbot = Cv(2 * D + 2 * W, H), Cv(W, D), Cv(W, D)
    for x0, w in ((0, D), (D, W), (D + W, D), (2 * D + W, W)):
        material(bst, x0, T(8.8), w, H - T(8.8), look)
    trim_band(bst, 0, T(8.8), 2 * D + 2 * W, T(1.2), look)
    bst.plate(bst.shape().ellipse(D + W // 2 - 2, T(8.6), 4, T(1.6)).mask(), look.glow, split=False)
    if look.tier >= 2:
        for ox in (D + T(0.6), D + W - T(2.6)):
            bst.plate(bst.shape().rrect(ox, T(10), T(2), T(2), 1).mask(), look.trim, split=False)
    # legs
    LW, LH, LD = T(4), T(12), T(4)
    lst, ltop, lbot = Cv(2 * LD + 2 * LW, LH), Cv(LW, LD), Cv(LW, LD)
    for x0 in (0, LW, 2 * LW, 3 * LW):
        material(lst, x0, 0, LW, T(9), look)
    trim_band(lst, 0, 0, 4 * LW, T(1), look)
    for x0 in (0, LW, 2 * LW, 3 * LW):
        lst.plate(lst.shape().rrect(x0, T(1.4), LW, T(3.6), 2).mask(), look.metal, tone=0.25)
    # knee guard
    kn = lst.shape().ellipse(LW + 1, T(5), LW - 2, T(2.8)).mask()
    lst.plate(kn, look.trim, tone=0.1)
    lst.px(LW + LW // 2, T(6.2), look.glow[5])
    trim_band(lst, 0, T(8), 4 * LW, T(1), look, rivets=False)
    lst.plate(lst.shape().rect(0, T(5), LW, T(4)).mask(), look.metal, tone=0.1, split=False)
    lst.vline(LW + LW // 2, T(8.5), T(1), look.trim[3])
    ltop.fill(0, 0, LW, LD, look.metal[2])
    lbot.fill(0, 0, LW, LD, look.metal[1])
    return (bst, btop, bbot), (lst, ltop, lbot)


def boots(look):
    LW, LH, LD = T(4), T(12), T(4)
    st, top, bot = Cv(2 * LD + 2 * LW, LH), Cv(LW, LD), Cv(LW, LD)
    y0 = T(8)
    for x0 in (0, LW, 2 * LW, 3 * LW):
        st.plate(st.shape().rect(x0, y0, LW, LH - y0).mask(), look.dark, tone=0.15, split=False, hot=False)
    trim_band(st, 0, y0, 4 * LW, T(1.1), look)
    toe = st.shape().rrect(LW + 1, T(10.2), LW - 2, T(1.8), 2).mask()
    st.plate(toe, look.trim, tone=0.05)
    for x0 in (0, LW, 2 * LW, 3 * LW):
        st.fill(x0, LH - 2, LW, 2, look.dark[0])
    st.px(LW // 2, T(9.5), look.trim[5])
    st.px(3 * LW + LW // 2, T(9.5), look.trim[5])
    bot.fill(0, 0, LW, LD, look.dark[0])
    return st, top, bot


# ---- assembling a sheet ------------------------------------------------------------------------------------------

def paste(sheet, cvs, strip_xy, top_xy, bot_xy):
    st, top, bot = cvs
    for cv, (x, y) in ((st, strip_xy), (top, top_xy), (bot, bot_xy)):
        live = cv.a[..., 3] > 0
        sub = sheet[y:y + cv.h, x:x + cv.w]
        sub[live] = cv.a[live]


def make_layers(look):
    l1 = np.zeros((T(32), T(64), 4), np.uint8)
    l2 = np.zeros_like(l1)
    paste(l1, head(look), (T(0), T(8)), (T(8), T(0)), (T(16), T(0)))
    paste(l1, chest(look), (T(16), T(20)), (T(20), T(16)), (T(28), T(16)))
    paste(l1, arm(look), (T(40), T(20)), (T(44), T(16)), (T(48), T(16)))
    paste(l1, boots(look), (T(0), T(20)), (T(4), T(16)), (T(8), T(16)))
    body, legs = legs_inner(look)
    paste(l2, body, (T(16), T(20)), (T(20), T(16)), (T(28), T(16)))
    paste(l2, legs, (T(0), T(20)), (T(4), T(16)), (T(8), T(16)))
    return Image.fromarray(l1, "RGBA"), Image.fromarray(l2, "RGBA")


def emission_mask(img: Image.Image, look) -> Image.Image:
    """White where a texel is one of the set's glow colours (gems, eye slits, star specks), transparent elsewhere."""
    a = np.array(img)
    glow = np.zeros(a.shape[:2], bool)
    for c in look.glow[3:]:
        glow |= (a[..., 0] == c[0]) & (a[..., 1] == c[1]) & (a[..., 2] == c[2]) & (a[..., 3] > 0)
    out = np.zeros_like(a)
    out[glow] = (255, 255, 255, 255)
    return Image.fromarray(out, "RGBA")


def with_emission(img: Image.Image, look) -> Image.Image:
    """Polymer's armor shader reads the right half of a layer image as the emission mask (emissivity 1)."""
    sheet = Image.new("RGBA", (img.width * 2, img.height), (0, 0, 0, 0))
    sheet.paste(img, (0, 0))
    sheet.paste(emission_mask(img, look), (img.width, 0))
    return sheet


METADATA = '{"scale": 2, "emissivity": 1}\n'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers/textures")
    parser.add_argument("--only", default="")
    args = parser.parse_args()
    only = [x for x in args.only.split(",") if x]
    (args.out / "models/armor").mkdir(parents=True, exist_ok=True)
    for name, spec in SPECS.items():
        if only and name not in only:
            continue
        l1, l2 = make_layers(Look(name, spec))
        look = Look(name, spec)
        with_emission(l1, look).save(args.out / f"models/armor/{name}_layer_1.png")
        with_emission(l2, look).save(args.out / f"models/armor/{name}_layer_2.png")
        for number in (1, 2):
            (args.out / f"models/armor/{name}_layer_{number}.polymer.json").write_text(METADATA, encoding="utf-8")
        (args.out / "item").mkdir(parents=True, exist_ok=True)
        for slot in ("helmet", "chestplate", "leggings", "boots"):
            armor_icons.make_icon(l1, l2, slot, look.metal[0], look.metal[1]).save(args.out / f"item/{name}_{slot}.png")
        print("wrote", name)


if __name__ == "__main__":
    main()
