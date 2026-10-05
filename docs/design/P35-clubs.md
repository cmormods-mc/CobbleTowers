# P35 (part 3): Clubs

Roadmap D2, regional towers only (Tideforge, Rootvale, Duskvale): Neutral and the Test tower never count.

## What a player sees
`/tower club` shows your club (tag, name, banner, score, members and their bests, the weekly goal). A club is a named crew of up
to twelve with a 2-4 character tag and a banner colour (the sixteen dye names).
* `create <name> [tag]`, `invite <player>`, `accept <club>` (an invite lasts five minutes), `leave`, `disband`, `kick <name>`,
  `banner <colour>`, `top` (the club board), `claim`.
* **Score** is the sum of each member's best regional cycle clear (the difficulty score the Difficulty board ranks). A player's best
  is kept whether or not they are in a club, so joining brings it along.
* **Weekly goal**: twelve regional cycle clears by members in an ISO week. When the clear that reaches it lands, the club is told;
  each member may then `claim` 300 CobbleDollars, once, that week. The week rolls over lazily on the next look.
* Only the owner invites, kicks, disbands or sets the banner. If the owner leaves, the longest-standing member takes over; the last
  member out ends the club.

## How it works
`club/ClubBook` (pure: every rule above), `persistence/TowerClubStore` (SavedData over a book, plain strings and ids),
`club/ClubService` (messages, invites in memory, the hook), `command/ClubCommand`. `MasteryService.cycleCleared` calls
`ClubService.onCycleCleared` with the players and the difficulty score; it ignores a tower with no regional theme.

## Not built (deliberately)
* **Seasons**: the roadmap's board is "this season". There are no seasons yet (P36), so the score is all-time and the weekly goal is
  the only thing that resets. P36 should reset or segment it.
* **Chat tag**: the tag is stored and shown in `/tower club` and the board, but not put in front of chat messages (that is a chat
  decoration hook of its own).
* **Banner as an item or in-world banner**: the banner is a colour setting shown in text.
* Invites survive only until a restart (in memory); a club itself is persisted.
* A club cap per server, renaming, and ownership transfer on request.

## Tests
Unit `ClubBookTest` (7): naming and validation, the twelve-member cap and ownership passing, owner-only actions, scoring and bests,
board order, the weekly goal and claims across a week, and the store round trip. Live `validation/smoke/club_test.py` 15/15 with two
bots: found, duplicate name, bad name, invite-only joining, owner-only kick and banner, a full ten-floor regional cycle raising the
score and the week count, the board, the claim refused before and allowed once after the goal, leave and disband.
