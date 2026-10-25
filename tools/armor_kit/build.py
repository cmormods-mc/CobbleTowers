#!/usr/bin/env python3
"""Rebuilds the worn layers, geo models and ornament textures for Tideforge, Rootvale and Duskvale.

    python tools/armor_kit/build.py [--out <assets/cobbletowers dir>] [--only tideforge,rootvale,duskvale,leviathan,heartwood,nightfall]

Writes (default: src/main/resources/assets/cobbletowers):
  textures/models/armor/<set>_layer_1|2.png   the shader-layer art (256x64-style sheet + glow mask), painted by art_<set>.py
  geo/<set>.geo.json                          the Armor Model API model: vanilla cubes + the painted attachment/accent planes
  textures/armor/<set>_1|2[_glowmask].png     the geo textures (layer art in rows 0-31, ornament parts packed below)

Design: art_tide / art_root / art_dusk paint the worn pieces; designs2 (tide, root) and designs (dusk) build the back attachments;
designs3 adds helmet and shoulder accents. sculpt.py is the shared kit (see its docstring: integer sizes only).
"""

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))

import art_dusk, art_root, art_tide  # noqa: E402
import designs, designs2, designs3, rig  # noqa: E402
import generate_armor_textures as gt  # noqa: E402
from sculpt import write  # noqa: E402

TIDEFORGE_PALETTE = dict(metal=(190, 208, 222), trim=(60, 140, 200), cloth=(30, 84, 140), glow=(170, 240, 255), helm="visor", top="ridge")
SETS = {"tideforge": (TIDEFORGE_PALETTE, art_tide, designs2.tide), "rootvale": ({}, art_root, designs2.root),
        "duskvale": ({}, art_dusk, designs.dusk)}


# the rarest (tier III) sets wear the same models and art as their regional set; files are written under the alias
ALIASES = {"leviathan": "tideforge", "heartwood": "rootvale", "nightfall": "duskvale"}


def build(out: Path, name: str) -> None:
    base = ALIASES.get(name, name)
    palette, art, attachment = SETS[base]
    gt.SPECS[base].update(palette)
    look = gt.Look(base, gt.SPECS[base])
    layers = out / "textures/models/armor"
    layers.mkdir(parents=True, exist_ok=True)
    l1 = np.zeros((128, 256, 4), np.uint8)
    l2 = np.zeros_like(l1)
    art.paint(l1, l2, look)
    gt.with_emission(Image.fromarray(l1, "RGBA"), look).save(layers / f"{name}_layer_1.png")
    gt.with_emission(Image.fromarray(l2, "RGBA"), look).save(layers / f"{name}_layer_2.png")
    parts = attachment(look) + designs3.ACCENTS[base](look)
    write(parts, name, layers, out)
    moving = rig.write(out / f"geo/{name}.geo.json", out / f"animations/{name}.animation.json", name)
    print(f"wrote {name}: {len(parts)} ornament planes/boxes, {moving} animated bones")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=HERE.parent.parent / "src/main/resources/assets/cobbletowers")
    parser.add_argument("--only", default=",".join(list(SETS) + list(ALIASES)))
    args = parser.parse_args()
    for name in args.only.split(","):
        build(args.out, name)


if __name__ == "__main__":
    main()
