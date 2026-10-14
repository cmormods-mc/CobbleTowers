// The mastery screen's rules (MasteryScreenPayload), as pure functions. Mirrors the native MasteryScreen.

export const TABS = [['mastery', 'Mastery'], ['speed', 'Speed'], ['ascension', 'Ascension'], ['difficulty', 'Difficulty'], ['clears', 'Clears']];

/** Width in GUI pixels of a tab for its label (the font is about 6 px a letter). */
export const tabWidth = (label) => label.length * 6 + 12;

/** Achievement tiles that fit on a page: two columns, rows of 45 px under the heading. */
export const nodesPerPage = (height) => Math.max(1, Math.floor((height - 90) / 45)) * 2;

/** The lines of a board, with a heading for each side of a split one. */
export function boardLines(board) {
  const out = [{ text: board.title, tone: 'gold' }];
  const side = (name, rows) => {
    if (name) out.push({ text: name, tone: 'bronze' });
    if (!rows.length) out.push({ text: '  no entries yet', tone: 'muted' });
    rows.forEach((r) => out.push({ text: '  ' + r, tone: 'plain' }));
  };
  if (board.split) { side('Solo', board.solo); side('Team', board.team); } else side(null, board.solo);
  return out;
}

/** The label of a tower button: ">" marks the one shown. */
export const towerLabel = (t, selected) => (t.id === selected ? '> ' : '') + t.displayName + ' L' + t.level;
