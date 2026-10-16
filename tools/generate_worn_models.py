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
    # a swept dorsal fin along the head, tallest toward the front
    crest.box((7.25, 9, 2.5), (8.75, 11, 5.5))
    crest.box((7.25, 9, 5.5), (8.75, 13.5, 8.5))
    crest.box((7.25, 9, 8.5), (8.75, 16, 11), skip=("down",))
    crest.box((7.25, 9, 11), (8.75, 12, 13.5))
    crest.box((7.5, 14.5, 9), (8.5, 17, 10.5), "glow")
    # temple fins
    crest.box((0.5, 3, 6.5), (3, 6.5, 10.5))
    crest.box((-0.5, 4, 7.5), (0.5, 6, 9.5), "trim")
    crest.box((13, 3, 6.5), (15.5, 6.5, 10.5))
    crest.box((15.5, 4, 7.5), (16.5, 6, 9.5), "trim")

    paul = Part("tideforge_pauldrons")
    paul.box((11, 25, 4.5), (17, 26.5, 11.5))
    paul.box((17, 23, 5), (19, 25.5, 11))
    paul.box((13, 26.5, 6.5), (15.5, 29.5, 9.5), "trim")
    paul.box((13.5, 29.5, 7.5), (14.5, 31, 8.5), "glow")
    paul.mirror_x()
    return [crest, paul]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent.parent / "src/main/resources/assets/cobbletowers")
    args = parser.parse_args()
    look = Look("tideforge", SPECS["tideforge"])
    ramps = {"metal": look.metal, "trim": look.trim, "glow": look.glow}
    atlas = worn_model.write(tideforge(), ramps, args.out, "tideforge_atlas")
    print("wrote tideforge parts", atlas.size)


if __name__ == "__main__":
    main()
