# Player engagement: systems and signature features

Status: **direction chosen, nothing built.** It maps what the tower already gives a player, where it runs thin, and a set of
features chosen because they are cheap *for this codebase* (they reuse seeds, boards, the modifier and battle-operation
machinery, the intermission) rather than because they are generic. Phase numbers (P32 onward) are the working plan.

## 0. Decisions so far (project owner, 2026-10-04)

| Question | Decision |
|---|---|
| Community | **Mid-size, mixed solo and group** (about 30-100 online): needs both a good solo habit and good group play |
| Cadence | **Heavy: daily streaks matter.** Daily trial and contracts, daily boards, escalating streak rewards |
| Features to build first | **Seeded Trials and Contracts** (daily habit), **Relics and Intermission Events** (run variety), **Echoes and Clubs** (social and competitive) |
| Scope of Echoes and Clubs | **Regional towers only** (Tideforge, Rootvale, Duskvale). Neutral and the Test tower do not take part |
| Trial length | **A short daily trial (five floors, about 15-20 minutes) and a full weekly trial (ten floors)**, each with its own board |
| Echo consent | **Automatic for top-10 runs**, with an opt-out. See D1 for what that means for names |
| Playlists and Rental mode | **Playlists in P32** (so the Daily Trial can rotate modes); **Rental as the next phase, before Relics**, built as a **pack-opening draft** with an animated opening experience (section 9) |

What "heavy" means for the design: the daily board resets every day (a week of them is kept), streak rewards escalate at 3, 7,
14, 30, 60 and 100 days, a login summary shows the streak and how long it has left, and contracts rotate daily. The rewards stay
bounded (TDS #9): streaks buy **cosmetics, titles and season points**, not an ever-growing pile of items. One safeguard is built in
and should stay unless you say otherwise: an earned **streak freeze** (one per seven-day streak, two held at most) so a sick day
or a holiday does not erase a hundred-day streak. Heavy cadence without it is the fastest way to turn players away for good.

## 1. Where we are: an honest read

| Loop | What exists | Strong | Thin |
|---|---|---|---|
| **Run** | lobby, party registration, 10 floors, a modifier draft each intermission, F5/F10 bosses, cash out or Ascend | the mechanics; Ascension makes it endless | every run has the same *shape*; variance is opponent draws plus three modifier cards |
| **Power** | rewards (items, cards, Raid Points, CobbleDollars), 4 armor sets with set bonuses, vendor | a clear reason to run | one linear ladder; no upgrade path, few sinks |
| **Mastery** | 30 achievements per tower, ranks, small perks | long-tail goals, all data-driven | individual only; never resets, so it only ever ends |
| **Competitive** | four all-time leaderboards | records carry revisions, solo and team apart | all-time boards go stale fast; nothing shared to compete on |
| **Social** | teams of up to four, teammate-targeted vendor purchases, spectate-when-knocked-out | co-op is real | no persistent groups, no audience, no reason to play together except difficulty |
| **Return** | none | | **nothing changes from one day to the next** |
| **Onboarding** | `/tower`, then ten floors | | no guided first run, no first-session goal |
| **Identity** | titles exist for raids (Stellar Titles); regional doctrines and jerseys are data | | nothing a player *is* or *shows* in the tower |

The biggest gap is the **return loop**: a player who has done a cycle has no reason to log in tomorrow rather than next month.
The second is **variety**: after three runs the surprise is the draw, not the run. Both are fixable with systems we already own.

## 2. Pillars and guardrails

1. **A reason to come back tomorrow**, not just to grind longer.
2. **Variety from systems, not content volume.** Few hands, so each feature is data-driven and multiplies what exists.
3. **Skill and teamwork over grind.** Hard-capped, diminishing rewards (TDS #9); nothing repeatable on a button (no stipends).
4. **Determinism is an asset.** Runs are seeded (TDS #29): opponents, boss and draft cards derive from the run seed. Shared seeds
   give fair, comparable, shareable runs almost for free.
5. **Locked decisions hold:** tower opponents are not catchable (#78); jersey aspects are never a reward (#79, #89); every
   record carries ruleset and definition revision (#90); every tower stays open (no unlock gating).
6. **Never punish with lost Pokemon.** Stakes are rewards and records, not ownership.
7. **Testable headless, and honest about what is not.** Every feature names its live test and what a real client must confirm.

## 3. The feature map

Cost: S = days, M = a phase, L = more than one. "Reuses" names what already exists.

| # | Feature | Loop | Cost | Reuses | Pitch |
|---|---|---|---|---|---|
| A1 | **Seeded Trials** (daily and weekly) | return | M | seeds, boards, mastery events | everyone plays the same run today; one board per trial |
| A2 | **Contracts** | return | M | achievement evaluator, event stream | three rotating objectives a day with bounded rewards |
| A3 | **Seasons** | return, competitive | M | boards, rewards, titles | 6-8 week arcs: board reset with a Hall of Fame, a spotlight region, a free reward track |
| B1 | **Relics** | variety | M-L | `ModifierEffects`, `CustomEffects`, battle ops | run-long passives found on the way, with synergies |
| B2 | **Intermission Events** | variety | M | draft, `IntermissionRound`, vendor | shrine, gambler, forge, rest: a choice between floors |
| B3 | **Branching routes** | variety | L | floor definitions, cycling content lookup | pick the safe or the hazardous way at floors 3 and 7 |
| B4 | **Playlists** (rulesets as modes) | variety | S-M | `RulesetDefinition`, `PartyValidation` | Monotype, level cap, underdog, hardcore, each with its own boards |
| B5 | **Rental mode** | variety, onboarding | L | party registration, rulesets | play a fixed curated team: fair boards, no strong team needed |
| C1 | **Doctrine pledge** | identity | M | regional themes, battle ops | pledge Momentum, Growth, Disruption or Adaptation for a run |
| C2 | **Titles, banners, Hall of Champions** | identity | M | mastery, boards, Stellar Titles | something to wear and a place to be seen |
| C3 | **Run Report** | identity, retention | S | run stats, ledger | a shareable end-of-run card |
| C4 | **Codex** | collection | M | scouting profiles, encounter data | a catalogue of what you have beaten |
| D1 | **Echoes** | social, competitive | L | boards, encounter construction | your winning team becomes someone else's boss |
| D2 | **Clubs** | social | M | mastery, boards | persistent crews with a shared board and goal |
| D3 | **Watch and announce** | social | S-M | spectator panel | watch a live run; records are announced |
| D4 | **Run codes** | social | S | seeds | race a friend on the exact same run |
| E1 | **Armor Forge** | power, sink | M | armor sets, wallet | upgrade a set through star tiers |
| F1 | **Rookie Circuit** | onboarding | S-M | lobby, tower | a guided first run with a checklist |

The five that make CobbleTowers *itself* rather than a generic dungeon crawler are **A1 Seeded Trials, B1/B2 Relics and
Events, C1 Doctrines, D1 Echoes and B4/B5 Playlists and Rental**. The rest are proven retention mechanics we can build cheaply.

---

### A1. Seeded Trials (the daily habit)

**What the player sees.** `/tower trial` (and a button on the play screen) shows today's Trial: a named, fixed challenge such as
"Tideforge, level-locked, Electric Terrain, no healing". Everybody gets *the same* opponents, boss and draft cards. A **short daily
trial (five floors)** and a **full weekly trial (ten floors)**, each with its own board. One scored attempt a day (further runs are
practice and do not post), **a daily board that resets every day** (the last seven days stay viewable) and a
weekly total, and a **streak**: consecutive days with a posted attempt. Streak milestones at 3, 7, 14, 30, 60 and 100 days award
titles, banners and season points; an earned **streak freeze** covers a missed day (see section 0). On login a one-line summary says
what today's Trial is, the streak, and how many hours it has left.

**How it works.**
* A `TrialDefinition` (JSON): tower, ruleset, forced modifiers, the date window, scoring rule. The seed is a hash of the trial id,
  so it is the same on every server without any coordination. `RunFactory.create` already takes a seed.
* Scoring is `time + faint penalty + difficulty`, from the active-time and faint tracking mastery already keeps.
* **Fairness:** opponent levels derive from the party's mean level, so a Trial uses a level-locking ruleset (enemy and player
  levels fixed) or it would measure how strong your Pokemon already are, not how well you played.
* `TowerLeaderboardStore` gets a trial id in its key. An attempts store (per player, per trial) enforces the limit.

**Why it is ours.** Determinism is a stated design value (TDS #29). Almost no Cobblemon content has a *comparable* run.

**Risks.** Server clocks (use a single configured reset hour); a stale seed after a content edit (the trial pins the tower
digest and refuses to run against a changed one); trivial farming (reward is small and bounded, the streak is the draw).

### A2. Contracts

**What the player sees.** Three contracts a day, one weekly: "Clear 3 floors without using the vendor", "Defeat a boss with only
Water types in the party", "Clear a floor in under 90 seconds". Each pays a small bounded reward and season points. One free reroll.

**How it works.** The achievement evaluator already judges single clears against constraints; generalise the conditions
(floors cleared, boss defeated, vendor used, type of party, active time) over a small **tower event stream**
(`floor cleared`, `boss defeated`, `faint`, `purchase`, `modifier drafted`, `run ended`) that `MasteryService.onTransition`
becomes the first consumer of. Contracts and achievements then share one pure evaluator and one JSON shape.

**Risks.** Farming (require minimum floor depth and a distinct run per contract; cap daily income); too much to read (three
lines, shown on the play screen and in chat on login).

### A3. Seasons

**What the player sees.** A named season of 6-8 weeks. All boards reset into a **Hall of Fame** that keeps the top ten of the
season forever. One region is the **spotlight**: its jersey species are drawn more often and its armor pieces drop more often.
A free **season track** of reward steps fills from contracts, trials and clears; the last steps award titles.

**How it works.** A season id is part of every board key (the all-time boards stay as a separate view). A `SeasonDefinition`
names the spotlight, an extra modifier pool, and the track. The spotlight is a weighting on pools and drop tables that already
exist (`RegionalWeighting`, reward tables).

**Risks.** The cliff at season end (carry mastery over untouched; reward, never remove); needs the Hall to feel like a prize
(see C2).

### B1. Relics

**What the player sees.** Run-long passive items. After each milestone boss you choose one of three. They are not a bigger
number: *Metronome* ("moves used three turns running deal +15%"), *Cracked Hourglass* ("your first switch each battle is free,
but your vendor is closed"), *Doctrine Banner* (empowers Momentum, Growth or Disruption synergies).

**How it works.** A relic is a typed bundle: ordinary `ModifierEffects` fields, a `CustomEffects` behavior, and/or battle
operations. All three delivery paths exist. New content is JSON; a new behavior is one enum entry and one method, as the four
custom modifiers were. Relics carry **tags** (momentum, growth, disruption, risk, economy) so that holding several of one tag
unlocks a set effect, the way armor sets do.

**Why it is ours.** The custom modifier work already proved the hook points (battle, economy, intermission, vendor). Relics are
the "deck-builder" layer that makes a run feel assembled rather than survived.

**Risks.** Balance and synergy explosion (cap at 6 held, tag effects at 2 and 4); content volume (30 to 40 relics is the floor
for variety, so ship 12 and grow).

### B2. Intermission Events

**What the player sees.** Between the draft and the ready-up a random **room** appears, voted on like the draft:
*Shrine* (take a curse, gain a relic), *Gambler* (the Wheel, but you choose the stake), *Forge* (a battle-only EV boon for the
next floor, paid in CobbleDollars), *Rest* (a free heal and a draft reroll), *Echo Hall* (see D1). Skippable. Seeded.

**How it works.** The intermission screen and `IntermissionRound` already carry a draft, a vendor, a ready vote and a cash-out
vote; an event is one more card type with a pure resolver and an effect delivered through the existing paths.

### B3. Branching routes

At floors 3 and 7 the team chooses between two variants of the next floor: a *safe route* and a *hazard route* (extra opponents,
a field condition, more reward). Floors already cycle through a content lookup, so a route is "floor N, variant B". The cost is
**authoring** (a second building or an alternative anchor set per branch) and UI; ship it after B1 and B2 prove the appetite.

### B4. Playlists (rulesets as modes)

**What the player sees.** A mode picker in the lobby: **Standard**, **Monotype** (every registered Pokemon shares a type),
**Level Cap 50**, **Underdog** (no fully evolved Pokemon), **Hardcore** (vendor closed, no items), **Solo Gauntlet**. Each
playlist has its own boards, mastery tracks "Scholar of X", and tuned rewards.

**How it works.** `RulesetDefinition` already holds party size and level bounds and `PartyValidation` already enforces party
rules at registration. Add *party clauses* (type clause, species clause, level cap, no-legendaries) as pure checks and let a
tower name its ruleset per playlist. This is the cheapest large variety win on the list: no new mechanics, new reasons to build a
new team.

### B5. Rental mode

Play a curated fixed team instead of your own: from a roster of authored sets (species, moves, ability, level 100, EVs). It is
the true "Battle Tower" heritage, it makes **boards fair** (everyone has the same pool), and it lets a new player with a weak
box experience the tower. Needs a way to put temporary Pokemon in a party and take them away safely; the party journal from P18
(journal, flush, move, restore on every exit) is exactly that machinery.

### C1. Doctrine pledge

The four regional doctrines (Momentum, Growth, Disruption, Adaptation, TDS #80) are today only labels. At lobby time a player
may **pledge** one for the run: a small passive (Momentum: your lead acts a little faster; Growth: your party recovers a little
between floors; Disruption: your first status move lands more often; Adaptation: resist the type that hit you hardest last
floor) and a matching restriction. Delivered as battle operations and custom effects. Pledging repeatedly feeds a per-doctrine
**mastery** track and relic synergies. It gives a run an identity decision before the first floor.

**Risk.** Four doctrines times every Pokemon is a balance surface; start with effects that are lopsided in *style*, not strength.

### C2. Titles, banners and the Hall of Champions

Mastery ranks and season results award **titles** (through Stellar Titles where present, otherwise a chat prefix) and **banners**
(cosmetic, shown on the run report and the board). A physical **Hall of Champions** in the lobby world shows each tower's season
winners and records using NPCs or display entities (Easy NPC is in the modpack). The point is a place to be seen: it turns the
leaderboard from a command into a destination.

### C3. Run Report

A card at the end of every run: floors, time, MVP Pokemon (most KOs), longest no-faint streak, relics and modifiers, score, rank
change, achievements unlocked, and a one-line shareable summary in chat. Everything it needs is already tracked or one counter
away. Cheap, and the single best "that felt like something" moment per run.

### C4. Codex

A catalogue of every species met per tower, every boss defeated, every jersey seen. Entries fill from scouting data. Each page
completes a small bonus and feeds a "Scholar" mastery track. It rewards curiosity and gives reasons to try other towers.

**Scope: the three regional towers only** (Tideforge, Rootvale, Duskvale). Neutral and the Test tower never record or serve Echoes.

**What the player sees.** When a regional tower cycle makes a tower's **top ten**, the team that did it is **automatically
recorded as an Echo**: a snapshot of the registered team (species, moves, ability, level, nature). A player can opt out for
good with one command (`/tower echo off`) and sees what was recorded (`/tower echo`); an Echo that falls off the top ten leaves the
pool. Because it is automatic, the Echo is named by the **board's own display name**, the same name the leaderboard already
shows publicly; there is no hidden data. An Echo then appears as an optional
**Echo Duel** room at a regional tower's milestone intermissions (an Intermission Event, B2): a bonus fight against a leaderboard
player's team, with a bounded reward, never a replacement for the CobbleRaids boss. Beating one tells its owner ("Your Echo has
defeated 12 challengers"). The count is a prestige stat on the board.

**Why it is ours.** It is PvP flavour with no scheduling, no lag and no grief: nobody is online and nobody loses a Pokemon. It makes
the boards matter to someone *else*, and it solves "the boss is always the same boss".

**How it works.** Needs a party snapshot (today a run stores only Pokemon uuids and levels), a way to build an opponent from a
snapshot (the encounter construction path takes species, level and properties, so it extends), an opt-in per player, and a size
cap on stored Echoes. **Cost L**, mostly the snapshot and moderation, so it is last.

### D2. Clubs

**Scope: regional towers only.** Only clears, trials and objectives in Tideforge, Rootvale and Duskvale count towards a club.

Persistent named crews of up to twelve: a shared banner, a **crew board** (sum of members' best regional clears this season), a
weekly crew goal with a shared reward, and a crew chat tag. It converts a one-off team into a standing reason to log on together.
A small store and a command set; no new combat.

### D3. Watch and announce

Any player can `/tower watch <name>` to follow a live run using the spectator panel knocked-out teammates already use. New
records and Ascension milestones are announced server-wide in a single line. It gives a run an audience.

### D4. Run codes

Every run's seed (plus its trial and playlist) prints as a short code on the report. Entering a friend's code starts the same
run. With A1 built this is almost free, and it is the natural way to challenge a friend.

### E1. Armor Forge

Each set gets three star tiers: stars come from materials the towers drop and Raid Points, raise the set bonus modestly, and add one
*quirk* at three stars. It is the long sink that makes the sets a progression instead of a drop table. Bounded (TDS #9).

### F1. Rookie Circuit

A guided three-floor run for a player's first visit: scripted tips at the intermission, a checklist (start a run, draft a card,
use the vendor, clear a boss), and a small one-time reward. It is the front of the retention funnel; without it every other
feature is invisible.

## 4. Recommended sequence

Re-ordered to the decisions in section 0: the daily habit and Playlists first, then the Rental Draft, then run depth, then the social layer.

| Phase | Theme | Features | Why this order |
|---|---|---|---|
| **P32** | **The daily habit and Playlists** | A1 Seeded Trials (short daily, full weekly, daily board, streaks, freezes), A2 Contracts, C3 Run Report, B4 Playlists, the tower event stream, login summary | closes the biggest gap (return); Playlists give the Daily Trial a rotating mode from day one |
| **P33** | **The Rental Draft** | B5 Rental, built as a pack-opening draft (section 9): Tower Packs, the animated opening screen, rentals through the party journal, a Rental playlist, a Rental Daily Trial | makes the daily and weekly boards fair and gives newcomers a way in; the opening experience is a signature moment |
| **P34** | **Run depth** | B1 Relics, B2 Intermission Events (incl. the Echo Duel *slot*), optionally C1 Doctrine pledge | the "assembled run" layer; builds on the proven custom-modifier hooks, and gives Echoes somewhere to live |
| **P35** | **Social and competitive** | D1 Echoes and D2 Clubs, regional towers only; D4 Run codes | heaviest builds; Echoes need the Echo Duel room from P34, Clubs need the boards and season ladder |
| **P36** | **Seasons and a place to be seen** | A3 Seasons, C2 Titles and Hall of Champions, D3 Watch | daily boards and club boards need a season rhythm to mean something; the Hall is where season winners live |
| later | **Long tail** | E1 Forge, C4 Codex, B3 Branching routes, F1 Rookie Circuit | grow the ones the data says players want |

Before P32, one **client verification pass** is worth doing: the play screen's Ascension picker, the mastery screen, the vendor
villager, the tooltips and the reward reveal have never been seen in a real client, and every phase above adds more screens.

## 5. How we will know it works

Extend the diagnostics store (P13) with **engagement counters**, keyed by day and by player id only (no names):

* players active per day and per week; returns after 1, 7 and 30 days (from a last-seen day per player);
* runs started, completed, cashed out, abandoned, and the **floor at which runs end** (the funnel);
* trial attempts and completions; contracts offered, taken, completed; streak lengths;
* time to a first clear; share of players who start a second run in a session.

A feature that does not move a number it was built for is cut or reworked, not left to accrete.

## 6. Risks and constraints

* **Scope.** The map is deliberately wider than any one phase; the sequence is a recommendation, not a commitment.
* **Exploits.** Anything with a reward needs a daily cap, a depth requirement and a distinct-run rule. Contracts and trials are the
  most exposed.
* **Fairness.** Competitive boards need level locking or Rental mode (B5); otherwise they measure box strength.
* **Content volume.** Relics, events and contracts need authored variety; each ships with a dozen and grows.
* **Server cost.** Everything new is bookkeeping on events that already fire, except Echoes (storage) and the Hall (entities).
* **Real-client blind spot.** Every UI feature is headless-tested only. Budget the verification pass.
* **Obligation.** Daily systems can feel like chores. Streak forgiveness, opt-in contracts and generous catch-up are design
  requirements, not polish.

## 7. Still open

1. **Session length.** Decided: a five-floor daily trial and a ten-floor weekly trial. Needs a way to run a five-floor trial on a
   ten-floor tower (the trial definition names the floor range; floors 1-5 end at a Trial summary instead of an intermission).
2. **Is there a hub?** The Hall of Champions (P35) needs a lobby area we can build into and Easy NPC (in the modpack) to stand in it.
3. **Echo opt-out.** Decided: automatic for top-10 runs. Confirm the opt-out (`/tower echo off`) and that removed players' Echoes
   leave the pool immediately.
4. **Day boundary.** One configured reset hour (suggest a quiet hour for the main player timezone) for trials, contracts and streaks.
5. **Playlists and Rental.** Decided: Playlists in P32, the Rental Draft in P33. Section 9 lists what is still open for the draft
   (pack size, draft shape, a power budget, rarity names).
6. **Doctrine pledge (C1).** In P33 or deferred? It is the only feature there that is a balance surface across every Pokemon.
7. **Streak freeze.** Keep the safeguard in section 0, or make streaks strict?

## 8. Playlists and Rental mode in depth

### 8.0 In plain words, with examples

**Playlists: "which Pokemon may I bring, and what are the house rules?"** Think of choosing a format in Pokemon Showdown
("Monotype", "Little Cup") before a match. Today the tower is one format: bring any six Pokemon. A playlist is a menu in the lobby
that changes the *entry rules* and nothing else. The floors and bosses are the same.

*Example.* Alex opens `/tower`, picks Tideforge, and sees the new row **Mode: Standard**. Alex taps it and chooses **Monotype**.
Alex's box has a Vaporeon, a Kyogre and a Blastoise (all Water) plus a Charizard. The lobby says:
`Cannot start: Charizard is not a Water type`. Alex swaps in a Starmie, and the run starts. Alex's clear posts to the
*Tideforge Monotype* board, separate from the Standard board, and counts towards a "Monotype Master" achievement. Other days Alex
tries **Level Cap 50** (the level 80 team has to stay home), or **Hardcore** (no vendor, no items, no healing between floors).
Same tower, a new puzzle: "what do I build for that rule?" It makes people dig into their boxes.

**Rental mode: "I will lend you a team."** In the main Pokemon games' Battle Tower/Battle Frontier you could borrow Pokemon instead
of using your own. This is that. In the lobby Alex picks **Mode: Rental** and then a pack, for example "Hyper Offense: Garchomp,
Dragapult, Kingambit, Gholdengo, Landorus, Volcarona", all level 100 with set moves and items. Alex's own Pokemon are not touched
(they stay safely stored) and the six rentals appear in the party for the run. When the run ends, they disappear. Every player who
picks that pack has **exactly the same team**, so the board shows who played best, not who owns the strongest Pokemon.

*Example.* Sam joined last week and has three level 20 Pokemon. In Standard Sam would be crushed by floor 3. With Rental Sam can
run the whole tower on a borrowed team, learn the floors, and see the rewards. Meanwhile, the **Daily Trial** can be "Rental,
Pack of the Day" so that Sam and a player with a maxed-out box compete on genuinely equal terms on the same daily board.

**The difference in one line.** A playlist restricts *which of your own Pokemon* you may bring; Rental replaces them with
*a team that is lent to you*. Rental is really a playlist whose rule is "you must use the pack".

**Why bother (the engagement point).** Playlists give a veteran a reason to build new teams and a new board to climb, without any
new content to author. Rental gives a newcomer a way in and gives *competition* a level field. Both feed the daily habit: the
Daily Trial can rotate "Monotype Tuesday", "Rental Friday", "Hardcore Sunday", so every day looks different.

**What it would cost.** Playlists are cheap (a rule layer and a menu row; no new combat). Rental is the larger build because the
lent Pokemon must never leak into a player's real collection (the section below explains how).

These were not chosen yet, so this is the full picture: what each one is, how it would be built here, and where it can go wrong.

### 8.1 Playlists: the same tower, a different rule about *who* you may bring

**The idea.** A playlist is a named rule set for the party, picked in the lobby. The tower, floors, bosses and economy do not
change; what changes is which Pokemon may enter and what the run forbids. Each playlist has its **own boards, its own mastery
track** and a small difficulty bonus, so a Monotype clear is a different achievement from a Standard clear.

| Playlist | Party rule | Run rule | What it asks of the player |
|---|---|---|---|
| **Standard** | any party | none | today's tower |
| **Monotype** | every registered Pokemon shares at least one type | none | build around a type; pairs well with regional doctrines |
| **Level Cap 50** | no registered Pokemon above level 50 | enemy levels follow the cap | strategy over raw levels; opens the tower to lower-level players |
| **Underdog** | no fully evolved Pokemon, no legendaries or mythicals | none | niche picks, NFE specialists |
| **Hardcore** | any party | vendor closed, no items | pure battle skill and team health |
| **Solo Gauntlet** | a party of one to three | enemy count scaled down | tight, high-skill runs; the existing "lone wolf" achievements fit |

**How it fits the code.**
* `RulesetDefinition` already holds the party size, level bounds, and `requires_battle_ready_party`. A playlist adds **party
  clauses**: `same_type`, `max_level`, `max_stage` (evolution stage), `banned_tags` (legendary, mythical, paradox...), `max_party`.
  All are pure checks over a richer `PartyMember` (today it is only an id, a level and a fainted flag, so it gains species, types and
  an evolution stage read from Cobblemon at the registration boundary, where the Cobblemon adapters already live).
* `PartyValidation` already runs at launch and `runs validate`; the new clauses are more `problems` in the same result, with the
  same wording ("Squirtle is not a Fire type").
* Run rules (vendor closed, items off) are existing mechanisms: the vendor refuses outside an intermission today and the ruleset has
  an item budget field that is parsed but enforced nowhere (TDS #47); Hardcore would be its first real consumer.
* A tower declares `playlists: [{id, ruleset, difficulty_bonus}]`; the lobby shows them; boards and mastery are keyed by playlist.

**The trap: mid-run changes.** A registered party is validated at launch, but the party can change during a run (P18 allows moves
after lock-in; the level policy skips changes). A Monotype party could swap in a Fire type at floor 4. Three options:
(1) **re-validate at every floor start** and refuse to open the floor until the party complies, which is simple and cheap;
(2) **freeze the registered party** for the run, which is the safest and closest to how competitive formats work, but changes
today's flexibility for every mode; (3) validate only at launch and accept the leak. Recommendation: **(1) for playlists only**,
leaving Standard exactly as it is.

**Rewards and difficulty.** A playlist's difficulty bonus feeds the difficulty score so the boards stay comparable; rewards are the
same tables, so a harder playlist is paid in records and mastery, not in a second economy.

**Cost.** S to M: no new combat, a pure rule layer, a lobby selector (one more row on the play screen, the same shape as the
Ascension picker), board keys, and a live test per clause. **Risk:** low; the only subtle part is the mid-run rule.

### 8.2 Rental mode: you play *a team the tower gives you*

**The idea.** Instead of your own Pokemon, you take a **rental team** for the run: a curated set of Pokemon with fixed species,
level, moves, ability, nature, item and EVs. Everyone who rents the same team has *exactly* the same power, so boards in this mode
measure play, not box strength. It is the original Battle Tower format, and it is also the front door for a new player with a
weak box, and the natural fairness layer for the Daily Trial.

**Two flavours, in the order I would build them.**
1. **Rental Packs.** A pack is a themed six ("Hyper Offense", "Stall Wall", "Weather Rain", "Doctrine: Momentum"). A player picks a
   pack in the lobby. 8 to 12 packs ship, each a JSON of six sets. Simplest, easiest to balance, and every pack can be tested by a
   bot.
2. **Rental Draft.** Each player is shown 12 candidate sets (seeded, so a Trial offers everyone the same twelve) and picks six, with
   team-mates seeing each other's picks so a four-player team can cover its types. More strategy, more content (a pool of 40 to 60
   sets), more UI.

**How Pokemon are provided: the real problem.** There are three ways to put a rental Pokemon in a player's hands:

| Approach | How | Good | Bad |
|---|---|---|---|
| **A. Real, temporary Pokemon in the party** | create real Cobblemon `Pokemon` from the set, tag them as rentals, move the player's own party aside, restore on exit | battles and the whole Cobblemon UI just work; reuses the P18 party journal (journal, flush, move, restore, crash recovery) | a rental must never leave the tower (no trade, release, drop, PC, evolve, keep XP or items); a crash must clean them up |
| **B. Ghost party** | the battle adapter builds the player's side from the set data without any Pokemon existing in storage | nothing can leak | Cobblemon expects a real party for a battle and its screens; large adapter work in the most fragile area |
| **C. Rented from the vendor** | a vendor service that gives rentals into spare PC slots | familiar | still real Pokemon with all the leak risk, plus needs free slots |

Recommendation: **A**, built on the party journal. The journal already solves the hard part (the player's own Pokemon must come
back after a crash, a disconnect or a hard kill), and a rental is the same operation in reverse: *the journal also lists the
rentals created, and every exit path deletes them*. Rentals are flagged with a persistent tag so a startup sweep can delete any
found outside a live rental run, the same shape as the vendor and cell sweeps. XP, evolution and held-item changes are disabled
for rentals by cancelling the relevant Cobblemon events for tagged Pokemon; commands that move Pokemon are already blocked in the
tower by the command guard (P27).

**What a rental run changes.**
* **Registration** is replaced by the pack or draft pick; party validation checks that the rentals are intact.
* **Level policy:** rentals are level-locked (100, or the playlist's level), so opponent levels are constant and comparable.
* **Boards and mastery:** a separate `rental` playlist key; "Rental Master" achievements; Daily Trial can offer a rental variant
  as an alternative to level-locking.
* **Rewards:** the same tables, so renting is not a way to farm more (and no rental Pokemon, XP or EVs can be earned for your
  own party).

**Authoring cost.** Sets need checking against Cobblemon's species, moves and the Showdown data (the same way the regional rosters
were verified), and a pack should be **play-tested with the existing simulator** (`ascension_sim.js` plays real Showdown battles) so
no pack is a trap or an auto-win. A pack that cannot clear Ascension 0 floors is a bug; one that clears Ascension 20 is also one.

**Risks.** A rental leaking into the real world is the one failure that damages trust; it needs the sweep, the journal and
a hard test (kill the server mid-run and check storage). Authoring balance. Players who dislike not using their own team (so it
must stay a *mode*, never a replacement).

**Cost.** L for Packs (the journal extension, the tagging and events, the sweep, the picker, 8 to 12 packs, a crash test), M more
for Draft. It is also the largest single enabler for **fair** daily and seasonal competition, which is why it may be worth
scheduling ahead of Playlists if boards are where the community lives.

### 8.3 How they fit together

Playlists change who may enter; Rental changes who you *are* when you enter, so Rental is simply a playlist whose party clause is
"the pack". Daily Trials can pick a playlist per day ("Monotype Tuesday", "Rental Friday"), which gives the daily habit its
variety without any new combat. If one has to come first: **Playlists** are the cheap variety win; **Rental** is the fairness and
onboarding win. They do not depend on each other.

### 8.4 Decision needed

Which to schedule, and when: (a) Playlists in P36 as planned; (b) Playlists earlier, as part of P32 so the Daily Trial can rotate
modes; (c) Rental Packs as their own phase before the social layer, because fair boards matter to a competitive community;
(d) neither yet.

## 9. The Rental Draft: a pack-opening experience

Decision (2026-10-04): Rental is built as a **draft**, and the draft is a **pack opening**. Instead of a fixed borrowed team, the
tower hands you packs of Pokemon cards; you open them, and you pick the six who will fight for you. Fixed **Preset Teams** (the
8.2 "Rental Packs") stay as the quick option and as the first-run onboarding path.

### 9.1 The player's experience

1. **Lobby.** Mode: *Rental Draft*. The lobby shows your **Tower Packs** (say three) and their theme (a regional pack leans to that
   region's types and doctrine; a *Wild Pack* is anything). Start.
2. **Opening.** A pack slides onto the screen, glints, and tears open. Five cards fan out face down. Click a card and it shakes,
   then flips; **the better the Pokemon, the longer the shake and the bigger the burst** (particles, rays, a camera shake, a
   rising sound). A rare pull lights the whole screen; a legendary pull stops the room.
3. **Choosing.** With the five revealed, a panel shows the selected card's types, ability, held item, four moves and a one-line
   role ("fast sweeper", "defensive pivot"). You **keep two of the five**; the others crumble. A team-mate can see your picks.
4. **Repeat** for each pack (three packs, keep two each, six Pokemon), then a **Team screen**: your six side by side with a type
   chart bar showing what you cover and what you do not. Confirm, and the run starts with those six in your party.
5. **Skips and comfort.** Space fast-forwards a reveal, *Open All* skips the show, and a **reduced motion** setting removes
   shake and flash. The animation is a treat, never a gate.

*Example.* Maya opens her Tideforge Pack: three commons (a Quagsire, a Floatzel, a Ludicolo), an uncommon (Rotom-Wash), and, after
a long shake and a purple burst, an **epic** Kyogre. She keeps Kyogre and Rotom-Wash. Her next pack gives her a Corviknight and a
Tyranitar to keep, and her third pack fills her last two slots. Her team is a *draft*: strong where she got lucky, and shaped by what
she chose, which is why the same tower plays differently every time.

### 9.2 Rules of the draft (server, pure and testable)

* A **Rental Set** is a data file: species, level, ability, nature, EVs, item, four moves, a role tag, and a **rarity** (common,
  uncommon, rare, epic, legendary) that reflects its power. 60 to 120 sets ship.
* A **draft is three packs of five cards; you keep two from each, six in all** (decided). A pack has a guaranteed shape (three
  common, one uncommon, one rare or better, as CobblemonCards' own packs do) so no pack is empty of choices.
* **A standout is guaranteed:** across the three packs of a draft **at least one card is epic or better** (decided; the draw
  places one if chance did not). Everyone's draft therefore has a moment to look forward to.
* A small chance of a **God Pack** (five epic-or-better cards) is the jackpot. **A Daily or Weekly Trial never rolls one**, so every
  player sees the same packs (decided).
* The draw is **deterministic from the seed** (a Daily Trial offers everyone identical packs), uses the existing `EncounterSeed`
  discipline, never repeats a species inside a team, and softly avoids a team with no type coverage.
* **No power budget** (decided). Fairness comes from the pack shape and the guarantee instead. One light safety rule is proposed,
  not yet agreed: **a team may keep at most two legendary or mythic cards**, so a God Pack cannot field six.
* **The animation reveals, it does not decide.** The server draws and records the packs before anything is shown; the client sends
  only pick indices, which the server validates. A client can neither choose its pulls nor see pulls it was not given.
* Balance is checked with the existing simulator (`validation/showdown/ascension_sim.js` plays real Showdown battles): the budgeted
  teams must clear Ascension 0 floors reliably and fall off around the same depth as a strong player team, so no pack is a trap and
  no pull is an auto-win.

### 9.3 What CobblemonCards gives us (verified against the 1.0.4 jar)

CobblemonCards is public, with source on GitHub (`Howlite-UI/CobblemonCards`; Fabric and NeoForge, Minecraft 1.21.1). Its licence is
permissive: `fabric.mod.json` and the repository say CC0, the Modrinth page says MIT. Either allows reuse; **we keep attribution and
the notice, and confirm the licence of the artwork separately** (card art contains Pokemon artwork, so we render it from the
installed mod at runtime and never ship a copy). There is **no formal API**, but these public pieces exist and are what we would use:

| Piece | What it is | How we use it |
|---|---|---|
| `BoosterPackScreen` | the opening screen: five cards slide in, click to shake and flip, rarity-tuned shake length, flip speed, particles, rays, halo, camera shake and sounds | **the reference to fork** (the source is public); generalise it from five fixed cards to *N*, and from items to our data |
| `CardData` + `ModDataComponents.CARD_DATA` | a record on an item stack: `pokemon_id`, shiny, `rarity`, stat, grade, background, effect | build a *display-only* card per rental set to draw with the mod's own card art |
| frame, background and Pokemon-art textures | `item/cards/frames/<rarity>`, `item/cards/pokemon/{regular,shiny}/<id>` | the card faces |
| `card_procedural_holo` / `card_procedural_bg` shaders and `CardItemRenderer` | the holographic foil | rare and better cards shine |
| `CUSTOM_BOOSTER_DATA` + `OpenBoosterPayload` / `GiveRewardPayload` | a booster stack can carry a custom list of cards that the existing screen opens | **not used**: it is hard-wired to five cards and its close handler gives the player the real cards |
| the rarity vocabulary | common, uncommon, rare, epic, legendary, mythic | our rarity names and colours match, so cards look native |

**Recommended integration: fork the animation, adapt the card faces.**

* **Fork** the opening screen's logic into our own `RentalPackScreen` (client): N cards, our payloads, no item grant on close,
  the same feel (shake by rarity, flip, rays, halo, particles, sounds), plus our **pick phase** and **team screen**.
* Put every card *face* behind one small interface, `CardFace`, with **two implementations**:
  1. **CobblemonCards-backed** when the mod is installed: a display-only `CardData` stack drawn with the mod's frame, art and
     holo shader, so a rental card looks like a real card from the collection;
  2. **fallback** when it is not: a frame we draw ourselves with the Pokemon rendered by Cobblemon's own GUI model support, so
     the feature works on any server and the cards mod stays *optional*.
* **The server never depends on CobblemonCards.** It sends our own payloads (a set id, a rarity name, the revealed details); the
  client decides how to draw them. Compile against the mod only on the client, as `modCompileOnly` from the Modrinth Maven
  (project `9asBGJMf`), exactly how Cobblemon and CobbleRaids are already referenced.
* **Optional crossover** (later): finishing a draft run could award the *real* card of a species you used once per species per
  tower (a cosmetic collectible, bounded), linking the tower to the cards collection the reward tables already feed with booster
  packs. Not required for the draft.

### 9.4 Screens (sketch)

```
 PACK TABLE                          REVEAL                                   PICK (keep 2)
 +------------------------+   +----------------------------------+   +-------------------------------+
 |  Pack 1 of 3  Tideforge|   |   [?]   [?]   [?]   [?]   [?]    |   | [Quagsire][Floatzel][Ludicolo]|
 |        ___             |   |    cards fan in; click to flip   |   | [Rotom-W ][ KYOGRE *epic*   ] |
 |       /   \  <- rip    |   |    shake length grows with rarity|   |  Kyogre  Water  Drizzle       |
 |      | pack |           |   |    sparks, rays, camera shake    |   |  Origin Pulse / Ice Beam ...  |
 |       \___/            |   |                                  |   |  role: weather abuser          |
 |  [ Open ]  [ Open all ]|   |  [ Skip ]        reduced motion  |   |  [Keep] [Keep]    2 of 2 chosen|
 +------------------------+   +----------------------------------+   +-------------------------------+
```

### 9.5 How it is built, in order

1. **Rental sets, the draw and the draft state** (server, pure): `RentalSetDefinition` JSON, a loader like the achievements one,
   `RentalPackDraw` (shape, God Pack, budget, no repeats), a draft state machine. Unit tests for determinism, budget, no
   repeats and the pack shape. A node check that every set loads in the real Showdown with legal moves.
2. **Rentals as real, temporary Pokemon through the party journal** (server): approach A of section 8.2. Journal them, tag them,
   forbid every way out (trade, release, drop, PC, evolve, experience, item changes), delete them on every exit, sweep any found
   outside a live rental run at startup. **Its own crash test** (kill the server mid-run and mid-draft; check storage) before
   anything else is built on it.
3. **The Rental playlist and registration**: lobby mode, the draft replaces registration, level lock, a separate board.
4. **The client screens**: `RentalPackScreen` (pack table, reveal, pick, team) with the fallback faces first, then the
   CobblemonCards faces, then the skip and reduced-motion settings.
5. **Rental in the Daily Trial** (same packs for everyone) and a mixed team option: a **shared table** where a team sees three
   common packs and picks in turns, a snake draft, for the group-play moment.

### 9.6 What can be proven headless, and what cannot

Headless bots can prove 1, 2, 3 and 5 completely: the draw, the budget, determinism, the journal, the leak sweep and the crash test.
**Nothing about the look, the timing or the feel of the animation can be proven that way**; it needs a real client, which is the same
verification pass already recommended before P32. Budget real-client time for the opening screen specifically.

### 9.7 Risks

* **A rental escaping into a player's real collection** is the failure that costs trust; the journal, the tag, the exit paths, the
  sweep and the crash test exist for it, and it is built and tested before the screens.
* **Licence and art.** Reuse the code with attribution; never copy the card textures; draw them from the installed mod.
* **Balance.** The budget and the simulator, plus a tuning pass on real play.
* **Opening fatigue.** Three packs must feel good in under a minute; fast-forward and Open All are requirements, not extras.
* **A required mod.** It must not be: the fallback faces keep the feature working without CobblemonCards.

### 9.8 Decided, and still open

Decided (2026-10-04): three packs, keep two from each (six); **no power budget**, a guaranteed epic-or-better card across the
three packs; a God Pack jackpot that Trials never roll; rarity names and colours match CobblemonCards.

Still open:

1. **The legendary cap:** keep a rule of at most two legendary or mythic cards per team (proposed), or none.
2. **Crossover with the cards mod.** The options, so the choice is clear:
   * **(a) Real CobblemonCards cards.** After a run the tower would give actual card items (the species you used) from the
     CobblemonCards mod. It ties the tower into the cards collection, binders and trading, but it needs a compile dependency on
     the mod's item and component classes (not only an item id), it feeds *their* economy (duplicates of rare cards, trading), and it
     can be farmed. The tower already grants CobblemonCards **booster packs** by item id through the reward tables; that stays.
   * **(b) A tower-owned collection.** The tower records which Pokemon you have drafted or used, per tower, in its own store, and
     shows them in mastery and the Codex. Cosmetic, no items, no dependency, nothing to farm.
   * **(c) Nothing.** Keep the two economies separate.
   Proposal: **(b) now**, leaving (a) as a possible later crossover through the booster packs the tower already awards. Not a
   blocker for the draft either way.
