# P53: A rarity rolled for every rental card

## Decided (owner, 2026-10-24)

- Each card in a rental pack gets its own rolled rarity (its **tier**), instead of the fixed rarity its species' set carries. Any species can
  come up at any tier. The card's look, its tooltip, and the AscensionLib profile the Pokemon is lent with all follow the tier.
- The "at most 2 legendary or mythic" team cap counts the **tier** on the card, the thing the player sees.

## Design

- **Species draw is unchanged.** `RentalDraw` still picks species by the set's own rarity (its class): three common, one uncommon and one
  rare-or-better per pack, an epic-or-better somewhere, an occasional God Pack, no species twice. That keeps the strength balance the
  sets were tuned for (`P33`), and keeps the Daily Trial draft a pure function of the seed.
- **Tiers are drawn beside it, with the same shape.** Each pack deals the tiers a pack always had (common x3, uncommon, then rare / epic /
  legendary / mythic for the top slot with the same odds; a God Pack of epic-or-better with at most two top tiers), the guarantee of an
  epic-or-better tier in the draft is kept, and then the tiers are shuffled onto the cards separately from the species. So a pack still
  looks like a pack, and which species wears which tier is random.
- **Where the tier is used:** `Pack.tiers()` (parallel to `cards()`), `RentalDraft.pick` (the cap), `Team.tiers()` (for placement),
  `RentalDraftPayload.Card.rarity` and `.ascension`, `AscensionLibGrants.profileRentals` (the library profile). **Where the class is still
  used:** which species are offered, and the CobblemonCards rewards (`CardRewardPolicy` / `RentalCards`), so the reward economy does not move.
- No persistence: a draft is in memory and re-derived from its seed, so the tiers are too.

## Not in this phase

- Tuning. Sets were tuned per class; a legendary species at common tier (or the reverse) is new. Watch `/tower report` rental telemetry
  before changing odds.
- Showing the species class on the card.

## Test plan

- A pack has the tier shape (3 common, 1 uncommon, 1 rare-or-better), a draft has an epic-or-better tier, a God Pack has epic-or-better
  tiers with at most two top, over many seeds.
- The same seed gives the same species and the same tiers.
- Tiers and species are not tied: across many seeds a legendary-class species is dealt more than one tier, and a tier is dealt to more
  than one class.
- The cap refuses a third top-tier card whatever its species.
- The payload carries the tier, and the lent Pokemon are profiled at it.

## As built

- `RentalDraw.Pack` has `tiers()` (parallel to `cards()`); `dealTiers` rolls them with their own contexts (`T...`), so the species draw is
  bit-for-bit what it was and existing trial seeds still offer the same species. Tiers are then shuffled onto the cards (`ts<pack>`).
  `Offer.hasEpicTierOrBetter()` is the tier twin of `hasEpicOrBetter()`.
- `RentalDraft.pick` counts tiers for the cap (`teamTiers()`); `Team` carries `tiers()`; `RentalPartyService` passes them to
  `AscensionLibGrants.profileRentals`, so a rental is lent with the AscensionLib profile its card showed.
- `RentalDraftPayload.Card.rarity` and `.ascension` come from the tier. The web UI needed no change: it already draws `card.rarity` and
  its cap check (`isBig`) reads the same field the server counts.
- The chat draft text (`RentalDraftService.describe`) shows the tier too.
- Not changed: which species are offered, and `CardRewardPolicy` / `RentalCards` (they read the species' own rarity).
- Tests: `RentalDraftTest` (tier shape, guarantees, independence from the species, the cap by tier, a mismatched tier list refused) and
  the payload test (the card shows its tier).
- Unverified: how it plays. A legendary species at common tier, and the reverse, are new; read `/tower report` before touching odds.
