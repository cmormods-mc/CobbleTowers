# Release notes (draft, updated 2026-10-08)

Covers the work after commit `9688678`. Items marked **(unverified)** are written but have not been built or run yet; do not publish them as done until
the checklist in `docs/RELEASE-CHECKLIST.md` is ticked.

## For players

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

**Behaviour changes to check before you upgrade** **(unverified)**
- **Season track steps now need a claim.** Before, a reached step was granted at once. Set `"auto_claim": true` in `config/cobbletowers-tracks.json` to keep the old behaviour.
  Steps already granted under the old rules count as claimed, so nobody receives them twice.
- **Unclaimed season steps stay claimable through the off-season** (the 7 days after a season ends) and are lost when the next season starts. The Progress tab and the login message remind players.
- **Mastery levels have claimable rewards.** The shipped track gives CobbleDollars at the rank levels (50, 100, 150, 200, 300, 400, 750 at levels 1, 5, 10, 15, 20, 25, 30). These are
  untuned; edit `mastery_tracks/default.json` in a datapack or override them in the config.
- The final payout of a run is now larger when the party held modifiers (see the risk bonus above). If you tuned reward amounts against the old payouts, expect them to rise.

**Rulesets and counters (P38)**
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
- The build version is now `0.23.0-p38-regions-armor` (it had never been bumped from `0.21.0-p21-warm-pixel-preview` before 2026-10-07), so a server log shows which jar is running. Bump it for every deployed build.

## For addon authors
- Mastery tracks and season steps are data. Add rewards and ranks with a datapack; files merge, they do not replace. See `docs/TRACKS-GUIDE.md`.
- Mastery perks are set by level, not claimed: `vendor_discount_percent`, `cobble_dollar_bonus_percent`, `raid_points_bonus_percent` (0 to 100).
- New client payloads `tracks_v1` and `track_action_v1`; a server without them keeps the old Progress cards.

## Known issues
- The wider UI font clips a few long labels (for example "Selected destina"); the toggle is the workaround.
- Cells are mostly built and cleared a slice per tick now, but a run start without a warm cell still takes about 340 ms of one tick, and the first allocation after boot 2 to 3 s (`docs/design/cell-allocation-async.md`).
- Mastery-level cosmetics are recorded but not shown anywhere yet.
