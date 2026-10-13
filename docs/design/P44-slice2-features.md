# P44 slice 2: feature screens and the K replacement (web)

Status: draft (2026-10-12). Re-scoped from the P44 order: the "Run" view is one section of the generic feature menu (`TowerFeatureState`), so building that screen turns on most of the Hall's "Coming soon" cards at once. Payloads: `TowerFeatureState` (server -> page), `TowerFeatureRequest` (page -> server).

## Behaviour (mirrors `TowerFeatureScreen`)
- **Sections** the server serves: `cosmetics, season, hall, echoes, club, club-top, club-alltime, watch, codes, contracts, report, modifiers, relics, run`. The page opens one by sending `open`; the reply carries the title, entries (title, lines, actions) and page-level actions.
- **List**: entries and page actions as cards, paged by the rows that fit; picking an entry shows its lines (wrapped, paged) and its first action as a button; "Overview" returns to the list, "Refresh" re-opens.
- **Actions**: action id `page` switches section; `lobby` sends the Hall's lobby request; one with a confirmation or an input hint goes through a review step (Confirm / Cancel, text box when asked); otherwise it is sent at once.
- **Staleness**: the page numbers its requests; a reply for another request or section is ignored. A reply with request id 0 (the server opened the screen by command) is accepted. No reply in 8 s shows "Response delayed. Refresh before another action." and blocks further actions until a refresh.
- **Hall wiring**: Codex tab -> `modifiers`, Echoes -> `echoes`; Social cards -> `club`, `watch`, `codes`; Progress cards -> `season`, `cosmetics`, `contracts`, `hall`, `report`. Mastery, Collection and Settings stay "Soon".
- **K replacement**: the web HUD page is already click-through and opens by itself while a run is live, so there is nothing to toggle. The interactive run view is the `run` section: `/tower run` (new, any player) opens it, and the Codex section keeps its "This run" link. An item use is not built (needs an item; owner can decide later).
- **Server changes**: `TowerFeatureService` sends and tests reachability through `ClientUi` instead of `ServerPlayNetworking`; `PageActions` decodes `TowerFeatureRequest`; `SUPPORTED` gains `TowerFeatureState`; `/tower run` command.

## Page
- A `feature` slice in the store; `view: 'feature'` inside the hall shell (rail shows no tab selected, title is the section's title, as native). Selection, review, typed text and page numbers are component state, reset by a new reply.
- Pure view-model `features.js` (`featureView(state, ui)`) decides the list slice, paging, which buttons are enabled; unit-tested.

## Tests
- Node: request id matching, review flow rules, paging, stale reply ignored, delay state.
- Playwright shots from fixtures: list, entry detail, review with input, run section.
- Java: `PageActionsTest` for the new request; `PayloadJsonTest` fixture for `TowerFeatureState`.
- Live: club/contracts/report on the test server with the owner's account.
