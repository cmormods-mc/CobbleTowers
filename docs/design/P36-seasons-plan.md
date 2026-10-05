# P36 plan: Seasons, the Hall of Champions, and Watch (PROPOSAL, not built)

Status: planning. Nothing here is implemented.

## Decided by the project owner (2026-10-04)

| # | Decision |
|---|---|
| 1 | **Season length: 6 weeks** of play. |
| 2 | **Reuse the trial rhythm**: the configured time zone and reset hour (`TrialClock`), so a season turns over at the same quiet hour as a trial day. |
| 3 | **A short off-season week** between seasons (results, the Hall, a rest). One cycle is therefore 6 + 1 = 7 weeks (49 days). |
| 4 | **Spotlight: drops and points only.** It never changes which opponents a seed produces, so run codes and trials keep their guarantee. |
| 5 | **Echo pool: option C** (the previous season's Hall teams serve until the new season has its own top ten). |
| 6 | **A run counts for the season it finishes in** (the cycle clear's date). |
| 7 | **Hall: text only for now** (`/tower hall`). The physical Hall waits for a place to build it. |

Still open (not needed for P36a): track rewards and cap, title delivery, club board scope, prestige marks, who authors season
content (section 12, items 6-8, 10, 12, 13). Detailed foundation design: `P36a-season-foundation.md`.

Roadmap items: A3 Seasons, C2 Titles/banners/Hall of Champions, D3 Watch and announce.

---

## 1. Why seasons

Every board today is **all-time and never resets**. The first people to fill the top of a board are effectively permanent, so a
newcomer cannot be on a leaderboard, Echoes are drawn from the same ten teams forever, and the club board only ever grows. Daily
and weekly trials already give a return loop, but nothing gives the *months-long arc* (a reason to push this month, a fresh
start, a place to be remembered). A season is that arc: a fixed period with its own boards, its own free reward track, one
**spotlight** region, and an archive (**Hall of Fame**) that keeps the winners forever.

Design stance: **reset the race, never the person.** Records, mastery, achievements, Ascension depth, wallets, cards and titles
already earned are never touched. Only the *competition surfaces* restart.

## 2. The model

| Concept | Meaning |
|---|---|
| **Season** | A numbered, named period (`Season 3: Tidebreak`) with a start date, an end date, a spotlight region, a track and optional extra rules. |
| **Phase** | `scheduled` (announced, not started), `active`, `ending` (grace window after the end date while results are finalised), `archived`. |
| **Season id** | `s3` etc.; a new dimension on every seasonal board key and on the player's season data. |
| **Spotlight** | One regional tower (Tideforge, Rootvale, Duskvale) featured for the season; rotates so each gets featured in turn. |
| **Track** | A free ladder of reward steps filled by *season points* earned from play. |
| **Hall of Fame** | Frozen top ten of each seasonal board at season end, kept forever. |
| **Season data** | Per player: points, claimed steps, per-season bests. Per club: season score. |

Time is a **pure function of the calendar**, exactly like `TrialSchedule`: `SeasonSchedule.of(config, date)` returns the season a
date falls in, so every server computes the same answer with no coordination and a test can ask about any date. A season is
defined by data (`data/cobbletowers/cobbletowers/seasons/*.json`), not code.

## 3. What resets and what never does

| Resets or restarts each season | Never resets |
|---|---|
| Seasonal leaderboards (Ascension depth, Speed, Difficulty; Clears is lifetime, see 4) | All-time boards (kept as a separate view) |
| Club **season score** and the club board | Mastery level, achievements, perks |
| Season track points and claims | Ascension records (they gate starting depth) |
| The spotlight region | Wallets, cards, relics-as-items, armor, titles and banners already earned |
| Echo pool (see 7, decision) | Trial streaks and the streak freeze (own daily cycle) |
| Season bests per player | Run codes, trial history |

Rule of thumb: **reward, never remove.** Ending a season only ever adds (a Hall entry, a title, a reward).

## 4. Boards

Today a board key is `(board, tower, mode, playlist)`. Add **`season`** as a fifth part:

* `season = ""` is the **all-time** board and keeps working exactly as now, so nothing existing breaks and no data is lost.
* Every board-relevant result (cycle clear, depth) is offered to **both** keys: all-time and the current season's.
* Trial boards keep their own period ids and are unaffected (a trial result may *earn season points*, not season rank).
* The **Clears** board is per-individual and lifetime, so it stays all-time only; its seasonal analogue is season points.
* Storage migration: old files have no season field, which reads as `""`. No rewrite is needed.
* Seasonal boards cap at 50 entries like today. Old seasons' live boards are **pruned** after the Hall snapshot (keep N, default 2).

Two correctness rules carry over from the existing design: every entry records ruleset revision, tower revision and digest
(TDS #90), and an entry is attributed to **the season in which the cycle clear completed** (see decision 9 for runs in flight).

## 5. The season track (free)

* Points come from: cycle clears (by region; spotlight counts extra), Trials posted, Contracts completed, daily-streak
  milestones. A **daily point cap** keeps it from being a grind (TDS #9: bounded rewards).
* The track is ~30 steps sized so a regular player finishes in about the season length and a casual one gets most of the way.
* Step rewards, **bounded and cosmetic-first**: CobbleDollars, a few cards (reusing the P33b card path), cosmetic banners,
  and **titles** at the last steps. No stipend, nothing repeatable on a button.
* Rewards are placed in the existing **pending reward** store so an offline player receives them at next login; the track screen
  is `/tower season` (text first; a screen later).
* Defined in JSON beside the season (`track: [{points, reward}, ...]`), validated at load and by `validate_definitions.py`.

## 6. The spotlight

The roadmap says the spotlight region's jersey species are drawn more often and its armor drops more often. These are very
different in risk:

* **Drops and track points** (armor weights in the reward table, extra points for clears there): safe. They never change which
  opponents a seed produces.
* **Encounter weighting** (`RegionalWeighting` boosting jersey species): changes the *opponents of a run*. That would silently
  break two promises already made: **run codes** (same code, same run) and **Trials** (same run for everyone). So if the
  spotlight touches encounters, it must (a) never apply to trials, and (b) put the season into the run code so a code carries the
  season it was made under (a `CT2-` code).

Recommendation: ship the spotlight as **drops + points only** first; add the encounter boost later only with the run-code change.

## 7. Interplay with what exists

* **Echoes.** An Echo exists while its run is in the top ten of a regional board. If those boards become seasonal, the Echo pool
  empties at every season start (no duels until new top tens exist) and refreshes with fresh teams. Options: (A) pool follows the
  seasonal boards (fresh, but empty early); (B) pool follows all-time boards (stable, but stale, the thing seasons fix); (C)
  seasonal boards, but the **previous season's Hall of Fame teams** serve until the new season has its own. I recommend C.
* **Clubs.** The club score today is the sum of members' all-time best regional clears; a season-scoped score needs a per-player
  **season best**. The **weekly goal stays weekly**. Top clubs of a season get a banner/title and a Hall entry. **A bug to fix on
  the way:** `ClubService.weekKey()` uses the server's raw local date, while trials and contracts use `TrialClock` (configured zone
  and reset hour). Two clocks will disagree at the boundary; clubs must move to `TrialClock`.
* **Mastery.** Untouched. Season points are a *separate* currency; mastery never resets.
* **Trials, contracts, streaks.** Keep their own cycles; they become **point sources**. The streak freeze is unaffected.
* **Rewards.** Everything goes through the existing pending-reward path, so offline players are paid and crashes cannot double-pay
  (grants are keyed by season and step).
* **Stellar Titles.** CobbleRaids is pinned to an exact version label for Stellar Titles; this plan **never relabels CobbleRaids**.
  Titles are delivered through Stellar Titles where present and otherwise a CobbleTowers-owned chat prefix (see 9).

## 8. Season end: the finalisation flow

Triggered by the first server tick after the end boundary, **idempotent and crash-safe** (a stored `lastFinalized` season id
means a crash mid-way simply resumes; nothing is applied twice):

1. Mark the season `ending`; announce it (a few days' warning is sent earlier).
2. Snapshot the top ten of every seasonal board into the **Hall of Fame store** (frozen forever).
3. Rank clubs; snapshot the club board.
4. Grant end-of-season rewards: titles/banners to Hall entrants and top clubs, via pending rewards, keyed so a replay is a no-op.
5. Carry the previous Hall teams into the Echo pool (if option C).
6. Open the next season (or a configured gap, decision 8); prune old live boards; announce results in one line per board.

A **dry-run** operator command prints exactly what step 2-4 would do without applying it.

## 9. Titles, banners, chat decoration, the Hall

* **Titles/banners** (C2): definitions in JSON; earned from track steps, Hall placement, mastery ranks. Shown on the run report,
  the boards and `/tower` lists.
* **Chat decoration** is needed twice (club tags are stored but not yet shown; titles need a prefix). Build **one** hook for both,
  owned by CobbleTowers, so the two features do not each invent one. It must never touch another mod's formatting beyond a prefix.
* **Hall of Champions**, phased: **(a) text Hall now**: `/tower hall [season]` lists a season's winners and records. **(b) physical
  Hall later**: display entities (vanilla text/item displays) in a lobby area showing each tower's winners; Easy NPC is optional.
  The physical Hall needs a place to build it, which is the roadmap's long-open question about a hub.

## 10. Watch and announce (D3)

Independent of seasons and small, so it can ship on its own: `/tower watch <name>` follows a live run through the spectator panel
knocked-out teammates already use; a single-line server announcement for a new season record or an Ascension milestone. Consent:
a player can opt out of being watched. Watching never reveals anything the boards do not.

## 11. Operations, safety, testing

**Operator tools**: `/cobbletowers season` (status), `start`/`end` (force a boundary), `skip`, `finalize --dry-run`, `spotlight <region>`,
`points <player> <n>`, and a master **off switch** (`seasons: false`: everything reads as the all-time world).
**Safety**: reward, never remove; every season rewrite is idempotent; the Hall is append-only; an operator can undo a bad
finalisation only by restoring the Hall store, so the dry-run is mandatory before the first real end.
**Clock**: reuse `TrialClock` (configured zone + reset hour) and its test seam (`TrialService.overrideDay`) so a live test can jump
to a boundary instead of waiting weeks.
**Tests**: pure `SeasonSchedule` (boundaries, leap years, year rollover, reset hour), dual-key board offers, track caps and claim
idempotency, finalisation resume after a simulated crash at each step, migration of an existing leaderboard file, then live tests that
advance the pinned day across a season boundary and check the Hall, the new boards, the rewards and the announcements.
**Persistence**: new stores `TowerSeasonStore` (state, points, claims, bests) and `TowerHallStore` (archive), plain values only, covered by
`validate_persistence.py`.

## 12. Decisions needed from the owner

1. **Season length**: 6 weeks (roadmap low end) or 8, or varied?
2. **First start date and reset hour** (and time zone); reuse the trial rhythm?
3. **Gaps**: back-to-back seasons, or a short off-season week for results and a rest?
4. **Spotlight scope**: drops and points only (recommended), or also encounter weighting (needs run codes to carry the season)?
5. **Echo pool**: A fresh each season, B all-time, or C previous Hall teams until the new season fills (recommended)?
6. **Club board**: season-scoped with a Hall entry (recommended), or keep all-time as well?
7. **Track rewards**: which kinds (CobbleDollars, cards, banners, titles), and the cap per season? Anything you do not want on a track?
8. **Titles**: deliver through Stellar Titles when installed with a chat-prefix fallback (recommended), or one mechanism only?
9. **Runs in flight at a boundary**: counted in the season they *finish* in (recommended, simplest) or the one they *started* in?
10. **Seasonal boards**: keep solo/team and playlist splits exactly as the all-time ones (recommended), or fewer boards per season?
11. **Hall**: text only for now (recommended), or do you already have a lobby place to build the physical one in?
12. **Prestige**: any visible "seasons completed" mark that carries over, beyond titles and banners?
13. **Seasons content**: who authors the names, spotlight order and track rewards for the first few seasons (I can draft three)?

## 13. Proposed phasing

| Phase | Contents | Depends on |
|---|---|---|
| **P36a** | `SeasonSchedule`, season definitions + registry, `TowerSeasonStore`, `season` on board keys with dual offers and migration, `/tower season`, `/tower hall` (text), finalisation + Hall store + dry-run, operator tools, off switch, clubs moved to `TrialClock` | decisions 1-3, 9-11 |
| **P36b** | Season points and the free track: sources, caps, claims, pending-reward delivery, `/tower season track` | 7 |
| **P36c** | Spotlight (drops and points), club season score and club Hall entries, Echo pool policy | 4-6 |
| **P36d** | Titles and banners, the shared chat-decoration hook (also shows club tags) | 8 |
| **P36e** | Watch and announce; the physical Hall if a place exists | 11 |

Each phase gets its own design note, unit tests, a live test, and the same regression sweep as before, and is committed and
pushed only after review. P36a is the foundation; everything after it is optional and can be reordered.
