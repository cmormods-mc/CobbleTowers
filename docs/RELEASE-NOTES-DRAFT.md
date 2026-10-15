# Release notes (draft, updated 2026-10-14)

Version `0.27.0-p46-rotating-tower`. Covers the work after commit `9688678`. Items marked **(unverified)** are written but have not been seen in a real client or a full
human run; do not publish them as done until the checklist in `docs/RELEASE-CHECKLIST.md` is ticked.

## For players

**Menus are web pages (P43-P44)**
- With the WebGUI mod on the server, every CobbleTowers screen is a page the server opens: Tower Hall (towers, Daily and Weekly trial briefings, lobby, party chooser),
  the intermission after a floor, rewards, the vendor, the Rental Draft, mastery and boards, scouting, the Progress tracks, club, season, cosmetics, contracts, Hall of
  Fame, run report, codex, echoes, collection, and the spectator panel. They look like the native screens and fill the window at Minecraft's own GUI scale.
- Open the menu with `/tower`. After a floor clears, the intermission opens by itself. Press Esc to close any page.
- The **K run-effects overlay is gone**. **Current Run** is a tab in the Tower Hall (and a button on the intermission): floor, difficulty rating, modifiers, relics and
  set bonuses.
- Reward and track pictures: your own items show their art, common Cobblemon items (candy, balls, potions, berries, apricorns, stones, apples, booster packs) show a
  drawn icon, anything else shows a gift.
- Your client no longer needs a CobbleTowers jar at all if the server runs the server-only build (see below).

**Rotating trials (P46)**
- **The daily and weekly trial are composed, not picked from a list.** Each day rolls a route (a region for every two floors, so a trial can run Tideforge into Duskvale into
  Rootvale), a house rule, an enemy level, up to two mutators, sometimes a starting relic and a room the run leans toward. Everyone gets the same trial that day. The board name is
  generated from the route (for example "Tideforge-Duskvale Gauntlet"). Each region's rules (Tideforge held items, Rootvale drain, Duskvale status) apply on its own floors.
- The **weekly** is the same recipe at ten floors plus a **weekly rule**: no vendor, no Rest rooms, double risk bonus, or a relic room on every second floor.
- About one day in seven (one week in four) is a hand-written featured trial instead.
- Boards, streaks, contracts, the one scored attempt and the reward tables are unchanged; a mixed tower pays the reward table of its last region.
- `/tower trial` and the Hall briefing list the route, starting relic, room lean and weekly rule. **All numbers (level lists, chances, the rule list) are first guesses.**

**Relics, set bonuses and rooms (P45)**
- **38 relics** (26 new), each with tags (sustain, economy, tempo, offense, scouting, boss, risk). Many have battle effects: type-themed damage, drain, resistance, tailwind,
  a burned or slowed foe lead.
- **Tag set bonuses:** holding 2 or 4 relics of one tag switches on a bonus (14 of them). Six relics is still the cap.
- **Four new intermission rooms:** Forge (pay CobbleDollars for a battle relic), Library (a scouting relic), Vault (a gamble: coins or an ambush), Mystic Pool (swap a held
  relic). The old rooms (Rest, Shrine, Gambler, Echo Duel) stay. About half the non-milestone floors have a room.

**Loot and rentals (P39-P40)**
- Fixed: two rewards showing as air (CobbleDollars and Raid Points have no item); they show a coin and the Raid Points crystal.
- Reward tables have floor bands and several rolls: early floors give berries and potions, middle floors Great Balls and small candy, late floors Hyper Potions, Ultra Balls
  and big candy; bosses add XL candy, Max Revive, PP Up and Rare Candy. **All numbers are first guesses.**
- **Rental teams grow:** level 50 on floors 1-3, then +10 levels every 3 floors up to 100. With AscensionLib, each step also gives a Pokemon random upgrades.
- **The rental pool has 150 sets** (up from 44); every type leads 6 to 10 sets.

**Modifier cards (P41)**
- Benefits are green and costs red on every modifier card and run view.

**Menus**
- A **Close** button on every CobbleTowers screen (a corner X, or a labelled Close beside Back in the Hall).
- A new, more readable pixel font (Pixelify Sans, SIL OFL) in every CobbleTowers menu. Settings > General > Pixel font turns it off.
- The Tower Hall header shows its towers whole. Menu icons were redrawn in the mod's own style (replacing the Cobblemon sprites) **(unverified)**.
- Menus draw about three times faster, and were checked under Sodium and Iris with a shader pack.

**Regions and armor (P38)**
- **Tideforge:** your Pokemon's held items do nothing in battle (they are not removed).
- **Rootvale:** enemies heal a share of the damage they deal, 10% on floor 1 rising to 20% on the last floor.
- **Duskvale:** every fight of a floor starts with that floor's status (poison, burn, paralysis, sleep or freeze) on your lead; bring cures.
- **Twelve armor sets, three per tower:** a utility set (drops from ordinary floors and bosses), the existing offense set, and a defense set that drops only from the F10 champion beside the God Pack Ticket.
  The new art is placeholder.
- **Boss fights and disconnects:** rejoining a boss fight inside the CobbleRaids hold window now resumes it instead of putting you in as a spectator.

**Modifiers**
- Every modifier card is painted for what it does (a tough boss, a fragile boss, a veteran enemy, a crowd, no healing, no setup, no switching, no items, a hoard of coins), and the
  risk is shown in colour. Descriptions were rewritten to say plainly what happens.
- **Risk now pays at the end of a tower.** Each modifier you hold adds a bonus to the rolled rewards of your final payout (completing the tower or cashing out):
  minor +3%, moderate +8%, severe +15%, added together. A milestone's guaranteed items are never scaled.

**Progress tab** **(unverified)**
- Battle-pass-style tracks: a **mastery track** and, during a season, a **season track** with a "season ends in" box. Scroll sideways with the wheel, by dragging, or with the
  Left and Right keys. Rewards are **claimed** from the track (Claim, or Claim all).
- The old cards (contracts, Hall of Fame, run report and the rest) are under **Records**.

## For server owners

**New requirements (P43)**
- **Polymer is now required on the server** (the bundled `polymer-bundled-0.9.19+1.21.1` jar). The tower key, the 48 armor pieces, the armor materials and the season trim
  templates are Polymer items, so players need no CobbleTowers jar to join. Armor tooltips and names come from the server.
- **WebGUI is optional** (`suggests`). Turn the web pages on with `config/cobbletowers-webgui.json` (`enabled`, `url`, `mode` of `fallback` or `prefer`,
  `heartbeat_seconds`). In `prefer` mode every player with WebGUI gets the pages; without WebGUI they keep the native screens (if they have the jar) or chat. The jar installs
  its pages into `config/webgui/web/cobbletowers/` at start.
- **Server-only build:** `./gradlew build -Pserveronly` makes `CobbleTowers-<version>-server.jar` with no client classes. Setup, including hosting the resource pack with
  Polymer's auto-host, is in `docs/SERVER-ONLY-SETUP.md`.
- The normal release jar leaves out the Test Tower, the dev tools and the bot-only commands. `-Pdev` builds the test jar with all of it.

**Behaviour changes to check before you upgrade** **(unverified)**
- **Season track steps now need a claim.** Before, a reached step was granted at once. Set `"auto_claim": true` in `config/cobbletowers-tracks.json` to keep the old behaviour.
  Steps already granted under the old rules count as claimed, so nobody receives them twice.
- **Unclaimed season steps stay claimable through the off-season** (the 7 days after a season ends) and are lost when the next season starts. The Progress tab and the login message remind players.
- **Mastery levels have claimable rewards.** The shipped track gives CobbleDollars at the rank levels (50, 100, 150, 200, 300, 400, 750 at levels 1, 5, 10, 15, 20, 25, 30). These are
  untuned; edit `mastery_tracks/default.json` in a datapack or override them in the config.
- The final payout of a run is now larger when the party held modifiers (see the risk bonus above). If you tuned reward amounts against the old payouts, expect them to rise.

**Rulesets and counters (P38)**
- The ruleset field `item_action_budget` and the API method `RulesetView.itemActionBudget()` are removed: nothing ever enforced them. A datapack that still sets the field is ignored.
- A ruleset may now set `player_held_items` (false to disable the player's items), `enemy_drain` (`start` and `end` percent, each 0 to 50) and `floor_status` (a list of `psn`, `brn`, `par`, `slp`, `frz`, `tox`). All are off by default, so existing rulesets and saved runs are unchanged.
- `/cobbletowers masteryadmin counters` prints balance tallies (track claims, season points, contract completions, modifier pick rates, the risk bonus, floor times).
- The warm pool and a finished run's cell are now built and cleared a slice per tick, so a run start or end no longer holds the server for a third of a second or more when a warm cell is ready.

**New files and folders**
- `config/cobbletowers-tracks.json` (optional): `auto_claim`, and `mastery` / `season` blocks merged after every datapack. Re-read on `/reload`.
- Datapack folders: `data/<ns>/cobbletowers/mastery_tracks/*.json` (new) and `season_tracks/*.json` (now also accepts `add_steps`). Guide: `docs/TRACKS-GUIDE.md`; sample pack:
  `docs/addon-example/`.
- Levels past 30 are supported by the track data, but a level is reached only by unlocking that many achievements, so you also need to add achievements.

**Requirements**
- Deploy the CobbleTowers jar together with the AscensionLib jar (rentals are locked out of upgrades only when both are present).
- Rewards that name CobblemonCards items need that mod on the server; a missing item is skipped with one log line, so players get less and nothing breaks.
- The build version is now `0.27.0-p46-rotating-tower`, so a server log shows which jar is running. Bump it for every deployed build.

## For addon authors
- Mastery tracks and season steps are data. Add rewards and ranks with a datapack; files merge, they do not replace. See `docs/TRACKS-GUIDE.md`.
- Mastery perks are set by level, not claimed: `vendor_discount_percent`, `cobble_dollar_bonus_percent`, `raid_points_bonus_percent` (0 to 100).
- **Trial recipes:** `data/<ns>/cobbletowers/trial_recipes/*.json` (one per kind) list what each part draws from. `trial_pools` still works as the fallback when no recipe is loaded.
  `/cobbletowers trialadmin roll <date>` prints what a date would hold.
- New client payloads `tracks_v1` and `track_action_v1`; a server without them keeps the old Progress cards.

## Known issues
- The web pages and the server-only mode were played through by the owner on 2026-10-14 (hall, features, tracks, lobby, rental draft, a run to floor 2, vendor, rooms) and looked and worked well; a long multi-floor run and a second player are still untested.
- The relic battle effects and the four new rooms are covered by unit tests but have not been seen in a real battle.
- A page closed with Esc may keep running in the background (WebGUI tells the server nothing when a page closes); the server opens menus you ask for (`/tower`) and the intermission after a floor clear regardless, but any other
  screen the server opens by itself may not show if the page is still alive.
- The wider UI font clips a few long labels (for example "Selected destina"); the toggle is the workaround.
- Cells are mostly built and cleared a slice per tick now, but a run start without a warm cell still takes about 340 ms of one tick, and the first allocation after boot 2 to 3 s (`docs/design/cell-allocation-async.md`).
- Mastery-level cosmetics are recorded but not shown anywhere yet.
