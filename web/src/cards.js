import { MOVES, ABILITIES } from './data/tables.js';
import DATA from './gen/data.js';
import { pretty } from './util.js';

const CATS = ['physical', 'special', 'status'];
const BY_SPECIES = new Map(DATA.rental.map((r) => [r.species, r]));

/** A server card ({species, name, rarity, details}) -> what the face needs; the sets file supplies types the payload leaves out. */
export function adaptCard(c) {
  const det = c.details || {};
  const known = BY_SPECIES.get(c.species);
  const moves = (det.moves || []).map((m) => {
    const table = MOVES[m.id];
    const cat = m.category ? Math.max(0, CATS.indexOf(m.category)) : table ? table[2] : 0;
    return { name: table ? table[0] : pretty(m.id), type: m.type || (table ? table[1] : 'normal'), cat };
  });
  return {
    species: c.species,
    name: c.name || (known && known.name) || pretty(c.species),
    rarity: c.rarity || (known && known.rarity) || 'common',
    level: det.level || (known && known.level) || 50,
    types: (known && known.types) || [],
    ability: ABILITIES[det.ability] || pretty(det.ability || ''),
    item: det.item ? pretty(det.item) : known ? known.item : 'No item',
    moves,
  };
}
