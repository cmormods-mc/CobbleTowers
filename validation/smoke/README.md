# Live verification

Scripts here drive a real server. They are not part of `ci_local.sh` and they are not run by
GitHub Actions: they need a modded server with Cobblemon and CobbleRaids installed, which CI has
no business downloading.

## `run_durability_test.py`

Proves that a checkpointed run survives the server being killed, and that a run whose last move was
not checkpointed does not.

That second half is the point. A test that only asserted "the run came back" would pass on a build
with no synchronous write at all, because an autosave might have fired in between. Driving one run
past a checkpointing move and another past a non-checkpointing one, then killing the process
outright, makes the difference between the two write paths the thing being measured:

| run | last move | expected state after the restart |
|---|---|---|
| A | `instance_allocated` (the table checkpoints it) | recovered from `PREPARING` |
| B | `party_submitted` (it does not) | recovered from `CREATED` |

The observable is the line the transition service logs during recovery, which names the state each
run was recovered *from*. If B ever comes back from `VALIDATING_PARTY`, an autosave fired and the
run proved nothing -- so that is a failure here, not a pass.

```sh
python validation/smoke/run_durability_test.py \
  --server-dir <rig>/testserver \
  --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.12.1+1/bin/java.exe" \
  --jar build/libs/CobbleTowers-<version>.jar
```

### What the rig needs

- Fabric 1.21.1 with fabric-api, Cobblemon 1.7.3, and a CobbleRaids jar at the version in
  `gradle.properties` -- CobbleTowers will not load without it.
- `enable-rcon=true`, an `rcon.password`, and `online-mode=false` so an offline bot can connect.
- `node` on PATH, and a `node_modules` containing mineflayer. By default the script looks for the
  CobbleRaids rig's, beside the server directory; `--node-modules` overrides it. This repo vendors
  no node dependencies of its own.

The run commands take a player selector, so `joinbot.js` connects one player who then does nothing.
Cobblemon's entity metadata is not in mineflayer's vanilla protocol definitions, so parse errors in
the bot's output are expected and harmless -- it never reads the world.

### Gotchas worth knowing

- **The kill must be a hard one.** A clean stop saves everything and hides exactly the bug this
  looks for.
- **Port probing must test `bind`, not `connect`.** A socket in TIME_WAIT, or a port held as the
  local end of an unrelated outbound connection, refuses connections while still blocking a bind.
  Probe IPv6 dual-stack too: Minecraft binds `*`, and an IPv4-only probe misses the conflict that
  actually stops the server.
- **Set `PYTHONIOENCODING=utf-8`** on Windows if any output might carry non-ASCII.
- The script replaces `CobbleTowers-*.jar` in the rig's `mods/` and nothing else. Keep the glob that
  narrow: a looser one once deleted an add-on data pack sitting beside the mod in the raids rig.
- **A bot username is at most 16 characters.** A longer one is rejected by the server's packet
  decoder as `Failed to decode packet 'serverbound/minecraft:hello'`, which reads like a protocol or
  mod-version mismatch and is nothing of the kind. This cost a run to find, because the bot's own
  output was being discarded at the time -- it is kept in `logs/towers-durability-bot.log` now.
- **Runs persist in the rig between invocations.** Each run parks in `RECOVERY_REQUIRED`, which is
  not terminal, so retention never retires them; `world/data/cobbletowers_runs.dat` can be deleted
  between sessions. The test creates fresh runs every time and does not care what else is there.

## `participant_test.py`

P7's paths, none of which a unit test can reach, because each is about a real connection or a real
cell: a disconnect mid-floor, a reconnect inside the grace window, the knockout/spectator cycle, the
intermission, `/cobbletowers runs leave`, the watchdog, and the cell a crash leaves behind.

```sh
python validation/smoke/participant_test.py \
  --server-dir <rig>/testserver \
  --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.12.1+1/bin/java.exe" \
  --jar build/libs/CobbleTowers-<version>.jar
```

Two things it does deliberately:

- **It runs the real watchdog, not a test-only one.** `/cobbletowers runs watchdog player|floor`
  sweeps the floors that are really open with the clock wound past a cap, so the ten-minute wait does
  not have to be sat through and no second code path exists to be right while the real one is wrong.
- **It does not insist the floor is won.** That is up to a bot with a real party against a real draw.
  Both outcomes are P7 paths -- a cleared floor proves the intermission, a wiped one proves the
  forfeit -- and the branch it took is printed. What is asserted either way is that the floor
  *resolved*: a floor somebody walked out of must never hang holding the run, the cell and its
  tickets.

### Gotchas this one added

- **`execute ... run say` returns nothing over RCON.** The reply is empty whether or not the command
  matched, so `"word" not in reply` is a check that can only pass. Probe with `data get entity`,
  which answers in the reply. One check in `floor_encounter_test.py` had been vacuous for this
  reason and is fixed.
- **A hard kill loses everything unwritten, entities included.** To test what a crash leaves in a
  cell, `save-all flush` first: a real crash follows an autosave, and without one the restart finds
  an empty cell and the test proves only that the harness can delete a Pokemon.
- **`wait_online` in `run_durability_test.py` waits for its own bot's name**, not for whoever you
  passed it. Wait for a named player instead.

## `draft_test.py`

P8's intermission draft: that one opens, that a run cannot skip it, that a majority settles it, that
the chosen modifier reaches a real floor, and that **a hard kill mid-draft brings back the same three
cards** (TDS #29).

```sh
python validation/smoke/draft_test.py   --server-dir <rig>/testserver   --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.12.1+1/bin/java.exe"   --jar build/libs/CobbleTowers-<version>.jar
```

The run is driven to its intermission with `runs advance` rather than by winning a fight. The subject
is the draft, and making it depend on two bots beating a real draw would make a red result mean "the
bots lost" as often as "the draft is broken". Every step is still a real transition through the real
table -- what is skipped is the battle, not the state machine. The last section does fight a real
floor, because that is the only way to see a modifier change one.

### Gotchas this one added

- **RCON returns a whole command's output as ONE line.** `runs show` prints a dozen lines in game and
  arrives here as a single space-separated string, so anything written with `splitlines()` finds only
  the first line and every later probe quietly returns `""` and passes. That is the same shape as the
  `execute ... run say` trap above: a check that cannot fail. Parse with a regex for the thing you
  are looking for, and assert on something that would be absent if it were missing.
- **A hard kill parks the run.** It comes back `RECOVERY_REQUIRED` and refuses gameplay events until
  `recovery_completed`, so a test that kills a server mid-run has to resume it before playing on --
  otherwise the next step fails with `ILLEGAL_EVENT` and it reads like a bug in the feature.
- **`Server.start()` deletes the log.** After a restart the first boot's lines are gone, so anything
  to be asserted about the run before the kill has to be asserted before the kill.
- **A draft is settled by the watchdog only after five minutes of emptiness**, and the timer starts
  when the emptiness is first seen. `runs watchdog player` winds the clock past that; waiting does
  not, within a test's patience.
- **A hard kill loses the bots' Cobblemon parties**, even after `save-all flush` -- Cobblemon keeps
  its storage on its own schedule. A floor cannot level an opponent against a party that is not
  there, so anything that restarts a server mid-test has to `pokegiveother` again before playing on.
- **`compileJava` does not produce the jar you are about to test.** Fabric Loom builds the remapped
  artifact in `remapJar`, so after `compileJava` the file in `build/libs/` is still the *previous*
  build. Two live cycles were spent reading real failures off a jar that was one edit old, and the
  tell is subtle: the jar exists, has the right version in its name, and loads. Run `./gradlew build`
  before any smoke test, and when a result makes no sense, compare the jar's timestamp against the
  source file you last changed.
- **Assert what is present, never the absence of a word.** A check written as `"plus" not in begun`
  was passed with flying colours by a floor that failed to start altogether, and the run it was
  guarding came back green with nothing behind it. This is the third shape of the same trap in this
  directory, after `execute ... run say` and `splitlines()` on RCON output: if the thing under test
  vanished entirely, would the check still pass? If yes, it is not a check.
