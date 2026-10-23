// What each line of a scouting report looks like: a picture, and the value broken into things worth drawing. Pure.
import { itemPicture } from '../itemart.js';

export const TYPES = ['normal', 'fire', 'water', 'electric', 'grass', 'ice', 'fighting', 'poison', 'ground', 'flying', 'psychic', 'bug', 'rock', 'ghost', 'dragon', 'dark', 'steel', 'fairy'];

const WEATHER = [[/rain|drizzle/i, 'weather:raindance'], [/sun|harsh|drought/i, 'weather:sunnyday'], [/sand/i, 'weather:sandstorm'], [/hail|snow/i, 'weather:snowscape']];

/** "Mr. Mime, level 48" -> "mrmime": the name the sprite sheet knows. */
export const speciesKey = (text) => String(text).split(',')[0].toLowerCase().replace(/[^a-z0-9]/g, '');

/** "Sitrus Berry" -> "cobblemon:sitrus_berry": the id the item pictures are named by. */
export const itemIdOf = (name) => 'cobblemon:' + String(name).trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');

/**
 * @param {{name: string, value: string}} cat one category of the report
 * @returns {{kind:'types'|'lead'|'items'|'moves'|'boss'|'weather'|'other', glyph:string, chips:{text:string, type:string, item:string}[], species:string, weather:string}}
 */
export function scoutView(cat) {
  const name = String(cat.name || '').toLowerCase(), value = String(cat.value || '');
  const list = value.split(',').map((x) => x.trim()).filter(Boolean);
  const view = (kind, glyph, extra = {}) => ({ kind, glyph, chips: [], species: '', weather: '', ...extra });
  if (/type/.test(name)) return view('types', 'swords', { chips: list.map((t) => ({ text: t, type: TYPES.includes(t.toLowerCase()) ? t.toLowerCase() : '', item: '' })) });
  if (/lead/.test(name)) return view('lead', 'crown', { species: speciesKey(value) });
  if (/held|item/.test(name)) return view('items', 'bag', { chips: list.map((t) => ({ text: t, type: '', item: itemPicture(itemIdOf(t)) || '' })) });
  if (/move/.test(name)) return view('moves', 'swords', { chips: list.map((t) => ({ text: t, type: '', item: '' })) });
  if (/boss/.test(name)) return view('boss', 'skull');
  if (/weather/.test(name)) {
    const w = (WEATHER.find(([re]) => re.test(value)) || [0, ''])[1];
    return view('weather', w ? { 'weather:raindance': 'rain', 'weather:sunnyday': 'sun', 'weather:sandstorm': 'sand', 'weather:snowscape': 'snow' }[w] : 'waves', { weather: w });
  }
  return view('other', 'scroll');
}
