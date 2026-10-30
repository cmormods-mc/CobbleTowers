#!/usr/bin/env python3
"""Exports the hand-edited Blockbench project to the runtime model, and puts the real atlas back into the project.

    python tools/goblin/bb_export.py

art/goblin_merchant/goblin_merchant.bbmodel is the source of truth from here on (goblin_build.py made the first version; rerunning it would
overwrite hand edits). Blockbench keeps cubes, rotations and per-face UVs; its saved texture can come back as a 16x16 placeholder, so the
project's texture is rewritten from art/goblin_merchant/goblin.png every time.
"""
from __future__ import annotations

import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BB = ROOT / "art/goblin_merchant/goblin_merchant.bbmodel"
ATLAS = ROOT / "art/goblin_merchant/goblin.png"
MODEL_OUT = ROOT / "src/main/resources/assets/cobbletowers/models/halloween/goblin_merchant/goblin.json"
TEX_OUT = ROOT / "src/main/resources/assets/cobbletowers/textures/item/halloween/goblin_merchant/goblin.png"
TEX_ID = "cobbletowers:item/halloween/goblin_merchant/goblin"
FACES = ("north", "east", "south", "west", "up", "down")


def num(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def main():
    bb = json.loads(BB.read_text(encoding="utf-8"))
    size = bb["resolution"]["width"]
    k = 16.0 / size
    elements = []
    for e in bb["elements"]:
        d = {"name": e["name"], "from": [num(v) for v in e["from"]], "to": [num(v) for v in e["to"]]}
        rot = e.get("rotation")
        if rot and any(rot):
            axes = [i for i in range(3) if rot[i]]
            assert len(axes) == 1, (e["name"], "a cube may turn about one axis only", rot)
            i = axes[0]
            assert rot[i] in (-45, -22.5, 22.5, 45), (e["name"], "vanilla allows 0, 22.5 and 45 degrees", rot)
            d["rotation"] = {"angle": num(rot[i]), "axis": "xyz"[i], "origin": [num(v) for v in e["origin"]]}
        faces = {}
        for f in FACES:
            fc = e["faces"].get(f)
            if not fc or fc.get("texture") is None:
                continue
            u = fc["uv"]
            faces[f] = {"uv": [round(u[0] * k, 5), round(u[1] * k, 5), round(u[2] * k, 5), round(u[3] * k, 5)], "texture": "#0"}
        d["faces"] = faces
        for v in (*d["from"], *d["to"]):
            assert -16 <= v <= 32, (e["name"], "outside the -16..32 vanilla range", v)
        elements.append(d)
    model = {"credit": "Grizelda the goblin merchant: original work for CobbleTowers (Blockbench project art/goblin_merchant)",
             "texture_size": [size, size], "textures": {"0": TEX_ID, "particle": TEX_ID}, "elements": elements}
    MODEL_OUT.parent.mkdir(parents=True, exist_ok=True)
    MODEL_OUT.write_text(json.dumps(model, indent=1) + "\n", encoding="utf-8")
    TEX_OUT.parent.mkdir(parents=True, exist_ok=True)
    TEX_OUT.write_bytes(ATLAS.read_bytes())

    tex = bb["textures"][0]
    tex.update({"width": size, "height": size, "uv_width": size, "uv_height": size,
                "source": "data:image/png;base64," + base64.b64encode(ATLAS.read_bytes()).decode(), "name": "goblin.png"})
    BB.write_text(json.dumps(bb), encoding="utf-8")
    print(f"{len(elements)} cubes -> {MODEL_OUT.relative_to(ROOT)}; project texture restored from {ATLAS.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
