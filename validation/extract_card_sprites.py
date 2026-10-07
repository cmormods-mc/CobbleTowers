#!/usr/bin/env python3
"""Bundles one 48x32 icon per Pokemon species from the Cobblemon Cards jar (CC0-1.0) into the mod's partner textures.

    python validation/extract_card_sprites.py [--jar <cobblemon-cards-fabric-*.jar>]

Writes src/main/resources/assets/cobbletowers/textures/gui/partners/<species>.png for every species folder in the jar, where
<species> is the folder's name with everything but letters and digits removed (the client does the same to a species id, so
"mr_mime" and "mrmime" are one file). The default icon is the plain one; a species that only has gendered icons gets the male
one (or the female one if that is all there is). Shiny icons and alternate forms are not bundled yet. Also writes manifest.json
(species -> source path in the jar), which is how a future change can tell what was copied from where.
"""
import argparse
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'src/main/resources/assets/cobbletowers/textures/gui/partners'
DEFAULT_JAR = Path('L:/claude-cobbleraids-work/testserver-full/mods-disabled-for-testing/cobblemon-cards-fabric-1.0.4.jar')
PATTERN = re.compile(r'assets/cobblemon-cards/textures/item/cards/pokemon/entity_icon/(\d+)_([^/]+)/([^/]+)\.png$')


def normalise(name):
    return re.sub(r'[^a-z0-9]', '', name.lower())


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--jar', type=Path, default=DEFAULT_JAR)
    args = parser.parse_args()
    folders = {}
    with zipfile.ZipFile(args.jar) as z:
        for info in z.infolist():
            m = PATTERN.match(info.filename)
            if m:
                folders.setdefault((m.group(1), m.group(2)), {})[m.group(3)] = info.filename
        manifest = {}
        for (dex, folder), files in sorted(folders.items()):
            candidates = [folder, folder + '_male', folder + '_female']
            pick = next((c for c in candidates if c in files), None)
            if pick is None:
                plain = sorted(n for n in files if not n.endswith('_shiny'))
                pick = plain[0] if plain else None
            if pick is None:
                continue
            key = normalise(folder)
            (OUT / f'{key}.png').write_bytes(z.read(files[pick]))
            manifest[key] = files[pick]
    (OUT / 'manifest.json').write_text(json.dumps(dict(sorted(manifest.items())), indent=0) + '\n', encoding='utf-8')
    total = sum(p.stat().st_size for p in OUT.glob('*.png'))
    print(f'{len(manifest)} species, {total // 1024} KB of icons in {OUT}')


if __name__ == '__main__':
    main()
