"""Idle animations for the back attachments and accents, written as Bedrock `.animation.json` next to the geo.

Armor Model API draws geo models static, so these do not play in the game: they are the intended motion for Blockbench (and for any
client or display-entity rig that wants to play them), and the in-game life comes from the server-side particle flares. Every
animation loops over LENGTH seconds so all the parts stay in step.

A bone is animated by the name pattern of its group (see the designs*.py files): tank_l, wheel_r, gauge, bubble_N, fin, row_N,
row_N_side, boss, crest_N, shoulder_N, moon, tendril_N, star_N, wisp_N. A `_m` suffix is the mirrored twin.
"""

import json
import math
import re
from pathlib import Path

LENGTH = 4.0
STEPS = 16


def _samples(fn):
    return {f"{LENGTH * i / STEPS:.3f}": [round(v, 4) for v in fn(i / STEPS)] for i in range(STEPS + 1)}


def _wave(amp, phase=0.0, axis=2):
    def f(t):
        v = [0.0, 0.0, 0.0]
        v[axis] = amp * math.sin(2 * math.pi * (t + phase))
        return v
    return f


def _pulse(lo, hi, phase=0.0):
    def f(t):
        k = 0.5 - 0.5 * math.cos(2 * math.pi * (t + phase))
        s = lo + (hi - lo) * k
        return [s, s, s]
    return f


def _rise(height, phase):
    """A bubble rising and shrinking away, wrapping so each one is at a different height at any moment."""
    def f(t):
        u = (t + phase) % 1.0
        return [0.0, height * u, 0.0]
    return f


def _bubble_scale(phase):
    def f(t):
        u = (t + phase) % 1.0
        s = 0.35 + 0.65 * math.sin(math.pi * min(1.0, u * 1.15))
        return [max(0.05, s)] * 3
    return f


def motion(name):
    """{channel: keyframes} for one bone, or None for a part that holds still."""
    base = re.sub(r"_m$", "", name)
    mirror = -1 if name.endswith("_m") else 1
    n = int(re.search(r"(\d+)", base).group(1)) if re.search(r"(\d+)", base) else 0
    if base in ("tank_l", "tank_r"):
        return {"rotation": _samples(_wave(0.7, 0.0 if base.endswith("l") else 0.5, 0))}
    if base in ("wheel_l", "wheel_r"):
        return {"rotation": _samples(_wave(14, 0.1 if base.endswith("l") else 0.6, 1))}
    if base == "gauge":
        return {"scale": _samples(_pulse(1.0, 1.1))}
    if re.match(r"bubble_\d+$", base):
        ph = (n * 0.17) % 1.0
        return {"position": _samples(_rise(9.0, ph)), "scale": _samples(_bubble_scale(ph))}
    if base == "fin":
        return {"rotation": _samples(_wave(5 * mirror, 0.0, 2))}
    if re.match(r"row_\d+(_side)?$", base):
        return {"rotation": _samples(_wave(2.5, -0.07 * n, 0))}
    if base == "halo":
        return {"scale": _samples(_pulse(0.9, 1.12, 0.1))}
    if re.match(r"mane_\d+$", base):
        return {"rotation": _samples(_wave(3.0, 0.15 * n, 2))}
    if re.match(r"sprout_\d+$", base):
        return {"rotation": _samples(lambda t, a=_wave(5.0, 0.04 * n, 0), b=_wave(2.0, 0.09 * n, 2): [x + y for x, y in zip(a(t), b(t))])}
    if re.match(r"wing_\d+$", base):
        return {"rotation": _samples(_wave((5.0 + n % 4) * mirror, 0.05 * (n % 4) + (0.25 if n >= 4 else 0.0), 2))}   # each row trails the last; the lower pair beats a quarter late
    if base == "ring":
        return {"scale": _samples(_pulse(0.96, 1.06))}
    if base == "gem":
        return {"scale": _samples(_pulse(1.0, 1.18, 0.1))}
    if base == "boss":
        return {"scale": _samples(_pulse(1.0, 1.14, 0.25))}
    if re.match(r"crest_\d+$", base):
        return {"rotation": _samples(_wave(2.5, 0.0, 2))}                       # all in step, so the leaves never cross each other
    if re.match(r"shoulder_\d+$", base):
        return {"rotation": _samples(_wave(5 * mirror, 0.2 * n, 2))}
    if base == "moon":
        return {"position": _samples(lambda t: [0.0, 0.7 * math.sin(2 * math.pi * t), 0.0]), "rotation": _samples(_wave(2.5, 0.25, 2))}
    if re.match(r"tendril_\d+$", base):
        return {"rotation": _samples(_wave(5.0, 0.13 * n, 2))}
    if re.match(r"star_\d+$", base):
        return {"scale": _samples(_pulse(0.35, 1.15, (n * 0.23) % 1.0))}
    if re.match(r"leafring_\d+$", base):
        return {"rotation": _samples(_wave(2.0 * (1 if n % 2 else -1), 0.07 * n, 2))}          # the layers sway a little against each other
    if re.match(r"wisp_\d+$", base):
        return {"rotation": _samples(_wave(7.0 * mirror, 0.3 * n, 2))}
    return None


def write(geo_path: Path, out_path: Path, set_name: str) -> int:
    """Writes `animation.<set>.idle` for every animatable bone found in the geo; returns how many bones move."""
    geo = json.loads(geo_path.read_text())["minecraft:geometry"][0]
    bones = {}
    for b in geo["bones"]:
        m = motion(b["name"])
        if m:
            bones[b["name"]] = m
    doc = {"format_version": "1.8.0", "animations": {f"animation.{set_name}.idle": {"loop": True, "animation_length": LENGTH, "bones": bones}}}
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(doc, indent=1), encoding="utf-8")
    return len(bones)
