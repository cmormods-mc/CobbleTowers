#!/usr/bin/env python3
"""The shipped tower datapack must parse and resolve.

The Java registry already skips a malformed file at runtime, on purpose: one bad JSON in a
third-party datapack must not stop a server booting. That safety net would also hide a mistake in
the content this mod itself ships, where a dangling floor id means a tower nobody can play. So this
checks the bundled data at build time, where failing is exactly what should happen:

- every definition file parses, and has the fields its kind requires
- every reference resolves: tower -> ruleset, tower -> reward table, tower -> floors,
  floor -> encounter pool, floor -> ruleset override, tower -> milestones
- floors are numbered 1..n in listed order
- a milestone lands on a floor marked for it, of the same kind
- weights are positive, level bounds are ordered, party size is 1..6, reward amounts are ordered
- every floor layout names a structure that exists, and every anchor in it is somewhere a player
  could actually stand: inside the structure, feet on something solid, head clear
- every floor names a boss: a loaded boss pool, or a milestone that names a raid definition

What is deliberately NOT checked: whether CobbleRaids has a raid definition by that id, or whether a
reward table's item ids actually exist. Those live behind registries this script (and the Java
registry that loads content) has no access to outside a running game -- CobbleRaids' behind an API
with no listing, vanilla's item registry behind a bootstrap this project's unit tests never run. A
missing definition or item fails at grant/start time instead, reported as a technical fault.

    python validation/validate_definitions.py
"""

from __future__ import annotations

import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from schem_to_structure import Reader  # noqa: E402  -- same directory, shared NBT reader

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src" / "main" / "resources" / "data"
STRUCTURES = DATA / "cobbletowers" / "structure"
ANCHORS = ("entry", "presentation", "spectator", "exit")
# Present in the structure but nothing to stand on. Not exhaustive -- it covers what the arenas
# actually contain, and anything else present counts as solid, which errs towards refusing an anchor
# rather than approving one.
NON_SUPPORTING = ("banner", "torch", "carpet", "button", "pressure_plate", "sign", "rail")
KINDS = ("towers", "floors", "encounter_pools", "rulesets", "milestones", "boss_pools", "modifiers",
         "reward_tables")
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


def read_structure(name: str):
    """A committed structure as (size, occupied positions, supporting positions)."""
    path = STRUCTURES / f"{name}.nbt"
    if not path.is_file():
        return None
    raw = path.read_bytes()
    data = gzip.decompress(raw) if raw[:2] == b"\x1f\x8b" else raw
    _, root = Reader(data).root()
    palette = [entry["Name"] for entry in root["palette"]]
    occupied, supporting = set(), set()
    for block in root["blocks"]:
        position = tuple(block["pos"])
        occupied.add(position)
        if not any(weak in palette[block["state"]] for weak in NON_SUPPORTING):
            supporting.add(position)
    return tuple(root["size"]), occupied, supporting


def check_layouts(content: dict, problems: list[str]) -> int:
    """Anchors are declared by hand, so they are checked against what was actually built.

    The schematics carry no marker blocks, so nothing derives these positions -- which makes an
    anchor inside a wall, or over a hole, an ordinary typo. Catching it here means it fails on the
    build that introduced it, rather than as a player standing inside a pillar.
    """
    checked = 0
    for floor_id, floor in content["floors"].items():
        layout = floor.get("layout")
        if layout is None:
            continue
        structure = layout.get("structure", "")
        if ":" not in structure:
            problems.append(f"{floor_id} names structure '{structure}', which is not a valid id")
            continue
        namespace, _, name = structure.partition(":")
        if namespace != "cobbletowers":
            continue  # somebody else's structure; not ours to verify
        loaded = read_structure(name)
        if loaded is None:
            problems.append(f"{floor_id} names structure {structure}, which is not committed"
                            f" (expected {STRUCTURES.name}/{name}.nbt)")
            continue
        size, occupied, supporting = loaded
        for anchor_name in ANCHORS:
            anchor = layout.get(anchor_name)
            if anchor is None:
                problems.append(f"{floor_id} layout has no {anchor_name} anchor")
                continue
            x, y, z = anchor.get("x", 0), anchor.get("y", 0), anchor.get("z", 0)
            where = f"{floor_id} {anchor_name} ({x},{y},{z})"
            if not (0 <= x < size[0] and 0 <= y < size[1] and 0 <= z < size[2]):
                problems.append(f"{where} is outside the structure, which is {size[0]}x{size[1]}x{size[2]}")
                continue
            if (x, y - 1, z) not in supporting:
                problems.append(f"{where} has nothing solid to stand on")
            if (x, y, z) in occupied:
                problems.append(f"{where} is inside a block")
            if (x, y + 1, z) in occupied:
                problems.append(f"{where} has no headroom")
            checked += 1
    return checked


def check_modifiers(content: dict, problems: list[str]) -> None:
    """Modifier references and the contradictions a machine notices (TDS #58).

    The one-file mistakes -- excluding itself, requiring what it excludes -- are refused by
    ModifierDefinition's own constructor, so a malformed file never loads. These are the checks
    that need the whole loaded set, which is the part Java cannot do until a server is running.
    """
    effect_fields_by_type = {
        "enemy": ("level_offset", "boss_level_offset", "boss_health_percent"),
        "encounter": ("extra_opponents",),
        "player_constraint": ("banned_moves", "allow_switching", "allow_items"),
        "field": ("weather", "terrain"),
        "reward": ("reward_percent",),
    }
    for modifier_id, modifier in content["modifiers"].items():
        for field in ("schema_version", "type", "display_name"):
            if field not in modifier:
                problems.append(f"{modifier_id} is missing required field '{field}'")

        kind = modifier.get("type")
        if kind is not None and kind not in effect_fields_by_type:
            problems.append(f"{modifier_id} has unknown type '{kind}'")

        excludes = modifier.get("excludes", [])
        requires = modifier.get("requires", [])
        for other in excludes:
            if other not in content["modifiers"]:
                problems.append(f"{modifier_id} excludes {other}, which does not exist")
        for other in requires:
            if other not in content["modifiers"]:
                problems.append(f"{modifier_id} requires {other}, which does not exist")
        if modifier_id in excludes:
            problems.append(f"{modifier_id} excludes itself")
        if modifier_id in requires:
            problems.append(f"{modifier_id} requires itself, which nothing could ever satisfy")
        for other in requires:
            if other in excludes:
                problems.append(f"{modifier_id} both requires and excludes {other}")

        if modifier.get("stack_limit", 1) < 1:
            problems.append(f"{modifier_id} has stack_limit {modifier.get('stack_limit')}; it must be >= 1")
        if modifier.get("weight", 100) < 1:
            problems.append(f"{modifier_id} has weight {modifier.get('weight')}; weights must be >= 1")

        # The mistake content actually makes: copy a modifier, change its type, keep the payload.
        effect = modifier.get("effect", {})
        if kind in effect_fields_by_type and not any(f in effect for f in effect_fields_by_type[kind]):
            problems.append(
                f"{modifier_id} is type '{kind}' but its effect sets none of "
                f"{', '.join(effect_fields_by_type[kind])}")

    # A prerequisite cycle can never be drafted: every member needs another member held first.
    requires_map = {mid: [r for r in m.get("requires", []) if r in content["modifiers"]]
                    for mid, m in content["modifiers"].items()}
    for start in requires_map:
        seen, stack = set(), [start]
        while stack:
            current = stack.pop()
            for nxt in requires_map.get(current, []):
                if nxt == start:
                    problems.append(f"{start} is in a prerequisite cycle, so it can never be drafted")
                    stack = []
                    break
                if nxt not in seen:
                    seen.add(nxt)
                    stack.append(nxt)


def check_reward_tables(content: dict, problems: list[str]) -> None:
    """Structural checks only. Whether an item id actually exists is not checked, here or on the Java
    side: vanilla's item registry is not bootstrapped in this project's plain-JVM unit tests, the same
    reasoning that already keeps this script (and BossPoolDefinition/EncounterPoolDefinition) from
    checking CobbleRaids or Cobblemon ids -- an unknown item fails when it is actually granted,
    reported as a technical fault.
    """
    tier_keys = ("opponent_defeated", "boss_defeated", "floor_cleared")
    for table_id, table in content["reward_tables"].items():
        for field in ("schema_version", "display_name"):
            if field not in table:
                problems.append(f"{table_id} is missing required field '{field}'")
        tiers = table.get("tiers", {})
        for key in tiers:
            if key not in tier_keys:
                problems.append(f"{table_id} has an unknown tier '{key}'; must be one of {tier_keys}")
        for key, entries in tiers.items():
            for entry in entries:
                if "item" not in entry:
                    problems.append(f"{table_id} tier '{key}' has an entry with no item")
                if entry.get("weight", 100) < 1:
                    problems.append(f"{table_id} tier '{key}' has an entry with weight {entry.get('weight')};"
                                    " weights must be >= 1")
                low, high = entry.get("min_amount", 1), entry.get("max_amount", 1)
                if not 1 <= low <= high:
                    problems.append(f"{table_id} tier '{key}' has min_amount/max_amount {low}/{high};"
                                    " must be >= 1 and ordered")


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
        for field in ("schema_version", "display_name", "ruleset", "reward_table", "floors"):
            if field not in tower:
                problems.append(f"{tower_id} is missing required field '{field}'")
        if tower.get("ruleset") not in content["rulesets"]:
            problems.append(f"{tower_id} names ruleset {tower.get('ruleset')}, which does not exist")
        if tower.get("reward_table") not in content["reward_tables"]:
            problems.append(f"{tower_id} names reward table {tower.get('reward_table')}, which does not exist")
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
            # Reserved since P1 and carried untouched; P8 is where it finally has to resolve.
            for modifier_id in floor.get("modifiers", []):
                if modifier_id not in content["modifiers"]:
                    problems.append(f"{floor_id} names modifier {modifier_id}, which does not exist")

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

    check_modifiers(content, problems)
    check_reward_tables(content, problems)

    for pool_id, pool in content["boss_pools"].items():
        entries = pool.get("entries", [])
        if not entries:
            problems.append(f"{pool_id} has no entries")
        for entry in entries:
            if "definition" not in entry:
                problems.append(f"{pool_id} has an entry with no definition")
            if entry.get("weight", 100) < 1:
                problems.append(f"{pool_id} has an entry with weight {entry.get('weight')}; weights must be >= 1")

    # Every floor has to be finishable: a boss pool, or a milestone that names one.
    for tower_id, tower in content["towers"].items():
        milestone_floors = {}
        for milestone_id in tower.get("milestones", []):
            milestone = content["milestones"].get(milestone_id)
            if milestone:
                milestone_floors[milestone.get("floor")] = milestone.get("raid_definition")
        for floor_id in tower.get("floors", []):
            floor = content["floors"].get(floor_id)
            if floor is None:
                continue
            if floor.get("boss_pool"):
                continue
            if not milestone_floors.get(floor.get("index")):
                problems.append(f"{floor_id} names no boss_pool and no milestone boss, so it cannot be finished")

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

    anchors_checked = check_layouts(content, problems)

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
          + f"; every reference resolves, {anchors_checked} anchor(s) stand on solid ground")


if __name__ == "__main__":
    main()
