#!/usr/bin/env python3
"""Points every floor of the four real towers at their own copy of the Battle Tower, one hall per floor.

    python tools/apply_battle_tower_layout.py

Only each floor's "layout" is rewritten; its pools, milestone and index are kept. The halls are the sealed round rooms of
validation/schematics/battle_tower.schem, found by scanning the building (a hall is open air above a solid ring floor): the
party stands at the y below, and all ten floors share one x/z, as the Test Tower's do. Floor 10 is the top hall under the dome.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FLOORS = ROOT / "src/main/resources/data/cobbletowers/cobbletowers/floors"
CENTRE_X, CENTRE_Z = 45, 42
STANDING_Y = [31, 42, 53, 64, 76, 88, 100, 111, 124, 141]
# How far from the middle the party, the opponent and the spectator stand, per hall. The floors are carpeted and stepped,
# and the runtime check wants a full block underfoot with clear air above, so these are the rings where that holds:
# hall 1 is smaller (ring 6), every other hall has it at ring 9.
REACH = [6, 9, 9, 9, 9, 9, 9, 9, 9, 9]


def layout(theme: str, y: int, reach: int) -> dict:
    return {
        "structure": f"cobbletowers:battle_tower_{theme}",
        "entry": {"x": CENTRE_X, "y": y, "z": CENTRE_Z - reach, "yaw": 0},
        "presentation": {"x": CENTRE_X, "y": y, "z": CENTRE_Z + reach, "yaw": 180},
        "spectator": {"x": CENTRE_X - reach, "y": y, "z": CENTRE_Z, "yaw": 270},
        "exit": {"x": CENTRE_X + reach, "y": y, "z": CENTRE_Z, "yaw": 90},
    }


def main() -> None:
    for theme in ("neutral", "tideforge", "rootvale", "duskvale"):
        for path in sorted((FLOORS / theme).glob("floor_*.json")):
            floor = json.loads(path.read_text(encoding="utf-8"))
            floor["layout"] = layout(theme, STANDING_Y[floor["index"] - 1], REACH[floor["index"] - 1])
            path.write_text(json.dumps(floor, indent=2) + "\n", encoding="utf-8")
        print(theme, "done")


if __name__ == "__main__":
    main()
