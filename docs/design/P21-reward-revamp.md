# P21: a Cobblemon reward catalog

Written before the code it describes, as the TDS gate requires. **The item lists and numbers below are
proposals for review**: they live in four JSON files, so changing any of them later is an edit, not a build.

## What the user asked for (2026-10-03)

"Completely revamp the loot reward tables with Cobblemon-related items and rewards." Decisions:

| Question | Answer |
|---|---|
| Item families | **Growth** (EXP candies, vitamins, PP Up) and **Utility** (healing, balls, apricorns, berries), plus **CobbleCards booster packs**, **Raid Points** from CobbleRaids, **custom armor sets with full-set bonuses**, and **high-tier vanilla items**; "more to be determined". |
| Structure | A shared core plus **a table per region**: Neutral, Tideforge, Rootvale, Duskvale. |
| Milestones (F5 boss, F10 champion) | A **signature drop and a bigger roll**, with a **guaranteed XL candy and a chase item**. Chase placeholders (one JSON edit to change): a region-matched booster pack at F5, the God Pack Ticket at F10. |
| Raid Points | Add a small public API to CobbleRaids now. |
| Generosity | Modest. |

**Custom armor sets are not in this phase.** They are a new feature in their own right (items, textures,
equipment-slot detection, a set-bonus system), not a reward-table entry. The tables are built so a set piece is
just another item id once it exists. Recorded in the come-back-later list.

## What the pipeline does today, and what is wrong with it

`RewardValuation` turns each ledger entry (an opponent defeated, a boss, a cleared floor) into **one weighted pick**
from that kind's tier, an amount rolled between min and max and grown 8 percent per floor, then split evenly
across the team. Three things this phase fixes:

1. **There is nothing special at a milestone.** F5 and F10 bank the run's rewards but pay exactly what any floor
   pays. A *guaranteed* drop needs a different shape from a weighted pick.
2. **A one-item reward always goes to the first player.** `evenSplit` hands the remainder to the first
   participants in order, so a team that earns a single Revive gives it to the host every time. With the
   catalog made of single items that becomes a real unfairness, so the remainder now **rotates** with the floor
   and the grant's position.
3. **A reward whose item is not registered is dropped for good.** `RewardDelivery.give` skips and logs it, but
   the pending reward has already been drained, so a typo in a table (or a mod that is not installed) silently
   costs players their drop. Nothing checks the catalog at startup. A startup check now reports every item
   a table names that does not resolve, separating a **typo in a loaded namespace** (an error) from an
   **optional mod that is not installed** (a note).

## The two currencies that are not items

`cobbletowers:cobble_dollar` already credits a wallet instead of giving a stack. This phase adds the same for
**`cobbleraids:raid_points`**, credited through the new `CobbleRaidsPoints.award` API. Both ids are reserved, so
a table names them like any item and delivery does the right thing.

CobbleRaids pays 25 to 100 Raid Points for a raid by rarity tier (starter 25, powerhouse 50, legendary 75,
mythical 100). Tower amounts are sized against that: a single floor's boss is worth a fraction of a raid, the F5
milestone about one starter raid, the F10 champion about one legendary raid.

## The CobbleRaids change

CobbleTowers builds against a CobbleRaids line that targets Cobblemon 1.7.3; the repository's `HEAD` moved to
Cobblemon 1.8.1 and refuses to load here. So the API is added on a **branch cut from 0.8.105**, the version this
project has been verified against, in a separate worktree so the main checkout is untouched:
`cobbletowers/raid-points-api`, version `0.8.105-raid-points-api`, one new public class
(`com.cobbleraids.api.points.CobbleRaidsPoints`: `balance` and `award`, no spend). Its own build-time API
boundary check passes (12 public types). It is **not merged or pushed**; folding it into the main line, whose
Cobblemon version has since moved, is the user's call.

## The milestone shape

A reward table gains an optional `milestones` section, keyed `boss` and `champion`:

```json
"milestones": {
  "boss": {
    "guaranteed": [ { "item": "cobblemon:exp_candy_xl", "amount": 1 },
                    { "item": "cobblemon-cards:booster_pack_water", "amount": 1 } ],
    "bonus_rolls": 2,
    "bonus_pool": [ { "item": "...", "min_amount": 1, "max_amount": 2, "weight": 100 } ]
  }
}
```

- **Guaranteed** items go to **every participant in full**, not split: "a guaranteed XL candy" is one per player.
  They are not scaled by floor depth or by reward modifiers, so the promise is exact.
- **Bonus rolls** are extra weighted picks from `bonus_pool`, valued and split exactly like any other grant. This
  is "the bigger roll".
- A new ledger kind, `MILESTONE_CLEARED`, is appended when a milestone floor is cleared, naming the milestone. It
  banks with everything else, so cashing out and forfeiting treat it the way they treat the floor's other
  entries: a lost run forfeits it, a banked one pays it.

## The catalog (proposed)

Every id below exists in the pinned Cobblemon 1.7.3 or in `cobblemon-cards` 1.0.4; they were read from the jars'
own language files, not guessed.

**Shared core** (every table starts from this and adds its own flavour):

| Tier | What | Roughly |
|---|---|---|
| `opponent_defeated` | berries (Oran, Sitrus, Lum, Pecha, Cheri...), apricorns, Potion and Super Potion, Poke Ball, Great Ball, status cures, XS EXP candy, CobbleDollars | frequent, small |
| `floor_cleared` | S and M EXP candy, Hyper Potion, Revive, Ultra Ball, a vitamin or PP Up (rare), emerald, CobbleDollars | steady |
| `boss_defeated` | M and L EXP candy, Revive, an evolution item, a booster pack (rare), Raid Points, diamond | the good rolls |

**Neutral**: general. Poke, Great and Ultra balls; Potion line; generic and generation booster packs.

**Tideforge** (Water, Steel, Electric; Momentum): Dive, Net and Quick balls; Water Stone, Deep Sea Scale and
Tooth, Metal Coat; Mystic Water and Magnet; water, steel and electric packs. F5 guaranteed: XL candy and a water
pack. F10: XL candy and the God Pack Ticket, bonus pool leaning to Water Stone, Deep Sea Scale, Mystic Water.

**Rootvale** (Grass, Bug, Poison; Growth): the berry and apricorn heavy table; Leaf Stone, Luminous Moss, Nest
and Friend balls; the highest vitamin weight of the three, because its doctrine is growth; grass, bug and poison
packs. Same milestone shape.

**Duskvale** (Dark, Ghost, Psychic; Disruption): Dusk and Quick balls; Dusk Stone, Black Glasses, Spell Tag, Reaper
Cloth, Scroll of Darkness; dark, ghost and psychic packs. Same milestone shape.

**High-tier vanilla** sits only in the rare end: diamond and emerald in the ordinary tiers, and in the milestone
bonus pools netherite scrap, golden apples, experience bottles and, at F10 only, an enchanted golden apple at low
weight. Nothing here is a Master Ball or Rare Candy: the user did not ask for them, and Rare Candy bypasses the
whole levelling game.

**Modest**: a clean solo run of ten floors should yield on the order of thirty small utility items, a handful of
growth items, two or three packs and a few hundred Raid Points across the run, with the milestones as the big
moments. The test below measures what a table actually pays rather than trusting that estimate.

## Where it fits

- `RewardTableDefinition` parses `milestones`; `RewardKind` is unchanged (milestone drops are not weighted tiers).
- `LedgerEntry.Kind.MILESTONE_CLEARED`; `TowerEncounters` appends it where it already appends `FLOOR_CLEARED`.
- `RewardValuation` values it, with a resolver from milestone id to `BOSS` or `CHAMPION`; `Grant` gains a
  `perPlayer` flag.
- `RewardBankService` queues a `perPlayer` grant in full for each participant; the rest are split with a
  rotating remainder.
- `RewardDelivery` credits `cobbleraids:raid_points` through the API.
- `RewardCatalogCheck` runs at server start.
- `validation/validate_definitions.py` learns the new section and reports unknown-looking ids.

## Tests

- `RewardMilestoneTest` (15): guaranteed items flagged per player; a guaranteed amount is exact (not grown by
  depth, not scaled by a +200 percent reward modifier); bonus rolls add that many ordinary grants; a milestone
  kind the table does not list, a table with no milestone section and an unknown milestone id all pay nothing
  extra; pricing is deterministic; the ledger entry survives a save and load; parsing (a good section, bonus rolls
  with no pool, an empty milestone, an amount below one, an unknown kind); and the remainder rotation (a one-item
  grant moves between teammates, rotation never changes the total, an even split is unaffected).
- `RewardCatalogCheckTest` (5): items that exist raise nothing; a missing item in a loaded namespace is an
  ERROR, in an absent mod a NOTE; the reserved currencies are never flagged; milestone items are checked and the
  problem says where.
- `ShippedRewardTablesTest` (5), reading the real JSON from the classpath: every table parses and rolls all
  three tiers; every table promises both milestones an XL candy and a bigger roll, with the agreed chase items;
  each region pays its own signature item and neutral does not; every table pays CobbleDollars and Raid Points;
  and a simulated clean solo ten-floor run is **measured** and bounded.
- `validate_definitions.py` now checks the milestone section and, for the Cobblemon and CobbleCards namespaces
  (ids read from the jars into `validation/known_items.json`), that every id exists. Checked by hand against a
  deliberately wrong id: `exp_candy_xxl`, `booster_pack_wter` and a malformed milestone are all reported.
- Live `milestone_reward_test.py` (10/10): a two-player run reaches floor 5, a milestone is earned, and at the
  intermission **each** player receives the guaranteed XL candy and each player's CobbleRaids Raid Points rose by
  the guaranteed amount; the banking line shows the grants flagged per player; the CobbleCards pack is skipped
  with that mod absent; startup reports no typo and notes the missing optional mod; no CobbleTowers exception.
- Re-run clean on the new CobbleRaids jar: `reward_test` 19/19 (its inventory check now reads the granted items
  from the banking line), `lobby_test` 12/12, `intermission_test` 9/9, `exit_test` 12/12, `vendor_test` 11/11,
  `teammate_vendor_test` 9/9, `milestone_floor_test` 7/7, `participant_test` 26/26, `party_registration_test`
  13/13, `battle_rules_test` 8/8, `draft_test` 25/25, `floor_encounter_test` 17/17.

## Outcome

Built as designed. **What a clean solo run pays, measured** (16 runs over the four towers and four seeds): 47 to
78 item units (mostly small balls, berries and potions), 100 to 145 Raid Points, 3 to 11 candy grants and 2 to 5
CobbleCards grants. The test bounds were set from those numbers, so a later table edit that changes the economy
by much will fail a test rather than ship unnoticed.

Things worth recording:

- **CobbleRaids `HEAD` cannot be used.** It was bumped to require Cobblemon 1.8.1; this project and the smoke rig
  are on 1.7.3, and the server refuses to boot with it. The Raid Points API is therefore on a branch cut from
  0.8.105 in a separate worktree (`L:/claude-cobbleraids-work/raids-p21`, branch `cobbletowers/raid-points-api`,
  commit `f0fc305`), published to Maven local as `0.8.105-raid-points-api`, and `gradle.properties` points at it.
  Your main CobbleRaids checkout was not touched. Nothing there is pushed or merged.
- The rig's CobbleRaids jar was replaced with that build (the old one is in `L:/claude-cobbleraids-work/raids-backup`).
- **Not built**: custom armor sets with full-set bonuses (a feature, not a table entry; see the come-back-later
  list), and any chase item beyond the agreed placeholders.
- **Not proven live**: the CobbleCards items actually landing in an inventory (the mod is not installed in this
  rig; the skip path is proven instead); the reward reveal screen presenting `cobbleraids:raid_points` nicely (it
  shows the raw id, as it does for CobbleDollars today).
