// Pure view models for the Frontier Desk intermission (P51 slice 2). The page shows "choices"; today they are the draft's cards,
// and P50 (the route map) will make them nodes on a route without changing how a choice is drawn.
import { parseLine } from '../lines.js';
import { cardView } from '../rooms.js';

const PAYOUT = /([+-]\d+%)\s+to your final payout/;
const BOILERPLATE = /^The risk you carry is paid out when the tower ends\.?$/;

/** 0 minor, 1 moderate, 2 severe, -1 for a room option or a relic. */
export const riskTone = (risk) => (risk === 0 ? 'ok' : risk === 1 ? 'warn' : risk === 2 ? 'bad' : 'event');

/**
 * One draft card as the page draws it.
 * @returns {{index:number,id:string,kind:string,title:string,sub:string,tone:string,payout:string|null,facts:object[],lines:object[],votes:number,theme:string,risk:number}}
 */
export function choiceOf(card, index) {
  const v = cardView(card);
  const parsed = v.lines.map(parseLine);
  const payoutLine = card.lines.map((l) => PAYOUT.exec(l)).find(Boolean);
  const facts = parsed.filter((l) => !PAYOUT.test(l.text) && !BOILERPLATE.test(l.text) && !/^Risk: /.test(l.text));
  return { index, id: card.id, kind: v.kind, title: v.title, sub: v.sub, tone: riskTone(card.risk), payout: payoutLine ? payoutLine[1] : null,
    facts, lines: parsed, votes: card.votes, theme: card.theme || '', risk: card.risk };
}

export const choicesOf = (draft) => (draft?.cards ?? []).map(choiceOf);

/**
 * The floor ladder: one node per floor of the cycle, cleared floors done, the next one current, milestones marked.
 * Empty when the server did not say how long the cycle is.
 */
export function pathNodes(extras) {
  const len = extras?.cycleLength ?? 0;
  if (len <= 0) return [];
  const ms = new Set(extras.milestones ?? []);
  const cleared = extras.cycleFloor ?? 0;
  const nodes = [];
  for (let n = 1; n <= len; n++) nodes.push({ n, state: n <= cleared ? 'done' : n === cleared + 1 ? 'now' : '', milestone: ms.has(n) });
  return nodes;
}

/** "Floor 4 of 10 cleared" or just "Floor 4 cleared" when the cycle length is unknown. */
export const clearedText = (floor, extras) => {
  const len = extras?.cycleLength ?? 0;
  return len > 0 && extras.cycleFloor > 0 ? 'Floor ' + extras.cycleFloor + ' of ' + len + ' cleared' : 'Floor ' + floor + ' cleared';
};

/** Health bar colour by percent. */
export const hpTone = (hp) => (hp <= 0 ? 'out' : hp < 35 ? 'low' : hp < 70 ? 'mid' : 'full');
