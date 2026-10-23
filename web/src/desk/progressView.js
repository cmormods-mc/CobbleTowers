// Pure view model for the Frontier Desk progress page (P51 slice 4), over the TrackState payload and src/tracks.js.
import { CLAIMABLE, CLAIMED, INFO, LOCKED, UNAVAILABLE, laneOf, timeLeft } from '../tracks.js';
import { rankTone, shortName, standingOf } from './standings.js';

/** How many rewards are ready to claim in each lane the payload carries. */
export function claimable(tracks) {
  const count = (lane) => (lane && lane.present ? lane.nodes.filter((n) => n.state === CLAIMABLE).length : 0);
  const mastery = count(tracks.mastery), season = count(tracks.season);
  return { mastery, season, total: mastery + season };
}

/** "No rewards ready", "1 reward ready", "3 rewards ready". */
export const claimLine = (n) => (n === 0 ? 'No rewards ready' : n === 1 ? '1 reward ready' : n + ' rewards ready');

/**
 * The left-hand list: one row per mastery tower, then the season when there is one. `extras` is the Hall's extras (for the rank name).
 * @returns {{kind:'tower'|'season', id:string, name:string, sub:string, tone:string, selected:boolean}[]}
 */
export function laneRows(tracks, want, extras) {
  const { kind } = laneOf(tracks, want);
  const rows = tracks.towers.map((t) => {
    const st = standingOf(extras, t.id);
    return { kind: 'tower', id: t.id, name: shortName(t.name), sub: st && st.level > 0 ? st.rank + ' · Level ' + st.level : 'Level ' + t.level, tone: rankTone(st || { level: t.level, rank: '' }),
      rank: st && st.level > 0 ? st.rank : '',
      selected: kind === 'mastery' && t.id === tracks.selectedTower };
  });
  if (tracks.season.present) rows.push({ kind: 'season', id: 'season', name: tracks.season.title, sub: tracks.season.subtitle, tone: 'n', rank: '', selected: kind === 'season' });
  return rows;
}

/** Share of the way to the next level or step, 0 to 100; 0 when the lane does not say. */
export const progressPercent = (lane) => (lane.need > 0 ? Math.max(0, Math.min(100, Math.round((100 * Math.min(lane.into, lane.need)) / lane.need))) : 0);

/** "Ends in 9 days" for a season; empty for mastery. `arrived` is when the payload arrived. */
export const timerText = (lane, arrived, now) => (lane.endsInMillis > 0 ? (lane.timerLabel ? lane.timerLabel.charAt(0) + lane.timerLabel.slice(1).toLowerCase() : 'Ends in') + ' ' + timeLeft(arrived + lane.endsInMillis - now) : '');

/** The word on a tile: what the player can do with it. */
export const stateWord = (node) => (node.state === CLAIMABLE ? 'Ready' : node.state === CLAIMED ? 'Claimed' : node.state === LOCKED ? 'Locked' : node.state === UNAVAILABLE ? 'Not here' : node.state === INFO ? '' : '');

/** A short name for a tile's reward: "2x Diamond" stays, a long sentence is cut. */
export const tileLabel = (node) => {
  const text = node.reward || node.note || '';
  return text.length > 22 ? text.slice(0, 21).trimEnd() + '…' : text;
};

/** The rank a "New rank: Silver" tile promises, or empty for any other tile. */
export const rankOfNote = (node) => { const m = /rank:\s*([A-Za-z]+)/i.exec(node.note || ''); return m ? m[1] : ''; };

// the picture for an achievement, by its name: the achievements are data, so a new one falls back to a gem
const ACHIEVEMENT_GLYPHS = [[/first step|floors? down/i, 'stairs'], [/flawless/i, 'heart'], [/speed|swift|fast/i, 'dash'], [/team|party/i, 'party'], [/ascen/i, 'chevup'],
  [/iron will|will/i, 'plate'], [/collect/i, 'coffer'], [/boss|slay|giant/i, 'skull'], [/scout|eye/i, 'eye']];
export const achievementGlyph = (name) => (ACHIEVEMENT_GLYPHS.find(([re]) => re.test(String(name))) || [0, 'gem'])[1];
