# P22: the first scouting profile

Written before the content it describes (a profile is data, so there was no code to write first), as the TDS
gate requires.

P12 built the scouting mechanism (`ScoutingProfileDefinition`, `ScoutingReveal`, the scouting screen and the Keen
Eye modifier that pushes concealment later) and shipped it against **zero** profiles, so every tower revealed
everything and the mechanism, Keen Eye included, did nothing. P21's content pass asked what the regions should
hide.

## Decision (user, 2026-10-03)

**The regions conceal late floors; Neutral reveals everything.** Typing and field conditions are hidden from
floor 7 and threat level from floor 9, so the top floors have real surprises and Keen Eye starts to matter.

## What ships

`scouting_profiles/regional.json`, referenced by `tideforge`, `rootvale` and `duskvale` through their
`scouting_profile` field. Neutral names none, which is TDS #49's baseline: everything reveals naturally.

| Category | Hidden from floor | Why |
|---|---|---|
| `typing` | 7 | The first thing a player plans a switch around; hiding it is the biggest change. |
| `field_conditions` | 7 | Weather and terrain modifiers, hidden alongside typing. |
| `threat_level` | 9 | The last thing to go: the top two floors still tell you roughly how hard a fight is. |

One profile for all three regions, not one each: the user asked for the same concealment everywhere, and a profile
per region can be split out later with no code change if a region wants its own.

## Keen Eye

`scouting_bonus` is added to each category's concealment floor, so one Keen Eye (+2) moves typing from floor 7 to
floor 9, and the +2 stack limit the modifier already has moves it to floor 11, past the end of a ten-floor
tower. That is the design: drafting Keen Eye twice buys back all the information, so the modifier is a real
choice rather than a number that changes nothing.

## Tests

`ShippedScoutingTest` reads the real files: the profile names exactly the three categories the reveal screen
understands; typing and field conditions are visible through floor 6 and hidden from 7; threat level is visible
through 8 and hidden from 9; Keen Eye shifts all of it two floors; each regional tower references the profile
and Neutral does not. `regional_content_test.py` (9/9) re-run live so the three towers still load and start
floors with a profile attached.

Not proven: the screen itself showing a hidden category (a headless bot cannot open one, and the server only
sends the reveal to a client that registered the channel), as before.
