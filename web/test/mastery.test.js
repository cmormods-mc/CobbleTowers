import test from 'node:test';
import assert from 'node:assert/strict';
import { badgeView, boardLines, nodesPerPage, tabWidth, towerLabel } from '../src/mastery.js';

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

test('the badge controls name the tower on the name and offer pin, unpin or nothing', () => {
  const towers = [
    { id: 'a', displayName: 'Neutral', level: 7, badge: 0 },
    { id: 'b', displayName: 'Tideforge', level: 3, badge: 2 },
    { id: 'c', displayName: 'Rootvale', level: 0, badge: 0 },
  ];
  assert.deepEqual(badgeView(towers, 'a'), { line: 'Tideforge', mode: 'pinned', button: { label: 'Show on name', action: 'pin', disabled: false } });
  assert.equal(badgeView(towers, 'b').button.action, 'auto');
  assert.equal(badgeView(towers, 'c').button.disabled, true);
  const auto = towers.map((t) => ({ ...t, badge: t.id === 'a' ? 1 : 0 }));
  assert.equal(badgeView(auto, 'a').mode, 'automatic');
  assert.equal(badgeView(auto, 'a').button.action, 'pin');
  assert.deepEqual(badgeView(towers.map((t) => ({ ...t, badge: 0 })), 'a').line, 'off');
});

test('the guide has a title, sections and short lines, and flattens in reading order', async () => {
  const { guideLines } = await import('../src/screens/Guide.js');
  const lines = guideLines({ title: 'T', sections: [{ heading: 'A', lines: ['one', 'two'] }, { heading: 'B', lines: ['three'] }] });
  assert.deepEqual(lines.map((l) => l.kind + ':' + l.text), ['title:T', 'heading:A', 'line:one', 'line:two', 'heading:B', 'line:three']);
  const real = guideLines();
  assert.ok(real.length > 10);
  assert.ok(real.every((l) => l.text.length > 0 && l.text.length < 260), 'every guide line is short enough to read');
});
