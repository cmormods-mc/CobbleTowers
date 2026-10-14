import test from 'node:test';
import assert from 'node:assert/strict';
import { canBuy, grantIcon, stockLine } from '../src/rewards.js';

test('currencies have no item: they are told apart by id or label', () => {
  assert.equal(grantIcon({ item: 'cobbletowers:cobbledollars', label: '' }), 'coin');
  assert.equal(grantIcon({ item: 'x:y', label: 'Raid Points' }), 'raid');
  assert.equal(grantIcon({ item: 'cobblemon:potion', label: '' }), 'item');
});
test('buying needs money, stock and an online recipient', () => {
  const vendor = { cobbleDollars: 20 };
  const online = { online: true };
  assert.equal(canBuy(vendor, { priceCobbleDollars: 10, remainingPurchases: -1 }, online), true);
  assert.equal(canBuy(vendor, { priceCobbleDollars: 25, remainingPurchases: -1 }, online), false);
  assert.equal(canBuy(vendor, { priceCobbleDollars: 10, remainingPurchases: 0 }, online), false);
  assert.equal(canBuy(vendor, { priceCobbleDollars: 10, remainingPurchases: 2 }, { online: false }), false);
  assert.equal(canBuy(vendor, { priceCobbleDollars: 10, remainingPurchases: 2 }, undefined), false);
  assert.equal(stockLine({ remainingPurchases: -1 }), 'Stock: unlimited');
  assert.equal(stockLine({ remainingPurchases: 3 }), 'Stock: 3');
});
