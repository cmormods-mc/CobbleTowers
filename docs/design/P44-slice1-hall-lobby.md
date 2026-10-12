# P44 slice 1: Hall, briefings and the lobby (web)

Status: draft (2026-10-12). Part of `P44-webgui-ui.md`. Payloads: `TowerHallState`, `PlayState`. Actions: `TowerHallAction`, `PlayAction`, `RentalDraftAction OPEN`.

## Behaviour (mirrors the native screens; no server change except SUPPORTED)
- **Views** (page-local, one `view` value plus a back stack): `hall` (tower tiles, Daily and Weekly tiles, section tabs), `tower` and `trial` briefings, `lobby`.
- **Hall tabs**: Tower Hall, Trials, Social, Progress. Social has "Party & invites" (-> action `lobby`). Cards whose screen is not built yet (Club lodge, Spectate, Run codes, Season, Cosmetics, Contracts, Hall of Fame, Last run report, Mastery, Collection, Codex, Echoes) are shown disabled with a "Soon" chip, so the layout is final and nothing is a dead click. Each is enabled in the slice that adds its screen and payload.
- **Tower briefing**: name, floors per cycle, milestones, ascension note; button "Select tower & open lobby" -> `tower <id>`; enabled when `runStatus` is empty, no countdown, lobby role is none or host.
- **Trial briefing**: title and rules; "Prepare trial lobby" -> `trial <kind> <revision>`; same enabled rule.
- **Footer**: "Lobby" (or "Return to run" when `canResume`, action `resume`), "Refresh". One request at a time: buttons disable until the next state or 8 s, then "No response" notice.
- **Lobby**: tower list (select), host's invite box, Ascension depth stepper, mode picker (host), Confirm mode, Draft your team, Ready/Unready, Accept/Decline (invited), Start with countdown, End team/Leave, team list with status. "Choose party" is hidden until slice 4 routes RegistrationState.
- **Opening rules**: `TowerHallState` opens the Hall. `PlayState` with `open` opens the lobby view; unflagged updates only refresh the slice. A `TowerHallState` carries a `play` that refreshes the lobby slice too. Messages (`play.message`) become toasts.
- `SUPPORTED` gains `TowerHallState` and `PlayState` in the same change.

## Page design
- Same 1280x720 stage and kit. Tower tiles: region banner painted with CSS gradients from the mod's palettes (tideforge, rootvale, duskvale, neutral) plus a stepped stone-tower silhouette; no new image files. Hover raises and brackets the tile.
- Reducer: `hall`, `play`, `view`, `stack`, `tab`, `busy` (request in flight, with timestamp). Pure and unit-tested; views are pure view-models (`lobbyView(play)` -> which controls show and enabled) so the role/countdown rules are tested without DOM.
- Performance: no timers except the lobby countdown display (1 s, only while a countdown shows) and the 8 s request timeout (one timer while busy).

## Tests
- Node: reducer (open rules, stack, busy clears on state), `lobbyView` for roles 0-3, rental readiness, countdown; tower briefing enabled rule.
- Playwright shots from fixtures: hall, tower briefing, trial briefing, lobby as host (rental, ready), as invitee, as member.
- Live: `/tower menu` on testserver-webgui, then the real lobby; two real clients needed for the invite path (owner has one; a bot covers the second).

## Open
- Sound on click: slice 5.
