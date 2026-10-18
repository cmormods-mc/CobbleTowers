import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { chooserHint, hasRoom, monCards, spriteOf } from '../src/desk/partyView.js';
import { hasSprite } from '../src/desk/standings.js';

const reg = (mutate) => {
  const d = JSON.parse(readFileSync(new URL('../fixtures/RegistrationState.json', import.meta.url), 'utf8')).data;
  if (mutate) mutate(d);
  return d;
};

test('sprite names are the display name without punctuation or case', () => {
  assert.equal(spriteOf('Mr. Mime'), 'mrmime');
  assert.equal(spriteOf('Nidoran-F'), 'nidoranf');
  assert.equal(spriteOf('Charizard'), 'charizard');
  assert.ok(hasSprite(spriteOf('Mr. Mime')));
  assert.ok(hasSprite(spriteOf('Pikachu')));
});

test('every owned Pokemon is a card with its place in the registered team', () => {
  const cards = monCards(reg());
  assert.equal(cards.length, 10);
  assert.deepEqual(cards.filter((c) => c.order).map((c) => [c.name, c.order]).sort((a, b) => a[1] - b[1]).length, 2);
  assert.ok(cards.filter((c) => c.order === 0).length === 8);
  assert.equal(cards[0].species, 'charizard');
  assert.equal(cards[2].fainted, true);
});

test('the hint says what an empty choice does, or how many are registered', () => {
  assert.match(chooserHint(reg((d) => { d.chosen = []; })), /current party will be used/);
  assert.equal(chooserHint(reg()), '2 of 6 registered');
  assert.equal(hasRoom(reg()), true);
  assert.equal(hasRoom(reg((d) => { d.chosen = d.pokemon.slice(0, 6).map((p) => p.id); })), false);
});
