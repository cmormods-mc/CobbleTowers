# P23: battle effects through a Showdown patch

Written before the code it describes, as the TDS gate requires.

The custom armor sets (P24) were asked to have **battle bonuses**: a type-damage boost, starting weather, stat
boosts. Cobblemon fires no event for any of those; the damage maths, the weather and the stat stages all live in
the Showdown simulator, a JavaScript program Cobblemon runs in a GraalVM context. The user's decision
(2026-10-03): **"We should create a patch for Showdown now. We need a clever and stable patch."** This phase is
that patch, built first and on its own because it is the riskiest piece and everything else can be tested without
it.

## What already exists, and what that teaches

CobbleRaids already patches Showdown, and has paid for the lessons:

- It copies `raid-patch.js` into Cobblemon's **unbundled** `showdown/` directory and edits `index.js` to
  `require` it, at a Mixin injection point just before the JavaScript context is built, then repairs it again at
  `SERVER_STARTED` because another mod (`mega_showdown`) overwrites those same files and which one wins is
  mod-load-order luck. A copy that lost the race "loads without the patch and nothing in the log says so".
- Its edits are matched **structurally**, not by exact text, so a reformatted copy still matches.
- It fails *closed* for raids and *open* for Cobblemon: if it cannot patch, raids are off and ordinary battles
  are untouched.
- Java reaches the simulator by adding fields to the `format` object inside the `>start` payload
  (`raidWeather`, `raidTerrain`); Showdown's `Format` constructor copies them onto `battle.format`, so the patch
  reads them there. `RaidBattleRegistryMixin` is the one `@Redirect` on `BattleRegistry.startShowdown`, and Java
  allows only one redirect per target.

Two more patchers in one install is the fragile outcome, so **CobbleTowers does not edit Showdown's files at
all.** It rides CobbleRaids' installer.

## Architecture

```
CobbleTowers                         CobbleRaids (branch from 0.8.105)              Showdown (JS)
------------                         ----------------------------------              -------------
registerModule("cobbletowers-fx") -> installs showdown/ext-cobbletowers-fx.js  ->  loaded by raid-patch.js,
registerFormatFields(provider)    -> adds fields to the >start format object   ->  each module isolated
provider returns towerFx JSON                                                       tower-fx.js reads
                                                                                    battle.format.towerFx
```

**A generic extension API in CobbleRaids** (`com.cobbleraids.api.showdown.ShowdownExtensions`, java and
net.minecraft types only, like its other API):

- `registerModule(id, Supplier<InputStream>)` -- a JavaScript module the installer writes beside
  `raid-patch.js` as `ext-<id>.js`, in the same two places and at the same two moments as `raid-patch.js`
  itself, so it survives the same race.
- `registerFormatFields(provider)` -- `fieldsFor(battleId, playerIds)` returns raw JSON values by name; the
  existing `startShowdown` redirect adds them to the `>start` format for **every** battle, not only raids.
  First provider to set a name wins; the names CobbleRaids and Cobblemon already use are reserved.
- `raid-patch.js` gains a small tail that `require`s every `ext-*.js` beside it, **each inside its own
  try/catch**: an extension that throws at load is logged and skipped, never allowed to take `raid-patch.js`, and
  with it the simulator, down.

This keeps all the fragile installation in the one place that has already been through the wars, and makes the
extension point reusable by any other mod. It is a change to **CobbleRaids**, on the same branch as the Raid
Points API (cut from 0.8.105, since its `HEAD` needs Cobblemon 1.8.1).

**`tower-fx.js` in CobbleTowers** is the patch itself. Its shape is the stability argument:

1. **It does nothing unless asked.** It wraps exactly one method, `Battle.prototype.start`, and returns
   immediately unless `battle.format.towerFx` is an array. A battle without it is byte-for-byte unchanged.
2. **It is declarative.** Java sends a list of named operations with validated parameters, not code. There is no
   `eval`, no string interpolated into JavaScript, no way for a bad value to become a different program.
3. **It never mutates a prototype for damage.** A damage multiplier wraps `modifyDamage` on **that battle's own
   `actions` instance**, so a Showdown mod that overrides the method on the instance (they do) is wrapped too, and
   no other battle can ever see it.
4. **Every operation is isolated.** One bad operation is logged into the battle as a message and skipped; the rest
   still apply and the battle always starts. The whole module is also inside the extension loader's try/catch.
5. **Everything is bounded.** At most 32 operations, percentages clamped to 1..300, stat stages to -6..6, ids must
   match `[a-z0-9]+` and exist in the battle's own dex. An unknown operation name is ignored, not guessed at.
6. **It reads back, never reports the request.** Applying weather, status or HP and then saying what the field
   actually holds, because Showdown quietly refuses ids it does not know (the lesson in `applyRaidField`).

### The operations

| Operation | Does | Parameters |
|---|---|---|
| `weather` | sets weather | `id`, `duration` (0 = until it changes) |
| `terrain` | sets terrain | `id`, `duration` |
| `boost` | stat stages on the leads | `sides`, `stat`, `stages` |
| `hp` | sets the leads' HP to a percentage (never raises it) | `sides`, `percent` |
| `status` | puts a status on the leads | `sides`, `status` |
| `damage` | scales damage the listed sides **deal** | `sides`, `type` or `any`, `percent` |
| `resist` | scales damage the listed sides **take** | `sides`, `type` or `any`, `percent` |
| `sidecondition` | adds a side condition (Tailwind, Reflect, ...) | `sides`, `id`, `duration` |

`sides` are concrete Showdown side ids (`p1`, `p2`...). Java resolves "the player's side" and "the opponent's
side" from the actors, so the JavaScript never has to guess who is who.

## The Java side in CobbleTowers

- `TowerShowdownFx` registers the module and a format-field provider at start-up.
- `TowerBattleFx` holds the effects waiting for a player's **next** tower battle and hands them to the provider
  once, so an effect can never leak into a later battle (or someone else's).
- `CobblemonBattleAdapter.start` and `TowerBossAdapter` set the pending effects just before starting a battle.
  Where the effects come from (a worn armor set) is P24; this phase proves the mechanism with an operator seam,
  `/cobbletowers runs fx <player> <json>`.

## Testing

Because Showdown is plain CommonJS, the patch can be **tested against the real simulator without a server**:

- `validation/showdown/tower_fx_test.js` loads the rig's own unbundled `showdown/` directory, installs
  `tower-fx.js` into it exactly as the extension loader would, and drives real battles through `BattleStream`
  with the same `>start` / `>player` lines Cobblemon writes. It asserts, per operation, what the simulator
  actually did: the weather the field holds, the stat stage on the Pokemon, the HP, the damage a move did with and
  without the multiplier, and **that a battle with no `towerFx` behaves identically** to one run with the
  patch absent (same seed, same log).
- It also feeds the patch hostile input -- unknown operations, huge numbers, bad ids, a 1000-element list, a
  non-array -- and asserts the battle still starts and nothing throws.
- CobbleRaids gets unit tests for the format-field merge (reserved names, a throwing provider, malformed JSON)
  and for the installer writing and cleaning up extension files.
- Live: an operator sets weather, a stat boost and a damage multiplier on a player's next floor and the real
  server battle reflects them; a floor with no effects is unchanged.

## Not in this phase

- Where effects come from. Armor sets and their bonuses are P24; this phase ships the mechanism and the seam.
- Persistent effects across switch-ins (a boost that follows the Pokemon in); v1 applies at battle start.
- Anything for non-tower battles: a provider returns nothing for a battle it does not own.
