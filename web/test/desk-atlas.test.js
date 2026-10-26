import test from 'node:test';
import assert from 'node:assert/strict';
import { atlas, hasGraph, rowY, atlasHeight, stopName } from '../src/desk/atlasMap.js';

// start 1; row 1: lanes 0,1; row 2: lanes 0,1,2; row 3 is a boss in the middle
const graph = (trail, reveal = 2) => ({
  rows: 3, reveal, trail,
  stops: [{ row: 0, lane: 1, kind: 'START' }, { row: 1, lane: 0, kind: 'BATTLE' }, { row: 1, lane: 1, kind: 'REST' },
    { row: 2, lane: 0, kind: 'FORGE' }, { row: 2, lane: 1, kind: 'BATTLE' }, { row: 2, lane: 2, kind: 'SHRINE' }, { row: 3, lane: 1, kind: 'BOSS' }],
  links: [{ row: 0, from: 1, to: 0 }, { row: 0, from: 1, to: 1 }, { row: 1, from: 0, to: 0 }, { row: 1, from: 0, to: 1 },
    { row: 1, from: 1, to: 1 }, { row: 1, from: 1, to: 2 }, { row: 2, from: 0, to: 1 }, { row: 2, from: 1, to: 1 }, { row: 2, from: 2, to: 1 }],
});
const stateOf = (m, id) => m.nodes.find((n) => n.id === id).state;

test('a run at the start can step to the first row, and the rest is seen or fogged', () => {
  const m = atlas(graph([1], 1), [{ lane: 0, card: 0 }, { lane: 1, card: 1 }]);
  assert.equal(m.row, 0);
  assert.equal(stateOf(m, '0:1'), 'current');
  assert.equal(stateOf(m, '1:0'), 'reach');
  assert.equal(stateOf(m, '1:1'), 'reach');
  assert.equal(stateOf(m, '2:0'), 'fog');
  assert.equal(stateOf(m, '3:1'), 'seen', 'a boss always shows');
  assert.deepEqual(m.nodes.filter((n) => n.state === 'reach').map((n) => n.card), [0, 1]);
});

test('the party stands where it went; the path behind it is done, the other branches passed', () => {
  const m = atlas(graph([1, 1]), [{ lane: 1, card: 0 }, { lane: 2, card: 1 }]);
  assert.equal(m.row, 1);
  assert.equal(m.lane, 1);
  assert.equal(stateOf(m, '0:1'), 'done');
  assert.equal(stateOf(m, '1:1'), 'current');
  assert.equal(stateOf(m, '1:0'), 'passed');
  assert.equal(stateOf(m, '2:1'), 'reach');
  assert.equal(stateOf(m, '2:2'), 'reach');
  assert.equal(stateOf(m, '2:0'), 'seen', 'in the next row but not linked from here');
  assert.equal(m.nodes.find((n) => n.id === '2:0').card, -1);
});

test('links are taken behind, open from here, dead on a branch not taken, future ahead', () => {
  const m = atlas(graph([1, 1]), []);
  const edge = (row, from, to) => m.edges.find((e) => e.row === row && e.from === from && e.to === to).state;
  assert.equal(edge(0, 1, 1), 'taken');
  assert.equal(edge(0, 1, 0), 'dead');
  assert.equal(edge(1, 1, 1), 'open');
  assert.equal(edge(1, 1, 2), 'open');
  assert.equal(edge(1, 0, 0), 'dead');
  assert.equal(edge(2, 1, 1), 'future');
});

test('no links skip lanes, and rows are drawn bottom to top', () => {
  const g = graph([1]);
  assert.ok(g.links.every((l) => Math.abs(l.from - l.to) <= 1 || l.row === 0));
  assert.ok(rowY(0, 3) > rowY(1, 3) && rowY(1, 3) > rowY(3, 3));
  assert.ok(atlasHeight(3) > rowY(0, 3));
});

test('only a graph with rows counts, and stops read by name', () => {
  assert.equal(hasGraph({}), false);
  assert.equal(hasGraph({ route: { graph: { rows: 0 } } }), false);
  assert.equal(hasGraph({ route: { graph: { rows: 10 } } }), true);
  assert.equal(stopName('TRICK_OR_TREAT'), 'Trick or Treat');
  assert.equal(stopName('POOL'), 'Mystic Pool');
  assert.equal(stopName('WHAT'), 'WHAT');
});
