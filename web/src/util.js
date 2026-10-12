export const pretty = (s) => String(s).split(/[_\s]+/).filter(Boolean).map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');

/** 83 -> "1:23" */
export function fmtClock(sec) {
  sec = Math.max(0, Math.floor(sec));
  return Math.floor(sec / 60) + ':' + String(sec % 60).padStart(2, '0');
}

/** The tail of an item id: "cobblemon:oran_berry" -> "oran_berry". */
export const idTail = (id) => String(id).split(':').pop();

export const RISK = ['minor', 'moderate', 'severe'];
export const RARITIES = ['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic'];
