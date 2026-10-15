# P47: The mastery badge (a tower icon and a level beside the name)

Status: **P47a built 2026-10-14 (`MasteryBadge`, `Segment.font`, `mastery_badge` config flag, `MasteryBadgeTest`); P47b-c not started.** Owner decisions made 2026-10-14. Replaces the long-standing "mastery-level cosmetics are recorded but shown nowhere" item. Written first, per the design gate.

## Why

A mastery level can carry `cosmetics`; claiming one records `m:<name>` in the season-progress store (`MasteryClaims`) and nothing reads it. The shipped mastery track never sets one. Players have no visible sign of the hours they put into a tower. Season titles already show in chat and the tab list (`ChatTags`, `Cosmetics.decoration`); mastery should show beside them without a second title to manage.

## Decisions (owner, 2026-10-14)

1. **No mastery titles.** Mastery gives no wearable cosmetic. The `m:` cosmetic hook stays for server owners and addons (earn commands, placeholders) but ships empty and is not promised in the UI.
2. **A small tower icon and a number** beside the name, always shown.
3. **The number is the mastery level** (1-30) in the tower the icon shows.
4. **The tower is the one with the highest level**, automatic, no menu. Ties go to the tower the player cleared most recently.
5. **The icon is a resource-pack glyph** (a custom font), one picture per tower. The server-only build already hosts the pack.
6. The season title slot, the club tag and `/tower title` are unchanged.

## What the name looks like

`[icon]12 Champion S1 [TC] Name` : icon, then the level in the rank's colour, then the existing decoration. Rank colours (from `mastery_tracks`): Bronze, Silver, Gold, Platinum, Diamond, Master, Champion, a vanilla chat colour each. A player with no mastery level in any tower has no badge.

Shown in the tab list, chat (the display-name mixin already used by `ChatTags.decorate`) and as a placeholder (`PlaceholderBridge`). Off with the existing `"chat_tags": false`, and with a new `"mastery_badge": false` in the cosmetics config.

## Build

* **Data:** `ChatTags.segmentsOf` already returns coloured pieces. Add a `Segment` kind that carries a font id, so a piece can be `(text, color, font)`; `Cosmetics.Segment` gains an optional `font` (a pure change, existing callers pass none).
* **Pure rule:** `MasteryBadge.of(Map<tower, level>, lastCleared)` returns `(tower, level, rank colour)` or empty. Unit-tested: highest wins, tie goes to the most recent, no levels gives none, level 0 gives none.
* **Reading levels:** `TowerMasteryStore.progressOf` per tower in `sortedTowerIds` (rotating trial towers are not in that list, and trials do not count as cycles). A bounded loop of four to a dozen towers, called when a name is built; add a small per-player cache invalidated by the same events that already call `ChatTags.refresh` (a level change in `MasteryService`).
* **Glyphs:** `assets/cobbletowers/font/tower_icons.json` (a `bitmap` provider, one private-use character per tower, 8x8 PNGs under `textures/font/`). Polymer's `addModAssets` already ships the mod's `assets/` into the pack, so no pack code is needed. A tower an addon adds with no icon uses a generic one; the character is chosen from the tower id's position in a stable sorted list so it does not move when towers are added (kept in a `tower_icons` data file mapping tower id to glyph).
* **Art:** four 8x8 icons (Neutral, Tideforge, Rootvale, Duskvale) plus a generic one, in the pixel style of the Hall. Placeholder art first, as elsewhere.

## Risks

* **Players without the pack** see a missing-glyph box where the icon should be (the font does not exist for them). The pack is optional (`required: false` in the setup notes). Mitigations in order of cost: say so in `docs/SERVER-ONLY-SETUP.md`; a per-player `/tower badge off`; or send the tab list a plain-text icon to players Polymer reports as lacking the pack (chat is shared, so chat keeps the glyph). Recommend the first two now.
* **Chat width:** the badge adds about three characters. Acceptable; the toggle covers servers that disagree.
* **Cost per name:** a name is built often (every chat line). The cache keeps it to a map lookup.
* **Another chat plugin** that rebuilds the display name will drop the decoration, as it already does for season titles; the placeholder is the route for those.

## Tests

Pure: the badge rule above; a segment with a font renders into a component with that font and keeps the existing title and club segments in order; the glyph map is stable (adding a tower does not change another tower's character). Live (dev jar, `masteryadmin` to set a level): the tab list and a chat line carry the badge; it changes when a higher-level tower overtakes; it disappears with `mastery_badge: false`; a stock client with the pack shows the icons (owner check).

## Not in this phase

Mastery titles, a choice of which tower to show, per-rank art, badges on the nameplate above the head, anything in the Hall beyond showing the same badge on the player's own mastery screen.

## Build order

1. **P47a** `MasteryBadge` pure rule, `Segment.font`, config flag, tests.
2. **P47b** reading levels with the cache and refresh on level change; wire into `ChatTags` and the placeholder.
3. **P47c** glyph font, placeholder icons, `tower_icons` map, `/tower badge off`; live check and the pack note.
