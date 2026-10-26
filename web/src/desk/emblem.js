// The medallions: one per tower (the web version of the name-badge icons) and one per mastery rank. A gold-or-metal ring round a jewel-toned
// field with a four-by-four emblem in the middle; 8 by 8 pixels, drawn as SVG so they stay sharp at any size.
import { html } from '../html.js';
import { PixGrid } from './pix.js';

// H, M and L are the ring's highlight, mid and shade; B is the field
const RING = ['..KKKK..', '.KHHHHK.', 'KHBBBBMK', 'KHBBBBML', 'KHBBBBML', 'KMBBBBLK', '.KMLLLK.', '..KKKK..'];
const METALS = { gold: ['Z', 'y', 'Y'], bronze: ['g', 'c', 'C'], silver: ['v', 'm', 'V'], platinum: ['x', 'a', 'A'], diamond: ['W', 'x', 'X'], master: ['q', 'p', 'P'], champion: ['y', 'r', 'R'] };

function medallion(metal, field, emblem) {
  const [hi, mid, lo] = METALS[metal];
  const rows = RING.map((row) => [...row.replace(/H/g, hi).replace(/M/g, mid).replace(/L/g, lo).replace(/B/g, field)]);
  emblem.forEach((line, dy) => [...line].forEach((ch, dx) => { if (ch !== '.') rows[2 + dy][2 + dx] = ch; }));
  return rows.map((r) => r.join(''));
}

/** Tower id tails with a picture of their own; anything else wears the star. */
export const TOWER_EMBLEMS = Object.freeze({
  neutral: medallion('gold', 'z', ['m..m', '.mm.', '.mm.', 'mmmm']),
  tideforge: medallion('gold', 'u', ['..w.', '.ww.', 'wWww', '.ww.']),
  rootvale: medallion('gold', 'o', ['..hh', '.hh.', 'hh..', 'h...']),
  duskvale: medallion('gold', 'k', ['.qq.', 'q...', 'q...', '.qq.']),
  halloween: medallion('gold', 'k', ['.ee.', 'cccc', 'cKKc', '.cc.']),
  other: medallion('gold', 'j', ['..W.', 'WWWW', '.WW.', '.W.W']),
});

const STAR = ['..W.', 'WWWW', '.WW.', '.W.W'];
const RANK_FIELD = { bronze: 'C', silver: 'S', gold: 'Y', platinum: 'A', diamond: 'X', master: 'P', champion: 'R' };
export const RANK_MEDALS = Object.freeze(Object.fromEntries(Object.keys(METALS).map((m) => [m, medallion(m, RANK_FIELD[m], STAR)])));

/** "cobbletowers:tideforge" -> "tideforge"; the test tower wears the neutral one, any other tower the star. */
export const towerKey = (id) => {
  const tail = String(id || '').split(':').pop().toLowerCase();
  return tail === 'test' ? 'neutral' : ['neutral', 'tideforge', 'rootvale', 'duskvale', 'halloween'].includes(tail) ? tail : 'other';
};

/** @param {{tower: string, w?: number}} props */
export function Emblem({ tower, w = 3.4 }) {
  return html`<${PixGrid} grid=${TOWER_EMBLEMS[towerKey(tower)]} w=${w} />`;
}

/** @param {{rank: string, w?: number}} props the rank name as the server sends it ("Silver"); an unknown or empty rank draws nothing */
export function RankMedal({ rank, w = 3 }) {
  const grid = RANK_MEDALS[String(rank || '').toLowerCase()];
  return grid ? html`<${PixGrid} grid=${grid} w=${w} />` : null;
}
