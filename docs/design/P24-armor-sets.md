# P24: Custom armor sets

Status: design, written before the code (the TDS gate). Builds on P21 (reward tables) and P23 (Showdown effects).

## Decisions already made with the user

| Question | Answer |
|---|---|
| Bonus kinds | **All four**: Cobblemon, Player, Battle, Tower |
| How pieces are obtained | **Drops from the tower** (entries in the reward tables), no crafting |
| Art | **Generated placeholder art**, replaceable later without code changes |
| Protection | **Diamond level** (3/8/6/3, toughness 2, durability factor 33, enchantability 10) |
| Sets | **Four**: Challenger (neutral), Tideforge, Rootvale, Duskvale |
| Battle bonuses | via the **Showdown patch** (P23) |

## The sets

Ids and item names follow one convention: `cobbletowers:<set>_<slot>` with slots `helmet`, `chestplate`,
`leggings`, `boots`; 16 items. A bonus needs a number of worn pieces of one set (tiers at **2** and **4**; the
four-piece tier is the "full set" bonus).

| Set | Tower | 2 pieces | 4 pieces (full set) |
|---|---|---|---|
| Challenger | Neutral | Player: +5% movement speed. Cobblemon: +5% experience | Battle: lead +1 Speed. Tower: 10% off vendor services |
| Tideforge | Tideforge | Player: +1 breath time, +50% water movement efficiency. Cobblemon: +10% catch rate | Battle: permanent rain, +20% Water damage dealt. Tower: +10% Raid Points |
| Rootvale | Rootvale | Player: +2 max health. Cobblemon: +10% experience | Battle: Grassy Terrain (5 turns), +20% Grass damage dealt. Tower: 10% off vendor services |
| Duskvale | Duskvale | Player: +1 luck. Cobblemon: +10% shiny odds | Battle: +20% Dark damage dealt, -10% damage taken. Tower: +10% Raid Points |

Numbers are deliberately modest (the user's earlier "Modest" answer for reward sizing); they are data and tunable.
A mixed set (two Tideforge and two Rootvale) gets both 2-piece bonuses and neither full-set bonus.

## Architecture

* **Items are Java, bonuses are data.** Items, armor materials and layer textures must exist before data loads, so the
  16 pieces are registered in `ArmorSetItems` from a fixed list of four sets. Everything about *what a set does* is a
  datapack file `data/<ns>/cobbletowers/armor_sets/<id>.json`, loaded by its own reload listener
  (`ArmorSetRegistry`, same malformed-file rule as `TowerDefinitionRegistry`: skipped and named, never thrown).
  A separate listener rather than an eleventh field on `TowerContent`: armor is not tower content and the record is
  already wide.
* **Pure core.** `ArmorSetDefinition` (parse + validate), `SetBonus` (a sealed hierarchy: `PlayerAttribute`,
  `CobblemonModifier`, `BattleEffects`, `TowerModifier`) and `SetBonusResolver` (worn item ids to active bonuses) have no
  Minecraft or Cobblemon types, so the rules are unit tests (the registry bootstrap gap applies: `BuiltInRegistries`
  throws in plain-JVM tests, so ids are checked at apply time and logged, never in the parser).
* **Detection.** `WornSets` samples a player's four armor slots every 10 ticks (`END_SERVER_TICK`), resolves active
  bonuses, caches them per player and, on a change, applies/removes attribute modifiers. Cache is dropped on
  disconnect. Everything that consumes a bonus reads the cache; nothing re-derives it.
* **Player bonuses** are `AttributeModifier`s with a stable id `cobbletowers:armor_set/<set>/<n>`; applied only when the
  tier is active and always removed when it is not (and on login, so a stale modifier from a crash cannot linger).
* **Cobblemon bonuses** hook `EXPERIENCE_GAINED_EVENT_PRE` (owner of the Pokemon), `POKEMON_CATCH_RATE` (thrower) and
  `SHINY_CHANCE_CALCULATION` (its per-player modification function). Each handler is wrapped so a failure leaves
  Cobblemon's own number untouched.
* **Battle bonuses** are P23 `towerFx` operations with logical sides. At the moment a tower battle is armed, the
  player's worn-set battle effects are merged with anything an operator queued: `TowerBattleFx.armFloorBattle(player,
  extra)` / `armBossBattle(players, extras)`. They never apply outside tower battles (that is P23's guarantee).
* **Tower bonuses**: `vendor_discount_percent` (applied where the vendor price is computed, on the buyer) and
  `raid_points_percent` (applied where Raid Points are delivered to a player). Both clamp to 0..50 and read the cache.
* **Drops.** Pieces are entries in the P21 reward tables: each regional table carries its own set (and Challenger sits in
  neutral and, rarer, in the shared core), at a weight that makes a full set an occasional-run achievement rather
  than a first-run one.
* **Art.** `tools/generate_armor_art.py` (Pillow) writes the layer textures (64x32, two per set) and 16x16 item icons
  from a per-set palette; item models are `item/generated`. The PNGs are committed, the script is how a human
  regenerates or replaces them.

## Data format

```json
{
  "schema_version": 1, "revision": 1,
  "display_name": "Tideforged",
  "pieces": {"head": "cobbletowers:tideforge_helmet", "chest": "...", "legs": "...", "feet": "..."},
  "bonuses": [
    {"pieces": 2, "kind": "player", "attribute": "minecraft:generic.oxygen_bonus", "operation": "add_value", "amount": 1.0},
    {"pieces": 2, "kind": "cobblemon", "modifier": "catch_rate_percent", "percent": 10},
    {"pieces": 4, "kind": "battle", "effects": [{"op": "weather", "side": "both", "id": "raindance"}]},
    {"pieces": 4, "kind": "tower", "modifier": "raid_points_percent", "percent": 10}
  ]
}
```

Limits: at most 16 bonuses per set, `pieces` 1..4, battle effects validated by the same `TowerBattleFx.validate` the
operator seam uses, percents 1..100 (damage ops 1..300), attribute amounts finite and within +-100.

## Testing

* Unit: parse/validate (good and every kind of bad), resolver (2/4 tiers, mixed sets, duplicates, none), the merge of
  armor effects with queued ones, vendor/Raid-Points arithmetic.
* A pure-Java check that every one of the 16 items has its model, icon, both layer textures and lang key.
* Live (`validation/smoke/armor_set_test.py`): put a set on a bot with `item replace`, assert the attribute
  modifiers appear at 2 and 4 pieces and vanish on removal, that a tower battle then carries the set's `towerFx`, and
  that a datapack set with a bad bonus is skipped without taking the others down.

## Deferred

Crafting/repair recipes beyond the diamond repair ingredient; set bonuses in the spectator HUD; per-piece (rather than
per-tier) bonuses; real art; a trinket/curio slot for accessories.
