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
