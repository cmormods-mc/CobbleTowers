# WebGUI bridge: tower screens as a web page

**Status:** implemented, unit-tested, and exercised live: on a real server with protocol-level clients, and with a real Fabric client running WebGUI's Chromium (see "Live test").

## What it is

CobbleTowers' screens are native client screens, drawn by the CobbleTowers client code. This lets the WebGUI mod
(`mc-webgui/webgui`) show the same screens as a web page instead. Nothing about the game changes: every screen already has a
**state payload** the server builds and an **action payload** the server handles. Only the transport changes.

**What this does not remove.** CobbleTowers registers items (armor sets, the tower key), so Fabric's registry sync still requires
the CobbleTowers jar on the client. Every real player therefore has the native screens too, and the useful setting is
`mode: prefer` (players who also install WebGUI get the page; everyone else keeps the native screens). `fallback` only matters for a
client that has no native channel for a screen: a future server-only build of the mod, or a screen the native client does not have.

```
                      native client                     WebGUI client
server builds   ->  ServerPlayNetworking.send     ->   emitToPage("tower", {kind, data})
state payload       (IntermissionStatePayload)          page draws it

page click      ->  ClientPlayNetworking.send      ->  postToGame({channel:"cobbletowers", kind, ...})
                    (IntermissionActionPayload)         PageActions.decode -> the same action payload
                                                        TowerNetworking.handleAction -> the same handler
```

## Code

| Class | Job |
|---|---|
| `network.ClientUi` | `canShow(player, type)` / `send(player, payload)`: the one place that picks native vs web. Replaces the scattered `ServerPlayNetworking.canSend` + `send` pairs. With the bridge off it is exactly those two calls. |
| `webgui.WebGuiBridge` | Decides (`takesOver`), delivers (`deliver`), receives (`onPage`). All server-thread. |
| `webgui.PageSession` | Pure rules: when to open a page, when to just update it, what to replay to a page that loaded late. Unit-tested as a timeline. |
| `webgui.PayloadJson` | Reflective record to JSON. The payload records are the contract, so a new field reaches the page with no second list to update. |
| `webgui.PageActions` | JSON to action payload. Refuses anything unreadable, bounds what it reads; never throws. |
| `webgui.WebGuiLink` | The only class that touches WebGUI, by reflection on `land.webgui.api.WebviewApi` (4 public methods). No compile dependency, so it cannot fail to link on a server without WebGUI. |
| `webgui.WebGuiConfig` | `config/cobbletowers-webgui.json`. Off by default. |
| `network.TowerNetworking.handleAction` | The server half of every screen action, whatever the transport. |

## Config

`config/cobbletowers-webgui.json`:

```json
{ "enabled": true, "url": "webgui:/cobbletowers/index.html", "mode": "fallback", "heartbeat_seconds": 8 }
```

* `mode: prefer`: web for everyone who has WebGUI, native for the rest. `mode: fallback` (the default, so enabling the file changes nothing by itself): web only for a player with no native channel for that screen.
* `url` must not carry a `#fragment`; the page chooses its own screen from the payload it receives.
* Page files go in `config/webgui/web/cobbletowers/`; WebGUI serves them to the player over their own connection.

## Protocol

**Server to page**: WebGUI event `tower`, detail `{ "kind": "<Record name without Payload>", "data": { ...record fields... } }`.
Kinds supported today: `IntermissionState`, `RentalDraft`, `RewardReveal`, `ScoutingReveal`, `VendorCatalog`, `TowerHallState`,
`PlayState`, `MasteryScreen`, `TrackState`, `RunEffects` (this one is for the HUD page). Field names and shapes are the payload
records'. An example of each is written to `build/webgui-fixtures/` by `PayloadJsonTest` on every test run.

**Page to server**: `webgui.postToGame({ channel: "cobbletowers", kind: ..., ... })`.

| `kind` | Meaning |
|---|---|
| `hello` (`hud`: bool) | The page has loaded. The server replies with what it missed while loading. Send this first. |
| `ping` (`hud`) | Heartbeat, every 1-2 s. WebGUI gives the server no "page closed" event, so a page is open while it keeps pinging. |
| `bye` (`hud`) | Optional, on `pagehide`: closed at once, without waiting for the heartbeat to lapse. |
| `IntermissionAction` (`action`, `argument`) | `PICK_CARD n`, `READY`, `UNREADY`, `CASH_OUT`, `STAY`, `VENDOR`, `REFRESH` |
| `RentalDraftAction` (`action`, `a`, `b`) | `OPEN`, `PICK a b`, `RESTART`, `READY`, `LOBBY` |
| `PlayAction` (`action`, `argument`) | `SELECT_TOWER`, `SET_PLAYLIST`, `SET_ASCENSION`, `START`, ... (the lobby buttons) |
| `TowerHallAction` (`action`, `argument`, `revision`) | Hall buttons |
| `TrackAction` (`action`, `lane`, `tower`, `number`) | `claim`, `claim_all`, `refresh` |
| `MasteryRequest` (`tower`, `tab`) | Another mastery view |
| `VendorPurchase` (`serviceId`, `targetPlayerId`) | Buy a service for a teammate (or yourself) |

Every action is decoded into the record the native screen sends and handled by the same code, which re-checks it exactly as it
does for a modified client. A page is untrusted input.

## What a payload does when it arrives

Mirrors the native client's handler for the same payload:

* `RewardReveal`, `ScoutingReveal`: **replace** whatever is showing.
* `IntermissionState`, `PlayState`: update an open page; open one only if `open` is true.
* `RentalDraft`, `TowerHallState`, `VendorCatalog`, `MasteryScreen`: update an open page, or open one.
* `TrackState`: update only. `RunEffects`: HUD page, opened when a run has a floor.

Opening a page does not send the state with it (the page is not loaded yet). The page says `hello` when it is, and the server
sends the latest of each kind produced since the page was opened. Older state is never replayed.

## Not covered (yet)

`RegistrationState` (the party chooser), `SpectatorPanel`, `TowerFeatureState` and `ArmorSets` still need CobbleTowers' own
client. A WebGUI-only player gets the chat fallback for those, as a player with no client always has. Page-side, the
Registration screen is the next one worth building.

## Limits and trade-offs

* WebGUI refuses an emitted payload over 32 768 characters; the bridge drops (and logs) a larger one. The rental draft with
  three packs is well under; a long mastery leaderboard is the one to watch.
* Page-to-server messages are rate limited by WebGUI (`pageEventsPerSecond`, default 20 per player). The heartbeat uses well under one.
* "Open" is inferred from the heartbeat, so a payload that arrives in the second or two after a player presses Esc is sent to a
  page that is gone. Payloads that must be seen (rewards, scouting) use `REPLACE`, which always reopens.
* WebGUI 1.8.1 requires Fabric Loader 0.18; CobbleTowers declares 0.17.2. A server running both needs the newer loader.
* The reflective link has no compile-time check against WebGUI. `WebGuiBridge.install` fails safe (logs and stays native) if the
  API is not what it expects. The four method signatures were checked against WebGUI 1.8.1.

## Live test

Run on a real server (Fabric 1.21.1, Cobblemon 1.8.1, CobbleRaids 0.8.164, this patch, WebGUI 1.8.1) with a throwaway world, using protocol-level
clients and then a real client (Fabric Loader 0.19.5, WebGUI with its bundled Rinku/Chromium). The rig is outside this repository.

* **Protocol-level clients** (a WebGUI-only client, a CobbleTowers-only client, a client with both, a client with neither): 39 checks in `fallback`
  mode, 6 in `prefer` mode, 13 on the intermission path (a run driven to its intermission with the `runs advance` dev commands). They cover:
  the bridge linking to WebGUI; the server delivering the page files over the player's connection, byte-identical to disk; `/tower menu` opening a
  GUI-mode page with a signed token and nothing sent before the page says `hello`; the hall, lobby, rental draft (three packs, validation refusals),
  intermission (vote, ready, vendor), and the reward reveal; garbage from a page being ignored without a kick; heartbeat expiry and `bye`;
  and the mode rules (a native-only client is never sent a page; in `prefer` a client with both gets the page).
* **Real client**, 20 checks, with real mouse clicks on the page: the page loads in WebGUI's browser from the server's own files and says `hello`;
  the hall, lobby, rental draft, intermission and reward reveal arrive; a click on *Keep these two* sends `PICK`; a click on a modifier card is
  answered "Vote recorded."; *Ready* is answered by the server's own rules; *Cash out* produces the Tower Rewards reveal; *Continue* sends
  `REFRESH`; Esc closes the page, the page sends `bye`, and the next screen reopens it at once; no JavaScript console errors in the whole session.

Nothing in the Java changed as a result of the live test: every failure it produced was in the test script or in the page. Things it taught us:

* WebGUI sends each emitted event to **every** open page of the player. When the server opens the HUD page for the run effects, it receives all the
  other kinds too, so a page must ignore everything but `RunEffects` when it is the HUD (`#hud`).
* A reward reveal is a `REPLACE`: the page is re-opened and the reveal arrives with its `hello`. A page that is still loading must not be sent anything.
* The mod's own rules reach the page unchanged ("Vote on the modifier before you ready up.", a solo vote followed by a shrine choice). A page must
  not assume a single-step draft.
* The reward reveal's `label` is empty for ordinary items (only currencies are named); a page falls back to the item id.
* Rinku keeps one machine-wide Chromium cache, so a second WebGUI/MCEF client on the same machine cannot start while another holds it.

Not covered: two real players in one team, the party chooser / spectator / feature screens (not bridged), and long idle sessions.

