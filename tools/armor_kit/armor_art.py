"""Worn-armor layers painted by hand-style code. Layout = the vanilla 64x32 armor sheet at 4 texels per pixel (128 x 256 per layer)."""
from __future__ import annotations
import math, random, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from sculpt import ramp, shade, put, erode, shift, BAYER, bayer, leaf
import sculpt

K = 4


def U(n):
    return int(round(n * K))


class Box:
    """Views into a layer array for a vanilla box UV: the four side faces as one strip, plus top and bottom."""

    def __init__(self, arr, ux, uy, w, h, d):
        self.d, self.w, self.h = U(d), U(w), U(h)
        x0, y0 = U(ux), U(uy + d)
        self.strip = arr[y0:y0 + self.h, x0:x0 + 2 * self.d + 2 * self.w]
        self.top = arr[U(uy):U(uy + d), U(ux + d):U(ux + d + w)]
        self.bottom = arr[U(uy):U(uy + d), U(ux + d + w):U(ux + d + 2 * w)]
        self.off = {"right": 0, "front": self.d, "left": self.d + self.w, "back": 2 * self.d + self.w}
        self.wid = {"right": self.d, "front": self.w, "left": self.d, "back": self.w}

    def face(self, n):
        return self.strip[:, self.off[n]:self.off[n] + self.wid[n]]


# ---- masks -------------------------------------------------------------------------------------------------------------

def _img(v):
    h, w = v.shape[:2]
    im = Image.new("L", (w, h), 0)
    return im, ImageDraw.Draw(im)


def rect(v, x, y, w, h):
    m = np.zeros(v.shape[:2], bool)
    m[max(0, y):max(0, y + h), max(0, x):max(0, x + w)] = True
    return m


def rrect(v, x, y, w, h, r):
    im, d = _img(v)
    d.rounded_rectangle([x, y, x + w - 1, y + h - 1], radius=r, fill=255)
    return np.array(im) > 0


def ell(v, x, y, w, h):
    im, d = _img(v)
    d.ellipse([x, y, x + w - 1, y + h - 1], fill=255)
    return np.array(im) > 0


def poly(v, pts):
    im, d = _img(v)
    d.polygon([(round(a), round(b)) for a, b in pts], fill=255)
    return np.array(im) > 0


def line(v, x0, y0, x1, y1, w=1):
    im, d = _img(v)
    d.line([(x0, y0), (x1, y1)], fill=255, width=w)
    return np.array(im) > 0


def ones(v):
    return np.ones(v.shape[:2], bool)


def px(v, x, y, c, a=255):
    if 0 <= y < v.shape[0] and 0 <= x < v.shape[1]:
        v[y, x] = (*c[:3], a)


def rivets(v, xs, y, rp):
    for x in xs:
        px(v, x, y, rp[5]); px(v, x + 1, y, rp[4]); px(v, x, y + 1, rp[2]); px(v, x + 1, y + 1, rp[1])


def fill(v, mask, rp, mode="v", lo=1.8, hi=4.6, edge=True, lit=0.38, inv=False):
    shade(v, mask, rp, mode, lo, hi, edge, lit, inv)


def stars(v, mask, n, seed, c1, c2):
    rnd = random.Random(seed)
    ys, xs = np.nonzero(mask)
    for _ in range(n):
        i = rnd.randrange(len(ys))
        x, y = xs[i], ys[i]
        px(v, x, y, c1)
        if rnd.random() < 0.4:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= y + dy < v.shape[0] and 0 <= x + dx < v.shape[1] and mask[y + dy, x + dx]:
                    px(v, x + dx, y + dy, c2)


def fade_alpha(v, y0, y1, seed=0):
    """Dither the rows y0..y1 out to transparent: a ragged, ghostly hem."""
    h, w = v.shape[:2]
    for y in range(max(0, y0), min(h, y1)):
        t = (y - y0 + 1) / max(1, y1 - y0)
        for x in range(w):
            if BAYER[y % 4, x % 4] < t * 1.05 - 0.02 * ((x * 7 + y * 3 + seed) % 5):
                v[y, x] = 0


def wave(v, y0, amp, period, phase=0.0, below=True):
    """A mask of everything under (or over) a sine line starting at row y0."""
    h, w = v.shape[:2]
    m = np.zeros((h, w), bool)
    for x in range(w):
        yy = int(round(y0 + amp * math.sin(2 * math.pi * x / period + phase)))
        if below:
            m[max(0, yy):, x] = True
        else:
            m[:max(0, yy), x] = True
    return m


def shingle(v, mask, y_top, y_bot, rpA, rpB, sw, sh, pitch, glow=None, down=True, x_start=-3):
    """Layered leaves (tips down), the bottom row first so every upper row overlaps the one below. Clipped to `mask`."""
    layer = np.zeros_like(v)
    rows = list(range(y_bot - sh + 2, y_top - sh, -pitch))
    for ri, y in enumerate(rows):
        for ci, x in enumerate(range(x_start + (sw // 2 if ri % 2 else 0), v.shape[1] + sw, sw - 1)):
            tile = np.zeros((sh, sw, 4), np.uint8)
            sm = np.flipud(leaf(sw, sh, bulge=0.5, wide=0.5)) if down else leaf(sw, sh, bulge=0.5, wide=0.5)
            rp = rpA if (ri + ci) % 2 == 0 else rpB
            shade(tile, sm, rp, "cyl", lo=1.8, hi=4.9, lit=0.3)
            cc = sw // 2
            for yy in range(2, sh - 3):
                tile[yy, cc] = (*rp[5], 255) if yy < sh - 5 else (*rp[3], 255)
            if glow is not None and ci % 3 == 1 and ri % 2 == 0:
                tile[sh - 5, cc] = (*glow[4], 255)
            y0, x0 = max(0, y), max(0, x)
            y1, x1 = min(v.shape[0], y + sh), min(v.shape[1], x + sw)
            if y1 > y0 and x1 > x0:
                sub = tile[y0 - y:y1 - y, x0 - x:x1 - x]
                live = sub[..., 3] > 0
                layer[y0:y1, x0:x1][live] = sub[live]
    live = (layer[..., 3] > 0) & mask
    v[live] = layer[live]
