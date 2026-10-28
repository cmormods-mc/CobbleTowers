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

SCALE = 3
PAD = 1


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


PUMPKIN = [(120, 46, 10), (188, 76, 12), (230, 108, 18), (255, 140, 30), (255, 172, 64)]
PUMPKIN_LIT = [(160, 64, 10), (214, 94, 14), (244, 126, 24), (255, 156, 44), (255, 188, 92)]
FLESH = [(84, 34, 8), (118, 48, 10), (156, 64, 12), (196, 86, 16), (226, 110, 22)]
FLESH_LIT = [(160, 70, 12), (200, 92, 16), (232, 118, 22), (252, 142, 32), (255, 172, 60)]
CHAR = [(22, 22, 26), (38, 38, 44), (56, 56, 64), (78, 78, 88), (106, 106, 118)]
CHAR_LIT = [(40, 40, 46), (60, 60, 68), (84, 84, 94), (112, 112, 124), (146, 146, 158)]
VINE = [(26, 72, 20), (44, 110, 26), (70, 146, 34), (104, 182, 46), (146, 214, 70)]
STEM = [(34, 90, 22), (54, 124, 28), (80, 158, 36), (112, 190, 50), (156, 218, 76)]
PLATE = [(140, 54, 8), (200, 84, 12), (240, 116, 20), (255, 146, 32), (255, 180, 72)]
EMBER = [(10, 4, 3), (24, 9, 5), (60, 20, 8), (140, 56, 10), (240, 140, 30)]
CANDY = {
    "red": [(140, 18, 26), (200, 36, 44), (236, 66, 68), (255, 140, 140), (255, 240, 240)],
    "pink": [(150, 22, 94), (204, 40, 130), (240, 72, 162), (255, 140, 200), (255, 226, 240)],
    "purple": [(64, 22, 112), (100, 40, 162), (140, 66, 200), (182, 116, 230), (232, 200, 252)],
    "green": [(34, 100, 22), (66, 148, 30), (108, 190, 46), (164, 228, 84), (230, 252, 196)],
    "yellow": [(176, 130, 8), (230, 178, 20), (255, 214, 50), (255, 238, 124), (255, 250, 220)],
    "orange": [(180, 72, 8), (230, 108, 16), (255, 150, 30), (255, 192, 84), (255, 234, 184)],
    "blue": [(22, 58, 142), (40, 98, 194), (72, 142, 234), (134, 190, 250), (218, 238, 255)],
}
MIX_ORDER = ("red", "pink", "purple", "green", "yellow", "orange", "blue")


def lerp_c(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _along(P, face):
    """The horizontal coordinate that runs along a wall for this face."""
    return P[0] if face in ("north", "south", "up", "down") else P[2]


def dith(P):
    """A faint ordered dither on the 0.25-unit texel grid: smooths band edges without adding noise."""
    ix, iy, iz = (int(math.floor(c * 4 + 1e-6)) for c in P)
    return (BAYER[ix % 4][(iy + iz) % 4] / 16.0 - 0.5) * 0.05


def paint_pumpkin(P, face, lit=False):
    x, y, z = P
    u = _along(P, face)
    pal = PUMPKIN_LIT if lit else PUMPKIN
    rib = math.floor(u / 2.0)
    w = (u / 2.0) - rib
    v = 0.58 + 0.04 * (math.floor(hsh(rib, 7) * 3) - 1)      # each rib one of three flat tones
    if w < 0.125:
        v -= 0.30                                            # groove, one texel wide pair
    elif w < 0.25:
        v -= 0.12
    elif w > 0.8:
        v += 0.10
    elif w > 0.68:
        v += 0.05
    if face == "up":
        v += 0.14
        w2 = (z / 2.0) - math.floor(z / 2.0)
        if w2 < 0.125:
            v -= 0.22
    elif face == "down":
        v -= 0.30
    v -= 0.05 * max(0.0, min(1.0, (5.0 - y) / 4.0))           # a step darker near the ground
    return ramp(pal, v + dith(P))


def paint_flesh(P, face):
    x, y, z = P
    v = 0.5 + (0.0 if face != "up" else -0.06)
    if face not in ("up", "down") and (y * 2) % 4 < 0.5:
        v -= 0.16                                            # plank lines
    v -= 0.04 * max(0.0, (6.0 - y))
    if tex(x * 1.3, y, z * 0.9) > 0.992:
        return (232, 190, 118)                               # a rare seed
    return ramp(FLESH, v + dith(P))


def paint_flesh_lit(P, face):
    x, y, z = P
    v = 0.58 + (0.07 if face == "down" else 0.0)
    if face not in ("up", "down") and (y * 2) % 4 < 0.5:
        v -= 0.2
    return ramp(FLESH_LIT, v + dith(P))


def paint_charcoal(P, face, lit=False):
    x, y, z = P
    u = _along(P, face)
    pal = CHAR_LIT if lit else CHAR
    if face in ("up", "down"):
        cu, cv, fu, fv = math.floor(x / 1.5), math.floor(z / 1.5), (x / 1.5) % 1, (z / 1.5) % 1
    else:
        cu, cv, fu, fv = math.floor(u / 1.5), math.floor(y / 1.5), (u / 1.5) % 1, (y / 1.5) % 1
    base = 0.42 + 0.08 * (math.floor(hsh(cu, cv, 3) * 3) - 1)    # each block one of three flat tones
    if fv < 0.1 or fu < 0.1:
        base -= 0.26                                         # mortar
    elif fv < 0.22 or fu < 0.22:
        base += 0.16                                         # lit edge
    elif fv > 0.88 or fu > 0.9:
        base -= 0.1                                          # shadow edge
    if face == "up":
        base += 0.1
    return ramp(pal, base + dith(P))


def paint_vine(P, face, lit=False):
    x, y, z = P
    v = 0.5 + (0.2 if lit else 0.0) + 0.1 * (math.floor(hsh(*(math.floor(c * 2) for c in P)) * 3) - 1)
    return ramp(VINE, v)


def paint_leaf(P, face):
    x, y, z = P
    v = 0.58 + 0.08 * (math.floor(hsh(*(math.floor(c * 2) for c in P)) * 3) - 1)
    if abs(((x + y + z) * 2) % 4 - 2) < 0.3:
        v -= 0.24
    return ramp(VINE, v)


def paint_stem(P, face, lit=False):
    x, y, z = P
    u = _along(P, face)
    v = 0.5 + (0.12 if lit else 0.0)
    if face not in ("up", "down") and (u * 2) % 2 < 0.5:
        v -= 0.16
    if face == "up":
        v += 0.26
    return ramp(STEM, v + dith(P))


def paint_plate(P, face):
    v = 0.6 - (0.28 if face == "down" else 0.12 if face in ("east", "west") else 0.0)
    return ramp(PLATE, v + dith(P))


def paint_plate_step(P, face):
    """The lower step of the face plate: a shade darker than the raised face, so the stepped bevel reads."""
    return ramp(PLATE, 0.36 + dith(P))


def paint_plate_face(P, face, island_pos, island_size):
    return ramp(PLATE, 0.66 + dith(P))


def paint_plate_back(P, face):
    x, y, z = P
    glow = max(0.0, 1.0 - abs(y - 4.8) / 3.0) * 0.2
    return ramp(EMBER, (0.06 + glow) if face == "north" else 0.12)


def paint_lid_under(P, face):
    x, y, z = P
    if x < 3.5 or x > 12.5:
        return paint_charcoal(P, "down", lit=False)
    return paint_flesh_lit(P, face)


def candy_stripe(kind, P, wrap=False, along=0):
    x, y, z = P
    pal = CANDY[kind]
    if wrap:
        v = 0.8 - (0.22 if (math.floor(y * 2) + math.floor(z * 2) + math.floor(x * 2)) % 2 else 0.0)
        return ramp(pal, v)
    chk = (math.floor(x * 1.34) + math.floor(y * 1.34) + math.floor(z * 1.34)) % 2
    return ramp(pal, 0.94 if chk else 0.46)


def paint_candy_mix(P, face):
    k = MIX_ORDER[int(hsh(*(math.floor(c * 1.3) for c in P), 5) * len(MIX_ORDER)) % len(MIX_ORDER)]
    return ramp(CANDY[k], 0.3 + 0.5 * hsh(*(math.floor(c * 2) for c in P), 9))


def paint_stave(mat, face, ipos, isize):
    """Pumpkin boards of the round body and lid: 'name:o|g:k:n' = bulging or recessed stave, layer k of n. Painted from the board's own
    UV so every stave carries its own lobe shading: a lit crown, dark edges and a baked ambient-occlusion ramp (darker low on the belly, and
    under each ledge). `pumpkin_lid` is the same on the dome, brightest toward the top."""
    name, kind, k, n = mat.split(":")
    k, n = int(k), int(n)
    i, j = ipos
    w, h = isize
    s = (i + 0.5) / w
    t = (j + 0.5) / h
    lobe = 1.0 - (2 * s - 1.0) ** 2
    bulge = kind == "o"
    v = (0.60 if bulge else 0.42) + (0.20 if bulge else 0.12) * lobe
    if t < 0.18:
        v += 0.08                                           # lit top lip of each board
    elif t > 0.78:
        v -= 0.12                                           # shaded bottom: the ledge below
    ao = 0.07 * (1.0 - k / max(1, n - 1))                    # the belly darkens toward the ground
    v += -ao if name == "pumpkin" else (0.04 * k / max(1, n - 1) - 0.06)
    if (i == 0 or i == w - 1) and w > 3:
        v -= 0.18                                           # the seam between boards
    return ramp(PUMPKIN, v + (BAYER[i % 4][j % 4] / 16.0 - 0.5) * 0.04)


def paint(mat, P, face, ipos, isize):
    if mat.startswith("pumpkin:") or mat.startswith("pumpkin_lid:"):
        return paint_stave(mat, face, ipos, isize)
    if mat == "pumpkin_top":
        return ramp(PUMPKIN, 0.82 + (BAYER[ipos[0] % 4][ipos[1] % 4] / 16.0 - 0.5) * 0.05)
    if mat == "pumpkin_bottom":
        return ramp(PUMPKIN, 0.12)
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
    if mat == "plate_step":
        return paint_plate_step(P, face)
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


NO_BEVEL = ("flesh", "candy_mix", "plate_back", "lid_under")


def bevel_edge(c, mat, k, j, w, h):
    """The one-texel outline that makes each face read as a clean tile: lit on the top/left edge, shaded on the bottom/right."""
    if mat in NO_BEVEL or mat.startswith("pumpkin") or h < 2 or (w < 2):
        return c
    f = 1.0
    if j == 0 and h >= 3:
        f *= 1.14
    elif j == h - 1 and h >= 3:
        f *= 0.74
    if w >= 4 and k == 0:
        f *= 1.08
    elif w >= 4 and k == w - 1:
        f *= 0.84
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


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
                c = paint(mat, P, f, (k, j), (w, h))
                arr[y + j, x + k] = (*bevel_edge(c, mat, k, j, w, h), 255)
        # extrude the border texels into the padding
        for d in range(1, PAD + 1):
            arr[y - d, x:x + w] = arr[y, x:x + w]
            arr[y + h - 1 + d, x:x + w] = arr[y + h - 1, x:x + w]
        for d in range(1, PAD + 1):
            arr[y - PAD:y + h + PAD, x - d] = arr[y - PAD:y + h + PAD, x]
            arr[y - PAD:y + h + PAD, x + w - 1 + d] = arr[y - PAD:y + h + PAD, x + w - 1]
        rects[(i, f)] = (x, y, w, h)
    return Image.fromarray(arr, "RGBA"), rects, w_atlas
