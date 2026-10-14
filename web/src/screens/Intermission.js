import { useEffect, useState } from 'preact/hooks';
import { html } from '../html.js';
import { act, closeGui, myName } from '../bridge.js';
import { fmtClock, RISK } from '../util.js';
import { parseLine } from '../lines.js';
import { at, Label, NB, Panel, screen, useScreen } from '../components/native.js';
import { sendFeature } from './Features.js';
import { cardView, offerHint } from '../rooms.js';

const send = (action, argument = 0) => act('IntermissionAction', { action, argument });
const RISK_NAME = ['Minor', 'Moderate', 'Severe'];

/** The card's heading line: its risk, or "Event" for a card with none. */
export const riskLabel = (risk) => (RISK[risk] ? RISK_NAME[risk] + ' risk' : 'Event');

/** What the confirm button says (mirrors IntermissionScreen.confirmLabel). */
export function confirmLabel(draft, selected, pending) {
  if (pending) return 'Sending...';
  if (draft.state === 2) return 'Settled';
  if (draft.state !== 1) return 'Confirm';
  if (selected < 0) return 'Choose a card';
  if (selected === draft.myVote) return 'Voted';
  return 'Confirm';
}

/** Counts down from the server's value at the moment it arrived; a 1 s timer, only while a countdown shows. */
function Countdown({ from, at: arrived }) {
  const [, tick] = useState(0);
  useEffect(() => { const id = setInterval(() => tick((n) => n + 1), 1000); return () => clearInterval(id); }, []);
  return fmtClock(from - (Date.now() - arrived) / 1000);
}

function Card({ x, y, w, h, card, selected, chosen, enabled, onPick, onHover }) {
  const v = cardView(card);
  return html`<button class=${'n-btn n-fr n-parch mcard' + (selected ? ' sel' : '') + (chosen ? ' chosen' : '')} disabled=${!enabled} style=${at(x, y, w, h)}
    onClick=${onPick} onMouseEnter=${() => onHover(true)} onMouseLeave=${() => onHover(false)}>
    <${Label} x=${-1} y=${-1} w=${w - 14} color="ink">${v.title}<//>
    ${v.kind === 'event' ? html`<div class="n-lines wrap" style=${at(-1, 11, w - 14, h - 40)}>${v.sub}</div>` : html`<${Label} x=${-1} y=${11} w=${w - 14} color="soft">${v.sub}<//>`}
    ${v.kind === 'relic' ? html`<div class="n-lines wrap" style=${at(-1, 23, w - 14, h - 52)}>${v.lines.filter((l) => !l.startsWith('Tags: ')).slice(0, 2).map((l) => parseLine(l).text).join(' ')}</div>` : null}
    <${Label} x=${-1} y=${h - 30} w=${w - 14} color="soft">${chosen ? 'Chosen' : selected ? 'Selected' : ''}<//>
    <${Label} x=${-1} y=${h - 21} w=${w - 14} color="ink">${card.votes} ${card.votes === 1 ? 'vote' : 'votes'}<//>
  </button>`;
}

/** The between-floors menu: modifier choice, vendor, ready-up and cash-out vote (IntermissionScreen). */
export function Intermission({ inter, at: arrived }) {
  useScreen();
  const SW = screen.w, SH = screen.h;
  const draft = inter.draft || { cards: [], chosen: -1, myVote: -1, state: 0 };
  const offer = draft.cards.map((c) => c.id).join('|') + '@' + inter.floor;
  const [view, setView] = useState({ offer, selected: draft.state === 2 ? draft.chosen : draft.myVote });
  const [hover, setHover] = useState(-1);
  const [page, setPage] = useState(0);
  const [pendingAt, setPendingAt] = useState(0);
  // A new offer starts from the player's own vote; a refresh of the same offer keeps the choice.
  let selected = view.selected;
  if (view.offer !== offer) { selected = draft.state === 2 ? draft.chosen : draft.myVote; setView({ offer, selected }); setPage(0); }
  useEffect(() => { setPendingAt(0); }, [inter]);
  useEffect(() => { if (!pendingAt) return undefined; const id = setTimeout(() => setPendingAt(0), 8000); return () => clearTimeout(id); }, [pendingAt]);

  const me = inter.members.find((m) => m.name === myName());
  const ready = me ? me.ready : false, cashOut = me ? me.cashOut : false;
  const open = draft.state === 1, idle = inter.countdown < 0, pending = pendingAt > 0;
  const n = draft.cards.length;
  const panelW = Math.min(SW - 12, 440), panelX = Math.floor((SW - panelW) / 2), gap = 4;
  const cardW = n === 0 ? 0 : Math.min(124, Math.floor((panelW - 12 - (n - 1) * gap) / n));
  const cardH = Math.max(78, Math.min(96, Math.floor(SH * 0.32)));
  const cardsLeft = Math.floor((SW - (n * cardW + Math.max(0, n - 1) * gap)) / 2), cardsTop = 32;
  const actionsTop = SH - 8 - 56;
  const sheetX = panelX + 6, sheetW = panelW - 12, sheetY = (n === 0 ? cardsTop : cardsTop + cardH) + 6, sheetH = Math.max(36, actionsTop - 6 - sheetY);
  const rowW = panelW - 12, confirmW = Math.floor(rowW * 0.58) - 2, third = Math.floor((rowW - 8) / 3);
  const inspected = hover >= 0 && hover < n ? hover : selected >= 0 && selected < n ? selected : n ? 0 : -1;
  const hint = inter.countdown >= 0 ? 'Next floor in ' : inter.message || (draft.state === 1 ? (pending ? 'Sending your vote...' : offerHint(draft.cards)) : draft.state === 2 ? 'Modifier chosen' : 'No modifier this floor');
  const confirm = () => { if (pending || selected < 0 || !open) return; setPendingAt(Date.now()); send('PICK_CARD', selected); };
  const card = inspected >= 0 ? draft.cards[inspected] : null;
  const cv = card ? cardView(card) : null;
  const perPage = Math.max(1, Math.floor((sheetH - 26) / 10));
  const pages = cv ? Math.max(1, Math.ceil(cv.lines.length / perPage)) : 1;
  const shown = Math.min(page, pages - 1);

  return html`
    <div class="ns" style=${`--g:${screen.g}px`}>
      <${Panel} x=${panelX} y=${4} w=${panelW} h=${SH - 8} />
      <${Label} x=${0} y=${12} w=${SW} center>Floor ${inter.floor} cleared<//>
      <${Label} x=${panelX + 12} y=${22} w=${panelW - 24} color=${inter.countdown >= 0 ? 'bronze' : 'muted'}>${hint}${inter.countdown >= 0 ? html`<${Countdown} from=${inter.countdown} at=${arrived} />` : ''}<//>
      <${NB} x=${panelX + panelW - 70} y=${9} w=${62} h=${16} kind="dark" onClick=${() => sendFeature('run')}>This run<//>
      ${draft.cards.map((c, i) => html`<${Card} key=${c.id} x=${cardsLeft + i * (cardW + gap)} y=${cardsTop} w=${cardW} h=${cardH} card=${c}
        selected=${open && i === selected} chosen=${draft.state === 2 && i === draft.chosen} enabled=${open && idle && !pending}
        onPick=${() => setView({ offer, selected: i })} onHover=${(on) => setHover(on ? i : -1)} />`)}
      <${Panel} x=${sheetX} y=${sheetY} w=${sheetW} h=${sheetH} kind="parch">
        ${cv ? html`<${Label} x=${0} y=${0} w=${sheetW - 22} color="ink">${cv.kind === 'event' ? cv.title : cv.title + '  -  ' + cv.sub}<//>
          <div class="n-lines" style=${at(0, 12, sheetW - 22, sheetH - 24)}>${cv.lines.slice(shown * perPage, (shown + 1) * perPage).map(parseLine).map((l) => html`<p class=${'ln ' + l.tone}>${l.sign ? l.sign + ' ' : ''}${l.text}</p>`)}</div>
          ${pages > 1 ? html`<${Label} x=${sheetW - 86} y=${sheetH - 24} color="soft">${shown + 1}/${pages}<//>` : null}`
        : html`<${Label} x=${0} y=${0} w=${sheetW - 22} color="ink">No modifier is on offer this floor.<//>
          <${Label} x=${0} y=${12} w=${sheetW - 22} color="soft">You can still visit the vendor, ready up or cash out.<//>`}
      <//>
      ${pages > 1 ? html`<${NB} x=${sheetX + sheetW - 48} y=${sheetY + sheetH - 17} w=${20} h=${14} kind="dark" onClick=${() => setPage((shown + pages - 1) % pages)}>${'<'}<//>
        <${NB} x=${sheetX + sheetW - 26} y=${sheetY + sheetH - 17} w=${20} h=${14} kind="dark" onClick=${() => setPage((shown + 1) % pages)}>${'>'}<//>` : null}
      <${NB} x=${sheetX} y=${actionsTop} w=${confirmW} h=${18} disabled=${!(open && idle && !pending && selected >= 0 && selected !== draft.myVote)} onClick=${confirm}>${confirmLabel(draft, selected, pending)}<//>
      <${NB} x=${sheetX + confirmW + 4} y=${actionsTop} w=${rowW - confirmW - 4} h=${18} kind="dark" onClick=${closeGui}>Close<//>
      <${NB} x=${sheetX} y=${actionsTop + 21} w=${third} h=${18} kind="dark" onClick=${() => send('VENDOR')}>Vendor<//>
      <${NB} x=${sheetX + third + 4} y=${actionsTop + 21} w=${third} h=${18} kind="dark" disabled=${!(ready || !open)} onClick=${() => send(ready ? 'UNREADY' : 'READY')}>${ready ? 'Not ready' : 'Ready'}<//>
      <${NB} x=${sheetX + (third + 4) * 2} y=${actionsTop + 21} w=${rowW - (third + 4) * 2} h=${18} kind="dark" onClick=${() => send(cashOut ? 'STAY' : 'CASH_OUT')}>${cashOut ? 'Keep going' : 'Cash out'}<//>
      <${Label} x=${panelX + 12} y=${actionsTop + 44} w=${panelW - 24} color="muted">${inter.members.map((m) => m.name + ' ' + (m.ready ? 'ready' : 'waiting') + (m.cashOut ? ' (cashing out)' : '')).join('    ')}<//>
    </div>`;
}
