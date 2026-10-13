import test from 'node:test';
import assert from 'node:assert/strict';
import { laneOf, currentIndex, anyClaimable, timeLeft, tileIcon, detail, subtitle, neighbour, centreOn } from '../src/tracks.js';
import { initial, reduce, selectNode } from '../src/reducer.js';

const node = (number, state, extra = {}) => ({ number, state, icon: '', reward: '', note: '', ...extra });
const lane = (nodes, current = 1, extra = {}) => ({ present: true, title: 'T', subtitle: 'sub', current, into: 0, need: 0, endsInMillis: 0, timerLabel: '', nodes, ...extra });
const tracks = (over = {}) => ({ autoClaim: false, towers: [{ id: 'a', name: 'Alpha', level: 1 }, { id: 'b', name: 'Beta', level: 2 }], selectedTower: 'a',
  mastery: lane([node(1, 2), node(2, 1)], 2), season: { ...lane([node(1, 0)]), present: false }, message: '', ...over });

test('the season lane shows only when asked for and present', () => {
  assert.equal(laneOf(tracks(), 'season').kind, 'mastery');
  assert.equal(laneOf(tracks({ season: lane([node(1, 0)]) }), 'season').kind, 'season');
  assert.equal(laneOf(tracks({ season: lane([node(1, 0)]) }), 'mastery').kind, 'mastery');
});

test('the current tile is the level reached, kept inside the lane', () => {
  assert.equal(currentIndex(lane([node(1, 2), node(2, 1), node(3, 0)], 2)), 1);
  assert.equal(currentIndex(lane([node(1, 0)], 0)), 0);
  assert.equal(currentIndex(lane([node(1, 0), node(2, 0)], 99)), 1);
  assert.equal(anyClaimable(lane([node(1, 2), node(2, 1)])), true);
  assert.equal(anyClaimable(lane([node(1, 2)])), false);
});

test('time left is rounded to the unit that matters', () => {
  assert.equal(timeLeft(0), 'ending now');
  assert.equal(timeLeft(3 * 86_400_000), '3 days');
  assert.equal(timeLeft(86_400_000 + 5), '24 hours');
  assert.equal(timeLeft(3_600_000), '1 hour');
  assert.equal(timeLeft(5 * 60_000), '5 min');
  assert.equal(timeLeft(1000), '1 min');
});

test('tile pictures: currency, item, rank note, or nothing', () => {
  assert.deepEqual(tileIcon(node(1, 0, { icon: 'cobblemon:cobble_dollar' })), { kind: 'ui', name: 'coin' });
  assert.deepEqual(tileIcon(node(1, 0, { icon: 'x:raid_points' })), { kind: 'raid' });
  assert.deepEqual(tileIcon(node(1, 0, { icon: 'minecraft:diamond' })), { kind: 'item', id: 'minecraft:diamond' });
  assert.deepEqual(tileIcon(node(1, 0, { note: 'New rank: Silver' })), { kind: 'ui', name: 'star' });
  assert.deepEqual(tileIcon(node(1, 0, { note: 'Perk' })), { kind: 'ui', name: 'gear' });
  assert.equal(tileIcon(node(1, 0)), null);
});

test('the detail panel names the level or step, its state, and what is on offer', () => {
  assert.equal(detail(null, 'mastery', null).hint, true);
  const d = detail(null, 'mastery', node(5, 1, { reward: '3x Poke Ball' }));
  assert.equal(d.title, 'Level 5  -  ready to claim');
  assert.equal(d.body, '3x Poke Ball');
  assert.equal(d.claimable, true);
  assert.match(detail(null, 'season', node(2, 0)).body, /No reward on this step/);
  assert.match(detail(null, 'mastery', node(2, 0)).body, /No reward at this level/);
  assert.equal(detail(null, 'mastery', node(2, 0, { note: 'Perk: x' })).body, '', 'a note stands in for the reward');
  assert.equal(detail(null, 'mastery', node(2, 2)).claimable, false);
});

test('the mastery lane names its tower; the arrows wrap', () => {
  assert.equal(subtitle(tracks(), tracks().mastery, 'mastery'), 'Alpha: sub');
  assert.equal(subtitle(tracks({ towers: [{ id: 'a', name: 'Alpha', level: 1 }] }), tracks().mastery, 'mastery'), 'sub');
  assert.equal(neighbour(tracks(), 1), 'b');
  assert.equal(neighbour(tracks(), -1), 'b');
  assert.equal(neighbour(tracks({ towers: [] }), 1), null);
});

test('scroll position centres a tile and stays inside the strip', () => {
  assert.equal(centreOn(0, 42, 38, 300, 50), 0);
  assert.equal(centreOn(10, 42, 38, 300, 50), 10 * 42 - (300 - 38) / 2);
  assert.equal(centreOn(49, 42, 38, 300, 50), 50 * 42 - 4 - 300);
  assert.equal(centreOn(3, 42, 38, 300, 5), 0, 'a short lane never scrolls');
});

test('a track payload is kept, resets the mastery pick when the tower changes, and its message becomes a notice', () => {
  let s = reduce(initial, 'TrackState', tracks(), 10);
  assert.equal(s.tracksAt, 10);
  s = selectNode(s, 'mastery', 3);
  assert.equal(reduce(s, 'TrackState', tracks({ message: 'Claimed.' }), 11).tSel.mastery, 3);
  assert.equal(reduce(s, 'TrackState', tracks({ message: 'Claimed.' }), 11).notice.text, 'Claimed.');
  assert.equal(reduce(s, 'TrackState', tracks({ selectedTower: 'b' }), 12).tSel.mastery, null);
});
