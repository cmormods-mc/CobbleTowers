// Pure view model for the Frontier Desk feature menus (P51 slice 5), over src/features.js. No DOM.
import { actionPlan } from '../features.js';

/** The rail pill a menu section belongs to; the others are not on the bar. */
export const pillFor = (section) => (section === 'modifiers' || section === 'relics' ? 'codex' : '');

/** Entries then actions, as one list of rows. */
export const entryList = (data) => [
  ...data.entries.map((e) => ({ kind: 'entry', id: e.id, label: e.title })),
  ...data.actions.map((a) => ({ kind: 'action', id: a.id, label: a.label, action: a })),
];

/** One line to show under an entry's title in the list: the first line that is not a bare risk note. */
export function previewOf(data, id) {
  const e = data.entries.find((x) => x.id === id);
  if (!e || !e.lines.length) return '';
  const line = e.lines.find((l) => !/^(Tags: |The risk you carry|Held for this run)/.test(l)) || e.lines[0];
  const text = line.replace(/^\[[+-]\]\s*/, '').replace(/\s+\*\s+/g, ' · ');
  return text.length > 90 ? text.slice(0, 89).trimEnd() + '…' : text;
}

/**
 * What the page shows for the menu's state: the list, an open entry, or a review.
 * @returns {{kind:'list'|'entry'|'review', heading:string, lines:string[], primary:object|null, review:object|null, needsInput:boolean}}
 */
export function featureMode(data, ui) {
  const entry = ui.sel == null ? null : data.entries.find((e) => e.id === ui.sel) || null;
  if (ui.review) {
    return { kind: 'review', heading: ui.review.label, lines: [ui.review.confirmation || ui.review.label], primary: null, review: ui.review, needsInput: !!ui.review.inputHint };
  }
  if (entry) return { kind: 'entry', heading: entry.title, lines: entry.lines, primary: entry.actions.length ? entry.actions[0] : null, review: null, needsInput: false };
  return { kind: 'list', heading: data.title, lines: [], primary: null, review: null, needsInput: false };
}

/** Whether choosing an action asks first (a confirmation or typed text). */
export const asksFirst = (action) => actionPlan(action) === 'review';
