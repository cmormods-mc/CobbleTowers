// Small pixel pictures for the Frontier Desk: one grid of characters per picture, drawn as a single crisp SVG (one path per colour).
// The same hand-drawn grids serve the modifier cards, the route map, the Social tiles and the scouting report, so they all share a look.
import { html } from '../html.js';

/** One letter per colour. "K" is the outline colour; `outline` adds it around a picture automatically. */
export const PAL = {
  K: '#211510', W: '#f4e8d0', w: '#bcd4ea', g: '#e3bd7f', G: '#a77b46', y: '#f6dc7a', Y: '#c88a1e', n: '#6b4a33', r: '#c9473c', R: '#8e2420',
  b: '#7fb0e0', B: '#3f6fb0', Z: '#fff1b8', u: '#1e54ac', o: '#2c7e3e', k: '#522c82', j: '#962432', z: '#5c5e78',
  c: '#d08a4e', C: '#8a4a22', v: '#e4eaf4', V: '#8c97aa', a: '#8fe8ee', A: '#3b9aa6', x: '#bfe9ff', X: '#4d9ad0', s: '#9aa4b0', S: '#5f6878', d: '#4a4658', e: '#6fb552', h: '#b4ea96', p: '#a070d8', P: '#5a3a98', q: '#ecd8ff', m: '#d8cdbb',
};

const ROWS = {
  rain: ['....KKKK....', '..KKbbbbKK..', '.KbbbbbbbbK.', 'KbbbbbbbbbbK', 'KbbbbbbbbbbK', '.KKKKKKKKKK.', '..B..B..B...', '.B..B..B..B.', '..B..B..B...'],
  sun: ['.....y.....', '.y...y...y.', '..y.yyy.y..', '...yyyyy...', '..yyyyyyy..', 'yyyyyWyyyyy', '..yyyyyyy..', '...yyyyy...', '..y.yyy.y..', '.y...y...y.', '.....y.....'],
  sand: ['.gggggggg...', 'gg......ggg.', '.........gg.', '..ggggggggg.', '.gg.........', 'gg..gggggg..', '.gg.....ggg.', '..gggggggg..'],
  snow: ['....w....', '.w..w..w.', '..w.w.w..', '...www...', 'wwwwwwwww', '...www...', '..w.w.w..', '.w..w..w.', '....w....'],
  bolt: ['.....yyyy.', '....yyyy..', '...yyyy...', '..yyyyyyy.', '.....yyy..', '....yyy...', '...yyy....', '...yy.....', '..yy......', '..y.......'],
  orb: ['..pppp..', '.pqqppp.', 'pqqpppPp', 'pqpppPPp', 'pppppPPp', 'pppPPPPp', '.pPPPPp.', '..pppp..'],
  waves: ['.bb..bb..bb.', 'b..bb..bb..b', '............', '..bb..bb..bb', '.b..bb..bb..'],
  grass: ['..e...e.e..', '.ee..ee.ee.', '.eh.ehe.eh.', 'eeheeheeheee'],
  shield: ['KKKKKKKKKKKK', 'KsssssssssSK', 'KsssssssssSK', 'KssssggssSSK', 'KssssggssSSK', 'KsssssssssSK', '.KsssssssSK.', '.KsssssssSK.', '..KsssssSK..', '...KsssSK...', '....KKKK....'],
  cracked: ['KKKKKKKKKKKK', 'KsssssKssssK', 'KssssKssssSK', 'KsssKKsssSSK', 'KssssKsssSSK', 'KsssKssssSSK', '.KssKsssssK.', '.KsssKssSSK.', '..KssKsSSK..', '...KsKSSK...', '....KKKK....'],
  crown: ['y....y....y.', 'yy..yyy..yy.', 'yyyyyyyyyyyy', 'yYYYYYYYYYYY', 'yrYyYYyYrYyy', 'yyyyyyyyyyyy', 'YYYYYYYYYYYY'],
  up: ['....g....', '...ggg...', '..gg.gg..', '.gg...gg.', '....g....', '...ggg...', '..gg.gg..', '.gg...gg.'],
  plus: ['...WW...', '...WW...', 'WWWWWWWW', 'WWWWWWWW', '...WW...', '...WW...'],
  bag: ['..nnnn..', '.nGGGGn.', 'GGGGGGGG', 'GggGGggG', 'GgGGGGgG', 'GggggggG', '.GGGGGG.'],
  swap: ['.......W....', '.......WW...', '.WWWWWWWWW..', '.......WW...', '.......W....', '............', '....W.......', '...WW.......', '..WWWWWWWWW.', '...WW.......', '....W.......'],
  coffer: ['...y.....y....', '.KKKKKKKKKKKK.', 'KnnnnnnnnnnnnK', 'KgyggyggyggyyK', 'KGGGGGGGGGGGGK', 'KnnnnnggnnnnnK', 'KnnnnngGnnnnnK', 'KnnnnnnnnnnnnK', 'KKKKKKKKKKKKKK'],
  shut: ['.KKKKKKKKKKK.', 'KnnnnnnnnnnnK', 'KGGGGGGGGGGGK', 'KnnnnngnnnnnK', 'KnnnnnggnnnnK', 'KnnnnnnnnnnnK', 'KKKKKKKKKKKKK'],
  crowd: ['.ddd...ddd...ddd.', '.drd...drd...drd.', '.ddd...ddd...ddd.', 'ddddd.ddddd.ddddd', 'ddddd.ddddd.ddddd', 'ddddd.ddddd.ddddd', 'd...d.d...d.d...d'],
  lantern: ['...KKK...', '..KGGGK..', '.KGyyyGK.', '.KyyWyyK.', '.KyyWyyK.', '.KyyyyyK.', '.KGyyyGK.', '..KGGGK..', '...KKK...'],
  crest: ['..KKKKKKKK..', '.KGGGGGGGGK.', 'KGGnnnnnnGGK', 'KGnnnnnnnnGK', 'KGnnnnnnnnGK', 'KGnnnnnnnnGK', 'KGnnnnnnnnGK', 'KGGnnnnnnGGK', '.KGGnnnnGGK.', '..KGGnnGGK..', '...KGGGGK...', '....KKKK....'],
  gem: ['..pppppp..', '.pwwppppP.', 'pwwppppppP', 'pppppppPPP', '.ppppPPPP.', '..pPPPP...', '...PPP....', '....P.....'],
  cannon: ['..bbbbb..', '.bwwbbbB.', 'bwwbbbbBB', 'bbbbKbbbB', '.bbbKbbB.', '..bbKbB..', '...bKB...', '....B....'],
  wheel: ['...ggggg...', '..g..g..g..', '.g...g...g.', 'g....g....g', 'g....g....g', 'ggggrgggggg', 'g....g....g', 'g....g....g', '.g...g...g.', '..g..g..g..', '...ggggg...'],
  cross: ['KKKKKKKKK', 'KWWWrWWWK', 'KWWWrWWWK', 'KWrrrrrWK', 'KWWWrWWWK', 'KWWWrWWWK', 'KKKKKKKKK'],
  mask: ['.dddddddd.', 'dddddddddd', 'dyydddyydd', 'dyydddyydd', 'dddddddddd', '.dd.dd.dd.'],
  dash: ['.....y....', '.....yy...', 'wwwwwwwyy.', 'wwwwwwwwyy', 'wwwwwwwyy.', '.....yy...', '.....y....'],
  plate: ['KKKKKKKKKK', 'KSsSSSSsSK', 'KSgSSSSgSK', 'KSSSSSSSSK', 'KSSSggSSSK', 'KSgSSSSgSK', 'KSsSSSSsSK', 'KKKKKKKKKK'],
  banner: ['nrrrrrrrr.', 'nrrryyrrr.', 'nrrryyrrr.', 'nrrrrrrrr.', 'nrrrrrrr..', 'nrrrrrr...', 'nrrrrr....', 'n.........', 'n.........', 'n.........'],
  // the Social page
  party: ['..KK....KK..', '.KWWK..KWWK.', '.KWWK..KWWK.', '..KK....KK..', '.KBBBK.KrrrK', 'KBBBBBKKrrrrK', 'KBBBBBKKrrrrK', 'KB.KB.KKr.Kr', 'K..K....K..K'],
  lodge: ['......KKKK..', '.....KrrrrK.', '.....KrrrK..', '.KKKKKKKKKKK', 'KnnnnnnnnnnnK', 'KnGGnnnnGGnnK', 'KnGGnnnnGGnnK', 'KnnnnKKnnnnnK', 'KnnnnKyKnnnnK', 'KKKKKKKKKKKKK'],
  eye: ['...KKKKKKK...', '.KKWWWWWWWKK.', 'KWWWWBBBWWWWK', 'KWWWBBKKBWWWK', 'KWWWBBKKBWWWK', 'KWWWWBBBWWWWK', '.KKWWWWWWWKK.', '...KKKKKKK...'],
  scroll: ['.KKKKKKKKKK.', 'KmmmmmmmmmmK', 'KmKKKKKKKKmK', 'KmmmmmmmmmmK', 'KmKKKKKKmmmK', 'KmmmmmmmmmmK', 'KmKKKKKKKmmK', 'KmmmmmmmmmmK', '.KKKKKKKKKK.'],
  // trial stats
  stairs: ['........yy', '......yyyy', '....yyyyyy', '..yyyyyyyy', 'yyyyyyyyyy'],
  dice: ['KKKKKKKKK', 'KWWWWWWWK', 'KWrWWWrWK', 'KWWWWWWWK', 'KWWWrWWWK', 'KWWWWWWWK', 'KWrWWWrWK', 'KWWWWWWWK', 'KKKKKKKKK'],
  chevup: ['....y....', '...yyy...', '..yy.yy..', '.yy...yy.', 'yy.....yy'],
  flask: ['...KKK...', '...KwK...', '...KwK...', '..KwwwK..', '.KwbbbwK.', 'KwbbbbbwK', 'KwbbbbbwK', '.KKKKKKK.'],
  // scouting
  swords: ['y.......y', 'yy.....yy', '.yy...yy.', '..yy.yy..', '...yyy...', '..gG.Gg..', '.gG...Gg.', 'gG.....Gg'],
  skull: ['.KKKKKKK.', 'KWWWWWWWK', 'KWWWWWWWK', 'KWKKWKKWK', 'KWKKWKKWK', 'KWWWKWWWK', '.KWWWWWK.', '.KWKWKWK.', '..KKKKK..'],
  book: ['.KKKKKKKKKK.', 'KbbbbKrrrrrK', 'KbWWbKrWWrrK', 'KbbbbKrrrrrK', 'KbWWbKrWWrrK', 'KbbbbKrrrrrK', '.KKKKKKKKKK.'],
  heart: ['.KK...KK.', 'KrrKKKrrK', 'KrrrrrrrK', 'KrrrrrrrK', '.KrrrrrK.', '..KrrrK..', '...KrK...', '....K....'],
};

// the fallback crest wears a question mark
const MARK = ['.ggg.', 'g...g', '....g', '...g.', '..g..', '.....', '..g..'];
ROWS.crest = ROWS.crest.map((row, y) => (y >= 2 && y <= 8 ? row.slice(0, 4) + [...MARK[y - 2]].map((c, i) => (c === '.' ? row[4 + i] : c)).join('') + row.slice(9) : row));

export const GLYPHS = Object.freeze(Object.fromEntries(Object.entries(ROWS).map(([name, rows]) => {
  const width = Math.max(...rows.map((r) => r.length));
  return [name, Object.freeze(rows.map((r) => r.padEnd(width, '.')))];
})));

const CACHE = new Map();

/**
 * The paths of a picture, one per colour: `{fill, d}`. Squares are one unit; `outline` rings the picture in the outline colour.
 * @returns {{fill:string, d:string}[]}
 */
export function pixelPaths(grid, outline = true) {
  const key = grid.join('|') + (outline ? '#o' : '');
  const hit = CACHE.get(key);
  if (hit) return hit;
  const rows = grid.length, cols = grid[0].length, pad = outline ? 1 : 0;
  const at = (x, y) => (x >= 0 && y >= 0 && x < cols && y < rows ? grid[y][x] : '.');
  const byColor = new Map();
  const put = (c, x, y) => { const f = PAL[c]; if (!f) return; byColor.set(f, (byColor.get(f) || '') + `M${x + pad} ${y + pad}h1v1h-1z`); };
  for (let y = -pad; y < rows + pad; y++) for (let x = -pad; x < cols + pad; x++) {
    const c = at(x, y);
    if (c !== '.') { put(c, x, y); continue; }
    // an outline only where the picture has no outline of its own along that edge
    const solid = (cx, cy) => { const n = at(cx, cy); return n !== '.' && n !== 'K'; };
    if (outline && (solid(x - 1, y) || solid(x + 1, y) || solid(x, y - 1) || solid(x, y + 1))) put('K', x, y);
  }
  const out = [...byColor].map(([fill, d]) => ({ fill, d }));
  CACHE.set(key, out);
  return out;
}

/** The size of a picture's drawing box (with its outline). */
export const boxOf = (grid, outline = true) => [grid[0].length + (outline ? 2 : 0), grid.length + (outline ? 2 : 0)];

/** Any grid as SVG; `w` is its width in --u (the height follows). */
export function PixGrid({ grid, w = 3, outline = false, label = '', slash = false }) {
  const [bw, bh] = boxOf(grid, outline);
  return html`<svg class="dpix" viewBox=${`0 0 ${bw} ${bh}`} style=${`width:calc(var(--u) * ${w});height:calc(var(--u) * ${w * bh / bw})`}
    shape-rendering="crispEdges" role=${label ? 'img' : null} aria-label=${label || null} aria-hidden=${label ? null : 'true'}>
    ${pixelPaths(grid, outline).map((p) => html`<path key=${p.fill} fill=${p.fill} d=${p.d} />`)}
    ${slash ? html`<path d=${`M0 ${bh} L${bw} 0`} stroke=${PAL.K} stroke-width="3.4" stroke-linecap="square" /><path d=${`M0 ${bh} L${bw} 0`} stroke="#e8584a" stroke-width="1.8" stroke-linecap="square" />` : null}</svg>`;
}

/** A named picture as SVG; `w` is its width in --u (the height follows). `slash` strikes it out as "not allowed". */
export function Pix({ name, w = 3, slash = false, outline = true, label = '' }) {
  return PixGrid({ grid: GLYPHS[name] || GLYPHS.crest, w, outline, label, slash });
}
