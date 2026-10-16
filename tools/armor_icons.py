"""32x32 item sprites for the armor pieces, drawn from the worn layer art.

A flat sprite in the vanilla way: a classic silhouette (helmet, chestplate with sleeves, leggings, boots) filled with the
front-face texels of the layer texture, so the icon always matches what is worn. Texel positions assume the 2x layout of
tools/generate_armor_textures.py (K = 2).
"""

from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw

SIZE = 32


def _region(img: Image.Image, x, y, w, h) -> Image.Image:
    return img.crop((x, y, x + w, y + h))


def _fit(src: Image.Image, w, h) -> Image.Image:
    """Nearest-neighbour resize keeps the pixel art crisp; alpha holes (a helmet's open face) stay holes."""
    return src.resize((w, h), Image.NEAREST)


def _mask(poly_list, size=SIZE) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    for pts in poly_list:
        d.polygon(pts, fill=255)
    return m


def _composite(canvas: Image.Image, tex: Image.Image, mask: Image.Image, at, hole_color):
    """Paste `tex` at `at`, clipped by `mask`; transparent texels inside the mask become the dark interior colour."""
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    layer.paste(tex, at)
    px = layer.load()
    mp = mask.load()
    cp = canvas.load()
    for y in range(canvas.height):
        for x in range(canvas.width):
            if mp[x, y] > 0:
                r, g, b, a = px[x, y]
                cp[x, y] = (r, g, b, 255) if a > 128 else (*hole_color, 255)


def _outline(img: Image.Image, color) -> Image.Image:
    src = img.load()
    out = img.copy()
    o = out.load()
    for y in range(img.height):
        for x in range(img.width):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and src[nx, ny][3] > 0:
                        o[x, y] = (*color, 255)
                        break
    return out


def _edge_shade(img: Image.Image, light, dark) -> Image.Image:
    """Light top-left rim and dark bottom-right rim inside the silhouette, like a hand-drawn sprite."""
    src = img.load()
    out = img.copy()
    o = out.load()
    for y in range(img.height):
        for x in range(img.width):
            if src[x, y][3] == 0:
                continue
            up = y == 0 or src[x, y - 1][3] == 0
            left = x == 0 or src[x - 1, y][3] == 0
            down = y == img.height - 1 or src[x, y + 1][3] == 0
            right = x == img.width - 1 or src[x + 1, y][3] == 0
            r, g, b, a = src[x, y]
            if (up or left) and not (down or right):
                o[x, y] = (min(255, int(r * 1.25 + 12)), min(255, int(g * 1.25 + 12)), min(255, int(b * 1.25 + 12)), 255)
            elif down or right:
                o[x, y] = (int(r * 0.72), int(g * 0.72), int(b * 0.78), 255)
    return out


def make_icon(l1: Image.Image, l2: Image.Image, slot: str, outline, interior) -> Image.Image:
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    if slot == "helmet":
        front = _fit(_region(l1, 16, 16, 16, 16), 20, 20)
        side = _fit(_region(l1, 32, 16, 16, 16), 7, 20)
        m = _mask([[(5, 11), (7, 6), (11, 4), (21, 4), (25, 6), (27, 11), (27, 26), (24, 27), (24, 24), (8, 24), (8, 27), (5, 26)]])
        # front face, then the right side as a sheared strip for depth
        tmp = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        tmp.paste(front, (6, 5))
        for i in range(7):
            col = side.crop((i, 0, i + 1, 20))
            tmp.paste(col, (26 + i - 1, 5 - (i // 2) + 2))
        m2 = _mask([[(5, 11), (7, 6), (11, 4), (21, 4), (27, 6), (31, 9), (31, 23), (26, 25), (26, 24), (8, 24), (8, 27), (5, 26)]])
        _composite(canvas, tmp, m2, (0, 0), interior)
    elif slot == "chestplate":
        body = _fit(_region(l1, 40, 40, 16, 24), 16, 20)
        arm = _fit(_region(l1, 88, 40, 8, 24), 7, 17)
        tmp = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        tmp.paste(arm, (1, 6))
        tmp.paste(arm.transpose(Image.FLIP_LEFT_RIGHT), (24, 6))
        tmp.paste(body, (8, 6))
        m = _mask([[(8, 6), (12, 6), (14, 8), (18, 8), (20, 6), (24, 6), (24, 26), (8, 26)],
                   [(1, 8), (4, 5), (8, 5), (8, 23), (6, 24), (1, 22)],
                   [(31, 8), (28, 5), (24, 5), (24, 23), (26, 24), (31, 22)]])
        _composite(canvas, tmp, m, (0, 0), interior)
    elif slot == "leggings":
        waist = _fit(_region(l2, 40, 58, 16, 6), 16, 5)
        leg = _fit(_region(l2, 8, 40, 8, 24), 7, 19)
        tmp = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        tmp.paste(leg, (8, 8))
        tmp.paste(leg.transpose(Image.FLIP_LEFT_RIGHT), (17, 8))
        tmp.paste(waist, (8, 4))
        m = _mask([[(8, 4), (24, 4), (24, 9), (24, 28), (17, 28), (17, 13), (15, 13), (15, 28), (8, 28), (8, 9)]])
        _composite(canvas, tmp, m, (0, 0), interior)
    else:  # boots
        boot = _fit(_region(l1, 8, 56, 8, 8), 11, 10)
        tmp = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        tmp.paste(boot, (3, 14))
        tmp.paste(boot, (18, 14))
        m = _mask([[(3, 14), (14, 14), (14, 24), (2, 24), (2, 20), (3, 18)], [(18, 14), (29, 14), (29, 24), (17, 24), (17, 20), (18, 18)]])
        _composite(canvas, tmp, m, (0, 0), interior)
    canvas = _edge_shade(canvas, None, None)
    return _outline(canvas, outline)
