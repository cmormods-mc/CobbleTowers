import { useEffect, useState } from 'preact/hooks';
import { html } from '../html.js';
import { Button, Chip } from '../components/ui.js';
import { act, myName } from '../bridge.js';
import { fmtClock, RISK } from '../util.js';
import { parseLine } from '../lines.js';

const RISK_COLOR = { minor: 'var(--minor)', moderate: 'var(--moderate)', severe: 'var(--severe)' };

function ModifierCard({ card, index, mine, chosen }) {
  const risk = RISK[card.risk];
  return html`
    <div style="flex:1;min-width:0;padding-top:22px;display:flex">
      <button class=${'nine parch mod' + (mine ? ' sel' : '')} onClick=${() => act('IntermissionAction', { action: 'PICK_CARD', argument: index })}>
        ${chosen ? html`<span class="rib">Chosen</span>` : null}
        <div class="hd"><b>${card.displayName}</b>
          ${risk ? html`<${Chip} color=${RISK_COLOR[risk]}>${risk.toUpperCase()}<//>` : html`<${Chip} color="var(--burg)">EVENT<//>`}</div>
        <div class="sub">${card.theme || ''}</div>
        ${card.lines.map(parseLine).map((l) => html`<p class=${l.tone}>${l.sign ? html`<i>${l.sign}</i>` : null}${l.text}</p>`)}
        <div class="ft"><span>${mine ? 'your vote' : 'click to vote'}</span><b>${card.votes} ${card.votes === 1 ? 'vote' : 'votes'}</b></div>
      </button>
    </div>`;
}

/** Counts down from the server's value at the moment it arrived; one 1 s timer, only while a countdown is showing. */
function Countdown({ from, at }) {
  const [, tick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => tick((n) => n + 1), 1000);
    return () => clearInterval(id);
  }, []);
  return html`<b>${fmtClock(from - (Date.now() - at) / 1000)}</b>`;
}

export function Intermission({ inter, at }) {
  const draft = inter.draft || { cards: [], chosen: -1, myVote: -1, state: 0 };
  const me = inter.members.find((m) => m.name === myName());
  const ready = me ? me.ready : false;
  const cashOut = me ? me.cashOut : false;
  const subtitle = inter.message && !/cleared/.test(inter.message) ? inter.message : 'Choose a card';

  const cards = draft.cards.length
    ? draft.cards.map((c, i) => html`<${ModifierCard} card=${c} index=${i} mine=${draft.myVote === i} chosen=${draft.chosen === i} />`)
    : html`<div class="nine dark grow" style="text-align:center">
        <b class="gold" style="font-size:22px">${draft.state === 0 ? 'No modifier is on offer this floor.' : 'The offer is being revealed...'}</b>
        <div class="sub">You can still visit the vendor, ready up or cash out.</div></div>`;

  return html`
    <div class="row center">
      <div class="grow"><h1>Intermission</h1><div class="sub">Floor ${inter.floor} cleared · ${subtitle}</div></div>
      ${inter.countdown >= 0 ? html`<div class="timer nine dark" style="padding:0 6px">Next floor in <${Countdown} from=${inter.countdown} at=${at} /></div>` : null}
    </div>
    <div class="row grow">
      <div class="row grow" style="gap:20px;align-items:stretch">${cards}</div>
      <div class="col" style="width:340px">
        <div class="nine dark grow scroll"><h3>This run</h3><div class="sub">Floor ${inter.floor}</div>
          <div style="margin-top:6px">${inter.members.map((m) => html`
            <div class="eff"><span class="grow">${m.name}</span>
              ${m.cashOut ? html`<${Chip} color="var(--burg)">CASH OUT<//>` : null}
              <${Chip} color=${m.ready ? 'var(--green)' : 'var(--brown)'}>${m.ready ? 'READY' : 'NOT READY'}<//></div>`)}</div></div>
        <div class="nine dark">
          <div class="row"><${Button} kind="sec" class="grow" onClick=${() => act('IntermissionAction', { action: 'VENDOR', argument: 0 })}>Vendor<//>
            <${Button} class="grow" onClick=${() => act('IntermissionAction', { action: ready ? 'UNREADY' : 'READY' })}>${ready ? 'Not ready' : 'Ready'}<//></div>
          <div class="row" style="margin-top:8px"><${Button} kind="sec" class="grow" onClick=${() => act('IntermissionAction', { action: cashOut ? 'STAY' : 'CASH_OUT' })}>${cashOut ? 'Keep going' : 'Cash out'}<//></div>
          <div class="sub" style="margin-top:6px">The risk you carry is paid out when the tower ends.</div>
        </div>
      </div>
    </div>`;
}
