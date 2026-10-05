# P36a: the season foundation (BUILT 2026-10-04; see "As built" at the end)

Part of `P36-seasons-plan.md`. This is the first phase: the calendar, season-aware boards, the Hall of Fame, finalisation, and the
operator tools. It adds **no gameplay change**: no points, no track, no spotlight effect, no Echo or club change except one
bug fix (the club week clock). Those are P36b-e. It is deliberately the smallest piece that makes "a season exists, ends, and is
remembered" true.

## 1. The calendar

* One cycle is **49 days**: 42 days of season (six Monday-to-Sunday weeks) then 7 days off-season. Anchored on a configured Monday.
* `season/SeasonSchedule` (pure, like `TrialSchedule`): `at(config, day)` returns one of
  * `BEFORE` (the anchor has not arrived: days until it),
  * `ACTIVE(number, weekIndex 1..6, startDay, lastDay, daysLeft)`,
  * `OFF_SEASON(endedNumber, nextNumber, nextStartDay, daysLeft)`.
  `number = floorDiv(daysSinceAnchor, 49) + 1`; `ACTIVE` when `daysSinceAnchor mod 49 < 42`.
* "Day" is `TrialClock.dayOf(now, rhythm)`: the configured zone and reset hour, so a season starts at the reset, not at midnight. The
  test seam `TrialService.overrideDay` moves seasons too, so a live test can jump to any boundary.
* **First anchor: Monday 2026-10-05** (the next Monday). Season 1 runs 2026-10-05 to 2026-11-15, off-season 11-16 to 11-22, Season 2
  starts 2026-11-23. The anchor is configuration, not code.
* Seasons beyond authored content get a generated name (`Season N`) and a spotlight rotated through the regional towers, so the system
  never runs out of definitions. Authored definitions (`data/.../seasons/season_<n>.json`: `number`, `name`, `spotlight`) override.
* Master switch `seasons_enabled` (default true). Off: every season id reads as empty and the world behaves as it does today.

## 2. Season-aware boards

* `LeaderboardRules.Key` gains a fifth component `season` (`""` = all-time, otherwise `s<number>`), keeping the existing 3- and
  4-argument constructors, so every existing call site still means "all-time".
* `TowerLeaderboardStore` writes and reads `season`; a file with no field reads as `""`, so **no migration step is needed** and no
  stored board changes meaning.
* New `Boards.offerBoth(store, key, entry, seasonId)`: offers the entry to the all-time key and, when a season is **active**, to
  the seasonal key too. `MasteryService` calls it for DIFFICULTY, SPEED and ASCENSION. CLEARS (lifetime) and TRIAL (own period
  ids) never go seasonal.
* During the **off-season week** results post to the all-time boards only and the finished season's boards are frozen (read-only),
  so the Hall cannot shift under people reading it. (A default I chose; see section 8.)
* Live seasonal boards of seasons older than the previous one are **pruned** after the Hall snapshot (the Hall keeps the top ten
  forever; the live boards are 50 deep and not worth keeping).
* **Readers keep behaving the same until chosen otherwise:** `EchoPolicy.topRuns` and the tuning report consider all-time keys only
  until P36c decides the Echo policy; the client leaderboard screen is unchanged.
* The `/tower leaderboard` text command gains a trailing optional choice: `season` (the default while a season is active, and the
  just-ended one during the off-season) or `alltime`. With seasons off, or none yet, it shows all-time as today.

## 3. The Hall of Fame

* `persistence/TowerHallStore` (SavedData, append-only): per season, `number`, `name`, `spotlight`, `endedOn`, and for every
  board that had entries, the **top ten** entries frozen with the same fields as a live entry (players and names at the time,
  value, run id, ascension, score, ruleset and tower revisions and digest, time).
* The entry encoding is shared with the leaderboard store (one `BoardCodec`), not copied.
* `/tower hall [season]` (text, no argument = the latest): for each regional tower and Neutral, the winners of each board
  (solo and team apart, as the boards are). Names are the board's own, so nothing is revealed that the board did not already show.
* Append-only: nothing edits or deletes a Hall season except an operator restoring the file; there is no player-facing way.

## 4. Finalisation

Runs on the server tick the first time `today` is past a season's last day and that season is newer than `lastFinalized`.

1. Compute the **plan** (a pure function: boards snapshot + definition to a list of Hall entries). No server involved, fully
   unit-tested, and what `--dry-run` prints.
2. Write the Hall season (step 1 of 3).
3. Prune old live seasonal boards (step 2).
4. Set `lastFinalized` and announce (step 3).

Progress is stored after each step in `TowerSeasonStore` (`lastFinalized`, and `inProgress: season + steps done`), so a crash resumes
at the next step and **no step runs twice**. Writing the Hall season is itself idempotent (a season already in the Hall is
left alone). The announcement is one line per board with a winner, sent once (guarded by the stored step).

## 5. Operator and player commands

* Player: `/tower season` (name, week x of 6, days left, the spotlight shown but with no effect yet, the next start; off-season: the
  countdown and "see /tower hall"), `/tower hall [season]`, and the `leaderboard` trailing choice above.
* Operator `/cobbletowers season`: `status`, `finalize [dry]`, `enable`/`disable`, and day pinning through the existing trial seam.
  `finalize dry` is **mandatory to try before the first real end**, as the plan says.

## 6. The one fix that rides along

`ClubService.weekKey()` reads the server's raw local date, while trials and contracts use `TrialClock` (configured zone and reset
hour). At a week boundary the two clocks disagree. Clubs move to `TrialClock.weekKey(TrialService.today())`. The stored key changes
from `2026-W41` to `2026-w41`, so a stored club week rolls over once, harmlessly (progress for the current week resets once).

## 7. Tests

* **Pure:** `SeasonScheduleTest` (the anchor day, day 41 last active, days 42-48 off-season, day 49 starts Season 2, before the
  anchor, a year rollover, the number and week index, the generated name and rotation, the off switch); `BoardKey` compatibility
  and NBT (an old tag with no `season` reads as all-time; a seasonal key round-trips); `offerBoth` (both keys in a season, only
  all-time off-season and for CLEARS/TRIAL); `SeasonFinalizer.plan` (top ten per board, solo and team apart, ties by time, a
  board with no entries is skipped); `TowerHallStore` and `TowerSeasonStore` round trips; finalisation resume after a stop at each
  step and idempotence of running it twice.
* **Live** `season_test.py`: pin the day inside Season 1, clear a cycle (the Test tower, by operator command) and check the result is on
  both the season and all-time boards; pin the day into the off-season and check finalisation fires once, the Hall lists the
  winner, the seasonal board is frozen, a new result posts to all-time only; pin Season 2 and check the new seasonal board is empty
  and all-time kept everything; `finalize dry` changes nothing; restarting mid-way (stop the server between steps) resumes.
* **Regression sweep:** the mastery, trial, contract, club, echo, ascension and relic tests, since boards and the club clock change.
* `ci_local.sh` (persistence validator picks up the two new stores).

## 8. Defaults I chose that you can overrule

1. **First season starts Monday 2026-10-05**, so seasons and the weekly trial share Monday boundaries.
2. **The off-season week freezes the seasonal boards**; new results post to all-time only and earn nothing seasonal. (Alternative: let
   them count toward the next season, which makes the first days of a season an unfair head start.)
3. **`/tower leaderboard` defaults to the current season** once seasons are on (all-time stays one word away). This changes what the
   default shows; the client screen is unchanged for now.
4. **Authored names**: I will draft Season 1-3 names and spotlight order (Tideforge, Rootvale, Duskvale) for you to edit; later seasons
   are generated.
5. **Hall depth is ten per board**, solo and team apart.

## As built (2026-10-04)

Built as designed, with these differences and facts worth knowing:

* **The end-of-season announcement is one line**, not one per board (thirty lines of chat for a Hall would be noise): "Season N: Name has
  ended. Its winners are in the Hall of Fame: /tower hall N." The start of a season is announced once too.
* **Commands**: players use `/tower season`, `/tower hall [season]` and `/tower leaderboard <board> [alltime] [tower [playlist]]`.
  Operators use `/cobbletowers seasonadmin status|check|finalize [dry]|enable|disable|clear`; the calendar is moved with the existing
  `/cobbletowers trialadmin day <date|off>`. `disable` is in memory until the next restart (the config file decides after that).
* **Config** is `config/cobbletowers-seasons.json` (`anchor`, `enabled`), beside the trial config; authored seasons are
  `data/cobbletowers/cobbletowers/seasons/season_<n>.json` (three ship: The Rising Tide, Deep Roots, The Long Dusk).
* `LeaderboardRules.Key` has the fifth `season` part with compatible constructors; `BoardCodec` is the shared entry encoding;
  `TowerLeaderboardStore.load` is now public like the other stores'. `Boards.offerBoth` is the one place a result is posted.
* **Readers kept on all-time**: `EchoPolicy.topRuns` and the tuning report ignore seasonal keys; the client leaderboard screen is
  unchanged and is used only for the all-time default (a season view, or any playlist view, is read in chat).
* **Fixed on the way**: the club weekly goal now uses the trial clock. Its stored week key changed shape (`2026-W41` to `2026-w41`),
  so a stored club week rolls over once.
* `validate_definitions.py` knows the `seasons` kind (numbers unique and from 1, any spotlight is a loaded regional tower).

**Verified**: 16 new unit tests (`SeasonScheduleTest`, `SeasonBoardsTest`); live `season_test.py` 16/16 (a cycle posts to the season and
all-time boards; the Hall records the winner when the season ends, once; a re-run finds nothing; the off-season freezes the season
board and posts all-time; season 2 starts empty with all-time and the Hall intact; the off switch restores the all-time world);
`ci_local.sh` clean. Regression sweep: see the commit.

**Crash-resume, proven live (2026-10-04)**: `validation/smoke/season_crash_test.py` 22/22. A test-only seam
(`-Dcobbletowers.testOnlyCrashAfterSeasonStep=N`, never set in production) halts the JVM right after step N is saved, with no shutdown hooks
and no further saves, which is what a real crash leaves on disk. After a hard crash after step 1, and again after step 2, the server was
booted again and: a dry run still saw the unfinished season; finalisation logged "resumes after step N"; the remaining steps ran; the season
finished exactly once; the step that had been saved did not run again; the Hall held the season once with both of its boards; and the end of the
season was announced once. Each step also now logs a line, which helps an operator reading a log.

**Still not proven live**: a server that was down across several finished seasons catching up (the loop is the same one, oldest first), and the
real clock turning over at a reset hour (the day pin was used).

## 9. Out of scope (later phases)

Points and the track (P36b); the spotlight's drops and points, club season score and club Hall, the Echo pool policy (P36c); titles,
banners and the chat hook (P36d); watch, announcements beyond the finalisation line, and the physical Hall (P36e).
