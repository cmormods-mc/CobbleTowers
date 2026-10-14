import test from 'node:test';
import assert from 'node:assert/strict';
import { cardView, offerHint } from '../src/rooms.js';

const modifier = { name: 'Far Sight', displayName: 'Far Sight', risk: 0, lines: ['[+] Minor risk', 'The risk you carry is paid out when the tower ends.'] };
const relic = { name: 'Ember Brand', displayName: 'Ember Brand', risk: 0, lines: ['[+] Fire moves deal more damage.', 'Tags: offense, risk', 'Held for this run; not permanent equipment.'] };
const room = { name: 'Forge: every player pays 175 CobbleDollars for a battle relic', displayName: 'Forge: every player pays 175 CobbleDollars for a battle relic', risk: -1, lines: ['x'] };

test('a room option splits at the colon: the room is the title, the choice is the text', () => {
  const v = cardView(room);
  assert.equal(v.kind, 'event');
  assert.equal(v.title, 'Forge');
  assert.equal(v.sub, 'Every player pays 175 CobbleDollars for a battle relic');
  assert.deepEqual(cardView({ ...room, name: 'Plain', displayName: 'Plain' }).lines, ['Plain']);
});
test('a relic shows its tags and drops the "held for this run" boilerplate', () => {
  const v = cardView(relic);
  assert.equal(v.kind, 'relic');
  assert.equal(v.sub, 'Relic: offense, risk');
  assert.ok(!v.lines.includes('Held for this run; not permanent equipment.'));
});
test('a modifier keeps its risk heading', () => {
  assert.equal(cardView(modifier).sub, 'Minor risk');
  assert.equal(cardView(modifier).kind, 'modifier');
});
test('the hint says what is on offer', () => {
  assert.equal(offerHint([relic, relic]), 'Choose a relic to carry');
  assert.equal(offerHint([room, room]), 'A room on the way: choose');
  assert.equal(offerHint([modifier]), 'Choose a modifier for the next floor');
  assert.equal(offerHint([]), 'No modifier this floor');
});
