"""Baked lighting for the geo armor textures: contact shadow (ambient occlusion) from the neighbouring boxes, plus a slight
top-to-bottom gradient, applied to the painted texels so stacked boxes read as one piece of armor.

Each texel of each box face is placed in 3D (Bedrock face layout: north = front at -z, u runs toward -x on the front, -z on
the east face, +z on the west face, +x on the back; the top's v runs toward +z). A few cosine-weighted rays leave the texel;
a ray that meets another box within `REACH` units counts as occlusion. Occluded texels move toward the ramp's deep colour,
quantised with an ordered dither so the result stays pixel art.
"""

from __future__ import annotations

import math

import numpy as np

REACH = 3.5
RAYS = 20
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


def _dirs(n: int) -> np.ndarray:
    """Cosine-weighted hemisphere directions about +z, as (n, 3)."""
    out = []
    for i in range(n):
        r = math.sqrt((i + 0.5) / n)
        phi = i * 2.399963
        out.append((r * math.cos(phi), r * math.sin(phi), math.sqrt(max(0.0, 1 - r * r))))
    return np.array(out)


HEMI = _dirs(RAYS)


def _basis(normal):
    n = np.array(normal, float)
    a = np.array([1.0, 0, 0]) if abs(n[0]) < 0.9 else np.array([0, 1.0, 0])
    t = np.cross(n, a)
    t /= np.linalg.norm(t)
    return t, np.cross(n, t), n


def _faces(sx, sy, sz):
    """name -> (uv offset x, y, w, h) in the vanilla layout, in texture units."""
    return {"top": (sz, 0, sx, sz), "bottom": (sz + sx, 0, sx, sz), "east": (0, sz, sz, sy), "north": (sz, sz, sx, sy),
            "west": (sz + sx, sz, sz, sy), "south": (2 * sz + sx, sz, sx, sy)}


def _place(face, lo, hi, s, t):
    """3D points for face coordinates s, t in 0..1 (arrays), and the face normal."""
    dx, dy, dz = hi - lo
    zero = np.zeros_like(s)
    if face == "north":
        return np.stack([hi[0] - s * dx, hi[1] - t * dy, zero + lo[2]], -1), (0, 0, -1)
    if face == "south":
        return np.stack([lo[0] + s * dx, hi[1] - t * dy, zero + hi[2]], -1), (0, 0, 1)
    if face == "east":
        return np.stack([zero + hi[0], hi[1] - t * dy, hi[2] - s * dz], -1), (1, 0, 0)
    if face == "west":
        return np.stack([zero + lo[0], hi[1] - t * dy, lo[2] + s * dz], -1), (-1, 0, 0)
    if face == "top":
        return np.stack([lo[0] + s * dx, zero + hi[1], lo[2] + t * dz], -1), (0, 1, 0)
    return np.stack([lo[0] + s * dx, zero + lo[1], hi[2] - t * dz], -1), (0, -1, 0)


def occlusion(cubes: list[dict], k: int, shape: tuple[int, int]) -> np.ndarray:
    """A (h, w) float map of occlusion 0..1 for every texel covered by a cube face; others stay 0.

    `cubes` are geo cubes (origin, size, uv, inflate, optional mirror), `k` texels per unit.
    """
    h, w = shape
    occ = np.zeros((h, w), np.float32)
    los = np.array([np.array(c["origin"], float) - c.get("inflate", 0) for c in cubes])
    his = np.array([np.array(c["origin"], float) + np.array(c["size"], float) + c.get("inflate", 0) for c in cubes])
    for idx, c in enumerate(cubes):
        sx, sy, sz = c["size"]
        u, v = c["uv"]
        mirror = c.get("mirror", False)
        near = np.where(np.all(his >= los[idx] - REACH, axis=1) & np.all(los <= his[idx] + REACH, axis=1))[0]
        near = near[near != idx]
        for face, (fx, fy, fw, fh) in _faces(sx, sy, sz).items():
            rect_face = {"east": "west", "west": "east"}.get(face, face) if mirror else face
            gx, gy, gw, gh = _faces(sx, sy, sz)[rect_face]
            x0, y0 = round((u + gx) * k), round((v + gy) * k)
            x1, y1 = round((u + gx + gw) * k), round((v + gy + gh) * k)
            if x1 <= x0 or y1 <= y0:
                continue
            ys, xs = np.mgrid[y0:y1, x0:x1]
            s = (xs - x0 + 0.5) / (x1 - x0)
            t = (ys - y0 + 0.5) / (y1 - y0)
            if mirror:
                s = 1 - s
            pts, normal = _place(face, los[idx], his[idx], s.ravel(), t.ravel())
            if len(near) == 0:
                continue
            tan, bit, nrm = _basis(normal)
            dirs = HEMI[:, 0:1] * tan + HEMI[:, 1:2] * bit + HEMI[:, 2:3] * nrm  # (R, 3)
            origin = pts + nrm * 0.03  # (N, 3)
            lo = los[near][None, None]  # (1, 1, B, 3)
            hi = his[near][None, None]
            o = origin[:, None, None]
            d = dirs[None, :, None]
            with np.errstate(divide="ignore", invalid="ignore"):
                inv = 1.0 / d
                ta, tb = (lo - o) * inv, (hi - o) * inv
            tmin = np.where(np.isnan(ta), -np.inf, np.minimum(ta, tb)).max(-1)
            tmax = np.where(np.isnan(tb), np.inf, np.maximum(ta, tb)).min(-1)
            hit = (tmax >= np.maximum(tmin, 0)) & (tmin < REACH) & (tmax > 0)
            dist = np.where(hit, np.maximum(tmin, 0), REACH).min(-1)  # (N, R)
            weight = (1 - dist / REACH).clip(0, 1) * (dist < REACH)
            occ[ys.ravel(), xs.ravel()] = np.maximum(occ[ys.ravel(), xs.ravel()], weight.mean(-1))
    return occ


def bake(img: np.ndarray, cubes: list[dict], k: int, ramps: list, skip: np.ndarray, strength: float = 1.5) -> np.ndarray:
    """Darkens texels by contact shadow. `ramps` is a list of six-colour ramps (darkest first) the texels were painted from;
    `skip` is a boolean mask of texels to leave alone (glow)."""
    h, w = img.shape[:2]
    occ = occlusion(cubes, k, (h, w))
    out = img.copy()
    palette = np.array([c for r in ramps for c in r], float)  # (P, 3)
    deep = np.array([r[1] for r in ramps for _ in r], float)  # each colour's ramp deep colour
    ys, xs = np.nonzero((occ > 0.02) & (img[..., 3] > 0) & ~skip)
    if len(ys) == 0:
        return out
    rgb = img[ys, xs, :3].astype(float)
    nearest = ((rgb[:, None] - palette[None]) ** 2).sum(-1).argmin(-1)
    target = deep[nearest]
    amount = (occ[ys, xs] * strength).clip(0, 0.8)
    step = np.floor(amount / 0.2 + BAYER[ys % 4, xs % 4]) * 0.2  # dithered to 0.2 steps
    out[ys, xs, :3] = (rgb + (target - rgb) * step[:, None]).round().astype(np.uint8)
    return out
