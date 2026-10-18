import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { prettyIds, SOCIAL, towerBrief, trialLines, whyNot } from '../src/desk/briefing.js';
import { asksFirst, entryList, featureMode, pillFor, previewOf } from '../src/desk/featureView.js';

const fixture = (name, mutate) => {
  const d = JSON.parse(readFileSync(new URL('../fixtures/' + name + '.json', import.meta.url), 'utf8')).data;
  if (mutate) mutate(d);
  return d;
};

test('a tower brief has its facts, residents and standing; a gone tower says so', () => {
  const hall = fixture('TowerHallState');
  const b = towerBrief(hall, 'cobbletowers:tideforge');
  assert.ok(b.found);
  assert.equal(b.name, 'Tideforge');
  assert.ok(b.facts.some(([k, v]) => k === 'Floors' && v === '10 per cycle'));
  assert.ok(b.facts.some(([k, v]) => k === 'Your mastery' && /Silver/.test(v)));
  assert.ok(b.residents.length >= 1);
  assert.equal(towerBrief(hall, 'cobbletowers:nowhere').found, false);
});

test('why a tower cannot be selected', () => {
  const free = fixture('TowerHallState', (d) => { d.runStatus = ''; d.play.lobby.role = 0; d.play.lobby.countdown = -1; });
  assert.equal(whyNot(free, false), '');
  assert.match(whyNot(free, true), /Waiting/);
  assert.match(whyNot(fixture('TowerHallState'), false), /run in progress/);
  const counting = fixture('TowerHallState', (d) => { d.runStatus = ''; d.play.lobby.role = 1; d.play.lobby.countdown = 4; });
  assert.match(whyNot(counting, false), /countdown/);
  const guest = fixture('TowerHallState', (d) => { d.runStatus = ''; d.play.lobby.role = 2; d.play.lobby.countdown = -1; });
  assert.match(whyNot(guest, false), /host/);
});

test('trial lines split the scored note off and tidy raw ids', () => {
  const t = { rules: ['5 floors / 2026-10-09', 'Starting modifiers: cobbletowers:double_trouble, cobbletowers:downpour', 'First launch is scored; later attempts are practice.'] };
  const l = trialLines(t);
  assert.equal(l.facts.length, 2);
  assert.equal(l.facts[1], 'Starting modifiers: Double Trouble, Downpour');
  assert.match(l.note, /First launch is scored/);
  assert.deepEqual(trialLines(undefined), { facts: [], note: '' });
  assert.equal(prettyIds('Mode: Level Cap 50 at 12:30'), 'Mode: Level Cap 50 at 12:30');
});

test('the social cards name where each opens', () => {
  assert.deepEqual(SOCIAL.map((c) => c.open), ['lobby', 'club', 'watch', 'codes']);
});

test('feature entries come before actions, and a list entry has a one-line preview', () => {
  const data = fixture('TowerFeatureState');
  const list = entryList(data);
  assert.equal(list[0].kind, 'entry');
  assert.equal(list.at(-1).kind, 'action');
  assert.equal(list.length, data.entries.length + data.actions.length);
  assert.match(previewOf(data, 'title:champion'), /^Unlocked at Gold\./);
  assert.equal(previewOf(data, 'nope'), '');
  assert.ok(previewOf({ entries: [{ id: 'x', lines: ['a'.repeat(200)] }] }, 'x').endsWith('…'));
});

test('the feature menu is a list, an open entry, or a review', () => {
  const data = fixture('TowerFeatureState');
  assert.equal(featureMode(data, { sel: null, review: null }).kind, 'list');
  const entry = featureMode(data, { sel: 'title:champion', review: null });
  assert.equal(entry.kind, 'entry');
  assert.equal(entry.primary.id, 'title-wear');
  assert.equal(featureMode(data, { sel: 'title:rookie', review: null }).primary, null);
  const review = featureMode(data, { sel: 'club', review: data.entries[3].actions[0] });
  assert.equal(review.kind, 'review');
  assert.ok(review.needsInput);
  assert.equal(featureMode(data, { sel: 'gone', review: null }).kind, 'list');
});

test('an action asks first when it has a confirmation or typed text; the rail pill follows the section', () => {
  assert.ok(asksFirst({ id: 'a', confirmation: 'Sure?', inputHint: '' }));
  assert.ok(asksFirst({ id: 'a', confirmation: '', inputHint: 'Name' }));
  assert.ok(!asksFirst({ id: 'a', confirmation: '', inputHint: '' }));
  assert.equal(pillFor('modifiers'), 'codex');
  assert.equal(pillFor('relics'), 'codex');
  assert.equal(pillFor('cosmetics'), '');
});
