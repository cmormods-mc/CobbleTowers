"""Procedural pixel painter and atlas packer for the Jack-o'-Lantern Vault.

Every element face gets its own island in one atlas at SCALE texels per model unit (a texel is half a unit, the grain of the concept
art). Texels are painted from the WORLD position of the texel centre, so neighbouring faces of different elements agree on ribs,
stone seams and noise. Islands are padded with 2 extruded edge texels so mipmaps do not bleed one island into the next.
Deterministic: no random state beyond integer hashes of world coordinates.
"""

from __future__ import annotations

import math

import numpy as np
from PIL import Image

SCALE = 2
PAD = 2


def hsh(*v):
    """Deterministic hash of integers to [0, 1)."""
    h = 2166136261
    for x in v:
        h ^= int(x) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
        h ^= h >> 13
    return (h & 0xFFFF) / 65536.0


def tex(*p, s=2.0):
    """Hash of the texel cell (0.5-unit grid) a world point falls in."""
    return hsh(*(int(math.floor(c * s + 1e-6)) for c in p))


BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


def ramp(colors, v, dither=0.0):
    """Pick from an ordered list (dark to light) by v in [0, 1] with optional ordered-dither value."""
    n = len(colors)
    i = int(max(0.0, min(0.9999, v + dither)) * n)
    return colors[i]


PUMPKIN = [(112, 44, 8), (160, 64, 10), (196, 84, 12), (224, 108, 16), (244, 132, 26), (255, 160, 48)]
PUMPKIN_LIT = [(150, 62, 10), (196, 84, 12), (224, 108, 16), (244, 132, 26), (255, 160, 48), (255, 186, 90)]
FLESH = [(74, 30, 8), (104, 42, 10), (138, 56, 12), (170, 72, 14), (200, 92, 18)]
FLESH_LIT = [(150, 64, 12), (190, 84, 14), (224, 108, 18), (248, 134, 28), (255, 164, 52)]
CHAR = [(20, 20, 22), (32, 32, 35), (46, 46, 50), (62, 62, 67), (84, 84, 90), (110, 110, 116)]
CHAR_LIT = [(38, 38, 42), (56, 56, 62), (76, 76, 84), (100, 100, 110), (130, 130, 140), (164, 164, 172)]
VINE = [(26, 70, 18), (40, 100, 24), (62, 134, 30), (92, 168, 40), (130, 200, 62)]
STEM = [(34, 88, 20), (52, 122, 26), (76, 156, 34), (106, 186, 48), (150, 214, 72)]
PLATE = [(150, 56, 8), (196, 82, 12), (232, 108, 18), (255, 140, 30), (255, 166, 56), (255, 196, 100)]
EMBER = [(12, 5, 3), (28, 11, 5), (70, 24, 8), (150, 60, 10), (240, 140, 30)]
CANDY = {
    "red": [(150, 20, 28), (200, 36, 44), (232, 60, 64), (250, 120, 120), (255, 232, 232)],
    "pink": [(160, 24, 100), (206, 40, 130), (240, 70, 160), (255, 130, 195), (255, 220, 238)],
    "purple": [(70, 24, 120), (104, 40, 164), (140, 64, 200), (178, 110, 228), (226, 190, 250)],
    "green": [(40, 110, 24), (70, 150, 30), (110, 190, 46), (160, 226, 80), (226, 250, 190)],
    "yellow": [(190, 140, 10), (232, 180, 20), (255, 214, 50), (255, 236, 120), (255, 250, 214)],
    "orange": [(190, 80, 10), (230, 110, 16), (255, 150, 30), (255, 190, 80), (255, 232, 180)],
    "blue": [(24, 64, 150), (40, 100, 196), (70, 140, 232), (130, 186, 250), (214, 236, 255)],
}
MIX_ORDER = ("red", "pink", "purple", "green", "yellow", "orange", "blue")


def lerp_c(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _along(P, face):
    """The horizontal coordinate that runs along a wall for this face."""
    return P[0] if face in ("north", "south", "up", "down") else P[2]


def paint_pumpkin(P, face, lit=False):
    x, y, z = P
    u = _along(P, face)
    pal = PUMPKIN_LIT if lit else PUMPKIN
    rib = math.floor(u / 2.0)
    within = (u / 2.0) - rib
    v = 0.66 + 0.06 * (hsh(rib, 7) - 0.5) * 2
    if within < 0.18:
        v -= 0.20                         # groove between ribs
    elif within < 0.45:
        v -= 0.05
    elif within > 0.8:
        v += 0.09
    if face in ("up",):
        v += 0.12
        rib2 = math.floor(z / 2.0)
        if ((z / 2.0) - rib2) < 0.2:
            v -= 0.14
    elif face == "down":
        v -= 0.25
    v += (tex(x, y, z) - 0.5) * 0.09 + 0.07
    v -= max(0.0, (6.0 - y)) * 0.012     # a little darker toward the ground
    d = (BAYER[int(math.floor(x * 2)) % 4][int(math.floor(y * 2 + z * 2)) % 4] / 16.0 - 0.5) * 0.06
    return ramp(pal, v, d)


def paint_flesh(P, face):
    x, y, z = P
    v = 0.52 + (tex(x, y, z) - 0.5) * 0.3
    v += 0.16 * math.sin(((x + z) * 1.7)) * 0.5
    if face == "up":
        v -= 0.05
    # seeds: sparse pale flecks
    if tex(x * 1.3, y, z * 0.9) > 0.985:
        return (226, 184, 110)
    # deeper toward the floor of the cavity
    v -= max(0.0, (6.0 - y)) * 0.03
    return ramp(FLESH, v)


def paint_flesh_lit(P, face):
    """The lid's inside and the upper cavity: candle-lit, with faint horizontal plank lines like the concept art."""
    x, y, z = P
    v = 0.55 + (tex(x, y, z) - 0.5) * 0.22
    if face not in ("up", "down") and (y * 2) % 4 < 0.6:
        v -= 0.2
    if face == "down":
        v += 0.08
    return ramp(FLESH_LIT, v)


def paint_charcoal(P, face, lit=False):
    x, y, z = P
    u = _along(P, face)
    pal = CHAR_LIT if lit else CHAR
    if face in ("up", "down"):
        cu, cv = math.floor(x / 1.75), math.floor(z / 1.75)
        fu, fv = (x / 1.75) - cu, (z / 1.75) - cv
    else:
        cu, cv = math.floor(u / 1.75), math.floor(y / 1.5)
        fu, fv = (u / 1.75) - cu, (y / 1.5) - cv
    base = 0.34 + (hsh(cu, cv, 3) - 0.5) * 0.22
    if fv > 0.78 or fu > 0.86:
        base -= 0.20          # shadow edge (bottom/right of each block)
    elif fv < 0.2 or fu < 0.12:
        base += 0.16          # lit edge
    if face == "up":
        base += 0.12
    base += (tex(x, y, z) - 0.5) * 0.12
    return ramp(pal, base)


def paint_vine(P, face, lit=False):
    x, y, z = P
    v = 0.45 + (tex(x, y, z) - 0.5) * 0.5 + (0.18 if lit else 0.0)
    return ramp(VINE, v)


def paint_leaf(P, face):
    x, y, z = P
    v = 0.55 + (tex(x, y, z) - 0.5) * 0.7
    if abs(((x + y + z) * 2) % 4 - 2) < 0.4:
        v -= 0.25                      # vein
    return ramp(VINE, v)


def paint_stem(P, face, lit=False):
    x, y, z = P
    v = 0.5 + (tex(x, y, z) - 0.5) * 0.35
    u = _along(P, face)
    if face not in ("up", "down") and (u * 2) % 2 < 0.5:
        v -= 0.16                      # faint vertical grain
    if face == "up":
        v += 0.28
    if lit:
        v += 0.12
    return ramp(STEM, v)


def paint_plate(P, face):
    x, y, z = P
    v = 0.62 + (tex(x, y, z) - 0.5) * 0.14
    if face == "down":
        v -= 0.25
    elif face in ("east", "west"):
        v -= 0.10
    return ramp(PLATE, v)


def paint_plate_face(P, face, island_pos, island_size):
    """The front of the plate: bevelled so the cut-outs read as carved, lit from the top-left like the art."""
    x, y, z = P
    v = 0.52 + (tex(x, y, 0) - 0.5) * 0.12
    i, j = island_pos
    w, h = island_size
    if j == 0 and h > 1:
        v += 0.2
    elif j == h - 1 and h > 1:
        v -= 0.2
    return ramp(PLATE, v)


def paint_plate_back(P, face):
    x, y, z = P
    v = 0.1 + (tex(x, y, z) - 0.5) * 0.1
    # a faint ember glow low in the cut-outs: the candle inside
    glow = max(0.0, 1.0 - abs(y - 4.5) / 3.0) * 0.18
    if face == "north":
        return ramp(EMBER, min(1.0, 0.06 + glow + (tex(x, y, z) - 0.5) * 0.1))
    return ramp(EMBER, v)


def paint_lid_under(P, face):
    x, y, z = P
    if x < 3.5 or x > 12.5:
        return paint_charcoal(P, "down", lit=False)
    return paint_flesh_lit(P, face)


def candy_stripe(kind, P, wrap=False, along=0):
    x, y, z = P
    pal = CANDY[kind]
    if wrap:
        # twist fins: pale, creased
        v = 0.78 + (tex(x, y, z) - 0.5) * 0.2
        if (math.floor(y * 2) + math.floor(z * 2) + math.floor(x * 2)) % 2:
            v -= 0.22
        return ramp(pal, v)
    chk = (math.floor(x * 1.34) + math.floor(y * 1.34) + math.floor(z * 1.34)) % 2
    v = 0.92 if chk else 0.46
    v += (tex(x, y, z) - 0.5) * 0.14
    return ramp(pal, v)


def paint_candy_mix(P, face):
    x, y, z = P
    k = MIX_ORDER[int(hsh(math.floor(x * 1.3), math.floor(y * 1.3), math.floor(z * 1.3), 5) * len(MIX_ORDER)) % len(MIX_ORDER)]
    return ramp(CANDY[k], 0.25 + hsh(math.floor(x * 2), math.floor(y * 2), math.floor(z * 2), 9) * 0.6)


def paint(mat, P, face, ipos, isize):
    if mat == "pumpkin":
        return paint_pumpkin(P, face)
    if mat == "pumpkin_lit":
        return paint_pumpkin(P, face, lit=True)
    if mat == "flesh":
        return paint_flesh(P, face)
    if mat == "flesh_lit":
        return paint_flesh_lit(P, face)
    if mat == "charcoal":
        return paint_charcoal(P, face)
    if mat == "charcoal_lit":
        return paint_charcoal(P, face, lit=True)
    if mat == "vine":
        return paint_vine(P, face)
    if mat == "vine_lit":
        return paint_vine(P, face, lit=True)
    if mat == "leaf":
        return paint_leaf(P, face)
    if mat == "stem":
        return paint_stem(P, face)
    if mat == "stem_lit":
        return paint_stem(P, face, lit=True)
    if mat == "plate":
        return paint_plate(P, face)
    if mat == "plate_face":
        return paint_plate_face(P, face, ipos, isize)
    if mat == "plate_back":
        return paint_plate_back(P, face)
    if mat == "lid_under":
        return paint_lid_under(P, face)
    if mat == "candy_mix":
        return paint_candy_mix(P, face)
    if mat.startswith("candy_"):
        wrap = mat.endswith("_wrap")
        kind = mat[len("candy_"):-len("_wrap")] if wrap else mat[len("candy_"):]
        return candy_stripe(kind, P, wrap)
    raise KeyError(mat)


# ----------------------------------------------------------------------------------------------------------------------------------
def face_point(frm, to, face, s, t):
    """World point on `face` at island parameters s (along u) and t (along v), in the vanilla UV orientation (see FaceBakery)."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    lerp = lambda a, b, k: a + (b - a) * k
    if face == "north":     # u runs toward -x (viewed from the front), v runs down
        return (lerp(x1, x0, s), lerp(y1, y0, t), z0)
    if face == "south":
        return (lerp(x0, x1, s), lerp(y1, y0, t), z1)
    if face == "east":      # u runs toward -z
        return (x1, lerp(y1, y0, t), lerp(z1, z0, s))
    if face == "west":
        return (x0, lerp(y1, y0, t), lerp(z0, z1, s))
    if face == "up":        # u +x, v +z
        return (lerp(x0, x1, s), y1, lerp(z0, z1, t))
    if face == "down":      # u +x, v -z
        return (lerp(x0, x1, s), y0, lerp(z1, z0, t))
    raise KeyError(face)


def face_extent(frm, to, face):
    w, h, d = (to[i] - frm[i] for i in range(3))
    return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}[face]


def island_size(frm, to, face):
    a, b = face_extent(frm, to, face)
    return max(1, round(a * SCALE)), max(1, round(b * SCALE))


def pack(items, width):
    """Shelf packer. items: list of (key, w, h). Returns ({key: (x, y)}, used_height). Each island is padded by PAD all round."""
    order = sorted(items, key=lambda it: (-it[2], -it[1], str(it[0])))
    pos, x, y, row_h = {}, 0, 0, 0
    for key, w, h in order:
        pw, ph = w + 2 * PAD, h + 2 * PAD
        if x + pw > width:
            x, y, row_h = 0, y + row_h, 0
        pos[key] = (x + PAD, y + PAD)
        x += pw
        row_h = max(row_h, ph)
    return pos, y + row_h


def build_atlas(model, size=None):
    """Paint every face. Returns (png image, {(element index, face): (x, y, w, h)}, size)."""
    items = []
    for i, e in enumerate(model.els):
        for f in ("north", "east", "south", "west", "up", "down"):
            if f in e.skip:
                continue
            w, h = island_size(e.frm, e.to, f)
            items.append(((i, f), w, h))
    w_atlas = size or 256
    pos, used = pack(items, w_atlas)
    h_atlas = size or 256
    while used > h_atlas:
        w_atlas = h_atlas = h_atlas * 2
        pos, used = pack(items, w_atlas)
    arr = np.zeros((h_atlas, w_atlas, 4), np.uint8)
    rects = {}
    for (i, f), (x, y) in pos.items():
        e = model.els[i]
        w, h = island_size(e.frm, e.to, f)
        mat = e.material(f)
        for j in range(h):
            for k in range(w):
                P = face_point(e.frm, e.to, f, (k + 0.5) / w, (j + 0.5) / h)
                arr[y + j, x + k] = (*paint(mat, P, f, (k, j), (w, h)), 255)
        # extrude the border texels into the padding
        for d in range(1, PAD + 1):
            arr[y - d, x:x + w] = arr[y, x:x + w]
            arr[y + h - 1 + d, x:x + w] = arr[y + h - 1, x:x + w]
        for d in range(1, PAD + 1):
            arr[y - PAD:y + h + PAD, x - d] = arr[y - PAD:y + h + PAD, x]
            arr[y - PAD:y + h + PAD, x + w - 1 + d] = arr[y - PAD:y + h + PAD, x + w - 1]
        rects[(i, f)] = (x, y, w, h)
    return Image.fromarray(arr, "RGBA"), rects, w_atlas
