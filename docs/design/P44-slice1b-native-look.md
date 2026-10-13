# P44 slice 1b: the web Hall looks like the native Hall

Status: built (2026-10-12). Owner: "this menu looks way different than the base screen". Slice 1's first Hall was a new design; the owner wants the native screens' look, so the pages are now a copy of them.

- **Grid:** the page stage is 1280x720 and is laid out in the mod's GUI pixels at scale 3 (427x240), the smallest size a player can have. Every box (`web/src/components/native.js`, `at(x, y, w, h)`) uses the same coordinates as `TowerMenuScreen`, `TowerHallScreen` and `PlayScreen`.
- **Chrome:** header panel, tab rail (Tower Hall, Trials, Codex, Progress, Echoes, Social, Collection, Settings), parchment sheet, dark and burgundy buttons with icons, hover brackets, the oak backdrop with its warm top glow. Nine-slice frames use the mod's textures with its 8 GUI px corner.
- **Art without a client jar:** the mod paints its icons and landscapes in code (`MenuIcons`, `TowerPanorama`). `web/tools/native-art.mjs` redraws them pixel for pixel at build time into an icon sheet and landscape PNGs per region; `test/art.test.js` pins the port.
- **Font:** the mod's Pixelify Sans, with letter spacing to match the native text width. (Slice 0 used Jersey 15 for the other pages; those are redone in the same look as their slices come up.)
- **Content parity:** towers two per page with paging, Daily/Weekly buttons, briefings, Social and Progress cards. Screens not built yet (Codex, Echoes, Collection, Settings, most cards) are shown dimmed with "Coming soon" and do nothing.
- **Lobby:** the `PlayScreen` layout (tower cards, host controls as buttons, mode cycling, team status, Start). "Choose party" waits for slice 4.
- **Messages:** toasts now rise from the bottom, where the native screens print them.
- The bundle stamp now hashes every shipped file, so an art-only change refreshes the installed copy.
