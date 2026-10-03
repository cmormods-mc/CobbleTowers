# P25: Armor set tooltips

Status: design, written before the code. Builds on P24 (armor sets).

## The problem

A player who finds `Tideforged Helm` sees only its name and protection. What the set is, which pieces they already wear,
and what the 2- and 4-piece bonuses do is invisible: P24's bonuses are real but undiscoverable. Tooltips are drawn by the
client, and the bonuses are server datapack data the client never receives.

## Decisions

* **Sync, don't duplicate.** The server sends each client a display-ready description of every armor set
  (`ArmorSetsPayload`) when they join and after every datapack reload. The client builds tooltips from that, so a
  datapack that retunes or adds a set changes the tooltip with no client update, and the tooltip can never disagree with
  what the server applies.
* **Descriptions are made once, on the server, from the real bonus records** (`SetBonusDescriber`), not re-derived from a
  second table on the client. One place turns "+20% Water damage dealt" into words.
* **The tooltip shows what the viewer is wearing.** Active tiers are lit, locked tiers are dimmed, and the header counts
  worn pieces (`2/4`). That is computed on the client from its own armor slots, so it updates the moment they re-equip.
* **Compact by default, detailed on Shift.** Default: set title and worn count, then each tier with its bonuses.
  Holding Shift adds the four-piece checklist. A full set's tooltip must not run off the screen.
* **Set colour is data.** An optional `color` (`"#rrggbb"`) in the set JSON tints the title, glyphs and active lines; the
  four shipped sets get their own. Default is a neutral grey.

## The look

```
Tideforged Helm                      <- the item's own name line, untouched
                                     <- spacer
◈ Tideforged Plate  2/4              <- set colour, bold title; count dimmed
✔ 2 pieces                           <- tier lit (green tick, set-colour text)
    + Breath lasts longer
    + Better water movement
    + 10% better catch rate
◇ ★ Full Set                         <- tier locked (dim); "Full Set" when every piece is needed
    + Rain for the whole battle
    + Your Water moves deal +20% damage
    + 10% more Raid Points
Hold Shift for pieces                <- dim italic; with Shift:
  ◆ Tideforged Helm                  <- worn: set colour
  ◇ Tideforged Chestplate            <- missing: dark grey
```

Only glyphs the stock Minecraft font draws: `◈ ◆ ◇ ✔ ★ ▸ +`.

## Pieces

* `ArmorSetDefinition` gains `color`; `SetBonusDescriber` (pure) turns each `SetBonus` into player-facing lines.
* `ArmorSetView` / `ArmorSetsPayload` (S2C): per set the id, name, colour, the four piece ids by slot, and tiers
  (`pieces`, lines). Built by `ArmorSetViews.of(definition)`.
* `ArmorSetSync` (server): sends on `JOIN` and on `END_DATA_PACK_RELOAD`.
* `ArmorTooltipBuilder` (common, pure over `Component`): `build(view, worn, expanded)`.
* `ArmorTooltips` + `ClientArmorSets` (client): store the payload, hook `ItemTooltipCallback`, read the viewer's armor.

## Testing

* Unit: the describer for every bonus kind and operation (including awkward numbers), view building and tier grouping,
  the builder's text, colours and lit/locked state for 0, 2 and 4 worn pieces, collapsed and expanded, and the payload
  codec round trip.
* Live: a bot joins and the server log records the sync; a datapack reload resends it (`armor_set_test.py` extended).
  How it looks on screen is judged on a real client (this rig is headless); a rendered mock-up of the text is produced
  by a unit test to review the wording.

## Deferred

Localisation (English only, like the rest of the mod); a bonus summary in the equipment screen or HUD; tooltips for
non-armor items that belong to a set; an animated shimmer for a completed set.
