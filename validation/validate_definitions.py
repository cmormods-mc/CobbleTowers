#!/usr/bin/env python3
"""The shipped tower datapack must parse and resolve.

The Java registry already skips a malformed file at runtime, on purpose: one bad JSON in a
third-party datapack must not stop a server booting. That safety net would also hide a mistake in
the content this mod itself ships, where a dangling floor id means a tower nobody can play. So this
checks the bundled data at build time, where failing is exactly what should happen:

- every definition file parses, and has the fields its kind requires
- every reference resolves: tower -> ruleset, tower -> floors, floor -> encounter pool,
  floor -> ruleset override, tower -> milestones
- floors are numbered 1..n in listed order
- a milestone lands on a floor marked for it, of the same kind
- weights are positive, level bounds are ordered, party size is 1..6

    python validation/validate_definitions.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src" / "main" / "resources" / "data"
KINDS = ("towers", "floors", "encounter_pools", "rulesets", "milestones")
MAX_PARTY = 6
MIN_LEVEL, MAX_LEVEL = 1, 100


def load(namespace_dir: Path, kind: str) -> dict[str, dict]:
    """Every definition of one kind, keyed by its id, as the registry keys them."""
    found: dict[str, dict] = {}
    base = namespace_dir / "cobbletowers" / kind
    if not base.is_dir():
        return found
    for path in sorted(base.rglob("*.json")):
        ident = f"{namespace_dir.name}:{path.relative_to(base).with_suffix('').as_posix()}"
        found[ident] = json.loads(path.read_text(encoding="utf-8"))
    return found


def main() -> None:
    problems: list[str] = []
    counts = {kind: 0 for kind in KINDS}
    content = {kind: {} for kind in KINDS}

    for namespace_dir in sorted(p for p in DATA.iterdir() if p.is_dir()):
        for kind in KINDS:
            try:
                loaded = load(namespace_dir, kind)
            except json.JSONDecodeError as ex:
                problems.append(f"{namespace_dir.name}/{kind}: invalid JSON: {ex}")
                continue
            counts[kind] += len(loaded)
            content[kind].update(loaded)

    for tower_id, tower in content["towers"].items():
        for field in ("schema_version", "display_name", "ruleset", "floors"):
            if field not in tower:
                problems.append(f"{tower_id} is missing required field '{field}'")
        if tower.get("ruleset") not in content["rulesets"]:
            problems.append(f"{tower_id} names ruleset {tower.get('ruleset')}, which does not exist")
        floors = tower.get("floors", [])
        if len(set(floors)) != len(floors):
            problems.append(f"{tower_id} lists the same floor twice")
        for position, floor_id in enumerate(floors, start=1):
            floor = content["floors"].get(floor_id)
            if floor is None:
                problems.append(f"{tower_id} names floor {floor_id}, which does not exist")
                continue
            if floor.get("index") != position:
                problems.append(f"{floor_id} has index {floor.get('index')} but is floor {position} of {tower_id}")
            if floor.get("encounter_pool") not in content["encounter_pools"]:
                problems.append(f"{floor_id} names encounter pool {floor.get('encounter_pool')}, which does not exist")
            override = floor.get("ruleset_override")
            if override is not None and override not in content["rulesets"]:
                problems.append(f"{floor_id} names ruleset override {override}, which does not exist")

        by_index = {content["floors"][f].get("index"): f for f in floors if f in content["floors"]}
        for milestone_id in tower.get("milestones", []):
            milestone = content["milestones"].get(milestone_id)
            if milestone is None:
                problems.append(f"{tower_id} names milestone {milestone_id}, which does not exist")
                continue
            floor_id = by_index.get(milestone.get("floor"))
            if floor_id is None:
                problems.append(f"{milestone_id} is for floor {milestone.get('floor')}, which {tower_id} lacks")
                continue
            marked = content["floors"][floor_id].get("milestone")
            if marked is None:
                problems.append(f"{floor_id} is used by {milestone_id} but is not marked as a milestone floor")
            elif marked != milestone.get("kind"):
                problems.append(f"{floor_id} is marked {marked} but {milestone_id} is {milestone.get('kind')}")
            if milestone.get("kind") == "boss" and not milestone.get("raid_definition"):
                problems.append(f"{milestone_id} is a boss milestone but names no raid_definition")

    for pool_id, pool in content["encounter_pools"].items():
        entries = pool.get("entries", [])
        if not entries:
            problems.append(f"{pool_id} has no entries")
        for entry in entries:
            if "species" not in entry:
                problems.append(f"{pool_id} has an entry with no species")
            if entry.get("weight", 100) < 1:
                problems.append(f"{pool_id} has an entry with weight {entry.get('weight')}; weights must be >= 1")

    for ruleset_id, ruleset in content["rulesets"].items():
        levels = ruleset.get("enemy_level", {})
        low, high = levels.get("min", MIN_LEVEL), levels.get("max", MAX_LEVEL)
        if not MIN_LEVEL <= low <= high <= MAX_LEVEL:
            problems.append(f"{ruleset_id} has enemy levels {low}..{high}; must be {MIN_LEVEL}..{MAX_LEVEL} and ordered")
        size = ruleset.get("registered_party_size", MAX_PARTY)
        if not 1 <= size <= MAX_PARTY:
            problems.append(f"{ruleset_id} has registered_party_size {size}; must be 1..{MAX_PARTY}")

    total = sum(counts.values())
    if not total:
        print("Tower definition validation: FAIL -- no definitions found; nothing was checked")
        sys.exit(1)
    if problems:
        print("Tower definition validation: FAIL")
        for problem in problems:
            print("  " + problem)
        sys.exit(1)
    print("Tower definition validation: PASS -- " + ", ".join(f"{counts[kind]} {kind}" for kind in KINDS)
          + "; every reference resolves")


if __name__ == "__main__":
    main()
