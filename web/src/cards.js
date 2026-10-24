import { MOVES, ABILITIES } from './data/tables.js';
import DATA from './gen/data.js';
import { pretty } from './util.js';

const CATS = ['physical', 'special', 'status'];
const BY_SPECIES = new Map(DATA.rental.map((r) => [r.species, r]));
const BY_SET = new Map(DATA.rental.map((r) => [r.id, r]));

/** A server card ({set, species, name, rarity, ascension, details}) -> what the face needs; the sets file supplies what the payload leaves out. */
export function adaptCard(c) {
  const det = c.details || {};
  const known = BY_SET.get(c.set) || BY_SPECIES.get(c.species);
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
    nature: det.nature ? pretty(det.nature) : known && known.nature ? pretty(known.nature) : '',
    // what the card rolled; the sets file only for a server that predates the roll
    ivs: (c.ivs && c.ivs.length === 6 && c.ivs) || (known && known.ivs) || null,
    evs: (c.evs && c.evs.length === 6 && c.evs) || (known && known.evs) || null,
    // AscensionLib's tier for this card; empty when the library is not installed on the server
    ascension: c.ascension || '',
    moves,
  };
}
