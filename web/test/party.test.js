import test from 'node:test';
import assert from 'node:assert/strict';
import { hint, pageCount, perPage, rows } from '../src/party.js';

const reg = (n, chosen = []) => ({ max: 6, chosen, pokemon: Array.from({ length: n }, (_, i) => ({ id: 'p' + i, name: 'Mon' + i, level: 10 + i, fainted: i === 1, where: 'Box 1' })) });

test('rows show the registration order and a fainted note', () => {
  const r = rows(reg(3, ['p2', 'p0']), 0, 8);
  assert.equal(r[0].label, '[2] Mon0  Lv 10  - Box 1');
  assert.equal(r[1].label, '[ ] Mon1  Lv 11  (fainted)  - Box 1');
  assert.equal(r[2].chosen, true);
});
test('paging and the rows that fit', () => {
  assert.equal(pageCount(reg(17), 8), 3);
  assert.equal(pageCount(reg(0), 8), 1);
  assert.equal(rows(reg(17), 2, 8).length, 1);
  assert.equal(perPage(240), 6);
  assert.equal(perPage(100), 3);
});
test('the hint', () => {
  assert.match(hint(reg(2)), /current party will be used/);
  assert.equal(hint(reg(2, ['p0'])), '1 of 6 registered');
});
