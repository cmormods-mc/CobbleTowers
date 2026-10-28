"""A small orthographic software renderer for the vault's cuboid models, textured from the real atlas with nearest sampling.

Used for the previews, the side-by-side with the concept art and the animation strip. It is NOT Minecraft's renderer: lighting is the
vanilla per-face brightness (up 1.0, north/south 0.8, east/west 0.6, down 0.5) and nothing else, no mipmaps, no antialiasing beyond a
2x supersample. Pose transforms are applied per piece to the element corners before rasterising.
"""
from __future__ import annotations
import math
import numpy as np
from PIL import Image
import vault_paint as P
FACES = ("north", "east", "south", "west", "up", "down")

SHADE = {"up": 1.0, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6, "down": 0.5}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}


def rot_matrix(axis, deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    if axis == "x": return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y": return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def face_quad(e, f):
    """Corners v0..v3 in vanilla order (u1v1, u1v2, u2v2, u2v1) before element rotation."""
    x0, y0, z0 = e.frm; x1, y1, z1 = e.to
    return {
        "north": [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)],
        "south": [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
        "east": [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)],
        "west": [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        "up": [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        "down": [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)],
    }[f]


class Pose:
    """Per-piece rigid transform: lid_deg about the hinge, plate_press (units, +z), root offset (units)."""
    def __init__(self, lid_deg=0.0, plate=0.0, root=(0, 0, 0), hinge=(8, 13.5, 17.8)):
        self.lid, self.plate, self.root, self.hinge = lid_deg, plate, np.array(root, float), np.array(hinge, float)

    def apply(self, piece, pts):
        pts = np.asarray(pts, float)
        if piece == "lid" and self.lid:
            R = rot_matrix("x", self.lid)
            pts = (pts - self.hinge) @ R.T + self.hinge
        elif piece == "face_plate" and self.plate:
            pts = pts + np.array([0, 0, self.plate])
        return pts + self.root

    def normal(self, piece, n):
        n = np.asarray(n, float)
        if piece == "lid" and self.lid:
            n = rot_matrix("x", self.lid) @ n
        return n


def render(model, atlas, rects, size, pieces, pose=None, az=-33, el=24, px=22.0, out=(640, 640), centre=(8, 8, 8), ss=2, bg=(20, 20, 24, 255),
           extra=None):
    """extra: list of (element list, transform fn) for free-floating pieces, e.g. reveal candy."""
    pose = pose or Pose()
    W, H = out[0] * ss, out[1] * ss
    zbuf = np.full((H, W), -1e9)
    img = np.zeros((H, W, 4), np.uint8); img[:] = bg
    a, p = math.radians(az), math.radians(el)
    c = np.array([math.sin(a) * math.cos(p), math.sin(p), -math.cos(a) * math.cos(p)])
    f = -c
    right = np.cross(f, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, f)
    at = np.asarray(atlas.convert("RGBA"))
    ah, aw = at.shape[:2]
    jobs = [(e, pose.apply, pose.normal) for pc in pieces for e in model.piece(pc)]
    for els, fn, nfn in (extra or []):
        jobs += [(e, fn, nfn) for e in els]
    for e, fn, nfn in jobs:
        idx = model.els.index(e)
        for face in FACES:
            if face in e.skip: continue
            quad = np.array(face_quad(e, face), float)
            if e.rot:
                ax, ang, org = e.rot
                quad = (quad - org) @ rot_matrix(ax, ang).T + org
            n = np.array(NORMAL[face], float)
            if e.rot: n = rot_matrix(e.rot[0], e.rot[1]) @ n
            wq = fn(e.piece, quad)
            wn = nfn(e.piece, n)
            if np.dot(wn, c) <= 1e-6: continue
            rect = rects.get((idx, face))
            if rect is None: continue
            rx, ry, rw, rh = rect
            rel = wq - np.array(centre)
            sx = (rel @ right) * px * ss + W / 2
            sy = -(rel @ up) * px * ss + H / 2
            depth = rel @ c
            uv = [(0, 0), (0, 1), (1, 1), (1, 0)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                _tri(img, zbuf, at, (rx, ry, rw, rh), [sx[i] for i in tri], [sy[i] for i in tri], [depth[i] for i in tri], [uv[i] for i in tri],
                     SHADE[face], wn)
    im = Image.fromarray(img, "RGBA")
    return im.resize(out, Image.LANCZOS) if ss > 1 else im


def _tri(img, zbuf, at, rect, xs, ys, zs, uvs, shade, wn):
    rx, ry, rw, rh = rect
    H, W = zbuf.shape
    x0, x1 = int(max(0, math.floor(min(xs)))), int(min(W - 1, math.ceil(max(xs))))
    y0, y1 = int(max(0, math.floor(min(ys)))), int(min(H - 1, math.ceil(max(ys))))
    if x1 < x0 or y1 < y0: return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
    if abs(d) < 1e-9: return
    l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
    l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
    l2 = 1 - l0 - l1
    m = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
    if not m.any(): return
    z = l0 * zs[0] + l1 * zs[1] + l2 * zs[2]
    u = l0 * uvs[0][0] + l1 * uvs[1][0] + l2 * uvs[2][0]
    v = l0 * uvs[0][1] + l1 * uvs[1][1] + l2 * uvs[2][1]
    sub = zbuf[y0:y1 + 1, x0:x1 + 1]
    m &= z > sub
    if not m.any(): return
    tx = np.clip(rx + (u * rw).astype(int), rx, rx + rw - 1)
    ty = np.clip(ry + (v * rh).astype(int), ry, ry + rh - 1)
    col = at[ty, tx].astype(float)
    col[..., :3] *= shade
    region = img[y0:y1 + 1, x0:x1 + 1]
    region[m] = np.clip(col[m], 0, 255).astype(np.uint8)
    sub[m] = z[m]
