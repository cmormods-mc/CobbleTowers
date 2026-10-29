#!/usr/bin/env python3
"""Copies the generator's hollow_tower.layout.json into the ten Halloween floor definitions, so the anchors in data always match the schematic that was last generated.

    python tools/halloween_tower/apply_layout.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FLOORS = ROOT / "src/main/resources/data/cobbletowers/cobbletowers/floors/halloween"


def main() -> None:
    layouts = json.loads((ROOT / "validation/schematics/hollow_tower.layout.json").read_text())
    for index, layout in layouts.items():
        path = FLOORS / f"floor_{int(index):02d}.json"
        floor = json.loads(path.read_text())
        old = floor["layout"]
        for key in ("entry", "presentation", "spectator", "exit", "gate_a", "gate_b", "doors"):
            if key in layout:
                old[key] = layout[key]
            else:
                old.pop(key, None)
        path.write_text(json.dumps(floor, indent=2) + "\n")
        print("updated", path.name)


if __name__ == "__main__":
    main()
