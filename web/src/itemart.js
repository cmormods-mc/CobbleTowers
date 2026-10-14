// Which picture an item gets when the page has no art for it: a shape and a colour chosen from the item's name.
// `SPECS` names every picture (drawn in tools/native-art.mjs); `itemPicture` picks one, or null for "use the gift".
import { idTail } from './util.js';

export const SPECS = {
  candy_xs: ['candy', 0xFF8FB8D8], candy_s: ['candy', 0xFF6FA55A], candy_m: ['candy', 0xFF4F7FA0], candy_l: ['candy', 0xFF9A6FC0],
  candy_xl: ['candy', 0xFFE3A95E], candy_rare: ['candy', 0xFFD86FA8],
  ball_poke: ['ball', 0xFFC0453A], ball_great: ['ball', 0xFF4F7FA0], ball_ultra: ['ball', 0xFFE3A95E], ball_master: ['ball', 0xFF9A6FC0],
  ball_other: ['ball', 0xFF5FA58F],
  bottle_blue: ['bottle', 0xFF6BA8D8], bottle_purple: ['bottle', 0xFFA070C8], bottle_pink: ['bottle', 0xFFD86FA8], bottle_gold: ['bottle', 0xFFE3A95E],
  bottle_red: ['bottle', 0xFFC0453A], bottle_yellow: ['bottle', 0xFFE3D05E], bottle_teal: ['bottle', 0xFF5FB8B0], bottle_white: ['bottle', 0xFFE8E4DA],
  bottle_green: ['bottle', 0xFF6FA55A],
  berry_red: ['berry', 0xFFC0453A], berry_yellow: ['berry', 0xFFE3C45E], berry_blue: ['berry', 0xFF4F7FA0], berry_pink: ['berry', 0xFFD86FA8],
  berry_green: ['berry', 0xFF6FA55A],
  nut_red: ['nut', 0xFFC0453A], nut_green: ['nut', 0xFF6FA55A], nut_blue: ['nut', 0xFF4F7FA0], nut_yellow: ['nut', 0xFFE3C45E],
  nut_black: ['nut', 0xFF3E3A44], nut_white: ['nut', 0xFFE8E4DA], nut_pink: ['nut', 0xFFD86FA8],
  gem_blue: ['gem', 0xFF4F7FA0], gem_green: ['gem', 0xFF4FA56A], gem_purple: ['gem', 0xFF8A5FB0], gem_red: ['gem', 0xFFC0453A], gem_yellow: ['gem', 0xFFE3C45E],
  gem_white: ['gem', 0xFFE8E4DA], gem_cyan: ['gem', 0xFF6FD0D8], gem_brown: ['gem', 0xFF7A5238],
  apple_red: ['apple', 0xFFC0453A], apple_gold: ['apple', 0xFFE3B64E], apple_green: ['apple', 0xFF7FB05A],
  pack: ['pack', 0xFFE3A95E], ticket: ['ticket', 0xFFF0DFBF], banner: ['banner', 0xFF9A3A3A],
};

const STONE = { water: 'blue', leaf: 'green', dusk: 'purple', fire: 'red', sun: 'red', thunder: 'yellow', shiny: 'yellow', moon: 'white', dawn: 'cyan', ice: 'cyan' };
const BERRY = { sitrus: 'yellow', oran: 'blue', pecha: 'pink', lum: 'green' };
const BOTTLE = { potion: 'blue', super_potion: 'purple', hyper_potion: 'pink', max_potion: 'gold', full_restore: 'white', full_heal: 'white', revive: 'gold', max_revive: 'gold',
  antidote: 'purple', burn_heal: 'red', paralyze_heal: 'yellow', awakening: 'teal', ice_heal: 'blue' };
const VITAMIN = /^(protein|iron|calcium|zinc|carbos|hp_up|pp_up|pp_max)$/;
const BALL = { poke_ball: 'poke', great_ball: 'great', ultra_ball: 'ultra', master_ball: 'master' };

/** @param {string} id an item id such as "cobblemon:oran_berry" @returns {string|null} a key of SPECS, or an existing icon name, or null */
export function itemPicture(id) {
  const tail = idTail(id);
  let m;
  if ((m = /^exp_candy_(xs|s|m|l|xl)$/.exec(tail))) return 'candy_' + m[1];
  if (tail === 'rare_candy') return 'candy_rare';
  if (/_ball$/.test(tail)) return 'ball_' + (BALL[tail] || 'other');
  if (BOTTLE[tail]) return 'bottle_' + BOTTLE[tail];
  if (VITAMIN.test(tail)) return 'bottle_green';
  if ((m = /^(.+)_berry$/.exec(tail))) return 'berry_' + (BERRY[m[1]] || 'red');
  if ((m = /^(red|green|blue|yellow|black|white|pink)_apricorn$/.exec(tail))) return 'nut_' + m[1];
  if ((m = /^(.+)_stone$/.exec(tail))) return 'gem_' + (STONE[m[1]] || 'white');
  if (tail === 'diamond' || /^deep_sea_/.test(tail)) return 'gem_cyan';
  if (tail === 'emerald') return 'gem_green';
  if (tail === 'netherite_scrap') return 'gem_brown';
  if (/apple$/.test(tail)) return /golden|enchanted|syrupy/.test(tail) ? 'apple_gold' : /tart|sweet/.test(tail) ? 'apple_green' : 'apple_red';
  if (/^booster_pack/.test(tail)) return 'pack';
  if (/ticket$/.test(tail)) return 'ticket';
  if (/banner_pattern$/.test(tail) || /trim_template/.test(tail)) return 'scroll';
  if (/banner$/.test(tail)) return 'banner';
  return null;
}
