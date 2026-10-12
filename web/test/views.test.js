import test from 'node:test';
import assert from 'node:assert/strict';
import { parseLine } from '../src/lines.js';
import { adaptCard } from '../src/cards.js';
import { fmtClock, pretty } from '../src/util.js';

test('modifier lines: benefits are green, costs red, plain lines neutral', () => {
  assert.deepEqual(parseLine('[+] Loot swells'), { sign: '+', text: 'Loot swells', tone: 'benefit' });
  assert.deepEqual(parseLine('[-] Start at 60% HP.'), { sign: '−', text: 'Start at 60% HP.', tone: 'cost' });
  assert.deepEqual(parseLine('The ground shifts'), { sign: '', text: 'The ground shifts', tone: 'neutral' });
});

test('the server\'s ASCII separator becomes a middle dot', () => {
  assert.equal(parseLine('[+] Minor risk  *  +3% to your final payout').text, 'Minor risk · +3% to your final payout');
});

test('a rental card takes display names from the tables and types from the sets', () => {
  const card = adaptCard({ species: 'arcanine', name: 'Arcanine', rarity: 'common', details: { level: 50, ability: 'intimidate', item: 'life_orb',
    moves: [{ id: 'flareblitz', type: 'fire', category: 'physical' }, { id: 'stoneedge', type: '', category: '' }] } });
  assert.equal(card.ability, 'Intimidate');
  assert.equal(card.item, 'Life Orb');
  assert.deepEqual(card.types, ['fire']);
  assert.equal(card.moves[0].name, 'Flare Blitz');
  assert.deepEqual(card.moves[1], { name: 'Stone Edge', type: 'rock', cat: 0 }, 'an empty type falls back to the table');
});

test('clock and names', () => {
  assert.equal(fmtClock(83), '1:23');
  assert.equal(fmtClock(-4), '0:00');
  assert.equal(pretty('life_orb'), 'Life Orb');
});
