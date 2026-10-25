"""Painted wings: a gold arm with fine feather strands in layered rows, drawn into one flat plane.

The plane is painted with the wing tip at the LEFT and the shoulder at the RIGHT (the way a back face reads from behind); the
caller flips it for the mirrored wing. Positions are in pixels: x runs from the root (right edge) toward the tip (left edge)."""
import math
import numpy as np
from sculpt import ramp, shade, put, bayer

CREAM, PEARL, WARM = ramp((240, 236, 226)), ramp((238, 238, 248)), ramp((236, 222, 196))
GOLD, GOLDD = ramp((252, 214, 100)), ramp((190, 142, 52))
TINTS = (CREAM, PEARL, CREAM, WARM, PEARL)


def feather(img, px, py, ang, length, width, rp, rng, lit=1.0, tip=None):
    """One strand from (px, py) pointing at `ang` radians (0 = toward the tip, positive = downward); shaded along and across."""
    h, w = img.shape[:2]
    dx, dy = -math.cos(ang), math.sin(ang)            # toward the tip is -x in pixels; downward is +y
    nx, ny = -dy, dx
    x0, x1 = int(max(0, min(px, px + dx * length) - width)), int(min(w, max(px, px + dx * length) + width + 1))
    y0, y1 = int(max(0, min(py, py + dy * length) - width)), int(min(h, max(py, py + dy * length) + width + 1))
    if x1 <= x0 or y1 <= y0:
        return
    yy, xx = np.mgrid[y0:y1, x0:x1]
    vx, vy = xx - px, yy - py
    along = vx * dx + vy * dy
    perp = vx * nx + vy * ny
    t = along / length
    hw = 0.5 * width * np.clip(t * 5 + 0.55, 0, 1) * (1 - np.clip(t, 0, 1) ** 3.0) ** 0.7
    jag = 0.5 * np.sin(along * 1.3 + rng.random() * 6.0) * (t > 0.7)             # a ragged, split tip
    m = (t >= 0) & (t <= 1.0) & (np.abs(perp) <= hw + jag)
    if not m.any():
        return
    u = np.clip(perp / np.maximum(hw, 0.6), -1, 1)                                  # -1 one edge, +1 the other
    lvl = 3.5 + 0.9 * (1 - np.abs(u)) - 0.9 * np.clip(u, 0, 1) * 1.0 + 0.7 * (t - 0.4) * lit
    barb = ((along / 2.6 + np.abs(perp) * 0.8).astype(int) % 4 == 0)
    lvl = lvl - 0.30 * barb
    idx = np.clip(np.floor(lvl + (bayer(*lvl.shape) - 0.5) * 0.5), 0, 5).astype(int)
    sub = img[y0:y1, x0:x1]
    for i in range(6):
        sel = m & (idx == i)
        sub[sel] = (*rp[i], 255)
    sub[m & (np.abs(perp) < 0.55)] = [*rp[5], 255]                                 # the shaft
    edge = m & (np.abs(perp) > hw - 0.9) & (u > 0)
    sub[edge] = (*rp[2], 255)                                                       # a shadowed lower edge separates the rows
    if tip is not None:
        sub[m & (t > 0.93)] = (*tip, 255)


def paint_feather(a, rp, seed, gold=None, glow=None):
    """A whole feather in one plane: base at the bottom edge, tip at the top; a gilded fringe when `gold` is given."""
    h, w = a.shape[:2]
    a[:] = 0
    rng = np.random.default_rng(seed)
    feather(a, (w - 1) / 2, h - 1.0, -math.pi / 2, h - 2.0, w - 0.5, rp, rng)
    if gold is not None:
        live = a[..., 3] > 0
        rim = live & ~np.roll(np.roll(live, 1, 0), 0, 1)                           # the upper edge of every barb
        rim[:int(h * 0.55)] &= True
        rim[int(h * 0.55):] = False
        a[rim] = (*gold[4], 255)
    if glow is not None:
        a[:3][a[:3, :, 3] > 0] = (*glow[4], 255)                                    # a glinting tip
