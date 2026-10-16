#!/usr/bin/env python3
"""A textured 3D preview of the armor layer textures on the vanilla humanoid armor model, without launching the game.

    python tools/armor_preview3d.py <set> [more sets] [--out sheet.png] [--layers <dir>]

Maps the REAL layer_1 / layer_2 textures through the vanilla box UV layout (head 0,0; legs 0,16; body 16,16; arms 40,16
on a 64x32 sheet, scaled for a larger texture), inflated 1.0 (layer 1) and 0.5 (layer 2) like the game, over a plain
skin-coloured player. Flat per-face light, no shaders, so it is a faithful check of the texture art, not of in-game
lighting. Importable: `render_set(layer1, layer2, yaw)` returns an RGBA image.
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw

ART = Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers/textures/models/armor"
SS = 2
SKIN = {"head": (224, 182, 146), "body": (92, 150, 200), "arm": (224, 182, 146), "leg": (70, 76, 150)}

# part: (centre-less min corner, size w,h,d, texture origin u,v, mirrored)
PARTS = {
    "head": ((-4, 24, -4), (8, 8, 8), (0, 0), False),
    "body": ((-4, 12, -2), (8, 12, 4), (16, 16), False),
    "rarm": ((-8, 12, -2), (4, 12, 4), (40, 16), False),
    "larm": ((4, 12, -2), (4, 12, 4), (40, 16), True),
    "rleg": ((-4, 0, -2), (4, 12, 4), (0, 16), False),
    "lleg": ((0, 0, -2), (4, 12, 4), (0, 16), True),
}


def inflate(lo, size, by):
    return ((lo[0] - by, lo[1] - by, lo[2] - by), (size[0] + 2 * by, size[1] + 2 * by, size[2] + 2 * by))


def face_defs(w, h, d, u, v, mirrored):
    """face -> (texture rect x,y,w,h in 64x32 units, flip_u). Faces named by world direction; front is +z."""
    reg = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    # world face -> region name; a mirrored part swaps the sides and flips every region horizontally
    names = {"-x": "right", "+x": "left", "+z": "front", "-z": "back", "+y": "top", "-y": "bottom"}
    out = {}
    for face, region in names.items():
        if mirrored:
            region = {"right": "left", "left": "right"}.get(region, region)
        out[face] = (reg[region], mirrored)
    return out


def corners(lo, size):
    x0, y0, z0 = lo
    x1, y1, z1 = x0 + size[0], y0 + size[1], z0 + size[2]
    return {
        # each face: 4 corners ordered (u0v0, u1v0, u1v1, u0v1) as seen from outside: u to the viewer's right, v downward
        "+z": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "-z": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "-x": [(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)],
        "+x": [(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)],
        "+y": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "-y": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }


NORMALS = {"+z": (0, 0, 1), "-z": (0, 0, -1), "+x": (1, 0, 0), "-x": (-1, 0, 0), "+y": (0, 1, 0), "-y": (0, -1, 0)}


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def split_emission(img):
    """A 256x64 layer carries the emission mask in its right half; returns (art, mask or None)."""
    if img is not None and img.width == img.height * 4:
        half = img.width // 2
        return img.crop((0, 0, half, img.height)), img.crop((half, 0, img.width, img.height))
    return img, None


def build_faces(scene, tex, parts, inflate_by, skin=None):
    """Appends (depth-key data) for every face of `parts` to scene; tex=None draws a flat skin colour."""
    emit = None
    if tex is not None:
        tex, emit = split_emission(tex)
    scale = tex.width / 64 if tex is not None else 1
    for name in parts:
        lo, size, (u, v), mirrored = PARTS[name]
        lo, size = inflate(lo, size, inflate_by)
        fdefs = face_defs(PARTS[name][1][0], PARTS[name][1][1], PARTS[name][1][2], u, v, mirrored)
        cs = corners(lo, size)
        for face, quad in cs.items():
            rect, flip = fdefs[face]
            scene.append((quad, NORMALS[face], rect, flip, None if tex is None else (tex, emit), scale, skin or SKIN["body"]))


def render_set(layer1: Image.Image, layer2: Image.Image, yaw_deg: float, size=(420, 560), scale=11.0,
               pieces=("head", "chest", "legs", "feet"), skin=True, focus=None) -> Image.Image:
    w, h = size[0] * SS, size[1] * SS
    S = scale * SS
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    yaw, pitch = math.radians(yaw_deg), math.radians(14)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)

    def project(p):
        x, y, z = p
        xr, zr = x * cy + z * sy, -x * sy + z * cy
        base = h * 0.9 if focus is None else h * 0.5 + focus * cp * S
        return (w / 2 + xr * S, base - (y * cp - zr * sp) * S, zr * cp + y * sp)

    scene = []
    # the player: skin everywhere, shirt and trousers so uncovered parts read
    for name, key in (("head", "head"), ("body", "body"), ("rarm", "arm"), ("larm", "arm"), ("rleg", "leg"), ("lleg", "leg")):
        if skin:
            build_faces(scene, None, [name], 0.0, SKIN[key])
    if "legs" in pieces:
        build_faces(scene, layer2, ["body", "rleg", "lleg"], 0.5)
    if "feet" in pieces:
        build_faces(scene, layer1, ["rleg", "lleg"], 1.0)
    if "chest" in pieces:
        build_faces(scene, layer1, ["body", "rarm", "larm"], 1.0)
    if "head" in pieces:
        build_faces(scene, layer1, ["head"], 1.0)

    # painter's algorithm over whole faces; layers are nested, so give outer layers a small depth bias
    items = []
    for order, (quad, n, rect, flip, tex, scl, skin) in enumerate(scene):
        nxr = n[0] * cy + n[2] * sy
        nzr = -n[0] * sy + n[2] * cy
        if nzr * cp + n[1] * sp <= 0.001:
            continue
        pts = [project(p) for p in quad]
        depth = sum(p[2] for p in pts) / 4
        items.append((depth, order, quad, n, rect, flip, tex, scl, skin, nxr, nzr))
    items.sort(key=lambda it: (it[0], it[1]))

    for depth, order, quad, n, rect, flip, tex, scl, skin, nxr, nzr in items:
        dot = max(0.0, nxr * -0.4 + n[1] * 0.8 + nzr * 0.45)
        light = 0.6 + 0.55 * dot
        if tex is None:
            draw.polygon([project(p)[:2] for p in quad], fill=tuple(int(c * light) for c in skin) + (255,))
            continue
        tex, emit = tex
        rx, ry, rw, rh = rect
        tx0, ty0, tw, th = int(rx * scl), int(ry * scl), int(rw * scl), int(rh * scl)
        p00, p10, p11, p01 = quad
        for j in range(th):
            for i in range(tw):
                si = (tw - 1 - i) if flip else i
                r, g, b, a = tex.getpixel((tx0 + si, ty0 + j))
                if a < 128:
                    continue
                u0, u1, v0, v1 = i / tw, (i + 1) / tw, j / th, (j + 1) / th
                def at(u, v):
                    return project(lerp(lerp(p00, p10, u), lerp(p01, p11, u), v))[:2]
                poly = [at(u0, v0), at(u1, v0), at(u1, v1), at(u0, v1)]
                lit = light
                if emit is not None and emit.getpixel((tx0 + si, ty0 + j))[3] > 0:
                    lit = 1.35
                col = (min(255, int(r * lit)), min(255, int(g * lit)), min(255, int(b * lit)), 255)
                draw.polygon(poly, fill=col, outline=col)
    return img.resize(size, Image.LANCZOS)


def icon(layer1: Image.Image, layer2: Image.Image, slot: str, outline, px=32) -> Image.Image:
    """A px x px item sprite of one piece: rendered 3/4 from the layer art, cropped, downsampled, outlined."""
    piece = {"helmet": "head", "chestplate": "chest", "leggings": "legs", "boots": "feet"}[slot]
    yaw = -36
    focus, scale = {"head": (28.0, 30.0), "chest": (18.0, 19.0), "legs": (9.0, 19.0), "feet": (3.0, 30.0)}[piece]
    big = render_set(layer1, layer2, yaw, size=(512, 512), scale=scale, pieces=(piece,), skin=False, focus=focus)
    box = big.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()
    if not box:
        return Image.new("RGBA", (px, px), (0, 0, 0, 0))
    big = big.crop(box)
    side = max(big.size)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.paste(big, ((side - big.width) // 2, (side - big.height) // 2))
    inner = px - 2
    small = sq.resize((inner, inner), Image.LANCZOS)
    a = small.getchannel("A").point(lambda v: 255 if v > 110 else 0)
    small.putalpha(a)
    out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    out.paste(small, (1, 1), small)
    # outline: every transparent pixel touching an opaque one
    src, dst = out.load(), out.copy().load()
    res = out.copy()
    rl = res.load()
    for y in range(px):
        for x in range(px):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < px and 0 <= ny < px and src[nx, ny][3] > 0:
                        rl[x, y] = (*outline, 255)
                        break
    return res


def load_layers(set_id: str, layers_dir: Path = ART):
    return (Image.open(layers_dir / f"{set_id}_layer_1.png").convert("RGBA"),
            Image.open(layers_dir / f"{set_id}_layer_2.png").convert("RGBA"))


def sheet(sets, out: Path, layers_dir: Path = ART, views=(-32, 148), size=(420, 560)):
    from PIL import ImageFont
    try:
        font = ImageFont.truetype("arial.ttf", 20)
    except OSError:
        font = ImageFont.load_default()
    cw = size[0] * len(views) + 20
    img = Image.new("RGB", (cw * len(sets), size[1] + 50), (30, 28, 36))
    d = ImageDraw.Draw(img)
    for n, set_id in enumerate(sets):
        l1, l2 = load_layers(set_id, layers_dir)
        d.rectangle([n * cw + 4, 4, n * cw + cw - 4, size[1] + 46], fill=(42, 39, 52), outline=(66, 62, 80))
        d.text((n * cw + 16, 12), set_id.title(), fill=(240, 232, 208), font=font)
        for k, yaw in enumerate(views):
            view = render_set(l1, l2, yaw, size)
            img.paste(view, (n * cw + 10 + k * size[0], 44), view)
    img.save(out)
    print(out, img.size)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("sets", nargs="+")
    parser.add_argument("--out", type=Path, default=Path("armor_preview3d.png"))
    parser.add_argument("--layers", type=Path, default=ART)
    args = parser.parse_args()
    sheet(args.sets, args.out, args.layers)
