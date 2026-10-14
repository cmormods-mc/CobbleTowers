// How the intermission shows a card that is not a plain modifier: a relic (P34) or a room's option (P34b, P45). Pure.

const RELIC_MARK = 'Held for this run; not permanent equipment.';
const RISK_NAME = ['Minor', 'Moderate', 'Severe'];

/** @returns {{kind:'modifier'|'relic'|'event', title:string, sub:string, lines:string[]}} */
export function cardView(card) {
  if (card.risk < 0) {
    // A room option: "Forge: every player pays 175 CobbleDollars for a battle relic" -> title "Forge", the rest below it.
    const i = card.displayName.indexOf(': ');
    const title = i > 0 ? card.displayName.slice(0, i) : card.displayName;
    const rest = i > 0 ? card.displayName.slice(i + 2) : '';
    const sub = rest.charAt(0).toUpperCase() + rest.slice(1);
    return { kind: 'event', title, sub, lines: sub ? [sub] : [card.displayName] };
  }
  if (card.lines.includes(RELIC_MARK)) {
    const tags = (card.lines.find((l) => l.startsWith('Tags: ')) || '').slice(6);
    return { kind: 'relic', title: card.displayName, sub: tags ? 'Relic: ' + tags : 'Relic', lines: card.lines.filter((l) => l !== RELIC_MARK) };
  }
  return { kind: 'modifier', title: card.displayName, sub: (RISK_NAME[card.risk] || 'Event') + ' risk', lines: card.lines };
}

/** What the screen says to do, by what is on offer. */
export function offerHint(cards) {
  if (!cards.length) return 'No modifier this floor';
  const kinds = new Set(cards.map((c) => cardView(c).kind));
  if (kinds.size === 1 && kinds.has('relic')) return 'Choose a relic to carry';
  if (kinds.size === 1 && kinds.has('event')) return 'A room on the way: choose';
  return 'Choose a modifier for the next floor';
}
