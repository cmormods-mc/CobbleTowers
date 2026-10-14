// The feature menus (TowerFeatureState): what the page shows and which buttons work, as pure functions. Mirrors TowerFeatureScreen.

export const FEATURE_ICON = { echoes: 'echo', club: 'flag', 'club-top': 'flag', 'club-alltime': 'flag', contracts: 'scroll', cosmetics: 'crown', season: 'star', hall: 'trophy', report: 'book', collection: 'cards' };
export const iconOf = (section) => FEATURE_ICON[section] || 'book';

/** The view state kept beside a menu: which entry is open, a review in progress, the typed text, the list page. */
export const freshUi = () => ({ sel: null, review: null, typed: '', page: 0 });

/**
 * The state after a reply: an open entry stays open if it is still there; a reply with a message opens the first entry (it is
 * usually about it); a review is always closed.
 */
export function uiAfterReply(ui, data) {
  let sel = null;
  if (data.message && data.entries.length) sel = data.entries[0].id;
  else if (ui.sel != null && data.entries.some((e) => e.id === ui.sel)) sel = ui.sel;
  return { ...ui, sel, review: null, typed: '', page: 0 };
}

/** How many list rows fit under the heading and above the footer (screen height in GUI pixels). */
export const rowsFor = (height, top) => Math.max(1, Math.floor((height - 64 - top - 34) / 24));

/**
 * @param {object} data TowerFeatureState @param {object} ui @param {number} height @param {number} top the content box's y
 * @returns what to draw: the list slice, or the open entry / review
 */
export function featureView(data, ui, height, top) {
  const rows = rowsFor(height, top);
  const items = [...data.entries.map((e) => ({ kind: 'entry', id: e.id, label: e.title })), ...data.actions.map((a) => ({ kind: 'action', id: a.id, label: a.label, action: a }))];
  const pages = Math.max(1, Math.ceil(items.length / rows));
  const page = Math.min(ui.page, pages - 1);
  const entry = ui.sel == null ? null : data.entries.find((e) => e.id === ui.sel) || null;
  const mode = ui.review ? 'review' : entry ? 'entry' : 'list';
  return {
    mode, rows, pages, page, items: items.slice(page * rows, page * rows + rows),
    heading: ui.review ? ui.review.label : entry ? entry.title : data.title,
    lines: ui.review ? [ui.review.confirmation || ui.review.label] : entry ? entry.lines : [],
    primary: mode === 'entry' && entry.actions.length ? entry.actions[0] : null,
    needsInput: !!(ui.review && ui.review.inputHint),
  };
}

/** What choosing an action does: switch section, open the lobby, ask for review, or send it. */
export function actionPlan(action) {
  if (action.id === 'lobby') return 'lobby';
  if (action.id === 'page') return 'page';
  return action.confirmation || action.inputHint ? 'review' : 'send';
}

/** The Current Run page's content, from the "run" menu's entries: a summary, then modifiers, relics and set bonuses. */
export function runPage(data) {
  const group = (prefix) => data.entries.filter((e) => e.id.startsWith(prefix)).map((e) => ({
    title: e.title.replace(/^(Modifier|Relic|Set bonus): /, ''),
    lines: e.lines.filter((l) => !l.startsWith('Tags: ') && !l.startsWith('The risk you carry') && !l.startsWith('Held for this run')),
  }));
  return {
    none: data.entries.find((e) => e.id === 'none') || null,
    summary: data.entries.find((e) => e.id === 'run') || null,
    modifiers: group('mod:'), relics: group('relic:'), bonuses: group('bonus:'),
  };
}
