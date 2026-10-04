# P33b: The CobblemonCards crossover

Status: **built** (design written before the code; see "As built" at the end). Follows P33 (the Rental Draft). Decided with the owner, 2026-10-04: build both the
**visual** crossover (the rental cards look like CobblemonCards cards when that mod is installed) and the **real-card rewards**
(finishing a rental run grants real CobblemonCards cards).

## What CobblemonCards is, as far as this needs (checked against its 1.0.4 jar and its public source)

* Licence **CC0-1.0**, mod id `cobblemon-cards`, needs Cobblemon >= 1.6.0 and Fabric Loader >= 0.18.6 (our rig runs 0.19.5).
* A card is the item `cobblemon-cards:card` with the data component `cobblemon-cards:card_data`:
  `{pokemon_id, is_shiny, rarity, stat, stat_value, grade, background?, effect?}`. `rarity` uses the same six names as the draft
  (common, uncommon, rare, epic, legendary, mythic). `stat` is one of 26 names; the 18 `<type>_spawn` stats boost encounters of that type.
* A card does something only inside a **binder** (equipped, or in hand): its `stat_value` is summed, scaled by a server-wide multiplier,
  and applied. The mod's own packs roll values from fixed ranges per rarity (common 0.005-0.01 up to mythic 0.20-0.25; shiny +0.03).
* A booster pack has the same shape as our draft pack (3 common, 1 uncommon, 1 rare-or-better; a God Pack is 5 shiny epic/legendary), and
  its own screen draws the cards with holographic shaders. A card is drawn by a built-in item renderer that needs a loaded world.
* Our reward tables already name its items (`booster_pack*`, `god_pack_ticket`); they are skipped when it is not installed.

## Two features, one idea: the card is a function of the set

Every rental set has one **collectible card**, computed from the set and nothing else, identically on the server and the client:

| Field | Rule |
|---|---|
| `pokemon_id` | the set's species |
| `rarity` | the set's rarity, capped at the playlist's `max_rarity` (default epic) |
| `stat` | `<primary type>_spawn` (the Pokemon's own type), so a Garchomp card boosts Dragon encounters |
| `stat_value` | the middle of the mod's own range for that rarity (+0.03 if shiny), so the mod's balance is untouched |
| `is_shiny` | true when the card was drawn from a God Pack (the mod's own God Pack is all shiny) |
| `background` | by primary type from the mod's own list; none for common |
| `effect` | none for common and uncommon; glint, sparkle, aurora, galaxy by rarity from rare up |
| `grade` | 0 |

A card the player saw in a pack is therefore the card they can win. Pure code (`rental/RentalCards`), tested without either mod.

## Feature 2: the look (client, optional)

`CardFace` already isolates how a card is drawn. `CobblemonCardsFace` builds the card item from the spec (the same `CardStacks` that
rewards use), draws it with the mod's own renderer at card size (frame, art, holographic shimmer), and puts our information (name,
level, ability, item, four moves) around it. It is chosen only when the mod is loaded on the client, through one class that is loaded
only then; otherwise the text face is used exactly as today. The server never knows. The payload carries each card's look
(`shiny`, `background`, `effect`) so the client does not need the sets.

## Feature 3: the rewards (server, optional)

* **When:** a rental run that reaches `COMPLETED` (a full cycle). Not on a cash-out, a wipe or an abandon. A trial run counts only if it
  was the scored attempt, so a trial cannot be farmed for cards.
* **What:** one real card per Pokemon the player ran with (up to six), built from the run's own team, as above.
* **How much:** a player can earn them from at most `runs_per_day` (default 3) completed rental runs per real day.
* **Where the team comes from:** the party journal already names the rentals before anything moves; it now also names each one's set
  and whether it came from a God Pack, so completion works even if the player is offline.
* **Delivery:** the existing pending-reward queue, which stores plain data only. A pending reward gains an optional `components` string
  (the card's data as text); delivery parses it into a real item. A server without the mod grants nothing and logs it once.
* **Idempotent:** a commit key on the run, and the existing sweep for a run that completed in a crash window.
* **Configuration:** the playlist file: `"card_rewards": {"enabled": true, "max_rarity": "epic", "runs_per_day": 3}`.
* **Display:** the reward reveal screen and chat line name each card ("Garchomp card (epic)") instead of an item id.

## Not in this phase

Grading, binders, cabinet or any of the mod's other systems; shiny cards from anything but a God Pack; a card for a Standard run;
changing the mod's own tables; a dependency on the mod at compile time (everything uses the item and component ids as data).

## Test plan

Unit: the card spec rules (every set, every rarity, the cap, shiny, stability), the reward policy (when, how many, the daily cap,
the trial rule), the pending reward round trip with and without `components`, the payload codec, the journal round trip with
tokens. Live (with CobblemonCards installed on a test server): a rental run is completed by an operator and real card items with
the right data arrive; a server without the mod grants nothing and does not fail; a card is delivered after a reconnect.
Client (with the mod on a real client): screenshots of the card face next to the text face, and the end-to-end run.

## As built

**The card is a function of the set.** `rental/RentalCards` (pure) makes the card of a set: the species, the set's rarity capped at the
playlist's `max_rarity`, a `<primary type>_spawn` stat, the middle of the mod's own stat range for that rarity (+0.03 if shiny), a
background from the mod's list by type (uncommon and up, or any shiny), a holographic effect by rarity (rare and up), shiny when the
card came from a God Pack. Each of the 44 sets gained a `types` list (checked against Showdown by `rental_sets_check.js`).
`CardStacks` turns a spec into a real item through the game's own item parser, from the item id and plain field names, so **nothing
needs CobblemonCards at compile time**; the item parser answers "empty" when the mod is not installed. The field names were checked
by `/give`-ing exactly this data on a real server.

**Rewards.** A playlist's `card_rewards` block (`enabled`, `max_rarity`, `runs_per_day`; Rental ships `true, epic, 3`).
`CardRewardPolicy` (pure) decides, `CardRewardService` acts when a run reaches `COMPLETED` (called beside the reward bank, before the
player's own Pokemon are restored) and again from the server-start sweep for a run that completed in a crash window. The team comes
from the party journal, which now records each rental's set and whether it was a God Pack. A pending reward carries an optional
`components` text and a `label`; `RewardDelivery` turns it into a real stack when it is handed over, and the reward reveal and chat line
name the card ("Garchomp card (epic)"). `TowerCardRewardStore` counts runs per day. Without the mod: nothing is granted, the log says
so once per run, and nothing fails. Live (`smoke/card_reward_test.py`, 10/10): no mod, none granted; with the mod, six real cards per
completed run with the right species, an epic cap on legendary sets, `_spawn` stats and the mod's own values; three runs earn, the fourth
is refused (`DAILY_LIMIT`); an abandoned run earns none.

**The look.** The pack payload carries each card's look (shiny, rarity, background, effect). `CobblemonCardFace` (client) builds the
card item from it and draws it with the mod's own renderer, with our name, ability, item and moves beneath; `RentalPackScreen` uses it
when `cobblemon-cards` is loaded and offers a "Cards: Collection / Plain" toggle, otherwise the text face. Seen on a real client
against a real server (`client_e2e.py --cards`, 12/12): real frames and Pokemon art on the table and the team summary, and the six
granted cards in the inventory drawn by the mod.

**Found on the way.**
* CobblemonCards needs the **Accessories** mod (and owo-lib) at runtime though its `fabric.mod.json` does not say so: without them the
  server stops at boot. Tests install the three jars and remove them afterwards.
* It needs Fabric Loader 0.18.6 or later; the client launcher now takes the rig's Loader.
* The client launcher now asks Gradle for the client classpath through an init script instead of reading a Loom side file that is not
  always there.

## Left open

Cards are granted only on `COMPLETED`; whether a cash-out after most floors should earn some is a balance call for real play. The daily
allowance is by run, not by card. Grading and binders are untouched. The legendary and mythic sets earn an epic card under the default
cap; raising the cap is one line in `rental.json`. How the card animates (tilt, shimmer) is the mod's renderer in a GUI slot: static
at our scale; a spinning card in the reveal would need the mod's showcase screen.
