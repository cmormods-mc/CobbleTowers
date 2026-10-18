import test from 'node:test';
import assert from 'node:assert/strict';
import { choiceOf, choicesOf, clearedText, hpTone, pathNodes, riskTone } from '../src/desk/choices.js';

const mod = { id: 'cobbletowers:far_sight', displayName: 'Far Sight', votes: 2, risk: 0, theme: 'scouting',
  lines: ['[+] Minor risk  *  +3% to your final payout', '[+] Your scouts see 3 floors further ahead.', 'The risk you carry is paid out when the tower ends.'] };
const room = { id: 'cobbletowers:event/forge_pay', displayName: 'Forge: every player pays 175 CobbleDollars for a battle relic', votes: 0, risk: -1, theme: 'event', lines: ['x'] };

test('a modifier card keeps its payout apart and drops the boilerplate from its face', () => {
  const c = choiceOf(mod, 0);
  assert.equal(c.kind, 'modifier');
  assert.equal(c.payout, '+3%');
  assert.equal(c.tone, 'ok');
  assert.equal(c.votes, 2);
  assert.deepEqual(c.facts.map((l) => l.text), ['Your scouts see 3 floors further ahead.']);
  assert.equal(c.lines.length, 3);
});

test('a room option names the room and carries the option as its text', () => {
  const c = choiceOf(room, 1);
  assert.equal(c.kind, 'event');
  assert.equal(c.title, 'Forge');
  assert.equal(c.sub, 'Every player pays 175 CobbleDollars for a battle relic');
  assert.equal(c.payout, null);
  assert.equal(c.tone, 'event');
});

test('choicesOf tolerates no draft and keeps card order and index', () => {
  assert.deepEqual(choicesOf(undefined), []);
  assert.deepEqual(choicesOf({ cards: [] }), []);
  assert.deepEqual(choicesOf({ cards: [mod, room] }).map((c) => c.index), [0, 1]);
});

test('risk tones', () => {
  assert.deepEqual([0, 1, 2, -1].map(riskTone), ['ok', 'warn', 'bad', 'event']);
});

test('the floor ladder marks cleared, current and milestone floors, and is empty without a cycle length', () => {
  const nodes = pathNodes({ cycleLength: 10, cycleFloor: 4, milestones: [5, 10] });
  assert.equal(nodes.length, 10);
  assert.deepEqual(nodes.slice(0, 6).map((n) => n.state), ['done', 'done', 'done', 'done', 'now', '']);
  assert.deepEqual(nodes.filter((n) => n.milestone).map((n) => n.n), [5, 10]);
  assert.deepEqual(pathNodes({ cycleLength: 0 }), []);
  assert.deepEqual(pathNodes(undefined), []);
  // the last floor cleared: nothing is current
  assert.ok(pathNodes({ cycleLength: 10, cycleFloor: 10, milestones: [] }).every((n) => n.state === 'done'));
});

test('the cleared line and the health colours', () => {
  assert.equal(clearedText(4, { cycleLength: 10, cycleFloor: 4 }), 'Floor 4 of 10 cleared');
  assert.equal(clearedText(14, { cycleLength: 10, cycleFloor: 4 }), 'Floor 4 of 10 cleared');
  assert.equal(clearedText(4, undefined), 'Floor 4 cleared');
  assert.deepEqual([0, 20, 50, 100].map(hpTone), ['out', 'low', 'mid', 'full']);
});
