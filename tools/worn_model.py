"""A tiny sculpting DSL for the worn 3D armor parts (P49): boxes in model units, one painted atlas, one model JSON.

Model units are vanilla's (16 per block, a player pixel is one unit). A part is authored around a pivot point: `pivot` is the
model point that lands on the display entity's origin axis; the entity is placed at the head's neck or the torso's centre
(see docs/design/P49-armor-models.md) so the part follows the wearer. Every box face gets its own atlas rectangle at
`SCALE` texels per unit, painted as a bevelled plate in the box's material colour.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image

from armor_paint import Cv

SCALE = 2          # atlas texels per model unit
FACES = ("north", "east", "south", "west", "up", "down")
TONES = {"up": 0.55, "north": 0.1, "south": 0.25, "east": -0.15, "west": -0.05, "down": -0.6}


@dataclass
class Box:
    frm: tuple
    to: tuple
    material: str = "metal"
    skip: tuple = ()  # faces not drawn (hidden against the body)


@dataclass
class Part:
    name: str
    boxes: list = field(default_factory=list)

    def box(self, frm, to, material="metal", skip=()):
        self.boxes.append(Box(tuple(frm), tuple(to), material, tuple(skip)))
        return self

    def mirror_x(self, centre=8.0):
        """Add the mirror image of every box across x = centre."""
        for b in list(self.boxes):
            self.boxes.append(Box((2 * centre - b.to[0], b.frm[1], b.frm[2]), (2 * centre - b.frm[0], b.to[1], b.to[2]), b.material, b.skip))
        return self


def _face_size(box: Box, face: str):
    w, h, d = (box.to[i] - box.frm[i] for i in range(3))
    if face in ("north", "south"):
        return w, h
    if face in ("east", "west"):
        return d, h
    return w, d


def build(parts: list[Part], ramps: dict, size: int = 128):
    """Pack every face of every part into one atlas; returns (atlas image, {part name: model json dict})."""
    cv = Cv(size, size)
    models, x, y, row_h = {}, 1, 1, 0
    for part in parts:
        elements = []
        for box in part.boxes:
            faces = {}
            for face in FACES:
                if face in box.skip:
                    continue
                w, h = (max(1, round(v * SCALE)) for v in _face_size(box, face))
                if x + w + 1 > size:
                    x, y, row_h = 1, y + row_h + 1, 0
                if y + h + 1 > size:
                    raise ValueError("atlas is full")
                mask = np.zeros((size, size), bool)
                mask[y:y + h, x:x + w] = True
                cv.plate(mask, ramps[box.material], outline=w >= 4 and h >= 4, split=w > 5 and h > 5, tone=TONES[face], hot=face == "up")
                faces[face] = {"uv": [round(x * 16 / size, 4), round(y * 16 / size, 4), round((x + w) * 16 / size, 4), round((y + h) * 16 / size, 4)],
                               "texture": "#0"}
                x, row_h = x + w + 1, max(row_h, h)
            elements.append({"from": list(box.frm), "to": list(box.to), "faces": faces})
        models[part.name] = {"texture_size": [size, size], "textures": {"0": "cobbletowers:item/worn/atlas", "particle": "cobbletowers:item/worn/atlas"},
                             "elements": elements}
    return Image.fromarray(cv.a, "RGBA"), models


def write(parts, ramps, out: Path, atlas_name: str, size: int = 128):
    atlas, models = build(parts, ramps, size)
    (out / "textures/item/worn").mkdir(parents=True, exist_ok=True)  # only block/ and item/ are stitched into the atlas
    (out / "models/worn").mkdir(parents=True, exist_ok=True)
    atlas.save(out / f"textures/item/worn/{atlas_name}.png")
    for name, model in models.items():
        model["textures"] = {"0": f"cobbletowers:item/worn/{atlas_name}", "particle": f"cobbletowers:item/worn/{atlas_name}"}
        (out / f"models/worn/{name}.json").write_text(json.dumps(model, indent=1), encoding="utf-8")
    return atlas
