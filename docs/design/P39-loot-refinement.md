# P39: loot refinement

Written after the owner's report from floor 6 (2026-10-08): two rewards read as AIR, the drops were mostly filler, and a floor paid little.

## What was wrong

* **"AIR".** CobbleDollars and Raid Points are currencies with no registered item, so the reward reveal looked their id up, got air, and drew and named it that. The chat line listed the raw id. Fixed: they are named and iconed (the coin; the owner's crystal for Raid Points) and listed with the amount actually paid (armor and mastery bonuses included).
* **Filler.** About 80% of an opponent's pool was berries, potions, status heals, apricorns and basic balls, at every depth.
* **Too little.** A ledger entry rolled exactly one item: an opponent, the floor clear, the boss.

## What changed (owner answers: tiers by floor, less filler, more per floor, regular floors first)

* **Floor bands.** A reward entry may name `min_floor` and `max_floor` (the floor within the tower's own cycle, so an ascending tower repeats the bands). An entry outside its band is not rolled; if the bands leave a pool empty the whole pool is used, so a table can never pay nothing.
* **Rolls.** A table may name `rolls` per tier (1 to 5). The first roll draws exactly what it drew before; extra rolls use their own ordinals, so a crash or replay gives the same items.
* **Content (all four tables, revision +2).**
  * Early floors (1 to 4): berries, Potions, Poke Balls and cheap status heals; XS candy.
  * Middle (3 to 7): Super Potions, Great Balls and the region's balls, Sitrus, small and medium candy.
  * Late (6 to 10): Hyper Potions, Ultra Balls, medium and large candy; the floor clear adds XL-adjacent items, Max Revive, PP Up, vitamins and stones; the boss adds XL candy, Max Revive, PP Up, Rare Candy (floor 7 on) and CobbleDollars.
  * Berries, apricorns and status heals have lower weights and fall away by floor 5 to 7. CobbleDollars weigh more in the opponent and floor pools.
  * `rolls`: the floor clear and the boss each roll **twice**; an opponent rolls once. A floor with two opponents pays about six items instead of four.
* Milestone (F5/F10) rewards and the armor drop weights are unchanged.

## Verified

Unit tests: floor bands, the first roll unchanged by asking for more, replay determinism, bad bands and roll counts refused. `reward_test.py` 19/19 and `milestone_reward_test.py` 10/10 on the rig. Every new item id exists in Cobblemon. Screenshot of the reveal for CobbleDollars and Raid Points.

## Open

All amounts, weights and bands are untuned; read `masteryadmin counters` and the owner's own runs, then edit the tables (data only). Items the client does not have draw as a gift icon instead of air.
