# P38: region rules and three armor sets per tower

Status: design, written before the code (the TDS gate). Owner answers 2026-10-08; the numbers below are my proposals for the owner to review before stage 2.

## Decisions already made

| Question | Answer |
|---|---|
| F10 chase | Keep the God Pack Ticket guaranteed; **armor pieces drop alongside it** (option C: a high-weight bonus-pool entry) |
| Armor scope | **Four towers, three sets each = 12 sets** (48 pieces). The four sets that exist become one of each tower's three |
| How a tower's three differ | **All of tier, role and sub-theme at once** (see the table) |
| Tideforge rule | No held items for the player's Pokemon |
| Rootvale rule | Enemy Pokemon heal a share of the damage they deal: **10% at floor 1 rising to 20% at floor 10** |
| Duskvale rule | Each floor puts an unavoidable status on the **lead** Pokemon, **random per floor** (seeded), to be answered with cure items |
| Raid Points | Unchanged (25 / 75) |

## Stage 1: region rules (needs code, no art)

Each is a ruleset (a new `rulesets/<tower>.json`, pointed at by its tower). A ruleset gains three optional fields, all off by default so `standard` and every
saved run are unchanged:

| Field | Meaning | Mechanism (all in the one Showdown extension, `tower-fx.js`, as P23) |
|---|---|---|
| `player_held_items: false` | The player's side ignores held items for the whole battle (no Leftovers, berries, Choice items, Life Orb). The Pokemon keep the item in their party; nothing is removed | a `suppress_items` operation wrapping `Pokemon.ignoringItem` for the named side only, so it is inert for the other side and for any battle without it |
| `enemy_drain_percent` (start and end) | After an enemy Pokemon deals damage it heals that share of it, capped at what it is missing | a `drain` operation: a rule on the named side, applied where damage is dealt (as the existing damage rules are), bounded and fail-open like them |
| `floor_status` (list of statuses) | At the first battle of each floor, one status from the list, chosen by the run seed and the floor, is put on the lead | the existing `status` operation, with the choice made on the Java side from `seed` and `floorIndex` so a replay gives the same status |

* Java: `RulesetDefinition` parses and validates the fields, `RulesetView` exposes them (the API boundary validator guards the new signatures), and the battle-effects builder adds the operations
  to the battle's `towerFx`. The drain percent is `lerp(10, 20, (floor - 1) / (floorCount - 1))`, rounded, so a tower with another floor count still ends at 20.
* Duskvale statuses: poison, burn, paralysis, sleep, freeze (`psn`, `brn`, `par`, `slp`, `frz`). Applied once per floor, to the lead only; a Pokemon that already has a status keeps it.
  The vendor must sell the matching cures (Antidote, Burn Heal, Paralyze Heal, Awakening, Ice Heal, Full Heal); a Duskvale vendor check is part of the stage's test. Battle items can be used in ordinary
  floor fights; a boss fight under the no-items rule cannot, which is part of the pressure (the owner can relax it).
* Hardcore (no vendor, no items) with Duskvale is the harshest combination and is allowed; it is not special-cased.
* Tests: `validation/showdown/` simulator tests for `suppress_items` (an item-using set loses its effect, the other side keeps it), `drain` (heals the stated share, never over max HP) and the
  deterministic status choice; unit tests for the ruleset parse and the drain curve; a live smoke test per tower that the effects reach the battle (the same "applied N of N" server log line the other effects use).

## Stage 2: twelve armor sets

Pieces are items registered in Java (`ArmorSetItems`, a fixed list), bonuses are data (`armor_sets/*.json`), so 48 items, lang entries, item models and layer textures are added; the
generator `tools/generate_armor_art.py` makes the placeholder art, replaceable later without code. Wearing rules, tooltips and mixing (2 and 4 piece tiers, mixed sets get both 2-piece bonuses and no full-set bonus) are unchanged.

| Tower | Tier I, Utility (new) | Tier II, Offense (the existing set) | Tier III, Defense (new) |
|---|---|---|---|
| Neutral | Recruit's Gear: +6% EXP, vendor -5% | Challenger's Regalia | Paragon's Aegis: -8% damage taken, +1 Defense to the lead |
| Tideforge | Tidewalker's Garb: water breathing, +8% catch rate, +5% Raid Points | Tideforged Plate | Leviathan Mail: -10% damage taken from Water and Electric, rain for 5 turns |
| Rootvale | Sprout Garb: +8% EXP, +1 max health | Rootwoven Mail | Heartwood Bulwark: -10% damage taken from Grass and Fairy, grassy terrain for 5 turns, +3 max health |
| Duskvale | Dusk Wanderer's Cloak: +1 luck, +5% shiny odds | Duskbound Guard | Nightfall Carapace: -10% damage taken from Dark and Ghost, +1 Speed to the lead |

All numbers are deliberately modest and are data; the set names for the new nine are working titles.

**Drops (reward-table entries, no crafting):**
* Tier I pieces: low-weight entries in the tower's `floor_cleared` and `boss_defeated` tiers from the start.
* Tier II pieces: where they are now (unchanged).
* Tier III pieces: a bonus-pool entry on the **F10 champion** only (weight about 40 per piece among the existing bonus entries), next to the guaranteed XL candy, God Pack Ticket and Raid Points. F5 pays no armor piece.

**Tests:** `ArmorSetRegistry` loads all twelve with no malformed file; `validate_definitions.py` and the reward-table checks resolve every new item id; the existing `armor_set_test.py` and `armor_hooks_test.py` run for a tier I and a tier III set; the client screenshot rig checks the item tooltips and a worn set.

## Open before stage 2

* The set names and numbers above (owner review).
* Whether tier III should be the strongest overall (as drafted) or equal in power to tier II and differ only in role.
* Whether the existing four sets staying as tier II is right: they keep their ids, so nothing a player already owns changes.
