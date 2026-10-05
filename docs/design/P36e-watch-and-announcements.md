# P36e: watching a run, and server announcements

Two small things that make the tower visible to people who are not in it. The physical Hall of Fame is still deferred: it needs a lobby place the owner has not chosen.

## `/tower watch <player>` and `/tower unwatch`

A player outside a run looks in on someone else's live run. The watcher is taken into the tower in **spectator mode**, placed at the participant, and rides their camera with the same information panel a defeated teammate sees (Pokemon remaining, floor, state).

- **Not run state.** A watch lives in memory in `Watching` and is never persisted. A restart ends it: the exit sweep finds the watcher in the tower with no run and sends them home, and `restoreMode` turns an orphaned non-operator spectator back into a survival player.
- **Refusals** (`WatchRules`, pure and tested): yourself; you are in a run of your own; target offline; target not in a live run; target between floors. Someone already watching may switch to another player.
- **The exit sweep** skips watchers while the watch is live. `Watching.sweep` runs first in each pass and ends any watch whose run is over or whose subject is gone, sending the watcher home *before* a cell is released, so nobody is ever left in an empty cell (the P20 lesson).
- **Game mode** is saved on the first watch and restored on the way out; their starting place is recorded with the same `TowerReturnStore` a run uses.
- Anyone may watch anyone for now. A privacy switch (a player opting out of being watched) is a likely follow-up if servers want it.

## Announcements

`Announcements` broadcasts to the whole server when:

1. a team takes **first place on the all-time Difficulty, Speed or Ascension board** of a tower ("X and Y set a new record: ..."); Clears and trial boards stay quiet;
2. a player reaches a **milestone Ascension** for the first time: Ascension 1, then every fifth.

`config/cobbletowers-announce.json` with `{"enabled": false}` turns them off (default on). A fresh server announces its very first clear as a record, since it does lead an empty board; that is accurate, if a little eager.

## Tests

Unit: `AnnouncementsTest` (milestones, which boards speak, sentence wording, durations), `WatchRulesTest` (every refusal, switching, endings).
Live: `validation/smoke/watch_test.py`, 15 checks with two real bots: refusal with no run, enters the tower in spectator mode next to the target, left alone for 12 s, `/tower unwatch` returns them in their old mode and place, a run ending sends the watcher home too, no quarantined cell, no exception. The announcements were seen live in the server log during `mastery_test` and `ascension_test`.

## Not verified

The watcher's camera and panel as rendered in a real client were not photographed; the camera is the same `setCamera` mechanism a defeated teammate already uses.
