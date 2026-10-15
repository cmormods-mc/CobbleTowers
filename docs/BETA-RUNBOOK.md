# Beta runbook

For the server owner and moderators during the player beta. Version at writing: `0.28.x`. Every command here is for an operator (permission 2) unless it starts with `/tower`.

## What should be on the server

`mods/`: Cobblemon, CobbleRaids (`0.8.162-admin-helper` or newer, pinned: never rename it), AscensionLib (the rebuilt jar, deployed with CobbleTowers), Polymer (`polymer-bundled`), WebGUI, and one CobbleTowers jar (the `-server` jar if players have no client mod). Setup: `docs/SERVER-ONLY-SETUP.md`.

A healthy start logs, in this order: `CobbleTowers <version> loaded`, `Installed the web UI <stamp>`, `Loaded N tower definition(s) ... all references resolved`, `Loaded N trial recipe(s)`, `Loaded N tower run(s) from disk` (only if runs were saved), `Done`. Anything else with ERROR in it is worth reading before players join.

## Backups

**What matters**
| What | Where | Why |
|---|---|---|
| Player progress | `world/data/cobbletowers_*.dat` (21 files: runs, wallets, mastery, trials, leaderboards, seasons, clubs, echoes, contracts, counters and the rest) | everything a player has earned |
| Settings | `config/cobbletowers-*.json`, `config/polymer/auto-host.json` | how this server is set up |
| Pokemon and parties | `world/cobblemonplayerdata/` and Cobblemon's own data | rentals and registered parties move Pokemon, so this matters too |
| The tower arenas | `world/dimensions/cobbletowers/tower/` | temporary run cells; a run that was live during a backup is recovered from its last checkpoint. Can be left out of routine backups |

**When:** before every deploy and every datapack change, and once a day during the beta. Stop the server or run `save-all flush` first, then copy the folders above. Keep the last three days.

## Deploying a new build, and going back

1. Back up (above). 2. Stop the server. 3. Replace the CobbleTowers jar (and the AscensionLib jar if it changed); never leave two CobbleTowers jars in `mods/`. 4. Start and read the log lines above.

To go back: stop the server, put the old jar back, and restore the `world/data/cobbletowers_*.dat` files from the backup taken before the deploy. A newer build reads older saves; an older build may not understand a newer save, so restore the data with the old jar.

## A run is stuck

1. `/cobbletowers runs list` finds it (state and floor). `/cobbletowers runs show <run>` prints the detail.
2. A silent player is dropped after 10 minutes and a floor that goes 30 minutes without progress is closed by the server's watchdog. Often waiting is enough.
3. Ask the player to use `/tower leave`.
4. If that fails: `/cobbletowers runs abandon <run>`. It ends the run (or a run waiting for recovery) and keeps what was already banked. Nothing else is paid.
5. A run in `RECOVERY_REQUIRED` after a crash recovers by itself at the next start; if it does not, abandon it as above.
6. If a floor's arena looks wrong: `/cobbletowers cells list`, `cells show <cell>`, `cells verify <cell>`; a bad one can be taken out of service with `cells quarantine <cell>` and returned with `cells clear <cell>`.
7. Note the run id and what the player did, and look for it in `logs/cobbletowers-reports.log` (below).

## Reading what players report

Players type `/tower report <what happened>`. Each report is appended to `logs/cobbletowers-reports.log` with the time, the player, the version, their text and their run (tower, floor, state, trial, players, seed) plus the last twelve things the run did. `/cobbletowers reportadmin list [count]` prints the latest in game. A player can send one every 30 seconds. After a run ends the player is reminded to report problems; `"report_prompt": false` in `config/cobbletowers-beta.json` switches the reminder off.

## Reading how the beta is going

`/cobbletowers masteryadmin counters` (the reply is cut near 4 KB; read it after a few days):
* `run_start.*` shows how many runs began, split into plain, trial_daily, trial_weekly, playlist and rental.
* `run_end.*` shows how they ended: completed, cashed_out, failed, abandoned. Many `abandoned` means players are quitting or hitting a problem.
* `floor_entered.fNN` against `run_lost_at.fNN` shows the floors that lose players. A floor with many losses and few entries is too hard; one with no losses may be too easy.
* `trial.daily|weekly.finished|completed` shows how the rotating trials are going.
* The older counters (claims, contracts, risk bonus, modifier pick rates) are described in `docs/design/tuning-with-real-data.md`.

Do not change balance numbers in the first days: read first.

## Wiping or fixing one player

| Need | Command |
|---|---|
| Give a trial attempt back | `/cobbletowers trialadmin reset <player>` |
| Reset mastery in one tower | `/cobbletowers masteryadmin reset <player> <tower>` |
| Clear every leaderboard (a test run polluted them) | `/cobbletowers masteryadmin clearboards` |
| Give a modifier to a run | `/cobbletowers runs grant <run> <modifier>` |
| Hand out a cosmetic | `/cobbletowers cosmeticsadmin grant <player> <id>` |
| See what today's trials are | `/cobbletowers trialadmin roll <yyyy-mm-dd>` |

Wallets, season points, clubs and the rest have no per-player reset. To wipe everything the mod stores for all players, stop the server and delete the `world/data/cobbletowers_*.dat` files (a deliberate reset of the whole beta); take a backup first.

## A bad datapack or recipe

After `/reload` run `/cobbletowers definitions`: it lists every problem it found ("every reference resolves" means none). A malformed file is skipped with its name in the log, never loaded half-way. If today's rotating trial cannot be built, the server logs it and uses the hand-written pool entry instead. To go back, restore the old datapack files and `/reload`.

## The resource pack

Polymer builds `polymer/resource_pack.zip` at start and, with `auto-host.json` enabled, serves it over the game port. Players who decline it see empty boxes for the name icons and plain armor, and everything still works. Behind a proxy that rewrites the address, set `forced_address`.

## Quick reference

`/cobbletowers runs list | show <run> | abandon <run>` ... `cells list | verify <cell>` ... `definitions` ... `diagnostics` ... `reportadmin list` ... `masteryadmin counters | reset | clearboards` ... `trialadmin roll | reset | day` ... `cosmeticsadmin grant`.
