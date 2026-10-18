import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultPick, prettyStatus, rankTone, residentsOf, shortName, standingOf, standingText, teamSummary } from '../src/desk/standings.js';

const st = (level, rank, toNext) => ({ tower: 'cobbletowers:tideforge', level, rank, toNext, ascension: 0 });

test('a standing reads as unranked, a gap to the next rank, or the top', () => {
  assert.equal(standingText(st(0, 'Unranked', 3)), 'Unranked');
  assert.equal(standingText(null), 'Unranked');
  assert.equal(standingText(st(5, 'Silver', 2)), 'Silver · 2 levels to next');
  assert.equal(standingText(st(9, 'Silver', 1)), 'Silver · 1 level to next');
  assert.equal(standingText(st(30, 'Champion', -1)), 'Champion (top rank)');
});

test('rank colours: known ranks, unknown ranks and level 0', () => {
  assert.equal(rankTone(st(5, 'Silver', 2)), 's');
  assert.equal(rankTone(st(1, 'Bronze', 4)), 'b');
  assert.equal(rankTone(st(10, 'Gold', 5)), 'g');
  assert.equal(rankTone(st(3, 'Mystery', 1)), 'n');
  assert.equal(rankTone(st(0, 'Bronze', 1)), 'n');
  assert.equal(rankTone(null), 'n');
});

test('standingOf finds a tower and tolerates a payload without extras', () => {
  const extras = { standings: [st(5, 'Silver', 2)] };
  assert.equal(standingOf(extras, 'cobbletowers:tideforge').rank, 'Silver');
  assert.equal(standingOf(extras, 'cobbletowers:rootvale'), null);
  assert.equal(standingOf(undefined, 'x'), null);
});

test('the team summary counts, ranges levels and counts the fainted', () => {
  assert.equal(teamSummary([]).text, 'No Pokémon in your party');
  assert.equal(teamSummary([{ level: 50, fainted: false }]).text, '1 Pokémon · Level 50');
  const s = teamSummary([{ level: 48, fainted: false }, { level: 62, fainted: true }, { level: 50, fainted: true }]);
  assert.equal(s.text, '3 of 6 · Levels 48 to 62');
  assert.equal(s.fainted, 2);
});

test('the Climb button starts on the run tower, else the first tower, else nothing', () => {
  const towers = [{ id: 'a:x' }, { id: 'a:y' }];
  assert.equal(defaultPick({ towers, extras: { runTower: 'a:y' } }), 'a:y');
  assert.equal(defaultPick({ towers, extras: { runTower: 'a:gone' } }), 'a:x');
  assert.equal(defaultPick({ towers, extras: undefined }), 'a:x');
  assert.equal(defaultPick({ towers: [], extras: undefined }), '');
});

test('residents come from the build data and are at most three; unknown towers have none', () => {
  const r = residentsOf('cobbletowers:tideforge');
  assert.ok(r.length >= 1 && r.length <= 3);
  assert.deepEqual(residentsOf('cobbletowers:nowhere'), []);
});

test('tower names lose their suffix and the run status is made readable', () => {
  assert.equal(shortName('Tideforge Tower'), 'Tideforge');
  assert.equal(shortName('Test Tower (Poke Ball Spire)'), 'Test Tower (Poke Ball Spire)');
  assert.equal(prettyStatus('Floor 6 / INTERMISSION'), 'Floor 6 · Intermission');
  assert.equal(prettyStatus('Floor 2 / IN PROGRESS'), 'Floor 2 · In progress');
  assert.equal(prettyStatus(''), '');
});
