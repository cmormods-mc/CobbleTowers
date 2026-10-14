import test from 'node:test';
import assert from 'node:assert/strict';
import { apply, DEFAULTS, load, save } from '../src/settings.js';

const store = (init) => { const m = { ...init }; return { getItem: (k) => (k in m ? m[k] : null), setItem: (k, v) => { m[k] = v; }, m }; };

test('defaults when nothing or junk is stored; a saved choice comes back', () => {
  assert.deepEqual(load(store({})), DEFAULTS);
  assert.deepEqual(load(store({ 'cobbletowers.ui': 'not json' })), DEFAULTS);
  assert.deepEqual(load(store({ 'cobbletowers.ui': '{"motion":"yes"}' })), DEFAULTS);
  const s = store({});
  save({ motion: false, pixelFont: true }, s);
  assert.deepEqual(load(s), { motion: false, pixelFont: true });
});

test('apply sets the root classes', () => {
  const set = new Set();
  const root = { classList: { toggle: (c, on) => (on ? set.add(c) : set.delete(c)) } };
  apply({ motion: false, pixelFont: false }, root);
  assert.deepEqual([...set].sort(), ['calm', 'plainfont']);
  apply(DEFAULTS, root);
  assert.equal(set.size, 0);
});
