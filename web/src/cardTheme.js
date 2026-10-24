// What the rental card draws from: type colours and glyphs, the six rarity tiers and how loud each reveal is, natures.

/** Type -> the colour its card is lit in. */
export const TYPE_HEX = {
  normal: '#a8a77a', fire: '#ee8130', water: '#6390f0', electric: '#f7d02c', grass: '#7ac74c', ice: '#96d9d6', fighting: '#c22e28', poison: '#a33ea1', ground: '#e2bf65',
  flying: '#a98ff3', psychic: '#f95587', bug: '#a6b91a', rock: '#b6a136', ghost: '#735797', dragon: '#6f35fc', dark: '#705746', steel: '#b7b7ce', fairy: '#d685ad',
};
export const typeHex = (t) => TYPE_HEX[t] || TYPE_HEX.normal;

/** A colour pulled toward white so it reads on the dark tooltip. */
export function lift(hex, amount = 0.3) {
  const n = parseInt(hex.slice(1), 16);
  const mix = (v) => Math.round(v + (255 - v) * amount);
  return '#' + [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v) => mix(v).toString(16).padStart(2, '0')).join('');
}

const svg = (inner) => `<svg viewBox="0 0 24 24" aria-hidden="true">${inner}</svg>`;
const fill = (d, extra = '') => svg(`<path fill="currentColor" ${extra} d="${d}"/>`);
const line = (d) => svg(`<path fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" d="${d}"/>`);
/** One simple glyph per type for the medallions on the card. */
export const GLYPH = {
  normal: svg('<circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" stroke-width="3"/>'),
  fire: fill('M12 2c1 4 5 6 5 11a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-4-1-6 1-10z'),
  water: fill('M2 11c2-3 4-3 6 0s4 3 6 0 4-3 8 0v3c-4-3-6-3-8 0s-4 3-6 0-4-3-6 0zM2 18c2-3 4-3 6 0s4 3 6 0 4-3 8 0v2c-4-3-6-3-8 0s-4 3-6 0-4-3-6 0z'),
  electric: fill('M13 2L5 14h6l-1 8 9-13h-6z'),
  grass: fill('M4 20c0-10 6-16 16-16 0 10-6 16-16 16z'),
  ice: line('M12 2v20M3.3 7l17.4 10M20.7 7L3.3 17'),
  fighting: fill('M6 10h12a3 3 0 0 1 3 3v4a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3zM6 10V7a2 2 0 0 1 3.5-1.3A2 2 0 0 1 13 5.5 2 2 0 0 1 16.5 6 2 2 0 0 1 18 8v2H6z'),
  poison: fill('M11 3c3 4 6 7 6 11a6 6 0 0 1-12 0c0-4 3-7 6-11zM19 4.5a1.8 1.8 0 1 0 .01 0zM21 10a1.2 1.2 0 1 0 .01 0z'),
  ground: fill('M2 20L9 7l4 6 3-4 6 11z'),
  flying: fill('M2 15c5-1 7-4 9-10 1 4 4 7 11 8-4 1-6 1-8 4-2-2-5-3-12-2z'),
  psychic: svg('<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2.2"/><circle cx="12" cy="12" r="4" fill="currentColor"/>'),
  bug: svg('<ellipse cx="12" cy="14" rx="5" ry="7" fill="currentColor"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M9 4l2 4M15 4l-2 4M3 12l4 2M21 12l-4 2"/>'),
  rock: fill('M7 3h10l5 8-4 10H6L2 11z'),
  ghost: fill('M12 3a7 7 0 0 0-7 7v11l3-2 2 2 2-2 2 2 2-2 3 2V10a7 7 0 0 0-7-7zM9 9.2a1.5 1.5 0 1 0 .01 0zM15 9.2a1.5 1.5 0 1 0 .01 0z', 'fill-rule="evenodd"'),
  dragon: fill('M12 2l8 8-8 12-8-12zM12 8l3 3-3 5-3-5z', 'fill-rule="evenodd"'),
  dark: fill('M20 14a8 8 0 1 1-9-11 7 7 0 0 0 9 11z'),
  steel: fill('M12 2l8 4.5v9L12 22l-8-6.5v-9zM12 8a4 4 0 1 0 .01 0z', 'fill-rule="evenodd"'),
  fairy: fill('M12 2l2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5z'),
};
export const glyph = (t) => GLYPH[t] || GLYPH.normal;

/**
 * The six rarity tiers. c: colour, st: stars, sh: shake in card pixels, parts: sparks, ct: how long the card back charges before it
 * flips (ms), punch: a zoom on landing, rainbow: sparks in every colour. Names match AscensionLib's ladder, except its top tier is "mythical".
 */
export const TIERS = {
  common:    { n: 'Common',    c: '#aab2c5', st: 1, sh: 0,  parts: 0,   ct: 450,  punch: 0, rainbow: 0, asc: 'common' },
  uncommon:  { n: 'Uncommon',  c: '#62d56f', st: 2, sh: 3,  parts: 20,  ct: 700,  punch: 0, rainbow: 0, asc: 'uncommon' },
  rare:      { n: 'Rare',      c: '#4ba8ff', st: 3, sh: 5,  parts: 45,  ct: 950,  punch: 0, rainbow: 0, asc: 'rare' },
  epic:      { n: 'Epic',      c: '#b866ff', st: 4, sh: 8,  parts: 90,  ct: 1200, punch: 0, rainbow: 0, asc: 'epic' },
  legendary: { n: 'Legendary', c: '#ffb52e', st: 5, sh: 12, parts: 150, ct: 1500, punch: 1, rainbow: 0, asc: 'legendary' },
  mythic:    { n: 'Mythic',    c: '#ff4f86', st: 6, sh: 16, parts: 230, ct: 1800, punch: 1, rainbow: 1, asc: 'mythical' },
};
export const tierOf = (rarity) => TIERS[rarity] || TIERS.common;

/** The tier an AscensionLib id belongs to ("mythical" is the library's spelling of mythic). */
export const tierOfAscension = (id) => TIERS[id === 'mythical' ? 'mythic' : id] || null;

/** How long a card's reveal takes from its start to the last landing animation, in ms. */
export const revealMs = (rarity) => 300 + tierOf(rarity).ct * 0.8 + 560 + 1500;

export const STATS = [['HP', '#ff3b3b'], ['ATK', '#ff9a2e'], ['DEF', '#ffc83a'], ['SP.ATK', '#3d8bff'], ['SP.DEF', '#3ddc4f'], ['SPE', '#e04fe0']];

/** Nature -> [stat it raises, stat it lowers]; the five neutral natures have none. */
export const NATURES = {
  lonely: ['ATK', 'DEF'], brave: ['ATK', 'SPE'], adamant: ['ATK', 'SP.ATK'], naughty: ['ATK', 'SP.DEF'],
  bold: ['DEF', 'ATK'], relaxed: ['DEF', 'SPE'], impish: ['DEF', 'SP.ATK'], lax: ['DEF', 'SP.DEF'],
  timid: ['SPE', 'ATK'], hasty: ['SPE', 'DEF'], jolly: ['SPE', 'SP.ATK'], naive: ['SPE', 'SP.DEF'],
  modest: ['SP.ATK', 'ATK'], mild: ['SP.ATK', 'DEF'], quiet: ['SP.ATK', 'SPE'], rash: ['SP.ATK', 'SP.DEF'],
  calm: ['SP.DEF', 'ATK'], gentle: ['SP.DEF', 'DEF'], sassy: ['SP.DEF', 'SPE'], careful: ['SP.DEF', 'SP.ATK'],
};
export const natureEffect = (name) => NATURES[String(name || '').toLowerCase()] || null;
