#!/usr/bin/env python3
"""
Tower V1 contract validation.

Exact functionality:
- Verifies the shared Tower dimension and dimension type JSON are present and parseable.
- Enforces the V1 void/fixed-day/no-bed/no-raid settings.
- Verifies the Java spatial contract retains the approved 192-block stride and 20-instance target.
- Rejects firework entity/item usage from gameplay Java/resources.
- Verifies both explosion phases are cancelled inside the Tower dimension through the required mixin.

Architectural role:
- Prevents later features from silently weakening private-instance isolation, reintroducing excluded
  effects, or removing the Tower-wide explosion safety boundary.

Performance impact:
- Build-time filesystem checks only; zero runtime server cost.
"""

from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
DIMENSION = ROOT / "src/main/resources/data/cobbletowers/dimension/tower.json"
DIMENSION_TYPE = ROOT / "src/main/resources/data/cobbletowers/dimension_type/tower.json"
CONTRACT = ROOT / "src/main/java/com/cobbletowers/instance/TowerDimensionContract.java"
EXPLOSION_MIXIN = ROOT / "src/main/java/com/cobbletowers/mixin/ExplosionMixin.java"
MIXIN_CONFIG = ROOT / "src/main/resources/cobbletowers.mixins.json"
FABRIC_MOD = ROOT / "src/main/resources/fabric.mod.json"


def fail(message: str) -> None:
    print(f"tower contract validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


for path in (DIMENSION, DIMENSION_TYPE, CONTRACT, EXPLOSION_MIXIN, MIXIN_CONFIG, FABRIC_MOD):
    if not path.is_file():
        fail(f"missing required file: {path.relative_to(ROOT)}")

try:
    dimension = json.loads(DIMENSION.read_text(encoding="utf-8"))
    dimension_type = json.loads(DIMENSION_TYPE.read_text(encoding="utf-8"))
    mixin_config = json.loads(MIXIN_CONFIG.read_text(encoding="utf-8"))
    fabric_mod = json.loads(FABRIC_MOD.read_text(encoding="utf-8"))
except json.JSONDecodeError as exc:
    fail(f"invalid required JSON: {exc}")

if dimension.get("type") != "cobbletowers:tower":
    fail("Tower dimension does not use cobbletowers:tower dimension type")

generator = dimension.get("generator", {})
if generator.get("type") != "minecraft:flat":
    fail("Tower dimension must use minecraft:flat")
settings = generator.get("settings", {})
if settings.get("biome") != "minecraft:the_void":
    fail("Tower dimension must use minecraft:the_void")
layers = settings.get("layers")
if layers != [{"block": "minecraft:air", "height": 1}]:
    fail("Tower flat generator must contain exactly one air layer")
if settings.get("structure_overrides") != []:
    fail("Tower dimension must disable vanilla structures")

required_type_values = {
    "natural": False,
    "bed_works": False,
    "respawn_anchor_works": False,
    "has_raids": False,
    "has_skylight": True,
    "has_ceiling": False,
    "fixed_time": 6000,
    "min_y": 0,
    "height": 256,
    "logical_height": 256,
}
for key, expected in required_type_values.items():
    if dimension_type.get(key) != expected:
        fail(f"dimension_type {key} must be {expected!r}, got {dimension_type.get(key)!r}")

contract_text = CONTRACT.read_text(encoding="utf-8")
if "DEFAULT_INSTANCE_STRIDE = 192" not in contract_text:
    fail("default instance stride must remain 192 blocks")
if "DEFAULT_MAX_INSTANCES = 20" not in contract_text:
    fail("default instance capacity must remain 20")

if "cobbletowers.mixins.json" not in fabric_mod.get("mixins", []):
    fail("fabric.mod.json must register cobbletowers.mixins.json")
if mixin_config.get("required") is not True:
    fail("Tower safety mixin config must be required")
if "ExplosionMixin" not in mixin_config.get("mixins", []):
    fail("Tower safety mixin config must include ExplosionMixin")
if mixin_config.get("injectors", {}).get("defaultRequire") != 1:
    fail("Tower safety mixins must require their target injections")

explosion_text = EXPLOSION_MIXIN.read_text(encoding="utf-8")
required_explosion_markers = (
    '@Inject(method = "explode"',
    '@Inject(method = "finalizeExplosion"',
    "TowerDimensionContract.DIMENSION_ID.equals(level.dimension().location().toString())",
    "ci.cancel()",
)
for marker in required_explosion_markers:
    if marker not in explosion_text:
        fail(f"ExplosionMixin missing required safety marker: {marker}")

forbidden_tokens = (
    "FireworkRocketEntity",
    "FireworkRocketItem",
    "minecraft:firework_rocket",
    "minecraft:firework_star",
)
scan_roots = [ROOT / "src/main/java", ROOT / "src/main/resources"]
for scan_root in scan_roots:
    for path in scan_root.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in {".java", ".json", ".mcfunction", ".properties"}:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        for token in forbidden_tokens:
            if token in text:
                fail(f"forbidden firework usage {token!r} in {path.relative_to(ROOT)}")

print("Tower V1 contract OK: fixed-day void, 192-block instances, 20-run target, explosions disabled, no fireworks")
