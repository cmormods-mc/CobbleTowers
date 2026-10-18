import test from 'node:test';
import assert from 'node:assert/strict';
import { lobbyView } from '../src/lobby.js';
import { MIN_SEATS, modeName, seatLine, seatsOf, startHint } from '../src/desk/seats.js';
import { readFileSync } from 'node:fs';

const play = (mutate) => {
  const d = JSON.parse(readFileSync(new URL('../fixtures/PlayState.json', import.meta.url), 'utf8')).data;
  if (mutate) mutate(d);
  return d;
};

test('a lone host gets their own seat and three open ones', () => {
  const p = play();
  const seats = seatsOf(lobbyView(p), 'WebPlayer');
  assert.equal(seats.length, MIN_SEATS);
  assert.equal(seats[0].kind, 'player');
  assert.ok(seats[0].host && seats[0].me);
  assert.equal(seats.filter((s) => s.kind === 'open').length, MIN_SEATS - 1);
});

test('guests show their state; more guests than seats still all get one', () => {
  const p = play((d) => { d.lobby.members = ['A', 'B', 'C', 'D'].map((name, i) => ({ name, accepted: i < 2, ready: i < 1 })); });
  const seats = seatsOf(lobbyView(p), 'B');
  assert.equal(seats.length, 5);
  assert.deepEqual(seats.slice(1, 5).map((s) => s.status), ['ready', 'drafting', 'invited', 'invited']);
  assert.ok(seats[2].me);
  assert.equal(seats[0].me, false);
});

test('the line under a name is the role and the state', () => {
  assert.equal(seatLine({ kind: 'open' }), 'Open seat');
  assert.equal(seatLine({ kind: 'player', host: true, status: 'host' }), 'Host');
  assert.equal(seatLine({ kind: 'player', host: false, status: 'drafting' }), 'Guest · Drafting');
});

test('the mode name is the chosen one, else Standard', () => {
  assert.equal(modeName(play()), 'Rental Draft');
  assert.equal(modeName(play((d) => { d.lobby.options.modes.chosen = ''; })), 'Standard');
});

test('the Start hint says why it is waiting', () => {
  const ready = play();
  assert.equal(startHint(lobbyView(ready), ready), 'Everything is ready.');
  const noTower = play((d) => { d.lobby.selected = ''; });
  assert.equal(startHint(lobbyView(noTower), noTower), 'Choose a tower first.');
  const unconfirmed = play((d) => { d.lobby.options.modes.readiness.confirmed = false; });
  assert.equal(startHint(lobbyView(unconfirmed), unconfirmed), 'Confirm the mode to continue.');
  const counting = play((d) => { d.lobby.countdown = 5; });
  assert.equal(startHint(lobbyView(counting), counting), 'The countdown is running.');
  const guest = play((d) => { d.lobby.role = 2; });
  assert.equal(startHint(lobbyView(guest), guest), '');
});
