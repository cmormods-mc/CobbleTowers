// Pure helpers for the Ranks tab: the region duel, from the RanksState payload. The server decides every number and who leads.

/** The display name of a region id, from the payload's region list. */
export const regionName = (ranks, id) => ranks.regions.find((r) => r.id === id)?.name ?? id;

/** Stats led by each side, and the sentence for the verdict bar. */
export function verdict(ranks) {
  const left = ranks.rows.filter((r) => r.lead < 0).length;
  const right = ranks.rows.filter((r) => r.lead > 0).length;
  const a = regionName(ranks, ranks.left), b = regionName(ranks, ranks.right);
  const text = left === right ? `Level at ${left} each` : left > right ? `${a} leads ${left} to ${right}` : `${b} leads ${right} to ${left}`;
  return { left, right, text };
}

/** The region picker for one side: every region, marking the chosen one, the viewer's own, and the other side's (not pickable). */
export function pickerOptions(ranks, side) {
  const chosen = side === 'left' ? ranks.left : ranks.right;
  const other = side === 'left' ? ranks.right : ranks.left;
  return ranks.regions.map((r) => ({ ...r, chosen: r.id === chosen, mine: r.id === ranks.yourRegion, taken: r.id === other }));
}

/** The pair to ask for when one side of the duel is changed to `id`; picking the other side's region swaps them. */
export function pairAfterPick(ranks, side, id) {
  if (side === 'left') return { left: id, right: id === ranks.right ? ranks.left : ranks.right };
  return { left: id === ranks.left ? ranks.right : ranks.left, right: id };
}

/** "3 players" / "1 player" / "no players yet". */
export const memberText = (n) => (n <= 0 ? 'no players yet' : n === 1 ? '1 player' : n + ' players');
