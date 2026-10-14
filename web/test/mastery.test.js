import test from 'node:test';
import assert from 'node:assert/strict';
import { boardLines, nodesPerPage, tabWidth, towerLabel } from '../src/mastery.js';

test('a split board lists solo then team, and says when a side is empty', () => {
  const lines = boardLines({ title: 'Speed', split: true, solo: ['#1 Ash'], team: [] });
  assert.deepEqual(lines.map((l) => l.text), ['Speed', 'Solo', '  #1 Ash', 'Team', '  no entries yet']);
  assert.deepEqual(boardLines({ title: 'Clears', split: false, solo: [], team: [] }).map((l) => l.text), ['Clears', '  no entries yet']);
});
test('tiles per page and tab width', () => {
  assert.equal(nodesPerPage(240), 6);
  assert.equal(nodesPerPage(60), 2);
  assert.equal(tabWidth('Speed'), 42);
});
test('the shown tower is marked', () => {
  assert.equal(towerLabel({ id: 'a', displayName: 'Neutral', level: 3 }, 'a'), '> Neutral L3');
  assert.equal(towerLabel({ id: 'b', displayName: 'Rootvale', level: 0 }, 'a'), 'Rootvale L0');
});
