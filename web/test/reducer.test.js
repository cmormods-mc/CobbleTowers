import test from 'node:test';
import assert from 'node:assert/strict';
import { initial, reduce, closeReward } from '../src/reducer.js';

const inter = (open) => ({ floor: 2, draft: null, members: [], countdown: -1, message: '', open });

test('an intermission that is not flagged open does not open a page by itself', () => {
  const s = reduce(initial, 'IntermissionState', inter(false), 1);
  assert.equal(s.screen, null);
  assert.equal(s.inter.floor, 2);
});

test('an open intermission shows, and later updates keep showing it', () => {
  let s = reduce(initial, 'IntermissionState', inter(true), 1);
  assert.equal(s.screen, 'intermission');
  s = reduce(s, 'IntermissionState', inter(false), 5);
  assert.equal(s.screen, 'intermission');
  assert.equal(s.interAt, 5);
});

test('the reward reveal sits over the screen and closes the vendor', () => {
  let s = reduce(initial, 'VendorCatalog', { services: [], team: [], cobbleDollars: 1, message: '' }, 1);
  assert.equal(s.vendorOpen, true);
  s = reduce(s, 'RewardReveal', { floorIndex: 3, grants: [] }, 2);
  assert.equal(s.vendorOpen, false);
  assert.equal(s.reward.floorIndex, 3);
  assert.equal(closeReward(s).reward, null);
});

test('a vendor catalog that arrives behind a reward does not pop over it', () => {
  let s = reduce(initial, 'RewardReveal', { floorIndex: 1, grants: [] }, 1);
  s = reduce(s, 'VendorCatalog', { services: [], team: [], cobbleDollars: 1, message: '' }, 2);
  assert.equal(s.vendorOpen, false);
});

test('a rental draft opens its screen and its message becomes a notice', () => {
  const s = reduce(initial, 'RentalDraft', { packs: [], current: 0, complete: false, message: 'Pick two.' }, 1);
  assert.equal(s.screen, 'draft');
  assert.equal(s.notice.text, 'Pick two.');
  const t = reduce(s, 'RentalDraft', { packs: [], current: 1, complete: false, message: 'Pick two.' }, 2);
  assert.notEqual(t.notice.id, s.notice.id, 'the same text again is a new notice');
});

test('run effects only feed the HUD slice', () => {
  const s = reduce(initial, 'RunEffects', { floor: 4, riskPercent: 20, items: [] }, 1);
  assert.equal(s.fx.floor, 4);
  assert.equal(s.screen, null);
});

test('an unknown kind changes nothing', () => {
  assert.equal(reduce(initial, 'SomethingNew', {}, 1), initial);
});
