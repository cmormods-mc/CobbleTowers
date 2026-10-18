import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { claimable, claimLine, laneRows, progressPercent, stateWord, tileLabel, timerText } from '../src/desk/progressView.js';

const tracks = (mutate) => {
  const d = JSON.parse(readFileSync(new URL('../fixtures/TrackState.json', import.meta.url), 'utf8')).data;
  if (mutate) mutate(d);
  return d;
};

test('claimable counts each lane and ignores a lane that is not there', () => {
  const t = tracks();
  const c = claimable(t);
  assert.equal(c.total, c.mastery + c.season);
  assert.ok(c.mastery >= 1);
  assert.deepEqual(claimable(tracks((d) => { d.mastery.present = false; d.season.present = false; })), { mastery: 0, season: 0, total: 0 });
});

test('the claim line is singular, plural or empty', () => {
  assert.equal(claimLine(0), 'No rewards ready');
  assert.equal(claimLine(1), '1 reward ready');
  assert.equal(claimLine(3), '3 rewards ready');
});

test('lane rows: a row per tower with its rank when the Hall knows it, then the season; the viewed lane is selected', () => {
  const extras = { standings: [{ tower: 'cobbletowers:tideforge', rank: 'Silver', level: 5, toNext: 2, ascension: 0 }] };
  const rows = laneRows(tracks(), 'mastery', extras);
  assert.equal(rows[0].name, 'Tideforge');
  assert.equal(rows[0].sub, 'Silver · Level 5');
  assert.equal(rows[0].tone, 's');
  assert.ok(rows[0].selected && !rows.at(-1).selected);
  assert.equal(rows.at(-1).kind, 'season');
  const season = laneRows(tracks(), 'season', extras);
  assert.ok(season.at(-1).selected && season.every((r) => r.kind === 'season' || !r.selected));
  // without extras the level from the track payload is used
  assert.equal(laneRows(tracks(), 'mastery', undefined)[0].sub, 'Level 5');
});

test('progress percent is bounded and zero without a target', () => {
  assert.equal(progressPercent({ into: 3, need: 5 }), 60);
  assert.equal(progressPercent({ into: 9, need: 5 }), 100);
  assert.equal(progressPercent({ into: 3, need: 0 }), 0);
});

test('the season timer reads from when the payload arrived', () => {
  assert.equal(timerText({ endsInMillis: 0, timerLabel: '' }, 0, 0), '');
  assert.equal(timerText({ endsInMillis: 777600000, timerLabel: 'ENDS IN' }, 1000, 1000), 'Ends in 9 days');
  assert.equal(timerText({ endsInMillis: 3_600_000, timerLabel: 'ENDS IN' }, 0, 3_000_000), 'Ends in 10 min');
});

test('tile words and labels', () => {
  assert.equal(stateWord({ state: 1 }), 'Ready');
  assert.equal(stateWord({ state: 2 }), 'Claimed');
  assert.equal(stateWord({ state: 0 }), 'Locked');
  assert.equal(stateWord({ state: 3 }), '');
  assert.equal(tileLabel({ reward: '2x Diamond' }), '2x Diamond');
  assert.equal(tileLabel({ reward: '', note: 'Silver rank' }), 'Silver rank');
  assert.ok(tileLabel({ reward: 'A very long reward description that cannot fit' }).endsWith('…'));
});
