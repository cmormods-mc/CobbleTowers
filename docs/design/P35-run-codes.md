# P35 (part 1): Run codes

Roadmap D4. P35 also names Clubs (D2) and Echoes (D1); those are separate, larger builds and are **not** in this slice.

## What a player sees
Every ordinary run prints a code when it starts: `Run code: CT1-tideforge-std-0-2k9x3q0a7ld-h`. A friend, or the same player
later, types `/tower play code <code>` as a host and the lobby is set to the same tower, mode and starting Ascension with the
same seed, so the opponents, bosses and draft cards are the same. `/tower play code` with no argument repeats the last code the
player started (in memory only; it is lost on restart, the code itself is not). Trials print no code: they already share a seed.

## How it works
* `runcode/RunCode` (pure): `CT1-<tower>-<mode|std>-<ascension>-<seed base36 unsigned>-<check>`. The check is one case-blind
  base-36 character over the rest, so a mistyped or truncated code is refused. Another namespace is written `ns.path`.
* `TowerLobby.seed` (cleared when the tower changes) is used by `LobbyService.launch` in place of the random seed.
* `LobbyService.useCode` validates (decodes, tower and mode loaded), then reuses the host's existing `select`, `setPlaylist`
  and `setAscension`, so every existing rule still applies (host only, team size for a mode, the team must have reached the
  Ascension). It sets the seed last.
* Nothing about scoring changes: a coded run is an ordinary run for boards and mastery.

## Not built
* A code for rental runs: the pack draft seeds from a random number per player, so a coded rental run shares the opponents but
  not the packs.
* Shown on the end-of-run report and in `runs show` (the starting Ascension is not stored on the run, only the floor).
* Clubs and Echoes.

## Tests
Unit `RunCodeTest` (round trip over 500 random seeds, case-blind, namespaces, damage refused). Live
`validation/smoke/runcode_test.py` 7/7: a run logs a code, a damaged code is refused, a valid one is accepted, the second run
carries the same code and the same first opponent species and level.
