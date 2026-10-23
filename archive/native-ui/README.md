# Native in-game UI (archived)

The web UI (WebGUI) is the only UI of this mod. The in-game Minecraft screens that came before it are kept here for reference and
are **not built, not shipped and not tested**. Nothing outside this folder refers to them.

- `src/client/` is the old `src/client` source set: `com.cobbletowers.client`, 39 classes (Hall, intermission and modifier cards,
  `ModifierScene`, rental draft cards, HUD, reward reveal, the screenshot/remote dev tooling).
- `validation/` holds the scripts that drove a real Minecraft client against a test server (`client_launch.py` and everything that
  imports it), the Hall/frame-time runs and the AscensionLib UI tests.

Still in `src/main` and still used by the server: the textures, fonts and models under `assets/cobbletowers` (the Polymer resource
pack, armor art and the name-badge font `tower_icons`), and the payload classes in `com.cobbletowers.network`.

To bring it back: `git mv archive/native-ui/src/client src/client`, restore `splitEnvironmentSourceSets()` and the
`mods { register('cobbletowers') { sourceSet main; sourceSet client } }` block in `build.gradle` (see the history before the commit that
added this folder), and give `fabric.mod.json` its `"client"` entrypoint `com.cobbletowers.client.CobbleTowersClient` again.
