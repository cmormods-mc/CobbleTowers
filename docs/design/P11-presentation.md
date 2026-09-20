# P11: giving a player something to look at

Written before the code it describes, as the TDS gate requires.

Three pieces of presentation have been deliberately left blank so far. TDS #67's jersey numbers were
reserved whole out of P10, with nothing yet to paint them on. TDS #25's spectator information panel has
had a state machine and a physical destination since P7 -- `TowerEncounters.knockOut` already flips a
losing player to `KNOCKED_OUT` and teleports them to their floor's spectator anchor -- but nothing is
ever rendered once they arrive; they just stand there. And P9's reward grant is delivered as one line of
chat (`RewardDelivery.deliver`), which its own design doc named as a deliberate stopgap: "No reward GUI.
P11 owns presentation... the same fallback CobbleRaids uses when its own GUI backend is unavailable."

All three currently render as chat text or nothing at all, for the same underlying reason: before this
phase, CobbleTowers has never shipped a line of client-side code. `fabric.mod.json` declares one
`main` entrypoint and no `client` entrypoint; there is no networking channel anywhere in `src/main`.
P11 is what changes that -- deliberately, not as a side effect of building one screen.

## Decisions taken into P11

| Decision | Choice | Why |
|---|---|---|
| Scope | **Jersey numbers, the spectator panel, teammate cycling, and the reward reveal screen. Vendor services and scouting profiles move whole to a new P12** | Matches the currently recorded phase plan. P10's own assumptions section guessed scouting would land in "P11's presentation" -- that guess is not binding. P1 reserved vendor services and scouting profiles together, in the same sentence that reserved regional pools and modifiers; giving them their own phase keeps that reservation resolved as the pair it was made as, instead of scouting riding in on a GUI phase built for a different purpose while vendor waits alone. |
| GUI architecture | **Real client-side screens and a HUD overlay, over a new CobbleTowers networking channel** | Chosen over vanilla-primitive-only presentation because the spectator panel needs live, continuously-updating state (who is being followed, that teammate's remaining Pokémon) that a title card cannot carry, and a reward reveal is exactly the one-shot informational popup a `Screen` exists for. This is the first client-side code and the first custom networking channel in the mod -- new infrastructure, opened deliberately, not a rider on existing plumbing. |
| Jersey encounter titles | **Vanilla title/subtitle packets, not a custom screen** | TDS #68 only asks for "concise regional entrance/title presentation" -- Minecraft's built-in title packets already are a two-line title card. Routing this through the same new client-rendered infrastructure the panel and reveal screen need would be exactly the over-abstraction P1 warns against ("introduce an interface only where it creates a stable extension boundary"). No client-side code is needed for this piece at all. |
| Where a jersey number is decided | **Inside `EncounterSnapshot`, seeded the same way its other presentation facts are** | TDS #67 A: generated from the encounter/run seed, so a restart cannot reroll it. `EncounterSnapshot` is already the one place a floor's presentation-relevant facts are pinned once per encounter (P5); a jersey number is one more derived value, the same way `aspects` already is. |
| Jersey-membership test | **Reuse `RegionalThemeDefinition.isJerseySpecies`, not a second check** | `RegionalWeighting.weightFor` already asks this exact question to decide draw weight (P10). A jersey number needing its own copy of "is this species one of the theme's five" would be the second source of truth for a fact that already has one. |
| Spectator state | **Ephemeral, server-held, never persisted** | Who a knocked-out player is currently following is a camera choice, not run state -- the same distinction TDS #7 already draws for battle presentation entities ("not authoritative run state"). It is rebuilt from `ParticipantState`/`Round` the moment a player reconnects or a floor resettles; nothing is lost by not writing it to `PersistedRun`. |
| Teammate cycling | **A keybind sends a request packet; the server decides who is next and reassigns the camera** | Vanilla's own spectator-mode camera cycling does not apply here -- a knocked-out tower player is still in survival gamemode, standing at the floor's spectator anchor (`TowerEncounters.sendToSpectatorAnchor`), not in creative spectator mode. Cycling has to be tower-driven. The server, not the client, decides who is a legal target (`ACTIVE` teammates only), matching every other C2S handler's posture of never trusting a client's claim about run state. |
| Reward reveal delivery | **A screen when the client can receive it; the existing chat line stays as the fallback** | P9 explicitly modeled `RewardDelivery`'s chat message on "the same fallback CobbleRaids uses when its own GUI backend is unavailable" -- P11 is what gives it something better to fall back *from*, not a reason to delete the fallback. `ServerPlayNetworking.canSend` tells the server whether this client registered the channel; when it has not (an out-of-date or vanilla client, however unlikely alongside a Cobblemon/CobbleRaids pack), the grant still reaches the player as chat text exactly as it does today. |

## 1. `EncounterSnapshot` gains a jersey number

```
EncounterSnapshot(ordinal, species, aspects, level, jerseyNumber: OptionalInt)
```

`JerseyNumbers`, a new pure class in `com.cobbletowers.encounter` -- the fourth "one place this
arithmetic exists" class alongside `TowerLevelPolicy`, reward growth, and `RegionalWeighting` -- takes
`EncounterSeed.of(runSeed, floorIndex, ordinal)` (the same per-encounter seed `EncounterDraw.pick`
already derives) and returns a number in `1..99`, sports-jersey range, deterministic per encounter.

`EncounterDraw.draw` calls it only when the resolved `theme` is present and
`theme.get().isJerseySpecies(entry.species())` is true; every non-jersey opponent's snapshot carries
`OptionalInt.empty()`, exactly as it does today in every respect but this one field. Existing callers
that build an `EncounterSnapshot` by hand gain one more constructor argument, the same shape every
prior phase's schema growth has taken.

## 2. Jersey encounter titles

No new payload. In `TowerEncounters.sendNextOpponent`, immediately after `CobblemonBattleAdapter.start`
succeeds, when `snapshot.get().jerseyNumber()` is present the server sends the player a title and
subtitle directly (`ClientboundSetTitleTextPacket` / `ClientboundSetSubtitleTextPacket`, vanilla
packets every client already understands): the theme's display name and doctrine as the title, `"#<n>
<species>"` as the subtitle. A non-jersey opponent sends nothing new -- today's silence stays exactly
as it is for Neutral and for a themed pool's non-signature entries.

## 3. Networking -- `com.cobbletowers.network`

New internal domain. Three payloads, modern Fabric networking (`CustomPacketPayload` records with a
`StreamCodec`, registered through `PayloadTypeRegistry`), installed once from `TowerNetworking.install()`
the same static-install shape `RewardDelivery.install()` already uses:

- **`SpectatorPanelPayload`** (S2C) -- the followed teammate's name, their remaining/total Pokémon
  count, the floor index, and a short run-state label. Sent once when a player starts spectating, once
  per cycle, and again whenever the followed teammate's own fight resolves (win, loss, disconnect) so
  the panel never shows a stale fight.
- **`CycleTeammatePayload`** (C2S) -- one field, `NEXT` or `PREVIOUS`. Sent when the spectator's keybind
  is pressed.
- **`RewardRevealPayload`** (S2C) -- the run id, the floor index banked through, and the item/amount
  pairs just granted. Sent from the same call sites that already exist for this: `RewardBankService.bank`
  (for an online participant at the moment of banking) and `RewardDelivery.deliver` (for a queued grant
  delivered on join).

## 4. Spectator panel and teammate cycling

`com.cobbletowers.spectator.SpectatorPresentation`, new internal domain, server-side, holding one map:
`playerId -> followedTeammateId`, keyed only by players currently spectating. Not part of
`PersistedRun` -- see the decision above.

- **Entering**: `TowerEncounters.knockOut`, right after `sendToSpectatorAnchor` succeeds, picks the
  first still-`ACTIVE` teammate in the run (falling back to "nobody yet" if none remain, which only
  happens on the same tick the floor is about to wipe), calls `ServerPlayer#setCamera(target)` so the
  spectator's view rides the teammate they are following, and sends the first `SpectatorPanelPayload`.
- **Cycling**: the `CycleTeammatePayload` handler looks up the sender's run, confirms their own
  `ParticipantState` is `isSpectating()`, and refuses anything else -- a request from a player who is
  not actually spectating (stale client state, a forged packet) is dropped and logged, never trusted.
  It then walks the run's participants for the next/previous `ACTIVE` teammate, reassigns the camera,
  and resends the panel payload.
- **Leaving**: the existing revival call sites (`ParticipantService.markRevivePending` /
  `reviveAtIntermission`) are the trigger to release the camera (`setCamera(self)`) and drop the map
  entry. No new teleport is needed -- the next floor's own entry teleport already moves a revived
  participant into position the same way it moves everyone else; this phase only has to stop
  overriding their camera once that happens.

The panel itself is client-rendered: `SpectatorHud`, a `HudRenderCallback` consumer that draws only
while the local player's last-known `CombatState` (cached from the most recent payload) is
`KNOCKED_OUT` or `SPECTATING_TEAM`. Deliberately a HUD overlay, not a modal `Screen`: a spectator is
still supposed to be watching the fight, and a modal dialog would block the exact thing TDS #25 asks
them to be shown.

## 5. Reward reveal screen

`RewardRevealScreen`, client-side, opened with `Minecraft.getInstance().setScreen(...)` the moment a
`RewardRevealPayload` arrives -- a plain informational `Screen`, not a container menu. TDS #9's grants
are never a player choice (P9: "No player choice in what is granted"), so there is nothing to drag,
click or confirm; the screen is a itemized list and a dismiss button, matching the read-only nature of
what it shows.

There is only one real call site, not two: `RewardBankService.bank`'s own delivery loop already just
calls `RewardDelivery.deliver(server, player)` for each online participant, the same method the join
handler calls for a queued grant. `RewardDelivery.deliver` gains one call, right after the existing
`player.sendSystemMessage(...)` line (unchanged, still the fallback): send a `RewardRevealPayload` built
from the same `delivered` list the chat line already summarizes, through `TowerNetworking.sendRewardReveal`,
which itself checks `ServerPlayNetworking.canSend` before sending. Neither method's control flow
otherwise changes.

## 6. Client module and build changes

- **`build.gradle`**: `loom { splitEnvironmentSourceSets() }`, giving `src/client/java` its own compile
  unit that depends on `main`, the standard Fabric Loom shape for a mod that is server-authoritative
  everywhere except its presentation layer.
- **`fabric.mod.json`**: gains `"client": ["com.cobbletowers.client.CobbleTowersClient"]` alongside the
  existing `"main"` entrypoint. `CobbleTowersClient` registers the payload receivers, the HUD callback,
  and the cycle-teammate keybind (Fabric API's `KeyBindingHelper`).
- **New internal domains**: `com.cobbletowers.network` (payload records, both sides) and
  `com.cobbletowers.client` (HUD, screen, client entrypoint) -- neither was in P1's suggested list of
  internal domains, which predates this phase's decision to build real client code at all. `spectator`
  was suggested there and is used, server-side only.

## 7. Validation impact -- a real gap this phase has to close

`validate_architecture.py` and `validate_api_boundary.py`'s pre-jar invocations in `ci_local.sh` read
`build/classes/java/main` only (both scripts hardcode that path). With `splitEnvironmentSourceSets()`,
client-only classes compile to `build/classes/java/client` -- a second directory neither script's
default path ever looks at. The two post-build, jar-based invocations later in `ci_local.sh` *do* still
catch anything wrong, because Loom packs client classes into the same remapped jar -- but that means a
violation in `com.cobbletowers.client` would sail through the two earlier "bytecode" steps and only be
caught by the two later "jar" steps, silently narrowing what the earlier steps actually check.

`ci_local.sh` needs one more line per script -- a third invocation pointed at
`build/classes/java/client` -- run alongside the existing `main`-classes step, not instead of it. This
is the same category of thing P10 found while implementing (a real gap the checker's own scope
statement did not admit to) rather than a new rule; it belongs in this phase's implementation, not as a
separate cleanup.

`validate_persistence.py` needs no change: nothing this phase adds is persisted (§ "Spectator state"
above), so there is no new surface for it to check.

## 8. Commands

`/cobbletowers definitions`' existing per-tower summary line gains nothing this phase -- jersey numbers
are per-encounter, not per-definition, and there is no new content kind to count. No new command is
introduced: everything here is either automatic (titles, panel, reveal) or driven by a keybind
(cycling), and TDS #25 does not ask for an operator-facing view into who is spectating whom.

## 9. Performance impact

- **Jersey numbers**: one more pure arithmetic call per jersey encounter draw, the same cost class as
  `RegionalWeighting`'s existing per-entry loop.
- **Titles**: two vanilla packets, only on a jersey encounter's start -- no new per-tick cost.
- **Spectator panel**: payload sends are event-driven (enter, cycle, followed-teammate's fight
  resolving), not polled every tick, matching TDS #33's "no global per-tick scans." `SpectatorPresentation`'s
  map is bounded by the number of players currently spectating, which is bounded by the number of
  players in a run (four).
- **Reward reveal**: one additional payload send at an existing call site that already does strictly
  more work (banking, store writes) than sending one more packet costs.

## 10. Assumptions and constraints

- **Vendor services and scouting profiles are not this phase's charter.** They move whole to a new P12,
  still reserved ids, still parsed and carried untouched (`TowerContent.of` gains nothing for them
  here). P10's own guess that scouting belonged in "P11" does not bind this phase's actual scope.
- **No preparation screen, no draft screen, no championship presentation screen.** TDS §13's fuller
  "preparation, scouting, intermission, spectator, draft and championship presentation" list for its
  own P11 is wider than this project's P11 charter. Those remain whatever they are today -- the
  existing chat/command-driven preparation and drafting flows -- until a future phase, if any, takes
  them on by name.
- **No real Tideforge/Rootvale/Duskvale jersey titles beyond doctrine and display name.** P10 shipped
  the framework and placeholder content only; a themed tower's title text is exactly as authored today,
  nothing this phase invents.
- **A client without the CobbleTowers mod still gets a working (if plainer) experience.** The reward
  chat fallback is retained deliberately; the spectator panel and jersey titles have no fallback beyond
  "nothing extra renders," which is exactly what happens today for every player before this phase
  ships.
- **This is the mod's first client-side code and first networking channel.** Every future phase that
  wants to show a player something now has infrastructure to build on instead of inventing its own; P11
  does not attempt to make that infrastructure more general than what its own three consumers need.
