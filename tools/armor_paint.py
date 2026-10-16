"""Pixel-art painting primitives for the armor textures: ramps, bevelled shapes, and material patterns.

Everything works in texels on a numpy RGBA canvas. A shape is a boolean mask; `Cv.plate` turns a mask into a bevelled
piece (dark outline, light top-left edge, shadow bottom-right edge, a two-tone body with a dithered join), which is what
makes flat colour read as metal, scale or bark. Ramps have six steps, darkest to lightest, hue-shifted (shadows cool,
lights warm) like the Blockbench style guide asks.
"""

from __future__ import annotations

import colorsys
import math
import random

import numpy as np
from PIL import Image, ImageDraw


def _hue_toward(h, target, amount):
    d = ((target - h + 0.5) % 1.0) - 0.5
    return (h + d * amount) % 1.0


def ramp(base):
    """Six colours, darkest to lightest: outline, deep, shadow, base, light, highlight."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in base))
    cool, warm = 250 / 360, 48 / 360

    def make(vm, sm, target, amount):
        hh = _hue_toward(h, target, amount)
        return tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hh, min(1, max(0, s * sm)), min(1, max(0, v * vm))))

    return [make(0.34, 1.05, cool, 0.35), make(0.52, 1.05, cool, 0.26), make(0.74, 1.0, cool, 0.14), make(1.0, 1, h, 0),
            make(1.16, 0.88, warm, 0.12), make(1.40, 0.55, warm, 0.22)]


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Shape:
    """A boolean mask the size of a canvas, drawn with PIL primitives."""

    def __init__(self, w, h):
        self.img = Image.new("L", (w, h), 0)
        self.d = ImageDraw.Draw(self.img)

    def rect(self, x, y, w, h):
        self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=255)
        return self

    def rrect(self, x, y, w, h, r):
        self.d.rounded_rectangle([x, y, x + w - 1, y + h - 1], radius=r, fill=255)
        return self

    def ellipse(self, x, y, w, h):
        self.d.ellipse([x, y, x + w - 1, y + h - 1], fill=255)
        return self

    def poly(self, pts):
        self.d.polygon(pts, fill=255)
        return self

    def line(self, a, b, width=1):
        self.d.line([a, b], fill=255, width=width)
        return self

    def mask(self):
        return np.array(self.img) > 0


class Cv:
    """A texel canvas."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), np.uint8)

    def shape(self):
        return Shape(self.w, self.h)

    def px(self, x, y, color, alpha=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = (*color[:3], alpha)

    def fill(self, x, y, w, h, color):
        x0, y0, x1, y1 = max(0, x), max(0, y), min(self.w, x + w), min(self.h, y + h)
        if x1 > x0 and y1 > y0:
            self.a[y0:y1, x0:x1] = (*color[:3], 255)

    def clear(self, x, y, w, h):
        x0, y0, x1, y1 = max(0, x), max(0, y), min(self.w, x + w), min(self.h, y + h)
        if x1 > x0 and y1 > y0:
            self.a[y0:y1, x0:x1] = (0, 0, 0, 0)

    def paint_mask(self, mask, color):
        self.a[mask] = (*color[:3], 255)

    def plate(self, mask, rp, outline=True, split=True, bevel=1, hot=True, tone=0.0):
        """Bevelled piece from a mask. `tone` shifts the body brighter (+) or darker (-) by whole ramp steps (0..1)."""
        m = mask.copy()
        pad = np.pad(m, 1)
        up = pad[:-2, 1:-1]
        down = pad[2:, 1:-1]
        left = pad[1:-1, :-2]
        right = pad[1:-1, 2:]
        edge = m & ~(up & down & left & right)
        inner = m & ~edge if outline else m.copy()
        out = np.zeros_like(m)
        if outline:
            out = edge
        ip = np.pad(inner, 1)
        iu, idn, il, ir = ip[:-2, 1:-1], ip[2:, 1:-1], ip[1:-1, :-2], ip[1:-1, 2:]
        tl = inner & ~(iu & il)
        br = inner & ~(idn & ir) & ~tl
        body = inner & ~tl & ~br
        ys, xs = np.nonzero(m)
        if len(ys) == 0:
            return
        x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
        span = max(1, (x1 - x0) + (y1 - y0))
        base = mix(rp[3], rp[4], tone) if tone >= 0 else mix(rp[3], rp[2], -tone)
        shade = mix(rp[2], rp[3], 0.55 + tone * 0.3)
        a = self.a
        a[out] = (*mix(rp[0], rp[1], 0.55), 255)
        big = (x1 - x0) >= 4 and (y1 - y0) >= 4 and bevel
        if big:
            a[tl] = (*rp[4], 255)
            a[br] = (*rp[2], 255)
        else:
            a[tl] = (*base, 255)
            a[br] = (*shade, 255)
        by, bx = np.nonzero(body)
        for y, x in zip(by, bx):
            t = ((x - x0) + (y - y0)) / span
            if not split:
                c = base
            elif t > 0.78 or (t > 0.66 and (x + y) % 2 == 0):
                c = shade
            else:
                c = base
            a[y, x] = (*c, 255)
        if hot and big:
            iy, ix = np.nonzero(inner)
            if len(iy):
                k = np.argmin(ix + iy)
                a[iy[k], ix[k]] = (*rp[5], 255)

    # ---- patterns ----------------------------------------------------------------------------------------------

    def lames(self, x, y, w, h, rp, rows, gap=0, round_=1, tone_step=0.12):
        """Horizontal overlapping lames (plate bands) filling the box, the top band lightest."""
        rh = max(2, math.ceil(h / rows))
        for i in reversed(range(rows)):
            sh = self.shape().rrect(x, y + i * rh, w, rh + 1 if i < rows - 1 else h - i * rh, round_)
            m = sh.mask()
            m[:, :x] = False
            self.plate(m, rp, tone=0.35 - i * tone_step)

    def scales(self, x, y, w, h, rp, size, tone_top=0.3):
        """Fish/dragon scales: offset rows of rounded-bottom scales, upper rows over lower ones."""
        rows = math.ceil(h / (size * 0.62)) + 1
        for r in reversed(range(rows)):
            yy = y + int(r * size * 0.62)
            off = 0 if r % 2 == 0 else size // 2
            tone = tone_top - 0.35 * (r / max(1, rows - 1))
            cols = w // size + 2
            for c in range(-1, cols):
                xx = x + c * size + off
                sh = self.shape().ellipse(xx, yy - size // 3, size, int(size * 1.25))
                m = sh.mask()
                clip = np.zeros_like(m)
                clip[max(0, y):min(self.h, y + h), max(0, x):min(self.w, x + w)] = True
                self.plate(m & clip, rp, tone=tone, split=False, hot=False)

    def planks(self, x, y, w, h, rp, plank, seed=1, knots=True):
        """Vertical bark planks with seams, grain dashes and knots."""
        rnd = random.Random(seed)
        n = max(1, w // plank)
        for i in range(n):
            px0 = x + i * plank
            pw = plank if i < n - 1 else x + w - px0
            tone = rnd.choice([-0.25, 0.0, 0.18, -0.1])
            m = self.shape().rect(px0, y, pw, h).mask()
            self.plate(m, rp, tone=tone, bevel=0, split=False, hot=False)
            for _ in range(max(2, h // 5)):
                gx = px0 + 1 + rnd.randrange(max(1, pw - 2))
                gy = y + 1 + rnd.randrange(max(1, h - 4))
                ln = rnd.randint(2, 4)
                for j in range(ln):
                    self.px(gx, gy + j, rp[1] if rnd.random() < 0.6 else rp[4])
            if knots and rnd.random() < 0.5 and h > 8 and pw >= 3:
                ky = y + 3 + rnd.randrange(max(1, h - 7))
                self.px(px0 + pw // 2, ky, rp[0])
                self.px(px0 + pw // 2, ky + 1, rp[0])
                self.px(px0 + pw // 2 - 1, ky, rp[2])

    def mail(self, x, y, w, h, rp):
        """Ring mail: a diagonal checker of two tones with a light speck per ring."""
        for j in range(h):
            for i in range(w):
                on = (i + j) % 2 == 0
                c = rp[2] if on else rp[3]
                if (i % 4 == 0 and j % 4 == 0):
                    c = rp[4]
                self.px(x + i, y + j, c)

    def cloth(self, x, y, w, h, rp, period=5, phase=0.0, seed=2):
        """Hanging cloth: vertical folds shaded by a sine, a little weave."""
        for i in range(w):
            wave = math.sin((i + phase) * 2 * math.pi / period)
            for j in range(h):
                t = 0.5 + 0.5 * wave
                idx = 2.0 + t * 1.5
                lo = int(idx)
                c = mix(rp[lo], rp[min(5, lo + 1)], idx - lo)
                if (i + j) % 7 == 0:
                    c = mix(c, rp[1], 0.2)
                self.px(x + i, y + j, c)

    def stars(self, x, y, w, h, color, count, seed=3, cross=True):
        rnd = random.Random(seed)
        for _ in range(count):
            sx, sy = x + rnd.randrange(w), y + rnd.randrange(h)
            self.px(sx, sy, color)
            if cross and rnd.random() < 0.35:
                dim = mix(color, (0, 0, 0), 0.45)
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    self.px(sx + dx, sy + dy, dim)

    def hline(self, x, y, w, color):
        self.fill(x, y, w, 1, color)

    def vline(self, x, y, h, color):
        self.fill(x, y, 1, h, color)

    def rivets(self, xs, y, rp):
        for x in xs:
            self.px(x, y, rp[5])
            self.px(x, y + 1, rp[1])

    def image(self):
        return Image.fromarray(self.a, "RGBA")


# ---- emblem shapes (masks sized n x n) -------------------------------------------------------------------------

def emblem_mask(kind, n):
    s = Shape(n, n)
    c = n / 2
    if kind == "crest":        # a heater shield with a blade
        s.poly([(1, 1), (n - 2, 1), (n - 2, n // 2 + 1), (c, n - 1), (1, n // 2 + 1)])
    elif kind == "shell":      # a scallop: fan over a base
        s.poly([(c, n - 1), (0, n // 2 + 1), (1, 3), (n // 4, 1), (n // 2, 0), (n - n // 4 - 1, 1), (n - 2, 3), (n - 1, n // 2 + 1)])
    elif kind == "leaf":
        s.poly([(c, 0), (n - 2, n // 3), (n - 2, n // 2 + 1), (c, n - 2), (1, n // 2 + 1), (1, n // 3)])
    elif kind == "moon":
        s.ellipse(0, 0, n, n)
        cut = Shape(n, n)
        cut.ellipse(int(n * 0.28), int(-n * 0.05), n, n)
        m = s.mask() & ~cut.mask()
        out = Shape(n, n)
        out.img = Image.fromarray((m * 255).astype("uint8"), "L")
        return out.mask()
    elif kind == "star":
        pts = []
        for i in range(10):
            r = c if i % 2 == 0 else c * 0.42
            a = -math.pi / 2 + i * math.pi / 5
            pts.append((c + r * math.cos(a), c + r * math.sin(a)))
        s.poly(pts)
    return s.mask()
