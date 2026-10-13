import test from 'node:test';
import assert from 'node:assert/strict';
import { featureView, freshUi, uiAfterReply, actionPlan, rowsFor, iconOf } from '../src/features.js';
import { initial, reduce, beginFeature, featureDelayed } from '../src/reducer.js';

const act = (id, extra = {}) => ({ id, label: id, argument: '', confirmation: '', inputHint: '', ...extra });
const entry = (id, actions = []) => ({ id, title: 'T ' + id, lines: ['a'], actions });
const data = (entries, actions = [], extra = {}) => ({ requestId: 1, section: 'club', title: 'Club', entries, actions, message: '', ...extra });

test('entries then page actions are listed together and paged by the rows that fit', () => {
  const d = data(Array.from({ length: 5 }, (_, i) => entry('e' + i)), [act('page')]);
  assert.equal(rowsFor(240, 43), 4);
  const v = featureView(d, freshUi(), 240, 43);
  assert.equal(v.pages, 2);
  assert.deepEqual(v.items.map((i) => i.id), ['e0', 'e1', 'e2', 'e3']);
  const w = featureView(d, { ...freshUi(), page: 1 }, 240, 43);
  assert.deepEqual(w.items.map((i) => i.kind), ['entry', 'action']);
  assert.equal(featureView(d, { ...freshUi(), page: 9 }, 240, 43).page, 1, 'a page past the end is clamped');
});

test('an open entry shows its lines and its first action; a review shows the confirmation', () => {
  const d = data([entry('e0', [act('go', { label: 'Go', confirmation: 'Really go?', inputHint: 'Name' })])]);
  const open = featureView(d, { ...freshUi(), sel: 'e0' }, 240, 43);
  assert.equal(open.mode, 'entry');
  assert.equal(open.primary.id, 'go');
  const review = featureView(d, { ...freshUi(), sel: 'e0', review: d.entries[0].actions[0] }, 240, 43);
  assert.equal(review.mode, 'review');
  assert.deepEqual(review.lines, ['Really go?']);
  assert.equal(review.needsInput, true);
});

test('what choosing an action does', () => {
  assert.equal(actionPlan(act('lobby')), 'lobby');
  assert.equal(actionPlan(act('page', { argument: 'run' })), 'page');
  assert.equal(actionPlan(act('x', { confirmation: 'sure?' })), 'review');
  assert.equal(actionPlan(act('x', { inputHint: 'text' })), 'review');
  assert.equal(actionPlan(act('x')), 'send');
});

test('after a reply an open entry stays if it is still there, a message opens the first, a review closes', () => {
  const ui = { ...freshUi(), sel: 'e1', review: act('x'), typed: 'abc', page: 3 };
  assert.equal(uiAfterReply(ui, data([entry('e0'), entry('e1')])).sel, 'e1');
  assert.equal(uiAfterReply(ui, data([entry('e0')])).sel, null);
  assert.equal(uiAfterReply(ui, data([entry('e0'), entry('e1')], [], { message: 'Saved.' })).sel, 'e0');
  const after = uiAfterReply(ui, data([entry('e1')]));
  assert.equal(after.review, null);
  assert.equal(after.typed, '');
  assert.equal(after.page, 0);
});

test('a reply for another request or section is ignored; id 0 (the server opening it) is taken', () => {
  let s = beginFeature(initial, 'club', 1);
  assert.equal(s.fReq, 1);
  assert.equal(s.view, 'feature');
  const late = reduce(s, 'TowerFeatureState', data([entry('e')], [], { requestId: 0 + 7, section: 'club' }), 5);
  assert.equal(late, s, 'wrong request id');
  assert.equal(reduce(s, 'TowerFeatureState', data([entry('e')], [], { requestId: 1, section: 'season' }), 5), s, 'wrong section');
  const ok = reduce(s, 'TowerFeatureState', data([entry('e')], [], { requestId: 1 }), 5);
  assert.equal(ok.feature.section, 'club');
  assert.equal(ok.fPending, 0);
  const cmd = reduce(initial, 'TowerFeatureState', data([entry('e')], [], { requestId: 0, section: 'run' }), 5);
  assert.equal(cmd.screen, 'hall');
  assert.equal(cmd.view, 'feature');
  assert.equal(cmd.fSection, 'run');
});

test('a section switch clears the old menu; no reply in time marks it delayed', () => {
  let s = reduce(beginFeature(initial, 'club', 1), 'TowerFeatureState', data([entry('e')], [], { requestId: 1 }), 2);
  s = beginFeature(s, 'season', 3);
  assert.equal(s.feature, null);
  const d = featureDelayed(s);
  assert.equal(d.fUncertain, true);
  assert.equal(d.fPending, 0);
  assert.equal(featureDelayed(d), d);
});

test('every section has an icon', () => {
  assert.equal(iconOf('contracts'), 'scroll');
  assert.equal(iconOf('modifiers'), 'book');
});
