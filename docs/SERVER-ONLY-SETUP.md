# Running CobbleTowers with no client mod (P43)

Players join with **Cobblemon, WebGUI and Rinku** only. CobbleTowers stays on the server.

## Build
- `./gradlew build` makes `CobbleTowers-<version>.jar`: no `com.cobbletowers.client` classes, `"environment": "server"`, no client entrypoint. It is the only build; the old native screens are archived in `archive/native-ui`.
- Smoke rigs and bots need `-Pdev` (see `docs/design/...`); the server-only jar is a release artifact. Checked 2026-10-14: the server-only jar boots on the Fabric server, loads, and reaches "Done".

## Server `mods/`
Cobblemon, CobbleRaids (>= `0.8.162-admin-helper`), AscensionLib (deploy the rebuilt jar with it), Polymer (the bundled jar, `polymer-bundled-0.9.19+1.21.1`), WebGUI (server side), and the `-server` CobbleTowers jar. Remove any older CobbleTowers jar.

`config/cobbletowers-webgui.json` is written on first start (web pages on) when WebGUI is installed; without that file the web pages stay off. Its contents: `{"enabled": true, "url": "webgui:/cobbletowers/index.html", "mode": "prefer", "heartbeat_seconds": 8}`. The jar installs its pages into `config/webgui/web/cobbletowers/` at start.

## Resource pack (armor worn layers, item models, tooltips' icons)
Polymer builds `polymer/resource_pack.zip` at start. Players need it for the worn armor and custom item models; without it they see dyed leather and plain items (everything still works).

Host it with Polymer's own auto-host, which serves the pack **over the game port** (no extra port), in `config/polymer/auto-host.json`:

```json
{ "enabled": true, "required": false, "type": "polymer:automatic",
  "settings": { "forced_address": "" } }
```

- `forced_address`: set it to `host:port` as players reach the server when the server sits behind a proxy or tunnel that rewrites the address (leave empty otherwise).
- `required: true` kicks a player who declines the pack. Leave it false unless the pack is essential.
- If the game port cannot carry HTTP (some DDoS-protection proxies), use `"type": "polymer:standalone"` with `{"port": <free port>, "external_address": "https://..."}` and open that port, or host `polymer/resource_pack.zip` yourself and set `resource-pack=` and `resource-pack-sha1=` in `server.properties`.
- Checked 2026-10-14: with auto-host enabled the server regenerates the pack (about 1 MB) and answers HTTP on the game port. Not yet checked with a real client downloading it.

### Optional client mod: Armor Model API
Four armor sets (Tideforge, Challenger, Rootvale, Duskvale) have real 3D models (ornaments, limbs that swing). A player sees them only with the Fabric mod **Armor Model API** (Modrinth `armor-model-api`, 1.1.0 for 1.21.1) in their own `mods/`. The server needs nothing extra: the models ship inside the Polymer resource pack, and the server tags each piece so the mod finds them. Without the mod (or without the pack) the player sees the flat worn layers instead; nothing breaks. Worth telling players on the server's mod list or Discord. `validation/validate_armor_geo.py` checks the models at build time (a broken one renders nothing on the client).

## Mastery badge (P47)
Names carry a small tower icon and the player's mastery level (the tower with the highest level). The icons are a font in the resource pack, so a player who declines the pack sees an empty box where the icon is; the level number still reads. Switch the badge off for everyone with `"mastery_badge": false` in `config/cobbletowers-cosmetics.json`; a player can hide their own with `/tower badge off`.

## What a player sees
Everything is a WebGUI page opened by the server (`/tower`, the intermission after a floor, rewards, vendor, rental draft, mastery, scouting, spectator HUD). There is no key binding: open the menu with `/tower` (the tower key item is a run-entry cost, not a menu opener).

## Verified
The owner played it through on 2026-10-14 (every page they opened, a run to floor 2, the vendor, rooms, the rental draft) and reported it all looks and works well. A long multi-floor run and a second player are still to do.
