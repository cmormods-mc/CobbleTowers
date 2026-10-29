# Rig recipe: live-testing and photographing the tower

The rig is a scratch Fabric 1.21.1 server (Cobblemon, CobbleRaids `0.8.191-world-boss`, Polymer, fabric-language-kotlin) with RCON on 25575 (password `walktest`), `-Dcobbletowers.ignoreWindows=true`
in `start.sh`, and a console fed from `console.txt` (`tail -n0 -F console.txt | start.sh`). It lives outside the repo (the cloud container is temporary), so rebuild it from this page.

1. Build the jar: `./gradlew build -x test -Pcobbleraids_version=0.8.191-world-boss --offline` (without those two flags the CobbleRaids artifact is missing).
2. `RIG=<rig> bash validation/rig/restart_rig.sh` stops the server, wipes the tower dimension and run state, installs the jar from `build/libs`, boots, and waits for `Done (`.
3. Walk test (about 10 min, 137 checks): `NODE_PATH=<rig>/bots/node_modules python validation/smoke/walk_tower_test.py --attach --server-dir <rig>/srv`. Needs `mineflayer` and `vec3` in that folder.
   Restart the rig between runs (the test reads the first run). Never print `runs show` over RCON (replies over 4096 bytes break the harness).
4. Pictures: `RIG=<rig> python validation/rig/hold_run.py &` starts a two-bot run and parks it at INTERMISSION (a run left unanswered in battle wipes itself), prints `ORIGIN x y z`.
   Start Xvfb (`Xvfb :99 -screen 0 1280x720x24`), launch a real 1.21.1 client as user `ShotCam` with `--quickPlayMultiplayer 127.0.0.1:25565`, click Yes on the resource-pack prompt, then
   `RIG=<rig> OUT=<folder> bash validation/rig/shots.sh shot name x y z yaw pitch seconds`. Set `view-distance=16` in the rig's `server.properties` for exterior shots.
5. Regenerating the building: see the last section of `docs/design/P55-explorable-towers.md` (generator, `apply_layout.py`, converter, `validate_definitions.py`, tests).

Pitfalls met so far: `pkill -f` / `pgrep -f` with a pattern that appears in your own command line kills your shell (write `[x]yz`); bots keep their party between runs, so use fresh names;
the dev-only `runs advance` is absent from the production jar, use `runs winfloor` (repeat it on floors with extra opponents).
