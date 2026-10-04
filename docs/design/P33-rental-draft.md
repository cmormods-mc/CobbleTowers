# P33: The Rental Draft

Status: **built** (design written before the code; see "As built" at the end). The product decisions are in `engagement-roadmap.md` section 9; this is the
build plan and the technical decisions behind it.

## Decided (owner, 2026-10-04)

Three packs of five cards; keep **two from each pack, six in all**. **No power budget**, but across the three packs **at least one
epic-or-better** card is guaranteed. A small **God Pack** jackpot that **Trials never roll**. Rarity names and colours match
CobblemonCards (common, uncommon, rare, epic, legendary, mythic). Playlists shipped in P32; Rental comes next, before Relics.

## What a rental run is

A rental run is an ordinary run whose party is **six Pokemon the tower lends**, drafted from packs in the lobby. The player's own
Pokemon are moved aside to their PC by the same journal machinery P18 built, the six rentals appear in the party, and every exit
path (finish, leave, wipe, crash, disconnect, hard kill) deletes the rentals and puts the originals back. Levels are fixed by the
set, so enemy levels are comparable, and rentals have a separate **Rental** board and mastery key.

Rental is a **playlist** with `"rental": true`: its "party rule" is "the draft".

## Stages

Built and tested in this order, because a rental that escapes into a real collection is the one failure that costs trust.

1. **Sets, the draw and the draft (pure).** `RentalSetDefinition` JSON (species, level, ability, nature, EVs, IVs, item, four
   moves, role, rarity), about 44 shipped. `RentalDraw` builds the three packs from a seed: each pack is three common, one uncommon
   and one rare-or-better; if chance gave no epic-or-better across the three, one card is upgraded; a God Pack (five epic-or-better)
   has a small chance and is never rolled for a trial; no species repeats in a player's offer. `RentalDraft` is the state machine:
   a pick is two distinct cards of the current pack, and a team may keep at most two legendary or mythic (the proposed cap). A node
   check validates every set against the real Showdown data (legal species, ability, moves), and a live check creates every one in
   Cobblemon.
2. **Rentals as real, temporary Pokemon (server).** The party journal entry gains the **rental ids**, written and flushed before
   anything moves. Order: journal, flush, move the player's own party to the PC (refused with a reason when the PC has no room),
   create the six rentals with the pre-generated ids and fill the party. Restore deletes any rental found by id, then restores the
   originals, idempotently. Rentals carry a persistent tag, are untradeable, gain no experience, and a **stray sweep** (on login and
   once a minute) deletes any tagged Pokemon that is not part of a live rental run. **A live crash test comes before any screen**:
   kill the server mid-draft, mid-lock and mid-run, restart, and check the collection.
3. **The Rental playlist and registration.** `/tower playlist rental`; each team member drafts (chat flow first:
   `/tower draft`, `/tower draft pick <a> <b>`); the host cannot start until every member has finished; launch locks rentals instead of
   registering the live party. A rental run's party validation checks the rentals, not the player's box.
4. **The pack-opening screen (client).** `RentalPackScreen`: pack table, reveal (shake by rarity, flip, particles, camera shake,
   skip and reduced motion), pick, team. The animation logic is forked from CobblemonCards' `BoosterPackScreen` (public source,
   CC0/MIT, attribution kept) and generalised from five fixed cards to N, with no item grant. Every card face is drawn through a
   `CardFace` interface with a fallback implementation (our own frame and Cobblemon's Pokemon rendering) and, when CobblemonCards is
   installed, an implementation using its frame, art and holographic shader. The server never depends on that mod.
5. **Rental in the Daily Trial.** A trial pool entry may name the Rental playlist; the packs are drawn from the trial seed (everyone
   sees the same twelve picks), with no God Pack.

## Technical decisions

* **Approach A** of the roadmap (real, temporary Pokemon through the journal), not a ghost party: Cobblemon battles and screens
  expect a real party, and the journal already solves "the player's own Pokemon must come back".
* **Creation** uses Cobblemon's own `PokemonProperties` (species, level, nature, ability, moves, held item, IVs, EVs, `tradeable =
  false`), so a set means exactly what Cobblemon understands. Rental ids are generated when the draft finishes and recorded in the
  run's participant registration, so `RunFactory` and party validation need no special case.
* **Leak containment, layered:** the journal (normal exits), the persistent tag and the stray sweep (anything else), the existing
  command guard (no trade, PC or release inside the tower), experience cancelled, untradeable. Any one failing is covered by the next.
* **Drafts are in memory**, like the lobby: a restart costs the draft, never a Pokemon, because nothing exists until launch.
* **Fairness:** rentals are fixed-level, fixed-moves and fixed-EVs; the pack shape and the guarantee shape variance; nothing is
  budgeted. A simulation pass (`ascension_sim.js` with rental teams) checks no pack is a trap and no draw is an auto-win.

## Not in this phase

The shared-table (snake) team draft, rental presets, the optional real-card crossover, per-set mastery, and any change to Standard
runs.

## Test plan

Unit: the draw (shape, guarantee, God Pack odds and exclusion, no repeats, determinism), the draft rules (two per pack, the
legendary cap), set parsing, the journal entry round-trip with rentals. Node: every set against Showdown's data. Live: every set
builds as a real Pokemon; a rental run's party is the six and the originals return after finish, after leave and after a disconnect;
a stray tagged Pokemon is swept; a **hard kill** mid-run restores the collection on restart; a daily trial offers everyone the same
packs. The screen is not seen in a real client.

## As built

Everything below is built and verified, including the pack screen in a real client (see Verification).

**Stage 1, sets, draw, draft (pure).** 44 sets in `data/cobbletowers/cobbletowers/rental_sets/` (16 common, 8 uncommon, 10 rare, 6
epic, 4 legendary; one per species, all level 50, 31 IVs, a held item, four moves). `RentalSetDefinition` validates on load (EVs
<= 252 each and <= 510, IVs 0-31, one to four moves), `RentalDraw` deals the packs from a seed (three common, one uncommon, one
rare-or-better; an epic-or-better is guaranteed somewhere across the three; a 3% God Pack that a trial never rolls; no species twice;
a pool too small to avoid repeats still deals), `RentalDraft` is the state machine (two cards from the current pack, never the same
card twice, at most two legendary or mythic on a team, restart, ids minted at the end). 24 unit tests in `RentalDraftTest` and
`PackRevealTest`, including 5,000 seeds for the epic guarantee and 20,000 for the God Pack odds (between 2% and 4%).
`validation/showdown/rental_sets_check.js` checks every set against Showdown's data (species, ability the species can have, every
move legal for it or a pre-evolution) and the Cobblemon jar (species file, held-item model): 44/44.

**Stage 2, rentals as real, temporary Pokemon.** `PartyJournalEntry` gained `rentals` (ids), written and flushed with the moved
originals before anything moves; an old journal without the field still loads. `RentalPartyService.lock` order: journal, flush, move
the player's own party to the boxes (`canMakeRoom` refuses with a reason first), create the six from their sets with the pre-minted
ids, fill the party; any failure restores. `PartyJournalService.restoreNow` deletes the rentals by id first, then restores the
originals, so every existing exit (finish, leave, wipe, disconnect, crash, recovery) covers rentals with no new path. Every rental
carries a persistent tag (`cobbletowers_rental`), is untradeable, and a `ExperienceGainedEvent.Pre` guard zeroes any experience. A
stray sweep (a minute after login and once a minute) deletes any tagged Pokemon that belongs to no live rental run. Operator probes:
`/cobbletowers play rentals <player> [stray|xp]`.

**Stage 3, the playlist and the lobby.** `playlists/rental.json` with `"rental": true` (difficulty bonus 5). `/tower playlist rental`,
`/tower draft` (opens the pack screen on a client that has it, chat otherwise), `/tower draft text|pick <a> <b>|restart`. The host
cannot start until every member has finished ("has not finished their draft"); the run registers the six rentals and the player's own
Pokemon are never judged. Drafts live in memory per lobby and are dropped when the lobby changes tower, mode or trial, a member
leaves, or it dissolves.

**Stage 4, the pack screen.** `RentalDraftPayload` (the three packs with display data, kept indices, current pack, message) and
`RentalDraftActionPayload` (OPEN, PICK a b, RESTART); the server checks every pick, the client decides nothing. `PackReveal` (common,
tested) holds the timing and feel: tear, flip order lowest rarity first so the best card is last, shake from epic up, sparkles from
rare up, the CobblemonCards rarity colours (mythic cycles hue). `RentalPackScreen` (client): sealed pack that shakes and tears,
five-card reveal, choose two, three packs, team summary; Space or a click skips, an Animations toggle turns it off, and a render fault
closes the screen with a note pointing at `/tower draft text`. Cards are drawn through `CardFace`; the built-in `FallbackCardFace` is
text only (rarity frame, name, level, role, ability, item, nature, four moves, translated through Cobblemon's own language keys).
A "Draft your team" button appears on the play screen in rental mode. **Not built:** a Pokemon model on the card, and any
CobblemonCards crossover (the seam is `CardFace`); neither was needed to make the draft work.

**Stage 5, trials.** One Daily (Draft Night, neutral tower) and one Weekly (Draft Marathon, Tideforge) entry use the Rental playlist.
A trial's draft is drawn from the trial seed, so everyone is offered the same twelve picks, and no God Pack is rolled.

### Verification

**A rental must never leak into a real collection.** That was the design's one hard requirement, and testing it found one the design had
missed: Cobblemon marks anything that enters a party as OWNED in the player's Pokedex, so six lent species were becoming permanent
Pokedex entries (servers hang ranks and rewards on Pokedex progress). `RentalPartyService` now cancels `POKEDEX_DATA_CHANGED_PRE` for a
tagged Pokemon; `smoke/rental_dex_test.py` proves it, and a negative control (guard off) shows six species OWNED and still there after the run.

Unit: 24 new tests; the whole suite and `ci_local.sh` pass. Live, `validation/smoke/rental_test.py` (a bot on a real server,
including two hard kills): the draft rules; a rental run's party is exactly six RENTAL Pokemon built exactly as their sets say
(level, nature, ability, item, EVs, moves, untradeable) with the player's own eight untouched; a rental earns no experience; the
rentals fight a real floor; ending the run deletes them and puts every original back in its exact slot; a kill after Cobblemon
saved the rentals, and a kill before it, both end with no rental and every Pokemon where it started; a stray rental is swept.

**Real client.** `validation/client_screens.py` launches a real production-form Fabric client (the dev client cannot run Cobblemon: Loom does
not remap Kotlin metadata) and saves pictures of every screen with sample data at the smallest GUI size a player can have (426x240); that found
real defects in the pack screen and in the older screens, all fixed (see the memory note on client verification for the list: a double
background blur, Play/Registration/Intermission/Vendor/Mastery layouts that overflowed or overlapped at 240 px, clipped text). `validation/client_e2e.py` then
puts a real client on the real server: it joins, types `/tower` commands, opens the pack screen from a real payload, clicks the pack and two
cards three times, starts the run (the first pictures of a drafted Machamp leading a real battle), checks the six rentals over RCON, opens
the intermission and vendor screens from real payloads, and ends the run. 7/7 then, and the armor tooltips of P25 were seen for the first time.

### Balance (simulation)

`validation/showdown/rental_sim.js` plays real Showdown battles with the real `tower-fx.js` (the enemy gets over-the-cap EVs as
a stand-in for floor depth): 1,500 sampled drafts against six level-50 opponents with +600 EVs. It found real problems and the sets
were tuned from it: the commons were a trap tier (20-30% against a 41% mean, nobody would pick them), Roserade, Tangrowth and
Floatzel in particular; Kingambit was a runaway (83%); Dragapult and Rayquaza under-delivered. After tuning the commons sit at 30-50%,
uncommons 46-67%, rares 49-70%, epics 61-71%, legendaries 50-83% (small samples, +-10), a team that always keeps the rarest cards wins
about 66% against 25% for the least rare, and nothing is below 30% or above 75% (Kingambit, still the strongest epic at about 71%). The
greedy player AI under-rates support sets, so the numbers rank sets relative to each other and say nothing about a real run.

### Left open

The 2-legendary cap is built as proposed; the CobblemonCards crossover (real cards, a tower-owned collection, or none) is still
undecided and is **none** for now; a Pokemon model on the card can now be tuned with the screenshot harness but is not built; how the
animation feels at a real frame rate, and the sound, need a person.
