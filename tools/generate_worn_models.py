#!/usr/bin/env python3
"""Worn 3D parts (P49 spike): Tideforge's helmet crest and chestplate pauldrons as cuboid models plus one painted atlas.

    python tools/generate_worn_models.py [--out <assets/cobbletowers dir>]

Head parts are authored with the neck centre at model (8, 0, 8) and the head 4..12 wide; torso parts with the feet centre at
(8, 0, 8) and the shoulder line at y = 24. The armor shell is about one unit larger than the body, so parts sit just outside it.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_armor_textures import SPECS, Look  # noqa: E402
import worn_model  # noqa: E402
from worn_model import Part  # noqa: E402


def tideforge() -> list[Part]:
    crest = Part("tideforge_crest")
    # a comb of three swept blades along the head, leaning back, with a glowing eye on the tallest
    crest.box((7, 9, 3), (9, 15, 6.5), rot=("x", -22.5, (8, 9, 4.5)))
    crest.box((7, 9, 6.5), (9, 18, 10), rot=("x", -22.5, (8, 9, 8)))
    crest.box((7, 9, 10), (9, 13.5, 13.5), "trim")
    crest.box((7.4, 13, 8.2), (8.6, 15.5, 9.4), "glow", rot=("x", -22.5, (8, 9, 8)))
    # a fin hanging off the nape
    crest.box((6.8, -1, 1.2), (9.2, 8, 3), skip=("south",))
    # swept temple fins, flaring out and back, mirrored
    sides = len(crest.boxes)
    crest.box((-0.5, 3, 3.5), (1.5, 10, 11), rot=("y", 22.5, (0.5, 3, 11)))
    crest.box((-1.5, 5, 6), (-0.5, 8.5, 9.5), "trim")
    crest.mirror_x(since=sides)

    mantle = Part("tideforge_mantle")
    # stacked shell pauldrons with a glowing pearl on top
    mantle.box((12, 24.5, 3.5), (19, 26.2, 12.5))
    mantle.box((13.5, 26.2, 4.5), (20, 27.7, 11.5))
    mantle.box((16, 27.7, 5.5), (20.5, 28.9, 10.5), "trim")
    mantle.box((14.6, 27.7, 7.3), (16, 29.3, 8.7), "glow")
    mantle.box((19, 21.5, 4.5), (21, 26, 11.5))
    mantle.box((20.5, 18.5, 5.5), (22, 24, 10.5), "trim")
    mantle.mirror_x()
    # a carapace down the back with a dorsal fin along the spine
    mantle.box((3.5, 12, 11.2), (12.5, 25, 12.8))
    mantle.box((4.5, 15, 12.8), (11.5, 24, 14.4))
    mantle.box((6, 18, 14.4), (10, 23, 16), "trim")
    mantle.box((7.2, 24.5, 11.8), (8.8, 29.5, 16), rot=("x", 22.5, (8, 24.5, 12)))
    mantle.box((7.5, 20, 15.8), (8.5, 22, 16.8), "glow")
    return [crest, mantle]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers")
    args = parser.parse_args()
    look = Look("tideforge", SPECS["tideforge"])
    ramps = {"metal": look.metal, "trim": look.trim, "glow": look.glow}
    atlas = worn_model.write(tideforge(), ramps, args.out, "tideforge_atlas", size=256)
    print("wrote tideforge parts", atlas.size)


if __name__ == "__main__":
    main()
