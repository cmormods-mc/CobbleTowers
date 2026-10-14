import test from 'node:test';
import assert from 'node:assert/strict';
import { ICON_NAMES } from '../tools/native-art.mjs';
import { itemPicture, SPECS } from '../src/itemart.js';

test('items get a shape and a colour from their name', () => {
  const cases = {
    'cobblemon:exp_candy_xl': 'candy_xl', 'cobblemon:rare_candy': 'candy_rare', 'cobblemon:poke_ball': 'ball_poke', 'cobblemon:dusk_ball': 'ball_other',
    'cobblemon:max_revive': 'bottle_gold', 'cobblemon:protein': 'bottle_green', 'cobblemon:lum_berry': 'berry_green', 'cobblemon:red_apricorn': 'nut_red',
    'cobblemon:water_stone': 'gem_blue', 'minecraft:diamond': 'gem_cyan', 'minecraft:enchanted_golden_apple': 'apple_gold',
    'minecraft:white_banner': 'banner', 'minecraft:flower_banner_pattern': 'scroll', 'cobblemon_cards:booster_pack_water': 'pack', 'cobblemon_cards:god_pack_ticket': 'ticket',
  };
  for (const [id, want] of Object.entries(cases)) assert.equal(itemPicture(id), want, id);
  assert.equal(itemPicture('somemod:mystery_widget'), null);
});

test('every picture the picker can name is drawn in the icon sheet', () => {
  for (const name of Object.keys(SPECS)) assert.ok(ICON_NAMES.includes(name), name);
  assert.ok(ICON_NAMES.includes('scroll'));
});

test('every shipped reward item resolves to something drawn or to the gift', async () => {
  const fs = await import('node:fs');
  const path = await import('node:path');
  const dir = path.resolve(import.meta.dirname, '../../src/main/resources/data/cobbletowers/cobbletowers/reward_tables');
  const ids = new Set();
  for (const f of fs.readdirSync(dir)) for (const m of fs.readFileSync(path.join(dir, f), 'utf8').matchAll(/"item":\s*"([^"]+)"/g)) ids.add(m[1]);
  assert.ok(ids.size > 30);
  for (const id of ids) { const pic = itemPicture(id); assert.ok(pic === null || ICON_NAMES.includes(pic), id + ' -> ' + pic); }
});
