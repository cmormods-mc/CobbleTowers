# P28: The Physical Vendor

Status: built and verified on a live server (`vendor_npc_test.py` 12/12). Written alongside the code, not before: small scope.

## What it is

P12 built the vendor's function (buy a service for CobbleDollars) and deliberately left out "a mob standing in a
room". This is the mob. During a run's intermission a villager stands in the cell; a run participant right-clicks it
and gets the existing `VendorScreen` (the same path as `/cobbletowers runs vendor` and the intermission screen's
Vendor button, `IntermissionService.vendor`). Nothing about prices, services, teammate purchases or the
INTERMISSION-only rule changed.

## Decisions (user, 2026-10-04)

| Question | Choice |
|---|---|
| Where | In each run's intermission: spawned in the run's leased cell, removed when the intermission ends. No lobby/overworld vendor. |
| What | A server-only vanilla villager (cleric), no AI, invulnerable, silent, named "Tower Vendor". No custom entity type, no renderer, no client code. |
| Who | Any participant of that run. Another team, or someone in no run, is refused with a sentence. |

## How it works

* `vendor.VendorNpcRules` (pure, 7 unit tests): the scoreboard tags a vendor carries (`cobbletowers_vendor` and
  `cobbletowers_vendor_run:<uuid>`), the access rule (`ALLOWED` / `NOT_IN_THIS_RUN` / `CLOSED`) and the yaw that makes it
  face the party's entry anchor.
* `vendor.VendorNpc`: `spawn`, `despawn`, the right-click handler (Fabric `UseEntityCallback`, consumed on both sides so
  the vanilla trading screen never opens) and an `ENTITY_LOAD` guard.
* Placement: the floor's **`exit` anchor** (already declared, validated solid with headroom, and described as "where the
  way to the next floor is"). No new layout field, so no floor file changed. It faces the `entry` anchor. Use is
  limited by vanilla's 3-block entity reach, so players walk to it.
* Lifecycle, all through the transition table rather than a new loop:
  * spawned from `IntermissionService.onArrival` (every road into an intermission already goes through it);
  * discarded in `RunTransitionService.apply` the moment a move leaves INTERMISSION (next floor, cash-out, wipe, abandon);
  * discarded again in `releaseCell` before the cell is verified, because a leftover mob would quarantine the cell;
  * a vendor that *loads* with no live intermission run behind it (a crash left it in a saved chunk) is discarded.
* `spawn` is idempotent (a run never has two), and looks only inside the run's own cell.

## Verified

`validation/smoke/vendor_npc_test.py` (real server, real right-clicks from headless bots via `vendorbot.js`): no vendor
during a floor; exactly one at the intermission; NoAI, Invulnerable and the run tag are set; a participant's click is
allowed and an outsider's is refused (read from the server log); the vendor is gone when floor 2 opens; floor 2's
intermission gets its own single vendor; a cash-out removes it; no cell was reported unfit for reuse. Unit tests and
the full build pass.

## Not proven / not done

* The screen that the click opens is not seen (headless bots cannot receive the payload); the click is proven server-side.
* Not seen in a real client: how the villager looks standing in each tower, or whether the exit anchor is a good spot in
  every hall. Content, not mechanism: if a hall wants a better spot, add an optional `vendor` anchor to the layout then.
* Crash cleanup (the `ENTITY_LOAD` guard) is unit-obvious but not live-tested (needs a hard kill during an intermission).
* The villager's biome/outfit is the default; one per region would be cosmetic content.
* Under server lag the entity appears a moment after the state changes; the test polls for it.
