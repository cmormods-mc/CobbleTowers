"""Region sets painted exactly like Paragon (same plates, rims, rivets, lames), with their own palette and chest emblem."""
import math
import numpy as np
import art_paragon as P
from armor_art import fill, poly, px


def _grid(f):
    return np.mgrid[0:f.shape[0], 0:f.shape[1]]


def wave(f, cx, cy, r, glow):
    """A ringed disc with a swell of water across a glowing core."""
    yy, xx = _grid(f)
    d = np.hypot(xx - cx, yy - cy)
    P.shade(f, d <= r, P.gold, "v", lo=3.0, hi=5.0, edge=True)
    P.shade(f, d <= r - 2.5, P.ivory, "v", lo=3.2, hi=5.0, edge=False)
    wv = (d <= r - 2.5) & (np.abs(yy - (cy + 1.6 * np.sin((xx - cx) * 0.9))) < 1.3)
    P.put(f, wv, P.gold[3])
    P.shade(f, d <= r - 5, glow, "v", lo=2.8, hi=5.2, edge=False)
    for k in range(6):
        a = k * math.pi / 3
        px(f, int(round(cx + (r + 2) * math.cos(a))), int(round(cy + (r + 2) * math.sin(a))), P.gold[4])


def leaf(f, cx, cy, r, glow):
    """A leaf: pointed oval on a ringed disc, with a glowing midrib."""
    yy, xx = _grid(f)
    d = np.hypot(xx - cx, yy - cy)
    P.shade(f, d <= r, P.gold, "v", lo=3.0, hi=5.0, edge=True)
    P.shade(f, d <= r - 2.5, P.ivory, "v", lo=3.0, hi=4.6, edge=False)
    u, v = (xx - cx + (yy - cy)) / 1.414, (xx - cx - (yy - cy)) / 1.414
    P.shade(f, (np.abs(u) / (r * 0.9) + (v / (r * 0.38)) ** 2 <= 1), P.silver, "v", lo=2.8, hi=5.0, edge=False)
    P.put(f, (np.abs(v) < 0.8) & (np.abs(u) < r * 0.75), glow[5])


def moon(f, cx, cy, r, glow):
    """A crescent moon in a ringed disc, with stars."""
    yy, xx = _grid(f)
    d = np.hypot(xx - cx, yy - cy)
    P.shade(f, d <= r, P.gold, "v", lo=3.0, hi=5.0, edge=True)
    P.shade(f, d <= r - 2.5, P.under, "v", lo=1.4, hi=3.0, edge=False)
    cres = (np.hypot(xx - cx + 0.5, yy - cy) <= r - 3.5) & (np.hypot(xx - cx - 2.2, yy - cy + 0.6) > r - 4.5)
    P.shade(f, cres, glow, "v", lo=3.0, hi=5.2, edge=False)
    for sx, sy in ((cx + 3, cy - 3), (cx + 4, cy + 2), (cx + 1, cy + 4)):
        px(f, int(sx), int(sy), glow[5])


THEMES = {
    "tideforge": dict(plate=(214, 232, 244), silver=(150, 190, 220), under=(18, 44, 84), mail=(62, 112, 164),
                      gold=(86, 190, 232), goldd=(30, 100, 160), emblem=wave),
    "rootvale": dict(plate=(188, 150, 100), silver=(132, 178, 82), under=(34, 50, 28), mail=(72, 104, 52),
                     gold=(226, 190, 96), goldd=(140, 104, 48), emblem=leaf),
    "duskvale": dict(plate=(176, 150, 226), silver=(122, 100, 184), under=(26, 16, 54), mail=(72, 52, 124),
                     gold=(250, 150, 210), goldd=(160, 74, 150), emblem=moon),
}


def painter(name):
    t = THEMES[name]

    def paint(l1, l2, look):
        P.themed(P.paint, l1, l2, look, t["plate"], t["silver"], t["under"], t["mail"], t["gold"], t["goldd"], t["emblem"])
    return type("Art", (), {"paint": staticmethod(paint)})
