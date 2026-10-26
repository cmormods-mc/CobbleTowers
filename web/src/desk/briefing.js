// Pure view models for the Frontier Desk briefings and the Trials and Social pages (P51 slice 5).
import { canSelect } from '../lobby.js';
import { pretty } from '../util.js';
import { residentsOf, shortName, standingOf, standingText } from './standings.js';

/** The facts of a tower for its briefing; `found` is false for a tower the server no longer lists. */
/** "Nov 2, 8:00 PM" in the player's own time zone; empty for a tower that never closes. */
export const endsText = (endsAt) => (endsAt > 0
  ? new Date(endsAt).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }) : '');

export function towerBrief(hall, id) {
  const t = hall.towers.find((x) => x.id === id);
  if (!t) return { found: false, name: '', facts: [], residents: [], standing: null };
  const st = standingOf(hall.extras, id);
  return {
    found: true, id, name: shortName(t.name),
    facts: [['Floors', t.floors + ' per cycle'], ['Milestones', String(t.milestones)], ['Ascension', t.ascends ? 'Continues into harder cycles' : 'Ends after the final floor'],
      ...(t.endsAt > 0 ? [['Limited time', 'Closes ' + endsText(t.endsAt)]] : []),
      ['Your mastery', standingText(st)]],
    residents: residentsOf(id), standing: st,
  };
}

/** Why a tower or trial cannot be selected right now, in a sentence; empty when it can. */
export function whyNot(hall, busy) {
  if (canSelect(hall, busy)) return '';
  if (busy) return 'Waiting for the server…';
  if (hall.runStatus !== '') return 'You already have a run in progress. Return to it first.';
  if (hall.play.lobby.countdown >= 0) return 'A countdown is running.';
  return 'Only the host of a lobby chooses its tower.';
}

/** "cobbletowers:double_trouble" in a server sentence becomes "Double Trouble"; anything else is left alone. */
export const prettyIds = (line) => String(line).replace(/\b[a-z][a-z0-9_]*:([a-z0-9_]+)\b/g, (_, path) => pretty(path));

/** The rule lines of a trial split into the headline facts and the long note (the last line says the first launch is scored). */
export function trialLines(trial) {
  if (!trial) return { facts: [], note: '' };
  const rules = trial.rules.map(prettyIds);
  const note = rules.length && /First launch is scored/.test(rules[rules.length - 1]) ? rules.pop() : '';
  return { facts: rules, note };
}

/** Cards of the Social page: title, what it is for, which request opens it, and the picture and landscape it wears. */
export const SOCIAL = [
  { title: 'Party & invites', sub: 'Gather your team', open: 'lobby', glyph: 'party', region: 'neutral' },
  { title: 'Club lodge', sub: 'Your community', open: 'club', glyph: 'lodge', region: 'tideforge' },
  { title: 'Spectate', sub: 'Watch challengers', open: 'watch', glyph: 'eye', region: 'duskvale' },
  { title: 'Run codes', sub: 'Scout an expedition', open: 'codes', glyph: 'scroll', region: 'rootvale' },
];

/** "2026-w41" -> "Week 41"; a date stays as it is. */
export const periodText = (p) => { const m = /^\d{4}-w(\d+)$/i.exec(String(p || '')); return m ? 'Week ' + Number(m[1]) : String(p || ''); };

/**
 * The rules of a trial as tiles (floors, mode, enemy level), the starting modifiers as a list, and whatever else the server said.
 * @param {string[]} facts the trial's rule lines, ids already made readable
 * @returns {{tiles: {glyph:string, value:string, label:string, sub:string}[], modifiers: string[], rest: string[]}}
 */
export function trialStats(facts) {
  const tiles = [], modifiers = [], rest = [];
  for (const line of facts) {
    let m;
    if ((m = /^(\d+) floors?\s*(?:\/\s*(.+))?$/i.exec(line))) tiles.push({ glyph: 'stairs', value: m[1], label: Number(m[1]) === 1 ? 'floor' : 'floors', sub: periodText(m[2]) });
    else if ((m = /^Mode:\s*(.+)$/i.exec(line))) tiles.push({ glyph: 'plate', value: m[1], label: 'mode', sub: '' });
    else if ((m = /^Enemy level:\s*(\d+)/i.exec(line))) tiles.push({ glyph: 'swords', value: 'Lv ' + m[1], label: 'enemies', sub: '' });
    else if ((m = /^Starting modifiers:\s*(.+)$/i.exec(line))) { if (!/^none$/i.test(m[1].trim())) modifiers.push(...m[1].split(',').map((x) => x.trim()).filter(Boolean)); }
    else rest.push(line);
  }
  return { tiles, modifiers, rest };
}
