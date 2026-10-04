# P29: Custom (coded) modifiers

Status: built; unit tests, `ci_local.sh` and `custom_modifier_test.py` (8/8) pass. Written alongside the code.

## What

Ordinary modifiers are numbers and flags (`ModifierEffects`). P29 adds `ModifierType.CUSTOM`: a modifier whose
`effect.custom` names a coded behavior (`definition.CustomBehavior`). It may also set ordinary effect fields as a price
or bonus. An unknown behavior is refused at load (Java) and by `validate_definitions.py`.

Decisions (user, 2026-10-04): rarity is just very low weights (5-6 against 40-120, so a run sees one roughly a quarter
of the time); run-warping boons and gambles; hooks into battle, economy, run structure and party.

## How it stays typed

`modifier.CustomEffects` (pure) reduces a run's held behaviors to typed parameters; consumers read a number or flag and
never ask which modifier is held (`DraftService.customs(run)`).

| Modifier | Behavior | Catch | Hook |
|---|---|---|---|
| Glass Cannon | party starts every battle +2 Atk, +2 Sp. Atk | party starts at 60% HP | P23 battle ops, merged with armor ops in both battle adapters |
| Field Hospital | whole party fully healed on arrival at every intermission | boss has 130% health | `IntermissionService.onArrival` |
| Fortune's Wheel | each reward grant spins: 30% x4, else x0.5 (about x1.55 on average) | variance | `RewardValuation` (deterministic from run seed, floor, ledger position) |
| Black Market | vendor prices halved | rewards x0.75 | `ArmorBonusEffects.vendorPrice`, on top of armor discounts |

## Not done

* A wipe-forgiving "Second Wind": a wipe goes straight to a terminal state, so forgiving one means a new transition
  and a floor restart. Left as its own piece of work.
* Live-proven only: Black Market's price, granting, and floor 2 opening with all four held. Heal and Wheel are unit tests;
  Glass Cannon's effect inside a real battle is not observed.
* Numbers are first guesses.
