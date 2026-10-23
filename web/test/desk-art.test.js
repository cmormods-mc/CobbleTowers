import test from 'node:test';
import assert from 'node:assert/strict';
import { GLYPHS, PAL, pixelPaths, boxOf } from '../src/desk/pix.js';
import { artOf, paintScene, roomKindOf } from '../src/desk/modArtData.js';
import { RANK_MEDALS, TOWER_EMBLEMS, towerKey } from '../src/desk/emblem.js';
import { ATLAS } from '../src/gen/atlas.js';

test('every glyph is a rectangle drawn only in palette colours', () => {
  for (const [name, rows] of Object.entries(GLYPHS)) {
    assert.ok(rows.every((r) => r.length === rows[0].length), name + ' is a rectangle');
    for (const ch of rows.join('')) assert.ok(ch === '.' || PAL[ch], name + ' uses unknown colour ' + ch);
  }
});

test('a picture is one path per colour, and its outline is added only where it has none of its own', () => {
  const kCount = (grid, outline) => { const k = pixelPaths(grid, outline).find((p) => p.fill === PAL.K); return k ? (k.d.match(/M/g) || []).length : 0; };
  // the shield draws its own outline: adding one must not thicken it
  assert.equal(kCount(GLYPHS.shield, true), GLYPHS.shield.join('').split('K').length - 1);
  // the sun has none, so one is drawn around it
  assert.ok(kCount(GLYPHS.sun, true) > 20);
  assert.equal(kCount(GLYPHS.sun, false), 0);
  assert.deepEqual(boxOf(GLYPHS.sun, true), [GLYPHS.sun[0].length + 2, GLYPHS.sun.length + 2]);
  const paths = pixelPaths(GLYPHS.rain, true);
  assert.equal(new Set(paths.map((p) => p.fill)).size, paths.length);
});

// the theme keys ModifierArt.java produces
const THEMES = ['weather:raindance', 'weather:sunnyday', 'weather:sandstorm', 'weather:snowscape', 'weather:hail', 'terrain:electricterrain', 'terrain:psychicterrain', 'terrain:mistyterrain',
  'terrain:grassyterrain', 'enemy', 'enemy:tough', 'enemy:fragile', 'enemy:champion', 'enemy:veteran', 'enemy:novice', 'encounter', 'encounter:crowd', 'constraint', 'constraint:no_heal',
  'constraint:no_setup', 'constraint:no_switch', 'constraint:no_items', 'scouting', 'reward_up', 'reward_up:hoard', 'reward_down', 'custom:glass_cannon', 'custom:fortunes_wheel',
  'custom:field_hospital', 'custom:black_market', 'custom:swift_start', 'custom:iron_hide', 'custom:war_banner', 'custom:something_new', 'unknown', 'event', '', 'custom:'];

test('every theme key the server can send has a picture, a colour set and a sprite the page knows', () => {
  for (const theme of THEMES) {
    const a = artOf(theme, theme === 'event' ? 'event' : 'modifier', 'Forge');
    assert.ok(a.room || GLYPHS[a.glyph], theme + ' has a glyph or a room icon');
    assert.equal(a.look.length, 3, theme);
    for (const c of a.look) assert.match(c, /^#[0-9a-f]{6}$/i, theme);
    assert.match(a.k, /^#[0-9a-f]{6}$/i);
    assert.ok(a.cat.length > 0);
    if (a.sprite) assert.ok(ATLAS.map[a.sprite], theme + ' sprite ' + a.sprite + ' is on the sheet');
  }
  assert.equal(artOf(null).glyph, 'crest');
  assert.equal(artOf(undefined).glyph, 'crest');
});

test('art says only what a modifier does', () => {
  assert.equal(artOf('weather:raindance').glyph, 'rain');
  assert.equal(artOf('weather:sunnyday').glyph, 'sun');
  assert.equal(artOf('terrain:electricterrain').glyph, 'bolt');
  assert.equal(artOf('reward_up:hoard').glyph, 'coffer');
  assert.equal(artOf('reward_down').glyph, 'shut');
  assert.notEqual(artOf('reward_up:hoard').glyph, artOf('reward_down').glyph);
  assert.equal(artOf('enemy:tough').glyph, 'shield');
  assert.equal(artOf('enemy:fragile').glyph, 'cracked');
  assert.equal(artOf('enemy:novice').flip, true);
  assert.equal(artOf('enemy:veteran').flip, false);
  assert.equal(artOf('encounter:crowd').glyph, 'crowd');
  for (const d of ['no_heal', 'no_setup', 'no_switch', 'no_items']) assert.equal(artOf('constraint:' + d).slash, true, d + ' is struck out');
  assert.equal(artOf('weather:raindance').slash, false);
  assert.equal(artOf('whatever').glyph, 'crest');
});

test('a relic wears a gem and a room option the icon of its room', () => {
  assert.equal(artOf('custom:', 'relic').glyph, 'gem');
  assert.equal(artOf('custom:', 'relic').cat, 'RELIC');
  assert.equal(artOf('event', 'event', 'Forge').room, 'FORGE');
  assert.equal(artOf('event', 'event', 'Mystic Pool').room, 'POOL');
  assert.equal(artOf('event', 'event', 'Something else').room, '');
  assert.equal(roomKindOf(' rest '), 'REST');
});

test('the scene is a deterministic opaque picture: sky above, ground below', () => {
  const a = artOf('weather:raindance');
  const px = paintScene(a, 96, 52);
  assert.equal(px.length, 96 * 52 * 4);
  assert.deepEqual([...paintScene(a, 96, 52)], [...px]);
  for (let i = 3; i < px.length; i += 4) assert.equal(px[i], 255);
  const at = (x, y) => [...px.slice((y * 96 + x) * 4, (y * 96 + x) * 4 + 3)];
  assert.notDeepEqual(at(40, 2), at(40, 50));
  assert.notDeepEqual([...paintScene(artOf('weather:sunnyday'), 96, 52)], [...px]);
});

test('a tower has a medallion of its own, and any other tower wears the star', () => {
  assert.equal(towerKey('cobbletowers:tideforge'), 'tideforge');
  assert.equal(towerKey('cobbletowers:test'), 'neutral');
  assert.equal(towerKey('cobbletowers:novel'), 'other');
  assert.equal(towerKey(''), 'other');
  for (const grid of [...Object.values(TOWER_EMBLEMS), ...Object.values(RANK_MEDALS)]) {
    assert.equal(grid.length, 8);
    assert.ok(grid.every((r) => r.length === 8));
    for (const ch of grid.join('')) assert.ok(ch === '.' || PAL[ch], 'medallion colour ' + ch);
  }
  assert.deepEqual(Object.keys(RANK_MEDALS), ['gold', 'bronze', 'silver', 'platinum', 'diamond', 'master', 'champion']);
  assert.notDeepEqual(RANK_MEDALS.bronze, RANK_MEDALS.silver);
});

test('a wheel notch is one row, and the part of a row left over carries to the next event', async () => {
  const { wheelSteps } = await import('../src/desk/stepScroll.js');
  assert.deepEqual(wheelSteps(0, 100), { steps: 1, acc: 0 });
  assert.deepEqual(wheelSteps(0, 10), { steps: 0, acc: 10 });
  assert.deepEqual(wheelSteps(90, 15), { steps: 1, acc: 5 });
  assert.deepEqual(wheelSteps(0, -250), { steps: -2, acc: -50 });
  assert.deepEqual(wheelSteps(0, 0), { steps: 0, acc: 0 });
});

test('a reward is judged by what it is: armor by tier, keys and rare candies above berries, big sums above small', async () => {
  const { grantTier, bestTier, TIER_LABEL } = await import('../src/desk/rewardView.js');
  const g = (item, amount = 1, label = '') => ({ item, amount, label });
  assert.equal(grantTier(g('cobblemon:oran_berry')), 'common');
  assert.equal(grantTier(g('cobblemon:potion', 2)), 'common');
  assert.equal(grantTier(g('cobblemon:ultra_ball')), 'fine');
  assert.equal(grantTier(g('cobblemon:rare_candy')), 'rare');
  assert.equal(grantTier(g('cobblemon:master_ball')), 'epic');
  assert.equal(grantTier(g('cobbletowers:recruit_boots')), 'fine');
  assert.equal(grantTier(g('cobbletowers:challenger_helmet')), 'rare');
  assert.equal(grantTier(g('cobbletowers:leviathan_chestplate')), 'epic');
  assert.equal(grantTier(g('cobbletowers:tower_key')), 'rare');
  assert.equal(grantTier(g('cobbletowers:novel_thing')), 'fine');
  // currencies have no item: they are named by label
  assert.equal(grantTier(g('', 50, 'CobbleDollars')), 'common');
  assert.equal(grantTier(g('', 2500, 'CobbleDollars')), 'fine');
  assert.equal(bestTier([g('cobblemon:oran_berry'), g('cobblemon:rare_candy')]), 'rare');
  assert.equal(bestTier([]), 'common');
  assert.equal(TIER_LABEL.common, '');
  assert.ok(TIER_LABEL.epic.length > 0);
});

test('a rank tile names its rank, and an achievement gets a picture by its name', async () => {
  const { rankOfNote, achievementGlyph } = await import('../src/desk/progressView.js');
  const { GLYPHS } = await import('../src/desk/pix.js');
  assert.equal(rankOfNote({ note: 'New rank: Silver' }), 'Silver');
  assert.equal(rankOfNote({ note: 'A reward note' }), '');
  assert.equal(rankOfNote({}), '');
  for (const name of ['First Steps', 'Ten Floors Down', 'Flawless', 'Speed Runner', 'Team Player', 'Ascendant', 'Iron Will', 'Collector', 'Something New']) {
    assert.ok(GLYPHS[achievementGlyph(name)], name);
  }
  assert.equal(achievementGlyph('Something New'), 'gem');
  assert.notEqual(achievementGlyph('Flawless'), achievementGlyph('Speed Runner'));
});

test('a choice carries the theme and risk the card art needs', async () => {
  const { choiceOf } = await import('../src/desk/choices.js');
  const c = choiceOf({ id: 'x', displayName: 'Downpour', votes: 0, risk: 1, theme: 'weather:raindance', lines: ['[+] Minor risk  *  +3% to your final payout'] }, 0);
  assert.equal(c.theme, 'weather:raindance');
  assert.equal(c.risk, 1);
  assert.equal(choiceOf({ id: 'y', displayName: 'Rest: heal', votes: 0, risk: -1, lines: ['x'] }, 0).theme, '');
});
