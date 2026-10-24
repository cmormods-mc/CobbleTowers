import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { memberText, pairAfterPick, pickerOptions, regionName, verdict } from '../src/desk/ranksView.js';
import { initial, reduce } from '../src/reducer.js';

const ranks = (mutate) => {
  const d = JSON.parse(readFileSync(new URL('../fixtures/RanksState.json', import.meta.url), 'utf8')).data;
  if (mutate) mutate(d);
  return d;
};

test('the verdict counts the stats each side leads and says who is ahead', () => {
  assert.deepEqual(verdict(ranks()), { left: 2, right: 1, text: 'Tideforge leads 2 to 1' });
  assert.equal(verdict(ranks((d) => d.rows.forEach((r) => { r.lead = 1; }))).text, 'Rootvale leads 3 to 0');
  assert.equal(verdict(ranks((d) => d.rows.forEach((r) => { r.lead = 0; }))).text, 'Level at 0 each');
});

test('the picker marks the chosen region, the viewer\'s own, and the one the other side holds', () => {
  const left = pickerOptions(ranks(), 'left');
  assert.deepEqual(left.map((o) => [o.id, o.chosen, o.mine, o.taken]), [['tideforge', true, true, false], ['rootvale', false, false, true], ['duskvale', false, false, false]]);
});

test('picking the other side\'s region swaps the sides; any other pick changes only that side', () => {
  const r = ranks();
  assert.deepEqual(pairAfterPick(r, 'left', 'rootvale'), { left: 'rootvale', right: 'tideforge' });
  assert.deepEqual(pairAfterPick(r, 'left', 'duskvale'), { left: 'duskvale', right: 'rootvale' });
  assert.deepEqual(pairAfterPick(r, 'right', 'tideforge'), { left: 'rootvale', right: 'tideforge' });
  assert.deepEqual(pairAfterPick(r, 'right', 'duskvale'), { left: 'tideforge', right: 'duskvale' });
});

test('names and member counts read plainly', () => {
  assert.equal(regionName(ranks(), 'duskvale'), 'Duskvale');
  assert.equal(regionName(ranks(), 'unknown'), 'unknown');
  assert.equal(memberText(0), 'no players yet');
  assert.equal(memberText(1), '1 player');
  assert.equal(memberText(41), '41 players');
});

test('a RanksState is kept for the tab and never opens a screen by itself', () => {
  const next = reduce(initial, 'RanksState', ranks(), 1);
  assert.equal(next.ranks.right, 'rootvale');
  assert.equal(next.screen, initial.screen);
  assert.equal(next.view, initial.view);
});
