# P19: paying for a teammate at the vendor

Written alongside the code rather than before it (the scope was small and fully specified by the user's
choice of "teammate vendor purchases" as the next item; recorded here so the gate is not skipped silently).

TDS #18: a player "may pay for recovery targeted at teammates". P12 built the server side -- the purchase
payload and `VendorPurchaseService.purchase` already take a payer and a *separate* target and re-check that
the target is in the run and online -- but left the vendor screen buying only for the buyer ("a teammate
picker is a client-side enhancement the protocol does not need to change for"). That is the gap. The only
live check, `vendor_test.py`, always bought for the same player, so the teammate path had never run.

## Decisions

| Question | Choice | Why |
|---|---|---|
| Who appears in the picker? | Everyone still in the run, the buyer first; an offline teammate is shown but disabled | `isInRun()` is the rule the server enforces, and showing a greyed-out name explains why they cannot be chosen. |
| Default target | The buyer | What the screen did before, so nothing changes for a solo player. |
| Does the choice persist? | Yes, across the catalog refresh after each purchase, unless that teammate has left the run | Buying two services for one teammate should not need re-selecting. |
| What does a refused purchase say? | A message on the screen: not enough CobbleDollars, sold out, teammate offline or gone, vendor closed | Before this, a refused purchase silently did nothing; the player had to infer why from a number. Every `Result` has a sentence, enforced by a unit test. |
| Is the teammate told? | Yes, a chat line naming who paid, since their party just changed | They did not click anything. |
| Who pays | Always the buyer; the teammate's wallet is untouched | TDS #18 says the payer is the buyer. |

## What changed

- `VendorCatalogPayload` gains the team (id, name, online) and a message.
- `TowerNetworking.sendVendorCatalog` builds the team and takes an optional message; the purchase handler
  turns the `Result` into one via `VendorPurchaseService.describe`.
- `VendorScreen` gets the teammate row and the message line.
- The server-side purchase rules are unchanged.

## Tests

- `VendorPurchaseMessageTest` (3): every `Result` has a message; a teammate purchase names them; the
  refusals that concern a teammate name them.
- Live `teammate_vendor_test.py` (9/9): two bots start a run through the lobby and reach an intermission;
  A pays for B (A debited, B not, counted against the run, the log names both); a player outside the run
  cannot be bought for and nothing is charged; an offline teammate cannot be targeted.
- `vendor_test.py` 11/11 re-run clean.

Not proven: the picker on screen (a headless bot cannot open one); `TARGET_OFFLINE` live (the operator
command refuses an offline selector before the service runs, so only a modified client can reach it);
that the heal actually lands on the teammate's party (the effect code is unchanged and is `vendor_test.py`'s
ground, but no live check damages a teammate's Pokemon).
