// The pure half of the modifier art: which scene a server theme key means, and the pixels of its sky and ground. No DOM, so it is unit-tested.

/** [sky, lower sky, ground] */
const LOOKS = {
  rain: ['#3E5468', '#6B7F8C', '#2F4A5C'], sun: ['#B8793A', '#E3A95E', '#7C5A33'], sand: ['#9C7F4F', '#C9A66B', '#8A7048'], snow: ['#6F8CA3', '#B5C9D6', '#D6E0E6'],
  bolt: ['#4A4A3A', '#8C8A4A', '#6E6A2E'], orb: ['#4B3A5C', '#8C6AA0', '#6A4A80'], mist: ['#5C4350', '#B98FA0', '#8A6678'], grass: ['#47603F', '#7C9A5E', '#4F6B3A'],
  enemy: ['#4A2A2A', '#8A4A3C', '#3A2A24'], crowd: ['#4F4A52', '#8B8470', '#5A5246'], rule: ['#3C3A44', '#7C7568', '#4F4A42'], scout: ['#2E3A44', '#5E7080', '#3A4650'],
  up: ['#5A4630', '#B8935A', '#6B4A33'], down: ['#3A3A40', '#6A6860', '#48443C'], unknown: ['#4A4650', '#857D70', '#5A5246'], relic: ['#4A2E52', '#A06A8A', '#5A3A4A'],
  room: ['#2F3A52', '#6A7A9A', '#3A4560'],
  glass: ['#4A3E52', '#8A7C98', '#574C60'], wheel: ['#4A3A2A', '#B8935A', '#6B4A33'], hospital: ['#4A5A4A', '#9CB596', '#6A7C64'], market: ['#2E2A36', '#5E566C', '#3C3646'],
  swift: ['#3E5468', '#8CB0C8', '#4F6670'], iron: ['#44464C', '#868A94', '#5A5E66'], banner: ['#4A2E32', '#9C5A52', '#4A3A34'],
};

/** Category label and accent colour by kind of modifier; the accent frames the picture. */
const CATS = {
  field: ['FIELD', '#3b82d6'], enemy: ['ENEMY', '#d6264e'], rule: ['RULE', '#8c5cc9'], scout: ['SCOUT', '#0e9aa7'], reward: ['REWARD', '#c98a06'],
  special: ['SPECIAL', '#d9468f'], relic: ['RELIC', '#c98a06'], room: ['ROOM', '#6b7a99'], other: ['MODIFIER', '#6b7a99'],
};

const NAME_TO_KIND = { forge: 'FORGE', rest: 'REST', shrine: 'SHRINE', gambler: 'GAMBLER', library: 'LIBRARY', vault: 'VAULT', 'mystic pool': 'POOL' };

/** The room a room option is, from its title ("Forge"); empty when the title is not a known room. */
export const roomKindOf = (title) => NAME_TO_KIND[String(title || '').trim().toLowerCase()] || '';

const scene = (cat, look, glyph, sprite, extra = {}) => ({ cat, look: LOOKS[look], lookKey: look, glyph, sprite, ...extra });

const CUSTOM = {
  glass_cannon: ['glass', 'cannon', 'gallade'], fortunes_wheel: ['wheel', 'wheel', 'meowth'], field_hospital: ['hospital', 'cross', 'chansey'],
  black_market: ['market', 'mask', 'sableye'], swift_start: ['swift', 'dash', 'jolteon'], iron_hide: ['iron', 'plate', 'aggron'], war_banner: ['banner', 'banner', 'conkeldurr'],
};

/**
 * What a card looks like, from its server theme key and kind.
 * @param {string} theme e.g. "weather:raindance"
 * @param {'modifier'|'relic'|'event'} kind
 * @param {string} title the card's title: for a room option it names the room
 * @returns {{cat:string, k:string, look:string[], lookKey:string, glyph:string|null, sprite:string|null, slash:boolean, flip:boolean, at:'top'|'ground', overlay:string, room:string}}
 */
export function artOf(theme = '', kind = 'modifier', title = '') {
  const t = String(theme || ''), i = t.indexOf(':');
  const key = i < 0 ? t : t.slice(0, i), detail = i < 0 ? '' : t.slice(i + 1);
  let s;
  if (kind === 'event' || key === 'event') s = scene('room', 'room', null, null);
  else if (kind === 'relic') s = scene('relic', 'relic', 'gem', null);
  else if (key === 'weather') {
    s = /sunny|desolate/.test(detail) ? scene('field', 'sun', 'sun', 'charizard', { overlay: 'sun' })
      : detail === 'sandstorm' ? scene('field', 'sand', 'sand', 'tyranitar', { overlay: 'sand' })
        : /hail|snow/.test(detail) ? scene('field', 'snow', 'snow', 'glalie', { overlay: 'snow' })
          : scene('field', 'rain', 'rain', 'politoed', { overlay: 'rain' });
  } else if (key === 'terrain') {
    s = detail === 'electricterrain' ? scene('field', 'bolt', 'bolt', 'raichu', { overlay: 'zigzag' })
      : detail === 'psychicterrain' ? scene('field', 'orb', 'orb', 'alakazam', { overlay: 'motes' })
        : detail === 'mistyterrain' ? scene('field', 'mist', 'waves', 'gardevoir', { overlay: 'bands' })
          : scene('field', 'grass', 'grass', 'rillaboom', { overlay: 'blades' });
  } else if (key === 'enemy') {
    const glyph = detail === 'fragile' ? 'cracked' : detail === 'champion' ? 'crown' : /veteran|novice/.test(detail) ? 'up' : detail === 'tough' ? 'shield' : 'swords';
    s = scene('enemy', 'enemy', glyph, 'garchomp', { flip: detail === 'novice' });
  } else if (key === 'encounter') s = scene('enemy', 'crowd', detail === 'crowd' ? 'crowd' : 'swords', 'arcanine', { at: detail === 'crowd' ? 'ground' : 'top' });
  else if (key === 'constraint') {
    const g = { no_heal: 'plus', no_setup: 'up', no_switch: 'swap', no_items: 'bag' }[detail];
    s = scene('rule', 'rule', g || 'swap', 'registeel', { slash: !!g || detail === 'no_switch' });
  } else if (key === 'scouting') s = scene('scout', 'scout', 'lantern', 'noctowl');
  else if (key === 'reward_up') s = scene('reward', 'up', 'coffer', 'persian', { at: 'ground' });
  else if (key === 'reward_down') s = scene('reward', 'down', 'shut', 'slowpoke', { at: 'ground' });
  else if (key === 'custom' && CUSTOM[detail]) s = scene('special', CUSTOM[detail][0], CUSTOM[detail][1], CUSTOM[detail][2]);
  else s = scene('other', 'unknown', 'crest', null);
  const [label, accent] = CATS[s.cat];
  return { overlay: '', at: 'top', slash: false, flip: false, ...s, cat: label, k: accent, room: s.cat === 'room' ? roomKindOf(title) : '' };
}

/** The scene as RGBA pixels, `w` by `h`: stepped sky, ground, and a weather or terrain overlay. Pure and deterministic. */
export function paintScene(art, w, h) {
  const px = new Uint8ClampedArray(w * h * 4);
  const [sky, low, ground] = art.look.map((c) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16)));
  const horizon = Math.round(h * 0.64);
  const put = (x, y, c, a = 1) => {
    if (x < 0 || y < 0 || x >= w || y >= h) return;
    const o = (y * w + x) * 4;
    for (let k = 0; k < 3; k++) px[o + k] = a >= 1 ? c[k] : Math.round(px[o + k] * (1 - a) + c[k] * a);
    px[o + 3] = 255;
  };
  const mixc = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
  const half = horizon / 2;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    if (y >= horizon) put(x, y, ground);
    else put(x, y, y < half ? sky : mixc(sky, low, Math.min(3, Math.floor(((y - half) / (horizon - half)) * 4)) / 3));
  }
  for (let x = 0; x < w; x += 6) for (let k = 0; k < 3; k++) put(x + k, horizon, [0, 0, 0], 0.2);
  const dot = (x, y, c, a = 1) => { put(x, y, c, a); put(x + 1, y, c, a); put(x, y + 1, c, a); put(x + 1, y + 1, c, a); };
  switch (art.overlay) {
    case 'rain': for (let i = 0; i < w / 4; i++) { const x = (i * 17) % w, y = (i * 11) % (horizon - 4); for (let k = 0; k < 4; k++) put(x, y + k, [157, 184, 208], 0.75); } break;
    case 'sand': for (let i = 0; i < w / 3; i++) put((i * 29) % w, (i * 13) % horizon, [216, 183, 120], 0.6); break;
    case 'snow': for (let i = 0; i < w / 5; i++) { const x = (i * 23) % w, y = (i * 11) % horizon; put(x, y, [240, 223, 191]); put(x + 1, y, [240, 223, 191]); } break;
    case 'zigzag':
      for (let x = 4; x < w - 12; x += 18) {
        const y = horizon + 3 + ((x / 18) % 2) * 3;
        for (let k = 0; k < 6; k++) put(x + k, y, [227, 189, 127]);
        for (let k = 0; k < 4; k++) put(x + 5, y + k, [227, 189, 127]);
        for (let k = 0; k < 6; k++) put(x + 5 + k, y + 4, [227, 189, 127]);
      }
      break;
    case 'motes': for (let i = 0; i < w / 8; i++) dot(3 + i * 8, horizon + 2 + ((i * 5) % 9), [217, 182, 232]); break;
    case 'bands': for (let y = horizon; y < h; y += 4) for (let x = 0; x < w; x++) { put(x, y, [240, 223, 191], 0.27); put(x, y + 1, [240, 223, 191], 0.27); } break;
    case 'blades': for (let x = 2; x < w; x += 5) for (let k = 0; k < 4 + (x % 3); k++) put(x, horizon + 1 + (x % 3) - k, [44, 82, 55]); break;
    case 'sun': for (let y = horizon - 10; y < horizon; y++) for (let x = 0; x < w; x++) put(x, y, [255, 236, 170], 0.12); break;
    default: break;
  }
  return px;
}
