# P36c: the spotlight, club seasons and the Echo pool (BUILT 2026-10-04)

Part of `P36-seasons-plan.md`. Owner decisions that shaped it: the spotlight is **drops only** (never opponents, so run codes and trials keep their
guarantee); the Echo pool is **option C** (the previous season's teams serve until the new season has its own); the armor drops **twice as often**; the
season's top clubs get **a Hall entry and a banner unlock, no items**; a club's **all-time score stays visible** beside the season score.

## 1. The spotlight (drops only)

The season's spotlight region drops its own armor at **double weight** in its own tower's loot. `season/SeasonSpotlight.weightPercent` is the whole
rule: 200 for a piece of the region's own armor set (`tideforge_*` in Tideforge) while a season is running with that tower in the spotlight, and 100 for
everything else. It reaches the loot through one new hook: `RewardDraw.pickItem(..., weightPercent)` scales each entry's weight (never below 1, so an
entry cannot vanish) and `RewardValuation.value(..., weightPercent)` threads it through ordinary rolls and milestone bonus rolls. `RewardBankService`
passes `SeasonSpotlight.weights(run.towerId())`. With a weight of 100 the draw is **identical** to before (a test checks forty thousand rolls).

What it deliberately does not do: change amounts, guaranteed milestone items, other regions' armor, other towers, opponents, bosses, draft cards or
trials. Season points for the spotlight (30 for a clear instead of 20) shipped in P36b.

Honest edge: the loot a run rolls depends on the weights at the moment it is banked. A run banked across a season boundary (or re-banked after a crash on the
far side of one) can roll differently from the original. Loot has never been part of the run-code promise (opponents, bosses and draft cards are).

## 2. Club seasons

* `ClubBook` now keeps each player's **best regional clear per season** (for seasons not yet finalised). A club's **season score** is the sum of its
  current members' bests in that season; the all-time score is unchanged and shown beside it. A clear outside any season (the off-season, or seasons
  off) counts for the all-time score and the week only.
* `/tower club top` shows the **season's** board by default while a season is on view; `/tower club top alltime` shows the lifetime one. `/tower club`
  shows both scores.
* **At season end** the Hall freezes the **season's club board** (top ten clubs: name, tag, banner, score, members' names) in step 1. Step 2 then
  reads the **podium from what the Hall froze** (so a crash and a resume awards the same clubs even if memberships changed): the top three clubs
  **unlock a prestige banner** (gold, silver, bronze) and get a line of honour (`Season 1: champion`), and every member gets a permanent mark
  (`s1:club_gold`, shown in the "Earned" list of `/tower season track`). All of it is idempotent. Old per-player season bests are then forgotten.
* A club may set a prestige banner only after earning it (`/tower club banner gold`); the sixteen dye colours stay free for everyone.
* `/tower hall` shows the top three clubs of a season under its boards.

## 3. The Echo pool (option C)

* An Echo now carries the **season it was earned in** (0 for one earned with seasons off or before they began).
* **Recording** reads the top ten of the **season's** boards for the season on view, instead of the all-time ones. **Pruning** keeps a current-season
  Echo only while its run is still in that top ten, always keeps the **previous** season's Echoes (the Hall teams) and drops anything older.
* **Serving** (`EchoPolicy.pool`): the season's own Echoes, plus the previous season's **while there are fewer than five** of the current season's, so a new
  season never starts with nobody to duel. With seasons off the pool is the all-time Echoes as before, and season 1 is served by the Echoes made before it began.
* Nothing about finalisation changes for Echoes: the season stamp does the work, so there is no ordering to get wrong.

## 4. Verification

Unit `SeasonCompetitionTest` (spotlight rule and its effect over 40,000 rolls, weight 100 identical, club season scores kept apart from all-time and
other seasons, the board's ranking and ties, prestige banners locked until earned and idempotent honours, season-best pruning, club storage, the Hall's
club board, the Echo pool and prune rules, the season-scoped top runs). Live `season_clubs_test.py` 16/16: a club founded in season 1 with a locked gold
banner; a real Tideforge cycle gives it a season score equal to its all-time score, a season board and an all-time board, and an Echo stamped season 1;
at season end the Hall holds the club board, the club is honoured with the gold banner unlocked and settable, the award is logged once, the Echo
outlives the season; season 2 starts with an empty club board while all-time keeps the club, and the champion's mark is on the track view; no exception while
banking under the spotlight. `season_crash_test.py` re-run, since finalisation gained a step's worth of work. `ci_local.sh` clean.

## 5. Not built or not proven

* The spotlight's doubling is proven on the draw (a large sample) and the banking ran live under it, but a real armor piece landing more often in a real
  run was not observed: a rate like that needs many runs.
* Echo **serving** under the new pool was proven as pure rules and by the recording and survival live; a duel against a Hall Echo in season 2 was not played.
* The prestige banners are names; they get a visual meaning with the banners in P36d. The club mark shows only as text.
* The club podium honours **members at the time of award**, not at the moment the season ended (they differ only if membership changes between a crash and the resume).
