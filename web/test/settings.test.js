import test from 'node:test';
import assert from 'node:assert/strict';
import { apply, DEFAULTS, load, save, THEMES } from '../src/settings.js';

const store = (init) => { const m = { ...init }; return { getItem: (k) => (k in m ? m[k] : null), setItem: (k, v) => { m[k] = v; }, m }; };

test('defaults when nothing or junk is stored; a saved choice comes back', () => {
  assert.deepEqual(load(store({})), DEFAULTS);
  assert.deepEqual(load(store({ 'cobbletowers.ui': 'not json' })), DEFAULTS);
  assert.deepEqual(load(store({ 'cobbletowers.ui': '{"motion":"yes"}' })), DEFAULTS);
  const s = store({});
  save({ motion: false, pixelFont: true }, s);
  assert.deepEqual(load(s), { motion: false, pixelFont: true, look: 'desk', theme: 'midnight' });
  save({ motion: true, pixelFont: true, look: 'desk' }, s);
  assert.equal(load(s).look, 'desk');
  assert.equal(load(store({ 'cobbletowers.ui': '{"look":"weird"}' })).look, 'desk');
});

test('apply sets the root classes', () => {
  const set = new Set();
  const root = { classList: { toggle: (c, on) => (on ? set.add(c) : set.delete(c)) } };
  apply({ motion: false, pixelFont: false }, root);
  assert.deepEqual([...set].sort(), ['calm', 'plainfont']);
  apply(DEFAULTS, root);
  assert.equal(set.size, 0);
});

test('the theme defaults to midnight, keeps a known choice, and falls back on junk', () => {
  assert.equal(DEFAULTS.theme, 'midnight');
  assert.deepEqual(Object.keys(THEMES), ['midnight', 'graphite', 'harbor', 'dusk']);
  const s = store({});
  save({ ...DEFAULTS, theme: 'harbor' }, s);
  assert.equal(load(s).theme, 'harbor');
  assert.equal(load(store({ 'cobbletowers.ui': '{"theme":"neon"}' })).theme, 'midnight');
  assert.equal(load(store({ 'cobbletowers.ui': '{"theme":"toString"}' })).theme, 'midnight');
});

test('apply puts one theme class on the root (none for midnight) and takes the old one off', () => {
  const set = new Set();
  const root = { classList: { toggle: (c, on) => (on ? set.add(c) : set.delete(c)) } };
  apply({ ...DEFAULTS, theme: 'dusk' }, root);
  assert.deepEqual([...set], ['th-dusk']);
  apply({ ...DEFAULTS, theme: 'graphite' }, root);
  assert.deepEqual([...set], ['th-graphite']);
  apply({ ...DEFAULTS, theme: 'midnight' }, root);
  assert.equal(set.size, 0);
});
