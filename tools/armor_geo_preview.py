#!/usr/bin/env python3
"""Offline picture of a geo armor set: ray-casts the model's boxes with its texture, no game or client needed.

    python tools/armor_geo_preview.py <assets/cobbletowers dir> <set> <layer 1|2|both> <out.png> [--views front,side,back]

Boxes use the same face layout as tools/armor_bake.py (so a wrong layout would show here and in the bake alike). Lit by a
fixed sun from the upper front-left so the shading can be judged; the real client lights armor differently.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import armor_bake  # noqa: E402

SUN = np.array([-0.45, 0.8, -0.55])
SUN /= np.linalg.norm(SUN)
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "top": (0, 1, 0), "bottom": (0, -1, 0)}


def load_boxes(geo_path: Path):
    geo = json.loads(geo_path.read_text())["minecraft:geometry"][0]
    desc = geo["description"]
    boxes = []
    for bone in geo["bones"]:
        for c in bone["cubes"]:
            boxes.append(c)
    return boxes, desc["texture_width"], desc["texture_height"]


def render(boxes, tex: np.ndarray, tw: int, th: int, view: str, size=(520, 700)) -> np.ndarray:
    k = tex.shape[1] / tw
    w, h = size
    yaw = {"front": 0, "side": 90, "back": 180, "side2": 270}[view]
    a = np.radians(yaw)
    # camera looks at the figure's middle from distance 70 on the -z side, rotated by yaw about y
    centre = np.array([0, 18, 0.0])
    cam = centre + 70 * np.array([np.sin(a) * -1, 0, -np.cos(a)]) + np.array([0, 4, 0])
    fwd = centre - cam
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    scale = 48.0 / h  # world units per pixel at the centre
    ys, xs = np.mgrid[0:h, 0:w]
    px = (xs - w / 2) * scale * 1.0
    py = (h / 2 - ys) * scale * 1.0
    d = fwd[None, None] * 70 + right[None, None] * px[..., None] * 1.0 + up[None, None] * py[..., None]
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    o = np.broadcast_to(cam, d.shape)
    best = np.full((h, w), np.inf)
    color = np.zeros((h, w, 4), np.float32)
    for c in boxes:
        lo = np.array(c["origin"], float) - c.get("inflate", 0)
        hi = np.array(c["origin"], float) + np.array(c["size"], float) + c.get("inflate", 0)
        with np.errstate(divide="ignore", invalid="ignore"):
            ta, tb = (lo - o) / d, (hi - o) / d
        tmin = np.nan_to_num(np.minimum(ta, tb), nan=-np.inf).max(-1)
        tmax = np.nan_to_num(np.maximum(ta, tb), nan=np.inf).min(-1)
        hit = (tmax >= tmin) & (tmin > 0) & (tmin < best)
        if not hit.any():
            continue
        t = tmin[hit]
        p = o[hit] + d[hit] * t[:, None]
        eps = 1e-3
        face = np.full(len(t), "", dtype=object)
        face[np.abs(p[:, 2] - lo[2]) < eps] = "north"
        face[np.abs(p[:, 2] - hi[2]) < eps] = "south"
        face[np.abs(p[:, 0] - hi[0]) < eps] = "east"
        face[np.abs(p[:, 0] - lo[0]) < eps] = "west"
        face[np.abs(p[:, 1] - hi[1]) < eps] = "top"
        face[np.abs(p[:, 1] - lo[1]) < eps] = "bottom"
        sx, sy, sz = c["size"]
        u0, v0 = c["uv"]
        mirror = c.get("mirror", False)
        dx, dy, dz = hi - lo
        rgba = np.zeros((len(t), 4), np.float32)
        for name in NORMALS:
            sel = face == name
            if not sel.any():
                continue
            q = p[sel]
            if name == "north":
                s, tt = (hi[0] - q[:, 0]) / dx, (hi[1] - q[:, 1]) / dy
            elif name == "south":
                s, tt = (q[:, 0] - lo[0]) / dx, (hi[1] - q[:, 1]) / dy
            elif name == "east":
                s, tt = (hi[2] - q[:, 2]) / dz, (hi[1] - q[:, 1]) / dy
            elif name == "west":
                s, tt = (q[:, 2] - lo[2]) / dz, (hi[1] - q[:, 1]) / dy
            elif name == "top":
                s, tt = (q[:, 0] - lo[0]) / dx, (q[:, 2] - lo[2]) / dz
            else:
                s, tt = (q[:, 0] - lo[0]) / dx, (hi[2] - q[:, 2]) / dz
            if mirror:
                s = 1 - s
            rect_face = {"east": "west", "west": "east"}.get(name, name) if mirror else name
            gx, gy, gw, gh = armor_bake._faces(sx, sy, sz)[rect_face]
            x0, y0 = round((u0 + gx) * k), round((v0 + gy) * k)
            x1, y1 = round((u0 + gx + gw) * k), round((v0 + gy + gh) * k)
            tx = np.clip(x0 + (s * (x1 - x0)).astype(int), x0, max(x0, x1 - 1))
            ty = np.clip(y0 + (tt * (y1 - y0)).astype(int), y0, max(y0, y1 - 1))
            texel = tex[ty, tx].astype(np.float32)
            lit = 0.55 + 0.45 * max(0.0, float(np.dot(NORMALS[name], SUN)))
            emissive = False
            texel[:, :3] *= lit
            rgba[sel] = texel
        ok = rgba[:, 3] > 0
        idx = np.nonzero(hit)
        sel_hit = np.zeros((h, w), bool)
        sel_hit[idx] = True
        yy, xx = idx[0][ok], idx[1][ok]
        better = t[ok] < best[yy, xx]
        yy, xx = yy[better], xx[better]
        color[yy, xx] = rgba[ok][better]
        best[yy, xx] = t[ok][better]
    return color


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("assets", type=Path)
    parser.add_argument("name")
    parser.add_argument("layer")
    parser.add_argument("out", type=Path)
    parser.add_argument("--views", default="front,side,back")
    args = parser.parse_args()
    boxes, tw, th = load_boxes(args.assets / f"geo/{args.name}.geo.json")
    layers = ["1", "2"] if args.layer == "both" else [args.layer]
    # layer 2 only has the legs and waist worth drawing; a combined view draws layer 1 parts then layer 2 parts
    frames = []
    for view in args.views.split(","):
        canvas = None
        for layer in layers:
            tex = np.array(Image.open(args.assets / f"textures/armor/{args.name}_{layer}.png").convert("RGBA"))
            img = render(boxes, tex, tw, th, view)
            canvas = img if canvas is None else np.where(img[..., 3:] > 0, img, canvas)
        bg = np.zeros_like(canvas)
        bg[..., :3] = (150, 175, 215)
        bg[..., 3] = 255
        frames.append(np.where(canvas[..., 3:] > 0, canvas, bg).clip(0, 255).astype(np.uint8))
    Image.fromarray(np.concatenate(frames, 1)[..., :3], "RGB").save(args.out)
    print("wrote", args.out)


if __name__ == "__main__":
    main()
