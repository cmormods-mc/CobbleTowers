# P51: Frontier Desk, slice 1 (the shell and the Hall)

Status: **Slice 1 built (2026-10-17), live check on the rig pending.** As built: `TowerHallStatePayload.extras` (`menu/HallExtras`, `PartyReader.cards`), `web/src/desk/` (`DeskHall.js`, `standings.js`, `desk.css`), residents in `tools/gen-data.mjs`, the look setting in Settings ("Play page look"), Outfit (OFL) bundled as the page font. Desk class names are prefixed (`dcard`, `dbtn`, ...) because the old UI's global CSS owns `.card`, `.btn`, `.top`, `.col` and more. Follows P44 (web UI) and P50 (route map). Written first, per the design gate. The owner chose this look from nine concepts (bright, clean, team-first); mockups were reviewed in chat. The map in P50 is built later on top of this shell.

## Why

The native-look web pages were built to match the old Minecraft screens. The owner is not sure about that look and picked "Frontier Desk": a light, clean UI where the team and what waits in each tower are always on screen, using the game's own Pokemon sprites and landscapes.

## Scope of slice 1

* A **Frontier shell**: top bar (logo, pills for Play / Trials / Progress / Social / Codex, currency slot, close), light surface, cards, type chips, mastery pills. Plain CSS, scoped under `.fd`, no change to the native look.
* The **Play** page (the Hall): the player's team, the Continue banner, tower rows with landscape, residents and mastery, Daily and Weekly buttons, and the Climb button.
* **Opt-in.** A setting in the page's Settings screen switches between "Native" and "Frontier Desk". Native stays the default until the owner flips it. Other pills and every other screen still show the native look for now.
* The tower and trial briefings and the lobby are unchanged in this slice; picking a tower on the Play page opens the existing tower briefing, as today.

## Server change (additive)

`TowerHallStatePayload` gains one nested field, `extras`, with:

| Field | Meaning |
|---|---|
| `runTower` | Id of the tower the player's current run is in, or empty |
| `party` | Up to 6 entries: name, species path, level, fainted, types |
| `standings` | Per tower: mastery rank name, mastery level, levels to the next rank (-1 at the top), deepest Ascension reached |

* Built in `TowerHallService.open` from existing readers: `PartyStorage` (Cobblemon party, species and types the way `PartyReader` already reads them), `TowerMasteryStore` / `MasteryPerks.rankOf` / `MasteryPerks.nextRankAt` (the same numbers the Progress tab shows), `TowerRuns.forPlayer`.
* The old five-argument constructor stays, so the native client screens compile and behave as before (they ignore the new field).
* The page reads it through the existing payload-to-JSON path (`PayloadJson`); no new payload, no new action, no change to `WebGuiBridge.SUPPORTED`.

## Web changes

* `web/src/desk/`: `desk.css`, `DeskHall.js`, and small pure helpers (`standings.js`, `eligibility.js` is **not** in slice 1).
* Residents (three foes shown on each tower row) are drawn from the tower's encounter pool **at build time** by `tools/gen-data.mjs`, so no payload field is needed. Species not in the sprite sheet show a plain ball.
* Types come from the server (the team) or the generated tables (residents). A party Pokemon whose sprite is not in the sheet shows a ball in the same box.
* The setting is stored in the browser with the other page settings (`settings.js`), applied as a class on `<html>`; `App.js` chooses `DeskHall` or the native Hall.

## Not in slice 1

* The eligibility line (Level Cap 50, Monotype). It needs the server's rule checks; showing a guess would be wrong. Slice 1 shows the team, levels and fainted state only.
* Modifier pick, lobby and progress in the new look, the map, Mastery, Collection and the other still-missing screens.
* Replacing the native default.

## Tests

* Java: a pure test for building the extras (rank text, next-rank gap, empty party, run tower); `PayloadJsonTest` and `CodexPayloadSizeTest` keep passing; the full unit suite.
* Web: node tests for the standings helper and the residents table; screenshots from fixtures for the desk Hall (`tools/shoot.mjs` gets a `desk_hall` shot and a four-tower and a no-run variant).
* Live (test rig): open the Hall with a real party and a real run; check the log is clean.

## Open questions

1. Best floor per tower is not stored anywhere today. Slice 1 shows rank and Ascension instead. A stored best floor is a separate small change if wanted.
2. Currency in the top bar is not in the Hall payload. Slice 1 leaves the slot empty rather than adding a wallet read.

## Slice 2: the between-floors screen (2026-10-17)

Same opt-in setting (Settings > General > Look). Built: `desk/DeskIntermission.js`, `desk/choices.js` (pure: offers as "choices", the floor ladder, health tones), `IntermissionStatePayload.extras` (tower name, floor within the cycle, cycle length, milestone floors, party with health; built in `IntermissionService.extrasOf` through the pure `IntermissionExtras`, failure-safe, hand-written stream codec because the record now has seven fields). `TowerHallStatePayload.Mon` gained `hp` (percent), so the Hall can show health too.

* Layout: team health on the left, the offers in the middle, the selected offer's full lines and the party's readiness on the right, a floor ladder across the top, Confirm / Vendor / Ready / Cash out along the bottom. Votes, pending state, countdown and actions are the native screen's logic unchanged.
* Rooms (Forge, Shrine, ...) show the room as the chip and the option as the headline.
* **Not built:** a scouting preview of the next floors. Scouting today is text about the floor you are on, sent when the floor starts (`FloorScouting.sendReveal`); previewing foes ahead is new server behaviour and belongs with P50 (the route map), where Far Sight's reach becomes route depth.
* The "choices" view model is what the route map will replace: a node is a choice with a room and a modifier.

## Slice 3: the lobby (2026-10-17)

Same opt-in setting. Built: `desk/DeskLobby.js`, `desk/seats.js` (pure: seats from `lobbyView`, the line under a name, the mode name, why Start is waiting), `desk/DeskToast.js`. The tower list, the party's seats and the settings are three cards; every button sends what the native lobby sends (`PlayAction` SELECT_TOWER / SET_PLAYLIST / SET_ASCENSION / CONFIRM_MODE / READY / UNREADY / ACCEPT / DECLINE / OPEN_CHOOSER / INVITE / START / LEAVE, and `RentalDraftAction` OPEN), and `lobbyView` still decides what is on offer.

* **Teams are shown for you only.** The payload carries other players' names and states, not their Pokemon, so a teammate's seat shows a state and no team. Showing teammates' teams is a server change (and a decision about what players may see of each other); not made here.
* Only the first open seat has the invite box.
* The Frontier Desk has its own toast (`fd-toast`) on the screens it draws; the oak toast stays on native screens.
* **Checks that are not unit tests:** `web/tools/desk-check.mjs` drives the dev build in Chromium (33 checks: native stays the default, the Hall, the Look setting in both directions, the intermission, the lobby, missing `extras` from an older server, no console errors, five window sizes). A pixel comparison of every native screenshot against the start of the session shows 36 of 37 identical; the one change is the Settings page, which gained the Look button.

## Slice 4: progress (2026-10-17)

Same opt-in setting; the Progress pill now opens the Desk page. Built: `desk/DeskProgress.js`, `desk/progressView.js` (pure: ready counts, the lane rows with the Hall's rank names, percent, season timer, tile words), `desk/icons.js` (the mod's icon sheet and item sheet at any size). No server change: it reads `TrackState` and sends `TrackAction` exactly as the native Tracks page does (`refresh`, `claim`, `claim_all`), and `src/tracks.js` still decides what a tile is and whether Claim is allowed.

* Layout: a banner with how many rewards are ready, a row per mastery tower and one for the season (click to switch lane), the "More" buttons for the other Progress cards (season and rewards, cosmetics, contracts, Hall of Fame, run report, mastery and boards), and a large card with the lane's progress bar, a grid of tiles (item pictures, claimed ticks, a "Ready" badge) and a detail strip with Claim.
* The Records card grid is not rebuilt; the More buttons cover the same pages.
* Tiles are real buttons (Tab, Enter); the page opens scrolled to the tile you are on.
* desk-check.mjs now has 41 checks (loading state, lanes, claims, an older server with no extras, no season).
