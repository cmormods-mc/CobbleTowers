# P40: rental team scaling and random upgrades

Owner request (2026-10-08): the Rental Draft puts every player's Pokemon at level 50, so they must grow with the run, and when AscensionLib gives a rental its rarity and affixes, the five upgrades its level has earned should be assigned to its affixes at random.

## What was built

* **Random upgrades (AscensionLib).** A Pokemon at level 50 has reached the milestones 10, 20, 30, 40 and 50: five pending upgrade credits. AscensionLib gains `ProfileService.autoUpgrade` and the contract method `AscensionGrants.upgradeRandomly(Pokemon)`: it awards any reached milestones, then spends every pending credit on a random slot still below rank V, one rank per credit. The pick is seeded from the Pokemon's id and its spent credits, so a repeat after a crash chooses the same slot and the store's operation ids make it a replay. It costs nothing and is not a player craft, so the rental craft lock does not apply. (AscensionLib is edited in place, not a git repo; backup `L:\claude-cobbleraids-work\ascensionlib-backup\src-2026-10-08-before-auto-upgrade`.)
* **CobbleTowers.** `AscensionLibGrants.profileRentals` now calls it after granting the profile; with an AscensionLib that lacks the method it logs once and rentals keep rank I affixes.
* **Growth.** `RentalScaling`: a rental is level 50 on floors 1 to 3 and gains 10 levels every three floors (60 on 4 to 6, 70 on 7 to 9, 80 on 10), capped at 100 however deep an ascending tower goes. Each step is a milestone, so each rental earns one new upgrade at once. Hooked at `NEXT_FLOOR_CONFIRMED`; a no-op for players with no rentals.
* **Why levels and upgrades together.** Enemy levels follow the party's mean level plus the floor, so levels alone move both sides. The upgrades are the part that moves the rental's strength relative to the enemy.
* Operator probe: `/cobbletowers play rentals <player> scale <floor>`.

## Verified

`RentalScalingTest` (the steps, the cap, every step a milestone). Live, `rental_ascension_test.py` 7/7: the lent team has profiles with 0 credits pending (affixes spread over ranks, for example one at IV and one at III from five upgrades), floor 4 makes every rental level 60 and one affix gains a rank, the craft lock still refuses players.

## Open

The step size (10 levels per 3 floors) and the cap are untuned guesses; both are constants in `RentalScaling`. Deploy the rebuilt AscensionLib jar with this CobbleTowers build.
