# P43: CobbleTowers on the server only

Status: draft (2026-10-12). Decided by the owner: a player's client carries **no CobbleTowers jar**; the only client-side mods are Cobblemon, WebGUI and Rinku. Polymer (latest for 1.21.1) fills the registry gap. The same goal holds for CobbleRaids, AscensionLib and AscensionDungeons (separate repos, same audit).

## Why a client needs the jar today
1. **Registries.** 48 armor items, 12 armor materials, the season trim templates and `tower_key` are registered in `BuiltInRegistries`. Fabric registry sync disconnects a client that lacks them.
2. **Screens and HUD.** 40 classes under `src/client` (screens, K overlay, HUD, armor tooltips, keybind).
3. **Assets.** Item models, armor textures, the font, `lang/en_us.json`.

Not a blocker: payload types (Fabric does not sync them; a client that did not announce the channel is simply skipped by `canSend`), datapack registries (dimensions, dimension types, trim patterns, structures: vanilla clients accept them), and `Component.translatable` (zero uses in `src/main`).

## Design
**Items (Polymer).** Each registered item becomes a Polymer item (`SimplePolymerItem` / `PolymerItem`) that tells vanilla clients "I am `<vanilla item>` with `item_model` = `cobbletowers:<id>`". Armor pieces appear as the matching leather piece (dyed per set so they look right even without the pack); the key as a vanilla item. The server keeps its own id, so loot tables, drops, the vendor and run code are untouched.
**Armor worn layer.** Needs a client resource pack (not a mod). Polymer's resource-pack builder generates it from our `assets/`; it is delivered as a server resource pack. Without the pack the player sees dyed leather.
**Tooltips.** `ArmorTooltips` (client) moves to server-side lore/`item_name` set in the Polymer `getPolymerItemStack` hook, so it works for every client.
**Screens.** The WebGUI bridge (branch `webgui-bridge`) is the screen layer. The 36 payloads/screens it does not yet route (Hall, Briefing, Run, Mastery, registration chooser, spectator, features) must be bridged before the client jar can go. Until then the native fallback is lost for jar-less players, so the order matters.
**HUD and K overlay.** A WebGUI HUD page that listens only to `RunEffects` (the existing trap in the handoff). The K key: WebGUI pages cannot bind keys through our mod, so the run view opens by a command, a lectern/NPC, or an item use. Decision needed.
**Font.** The pixel font is served by the web page (already inlined in the mockup assets), not by `ui.json`.

## Steps
1. Add Polymer core + resource-pack-lib as `include`d dependencies (or `modImplementation` and require it on the server). Verify it builds on the existing Loom 1.13.6.
2. Polymer-ize `TowerKeys` (1 item) first: prove a vanilla client with only Cobblemon joins and sees it.
3. Polymer-ize `ArmorSetItems`, `SeasonTrimItems` and the armor materials; move tooltips server-side.
4. Resource pack generation and delivery.
5. Bridge the remaining screens and the HUD page.
6. Keep `src/client` working while a client jar is still optional, then gate it behind a switch so it can be dropped.
7. Join test with a stock client (Cobblemon + WebGUI + Rinku only): inventory, armor worn, vendor, run, every screen.

## Open decisions
- K key replacement.
- Resource pack hosting for the real server.
- Whether existing inventories need migration (item ids do not change, so likely none).
- CobbleRaids, AscensionLib, AscensionDungeons: audited separately; each repeats this list.

## Risks
- Polymer API for 1.21.1 and `ArmorMaterial` holders: armor stat and trim behavior must stay server-authoritative.
- Smithing/trim templates sent to vanilla clients render with the base item's look.
- Rinku's machine-wide Chromium cache means testing on the owner's machine needs their own client closed.
