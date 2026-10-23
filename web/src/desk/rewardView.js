// How special a reward is, so the reveal can make more of the rare ones. The server sends no rarity for a grant, so this is judged from what
// the grant is: the mod's own armor by tier, tower keys, and Cobblemon items by the family the page already draws them as. Pure.
import { itemPicture } from '../itemart.js';
import { grantIcon } from '../rewards.js';
import { idTail } from '../util.js';

export const TIERS = ['common', 'fine', 'rare', 'epic'];
/** What the reveal calls each tier; common rewards carry no tag. */
export const TIER_LABEL = Object.freeze({ common: '', fine: 'Fine find', rare: 'Rare find', epic: 'Treasure' });

// the three armor tiers of every tower: first, second and third piece of a set
const ARMOR_TIER = {
  recruit: 'fine', sprout: 'fine', tidewalker: 'fine', duskwanderer: 'fine', challenger: 'rare', rootvale: 'rare', tideforge: 'rare', duskvale: 'rare',
  paragon: 'epic', heartwood: 'epic', leviathan: 'epic', nightfall: 'epic',
};

/** @param {{item: string, label?: string, amount?: number}} grant @returns {'common'|'fine'|'rare'|'epic'} */
export function grantTier(grant) {
  if (grantIcon(grant) !== 'item') return (grant.amount || 0) >= 1000 ? 'fine' : 'common';
  const id = String(grant.item || ''), tail = idTail(id);
  if (id.startsWith('cobbletowers:')) {
    if (/tower_key|season_trim_template/.test(tail)) return 'rare';
    const set = tail.replace(/_(helmet|chestplate|leggings|boots)$/, '');
    return ARMOR_TIER[set] || 'fine';
  }
  const pic = itemPicture(id);
  if (pic === 'ball_master') return 'epic';
  if (pic === 'candy_rare' || pic === 'candy_xl' || pic === 'pack' || pic === 'ticket' || pic === 'scroll') return 'rare';
  if (pic === 'ball_ultra' || pic === 'candy_l' || (pic && pic.startsWith('gem_')) || pic === 'apple_gold' || pic === 'bottle_gold') return 'fine';
  return 'common';
}

/** The highest tier among the grants: it sets the mood of the whole reveal. */
export const bestTier = (grants) => grants.reduce((best, g) => (TIERS.indexOf(grantTier(g)) > TIERS.indexOf(best) ? grantTier(g) : best), 'common');
