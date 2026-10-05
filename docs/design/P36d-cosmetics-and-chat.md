# P36d: cosmetics, titles, banners and the chat tag (BUILT 2026-10-05; see "As built" at the end)

Part of `P36-seasons-plan.md`. Roadmap C2.

## Decided by the owner (2026-10-04)

| Question | Answer |
|---|---|
| How a title or club tag shows in chat and the tab list | **Both**: one small mixin puts the club tag and title in front of the display name (no other mod needed), **and** Placeholder API placeholders are registered when that mod is present so a chat formatter can place them itself. |
| Console commands when a cosmetic is earned | **Yes, off by default**: a config file lists commands (for example a LuckPerms permission set) so LuckPerms and StellarRankUp (which can gate a rank on a permission) can turn a cosmetic into a rank, prefix or popup. Does nothing until commands are added. |
| The three season banners (steps 6, 12, 18) | **Both**: a real Minecraft banner item (vanilla patterns, the season's colours) **and** a recorded banner cosmetic. |
| What a player can wear | **Title only**: only the title is selectable and displayed; banners and badges are collectables viewed in a list. |

One conflict resolved: "Both" for banners said "a selectable banner shown on boards and reports", while "Title only" said banners are not selectable.
I followed the later, more specific answer: banners are items plus listed collectables, and **not** selectable or shown on boards. Making a banner
selectable later is a small addition (one more selected slot and one more column on the run report).

## What the Stellar jar turned out to be

`StellarRankup-1.3.1.jar` (MIT) is a **rank ladder** (tracks of ranks gated by Pokedex %, playtime, species, money, or a **permission**) that runs reward
**commands**, pays money, shows an on-screen title popup, syncs with LuckPerms and provides Placeholder API placeholders. It has **no API** to store a
persistent cosmetic title, so "titles through Stellar Titles" cannot be a direct call. The bridge is a **permission**: CobbleTowers runs the
configured command when a cosmetic is earned (for example `lp user {player} permission set cobbletowers.cosmetic.{id} true`) and an admin gates a
StellarRankUp rank on that permission. The CobbleRaids Stellar Titles addon jar was not available, so nothing here assumes how it works.

## 1. Cosmetics

An earned cosmetic is a name already recorded by the season track and the club podium:
`s<N>:title_challenger`, `s<N>:title_champion`, `s<N>:badge`, `s<N>:banner_1..3`, `s<N>:club_gold|silver|bronze`.
`season/Cosmetics` (pure) turns one into words: its kind, its season, a full name ("Champion of The Rising Tide", "The Rising Tide Banner II",
"Club champion of The Rising Tide") and a short chat form ("Champion S1"). Titles come from the track steps 24 and 30; mastery rank titles are not part
of this phase.

## 2. Wearing a title

`/tower cosmetics` lists everything a player owns, grouped (titles, banners, badges, club marks), with the worn title marked.
`/tower title` lists owned titles with numbers; `/tower title <n>` wears one; `/tower title off` takes it off. The worn title is stored in
`TowerSeasonProgressStore` (a selected id, valid only while the player still owns it).

## 3. The chat tag (two parts)

* **Mixin.** One new mixin on the server player's display name (and a second on its tab-list name) returns the name with the decoration in front:
  the worn title in short form, then the club tag in the club's banner colour: `Champion S1 [TC] Ash`. A player with neither is unchanged. Because chat,
  death messages, `/msg` and the tab list all build the sender's name from these, one change reaches every place. A change (a new title, joining or leaving a
  club) refreshes the tab list entry at once. Off switch: `config/cobbletowers-cosmetics.json` `"chat_tags": false`.
* **Placeholders.** If Placeholder API is installed, CobbleTowers registers `%cobbletowers:title%`, `%cobbletowers:club_tag%`, `%cobbletowers:club_name%`
  and `%cobbletowers:prefix%` so a server's own chat or tab formatter can place them. Absent, nothing happens and nothing breaks.

## 4. Earn commands (off by default)

`config/cobbletowers-cosmetics.json` has `"on_earn_commands": []`. Each entry is a console command with `{player}`, `{uuid}`, `{id}` (the cosmetic name),
`{kind}` and `{season}` filled in. A command runs only for a cosmetic that was **newly** added (granting it twice runs nothing), on the server thread, as
the console. A failing command is logged and never blocks the grant.

## 5. Banner items

The track JSON learns two optional fields on a grant, `components` and `label`, with the tokens `{season}`, `{season_name}` and `{color}` ({color} is the
spotlight region's dye: Tideforge blue, Rootvale green, Duskvale purple). Steps 6, 12 and 18 grant a real banner of that colour with vanilla patterns
(I: a bottom stripe; II: adds a border; III: adds a circle and a flower) and a custom name ("The Rising Tide Banner I"). The same steps keep their
`banner_N` cosmetics.

## 6. Tests

Unit: the cosmetic names and short forms, title ownership and selection (including a selection that stops being valid), the decoration text and the club
colour mapping, the command templating and "only newly earned", the banner token substitution. Live: a bot reaches the steps and the banners arrive as real
items with their name and patterns; `/tower title`, the chat line in the server log carries the decoration, the tab-list name is decorated (an operator
read-out of the two names), the earn command fires once, a real client's chat shows the tag (picture), and with Placeholder API in the rig the
placeholders resolve (`/placeholder`).

## 7. Not in this phase

Selectable banners, mastery rank titles, a physical Hall (P36e), watch (P36e), integration beyond the permission bridge.


---

## As built (2026-10-05)

Built as designed. What exists:

* `season/Cosmetics` (pure): parsing `s<N>:<name>`, full names, short chat forms, title ordering and selection validity, the decoration segments and the
  banner-colour-to-chat-colour map, and the `{token}` expander. `season/CosmeticsService` is the one door every earned cosmetic goes through
  (`award`): recorded, only newly earned ones do anything else, the first title earned is worn automatically, the configured console commands
  run, the tab list is refreshed. The season track and the club podium both call it.
* **Wearing a title**: `/tower cosmetics` (everything owned, grouped, the worn title marked) and `/tower title [n|off]`; the choice is stored in
  `TowerSeasonProgressStore` and is honoured only while the player still owns it.
* **The chat tag**: `mixin/PlayerDisplayNameMixin` (on `Player.getDisplayName`) and `mixin/ServerPlayerTabNameMixin` (on
  `ServerPlayer.getTabListDisplayName`) call `season/ChatTags`, which puts the worn title (gold, short form such as `Champion S1`) and then the
  club tag in the club's banner colour in front of the name. A change refreshes the tab list at once. `"chat_tags": false` in
  `config/cobbletowers-cosmetics.json` switches it off.
* **Placeholder API**: `season/PlaceholderBridge` registers `%cobbletowers:title%`, `%cobbletowers:club_tag%`, `%cobbletowers:club_name%` and
  `%cobbletowers:prefix%`. It is the only class that touches Placeholder API and `ChatIntegration` loads it only when the mod is installed
  (compile-only dependency from the Modrinth maven the build already uses). CobbleRaids already depends on Placeholder API, so a server with it
  very likely has it.
* **Earn commands** (off by default): `on_earn_commands` in the same config, console commands with `{player}`, `{uuid}`, `{id}` (colon written as
  `_`), `{kind}` and `{season}`, run once for each *newly* earned cosmetic; a failing command is logged and never blocks the award. This is the
  bridge to LuckPerms and StellarRankUp (a rank can require a permission): for example
  `lp user {player} permission set cobbletowers.cosmetic.{id} true`. StellarRankUp 1.3.1 itself has no API for persistent titles.
* **Banners**: the track grant gained `components` and `label` (the item as the game's own item tag text, with `{season}`, `{season_name}` and
  `{color}` tokens, the colour following the season's spotlight region). Steps 6, 12 and 18 grant a real banner (a stripe; plus a border; plus a
  roundel and a flower) named "<season> Banner I/II/III", and the step-30 templates now have a proper name in the reward screen.
* Operator tools: `/cobbletowers cosmeticsadmin grant <player> <id>`, `names <player>` (the decorated display and tab names) and
  `parse <player> <text>` (resolves a placeholder through Placeholder API's own parser).

**Owner decisions followed**: both a mixin and placeholders; earn commands, off by default; banners as real items (and listed collectables);
title-only wearing. The conflicting "selectable banner" answer was resolved in favour of the more specific one.

**Verified**: unit `CosmeticsTest` (names, ordering, selection, decoration and colours, templating, the store, and every shipped banner parsed by the
game's own NBT parser for seasons 1 to 3) and `ci_local.sh` clean (including the architecture and API-boundary checks). Live `cosmetics_test.py`
24/24 with Placeholder API installed in the rig and 20/20 without: real banner items with their patterns and names, the first title worn at once,
`/tower title` changing it, display and tab names decorated, the chat line in the server log, each earn command run once per new cosmetic (a repeat
award runs nothing), leaving the club removes the tag, and the placeholders resolving through Placeholder API. **Real client**
`validation/client_chat.py` 6/6: the chat line shows "Champion S1" in gold and the club tag, and the inventory shows the three banners with
their patterns.

**Not proven or not built**: the tab list was verified as the name the server hands out (`names`), not by a picture of the tab overlay; a server
with another chat plugin that formats names itself may show the tag twice or not at all (the switch and the placeholders exist for that);
mastery rank titles are not part of this; the banner and badge cosmetics are collectables only; the physical Hall and watch are P36e.
