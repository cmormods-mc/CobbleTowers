#!/usr/bin/env python3
"""Builds a shop back attachment on its own (no armor under it): geo, glow mask and texture into <out>/<name>/.

    python tools/armor_kit/build_cosmetic.py rootvale_blossom --out L:/RegionCosmetics/shop_attachments

The attachment sits on armorBody; the base armor art is only the placeholder in the texture's top rows."""
import argparse
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))

import designs8, designs9  # noqa: E402
import generate_armor_geo as gg_  # noqa: E402
import sculpt  # noqa: E402
import rig  # noqa: E402

COSMETICS = {  # name -> (art set the palette comes from, parts function, sheet height)
    "rootvale_blossom": ("rootvale", designs9.root_blossom, 384),
    "rootvale_shield": ("rootvale", designs8.root_shield, 512),
    "duskvale_wings": ("duskvale", designs8.dusk_wings, 512),
}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("name", choices=list(COSMETICS))
    ap.add_argument("--out", type=Path, default=Path("L:/RegionCosmetics/shop_attachments"))
    args = ap.parse_args()
    base, fn, height = COSMETICS[args.name]
    layers = HERE.parent.parent / "src/main/resources/assets/cobbletowers/textures/models/armor"
    out = args.out / args.name
    old = gg_.TEX_H
    sculpt.TEX_H = gg_.TEX_H = height
    try:
        import generate_armor_textures as gt
        parts = fn(gt.Look(base, gt.SPECS[base]))
        sculpt.write(parts, base, layers, out)
    finally:
        sculpt.TEX_H = gg_.TEX_H = old
    geo_path = out / f"geo/{base}.geo.json"
    g = json.loads(geo_path.read_text())
    G = g["minecraft:geometry"][0]
    keep = []
    for b in G["bones"]:                                    # drop the armor and keep only the attachment
        if b["name"] == "armorBody":
            b["cubes"] = [c for c in b["cubes"] if c["uv"][1] >= 32]
            keep.append(b)
        elif b.get("parent") == "armorBody":
            keep.append(b)
    G["bones"] = keep
    geo_path.with_name(f"{base}_attachment.geo.json").write_text(json.dumps(g, indent=1), encoding="utf-8")
    rig.write(geo_path, out / f"animations/{base}.animation.json", base)
    print(f"wrote {args.name}: {sum(len(b['cubes']) for b in keep)} cubes")


if __name__ == "__main__":
    main()
