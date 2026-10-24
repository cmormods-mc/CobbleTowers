import test from 'node:test';
import assert from 'node:assert/strict';
import { adaptCard } from '../src/cards.js';
import { lift, natureEffect, revealMs, tierOf, tierOfAscension, typeHex } from '../src/cardTheme.js';
import { tipHtml } from '../src/components/InspectTip.js';

const arcanine = { set: 'cobbletowers:arcanine', species: 'arcanine', name: 'Arcanine', rarity: 'common', ascension: 'common', details: { level: 50, ability: 'intimidate', nature: 'adamant', item: 'life_orb', moves: [{ id: 'flareblitz', type: 'fire', category: 'physical' }] } };

test('a card carries the set\'s nature, IVs and EVs from the sets file, and the server\'s Ascension tier', () => {
  const c = adaptCard(arcanine);
  assert.equal(c.nature, 'Adamant');
  assert.equal(c.ivs.length, 6);
  assert.equal(c.evs.length, 6);
  assert.equal(c.ascension, 'common');
  assert.deepEqual(c.types, ['fire']);
});

test('without AscensionLib the server sends no tier and the card has none', () => {
  const c = adaptCard({ ...arcanine, ascension: undefined });
  assert.equal(c.ascension, '');
  assert.ok(!tipHtml(c).includes('Ascension:'));
});

test('the tooltip names the Ascension tier in the library\'s own spelling, mythical shown as Mythic', () => {
  assert.equal(tierOfAscension('mythical').n, 'Mythic');
  assert.equal(tierOfAscension('epic').n, 'Epic');
  assert.equal(tierOfAscension('nonsense'), null);
  const html = tipHtml(adaptCard({ ...arcanine, rarity: 'mythic', ascension: 'mythical' }));
  assert.match(html, /Ascension:<\/u> <b[^>]*>Mythic<\/b>/);
});

test('an unknown Ascension id adds no row rather than a wrong one', () => {
  assert.ok(!tipHtml(adaptCard({ ...arcanine, ascension: 'bogus' })).includes('Ascension:'));
});

test('the tooltip shows the nature\'s stats, the IV and EV totals and escapes names', () => {
  const html = tipHtml({ ...adaptCard(arcanine), name: '<b>x</b>' });
  assert.match(html, /\+ATK/);
  assert.match(html, /-SP\.ATK/);
  assert.match(html, /\/186/);
  assert.match(html, /\/510/);
  assert.ok(!html.includes('<b>x</b>'));
});

test('natures: the five neutral ones change nothing, the rest raise one stat and lower another', () => {
  assert.equal(natureEffect('hardy'), null);
  assert.deepEqual(natureEffect('Timid'), ['SPE', 'ATK']);
  assert.deepEqual(natureEffect('careful'), ['SP.DEF', 'SP.ATK']);
});

test('higher rarities charge longer and shake harder', () => {
  const order = ['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic'];
  for (let i = 1; i < order.length; i++) {
    assert.ok(tierOf(order[i]).ct > tierOf(order[i - 1]).ct);
    assert.ok(tierOf(order[i]).sh > tierOf(order[i - 1]).sh);
    assert.ok(revealMs(order[i]) > revealMs(order[i - 1]));
  }
});

test('type colours and the lift toward white', () => {
  assert.equal(typeHex('fire'), '#ee8130');
  assert.equal(typeHex('nonsense'), typeHex('normal'));
  assert.equal(lift('#000000', 0.5), '#808080');
});
