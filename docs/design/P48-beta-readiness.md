# P48: Beta readiness (one week before players test)

Status: **in progress 2026-10-15.** Owner approved the list and the order (feedback, first run, known gaps, then safety). Written first, per the design gate. One commit at the end.

## 1. Feedback you can act on

* **`/tower report <text>`** logs a report to `logs/cobbletowers-reports.log` (append only): time, player and uuid, mod version, the text (400 characters, one line), and the player's run (id, tower, floor, state, trial, players, seed) with the last twelve transitions of that run. A report is limited to one every 30 seconds per player. `/cobbletowers reportadmin list [count]` prints the last reports; the log line is also written to the server log.
* **Run history** is a small in-memory ring (twelve transitions per run, two hundred runs) fed by `RunTransitionService`. Not persisted: it exists so a report can say what the run was doing.
* **Telemetry** goes into the existing counters (`masteryadmin counters`): `run_start.*` (total, trial_daily, trial_weekly, playlist, plain), `run_end.*` (completed, cashed_out, failed, abandoned), `floor_entered.fNN`, `run_lost_at.fNN` (a run that ended in failure or abandonment, by the floor it was on) and `trial.daily|weekly.finished|completed`.
* **Feedback prompt:** when a run ends, its online players get one chat line pointing at `/tower report`. `config/cobbletowers-beta.json` `{"report_prompt": false}` switches it off.

## 2. First-run experience

* **Guide panel** on the web Hall: a short "how a run works" page (key and party, floors and bosses, the intermission, relics and rooms, cash out, trials, mastery and the badge). It opens by itself the first time a browser opens the Hall (remembered in the page's own storage) and from a Guide button any time.
* **`/tower help`** prints the same in chat for players without the page, and is the place the web guide's wording comes from.
* **Empty states:** the messages for no party, no key and no level were read and reworded to say what to do next.

## 3. Known gaps

* Relic battle effects and the four new rooms are put through a real battle on the rig (a relic forced onto a run, a bot fight, the Showdown log read for the effect).
* The web page shows a "Waiting for the server" banner if no message has come from the server for a while.
* Placeholder art (modifier cards, badge icons) is recorded as art work for later; nothing is built for it this week.

## 4. Beta safety

* A soak run on the rig (the existing soak test, bots) and a read of the log afterwards.
* A deploy dry run of the beta file set on a clean rig (server-only CobbleTowers jar, rebuilt AscensionLib, Polymer, WebGUI), with a bot joining, reading the log for errors.
* `docs/BETA-RUNBOOK.md`: what to back up and when, how a run is reset or a player's progress wiped, what to do about a stuck run, a full server stop, a bad datapack, and where each kind of data lives.

## Not in this phase

New towers, new rooms, balance changes (the counters above are for tuning after the first days), real art.
