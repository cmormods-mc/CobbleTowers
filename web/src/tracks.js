// The battle tracks (TrackStatePayload) as pure functions: which lane shows, what a tile looks like, what the buttons may do. Mirrors TowerHallScreen's track view.

export const LOCKED = 0, CLAIMABLE = 1, CLAIMED = 2, INFO = 3, UNAVAILABLE = 4;

/** The lane on screen: the season lane only when asked for and present, else mastery. */
export function laneOf(tracks, want) {
  const season = want === 'season' && tracks.season.present;
  return { kind: season ? 'season' : 'mastery', data: season ? tracks.season : tracks.mastery };
}

/** Index of the node the player is on (level or step reached), or the first. */
export const currentIndex = (lane) => Math.max(0, Math.min(lane.nodes.length - 1, lane.current - 1));

export const anyClaimable = (lane) => lane.nodes.some((n) => n.state === CLAIMABLE);

/** "12 days", "5 hours" or "40 min": how long is left, rounded to the unit that matters. */
export function timeLeft(millis) {
  if (millis <= 0) return 'ending now';
  const days = Math.floor(millis / 86_400_000), hours = Math.floor(millis / 3_600_000);
  if (days >= 2) return days + ' days';
  if (hours >= 1) return hours + (hours === 1 ? ' hour' : ' hours');
  return Math.max(1, Math.floor(millis / 60_000)) + ' min';
}

/** Which kind of picture a tile gets: the item named, a coin or crystal for a currency, a star or gear for a note, or nothing. */
export function tileIcon(node) {
  if (node.icon) {
    if (/cobble_dollar$/.test(node.icon)) return { kind: 'ui', name: 'coin' };
    if (/raid_points$/.test(node.icon)) return { kind: 'raid' };
    return { kind: 'item', id: node.icon };
  }
  if (node.note) return { kind: 'ui', name: /rank/.test(node.note) ? 'star' : 'gear' };
  return null;
}

const STATE_WORD = { [CLAIMABLE]: 'ready to claim', [CLAIMED]: 'claimed', [LOCKED]: 'locked', [UNAVAILABLE]: 'unavailable here', [INFO]: '' };

/** The detail panel's text for the selected node (or the hint when none is selected). */
export function detail(lane, kind, node) {
  if (!node) return { title: '', body: 'Select a tile to see its reward. Scroll with the wheel, drag, or use the Left and Right keys.', note: '', hint: true };
  const state = STATE_WORD[node.state] || '';
  const none = kind === 'season' ? 'No reward on this step.' : 'No reward at this level. Keep unlocking achievements to climb.';
  return {
    title: (kind === 'season' ? 'Step ' : 'Level ') + node.number + (state ? '  -  ' + state : ''),
    body: node.reward || (node.note ? '' : none),
    note: node.note,
    claimable: node.state === CLAIMABLE,
    hint: false,
  };
}

/** The line under the lane title: the mastery lane names its tower when there is more than one. */
export function subtitle(tracks, lane, kind) {
  if (kind === 'mastery' && tracks.towers.length > 1) {
    const t = tracks.towers.find((x) => x.id === tracks.selectedTower);
    if (t) return t.name + ': ' + lane.subtitle;
  }
  return lane.subtitle;
}

/** The tower before or after the selected one (the arrows wrap). */
export function neighbour(tracks, step) {
  const list = tracks.towers;
  if (list.length < 2) return null;
  const at = Math.max(0, list.findIndex((t) => t.id === tracks.selectedTower));
  return list[(((at + step) % list.length) + list.length) % list.length].id;
}

/** Scroll position that puts tile `index` in the middle of a strip `width` wide (tiles are `pitch` apart, `tile` wide). */
export const centreOn = (index, pitch, tile, width, count, gap = 4) =>
  Math.max(0, Math.min(Math.max(0, count * pitch - gap - width), index * pitch - (width - tile) / 2));
