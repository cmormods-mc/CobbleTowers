// Pure view models for the Frontier Desk briefings and the Trials and Social pages (P51 slice 5).
import { canSelect } from '../lobby.js';
import { pretty } from '../util.js';
import { residentsOf, shortName, standingOf, standingText } from './standings.js';

/** The facts of a tower for its briefing; `found` is false for a tower the server no longer lists. */
export function towerBrief(hall, id) {
  const t = hall.towers.find((x) => x.id === id);
  if (!t) return { found: false, name: '', facts: [], residents: [], standing: null };
  const st = standingOf(hall.extras, id);
  return {
    found: true, id, name: shortName(t.name),
    facts: [['Floors', t.floors + ' per cycle'], ['Milestones', String(t.milestones)], ['Ascension', t.ascends ? 'Continues into harder cycles' : 'Ends after the final floor'],
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

/** Cards of the Social page: title, what it is for, and which request opens it. */
export const SOCIAL = [
  { title: 'Party & invites', sub: 'Gather your team', open: 'lobby' },
  { title: 'Club lodge', sub: 'Your community', open: 'club' },
  { title: 'Spectate', sub: 'Watch challengers', open: 'watch' },
  { title: 'Run codes', sub: 'Scout an expedition', open: 'codes' },
];
