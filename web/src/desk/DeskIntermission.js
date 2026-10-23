// The Frontier Desk look for the between-floors screen (P51 slice 2): your team's health, the offers, the details of the one you are
// looking at, and the party's readiness. Same actions and the same vote logic as the native screen (screens/Intermission.js).
import { useEffect, useState } from 'preact/hooks';
import { html } from '../html.js';
import { act, closeGui, myName } from '../bridge.js';
import { fmtClock } from '../util.js';
import { offerHint } from '../rooms.js';
import { confirmLabel } from '../screens/Intermission.js';
import { sendFeature } from '../screens/Features.js';
import { DeskSprite } from './DeskHall.js';
import { choicesOf, clearedText, hpTone, pathNodes } from './choices.js';
import { RouteMap } from './DeskRoute.js';
import { ModArt, RiskGems, artOf } from './modArt.js';
import { hasRoute } from './routeMap.js';

const send = (action, argument = 0) => act('IntermissionAction', { action, argument });

/** Counts down from the server's value at the moment it arrived; a 1 s timer, only while a countdown shows. */
function Countdown({ from, at: arrived }) {
  const [, tick] = useState(0);
  useEffect(() => { const id = setInterval(() => tick((n) => n + 1), 1000); return () => clearInterval(id); }, []);
  return fmtClock(from - (Date.now() - arrived) / 1000);
}

function Path({ extras }) {
  const nodes = pathNodes(extras);
  if (!nodes.length) return null;
  return html`<div class="dpath" aria-label="Floors of this tower">${nodes.map((nd, i) => html`<span key=${nd.n} class="dpathpart">
    <span class=${'dnd ' + nd.state + (nd.milestone ? ' ms' : '')} title=${nd.milestone ? 'Floor ' + nd.n + ' (milestone)' : 'Floor ' + nd.n}>${nd.n}</span>
    ${i < nodes.length - 1 ? html`<span class=${'dbar' + (nd.state === 'done' ? ' on' : '')}></span>` : null}</span>`)}</div>`;
}

function Team({ party }) {
  return html`<div class="dcard dcol"><div class="ch"><h3>Your team</h3><span class="lbl">After this floor</span></div>
    ${party.length === 0 ? html`<div class="empty">No team to show.</div>` : party.map((m, i) => html`<div class=${'dteam' + (m.fainted ? ' out' : '')} key=${i}>
      <${DeskSprite} name=${m.species} w=${5} />
      <div style="flex:1;min-width:0"><div class="dname">${m.name}<small>Lv ${m.level}</small></div>
        <div class="dhp" role="img" aria-label=${m.fainted ? 'Fainted' : m.hp + ' percent health'}><i class=${'t-' + hpTone(m.hp)} style=${`width:${m.fainted ? 0 : m.hp}%`}></i></div></div></div>`)}</div>`;
}

function Choice({ c, selected, chosen, enabled, onPick, onHover }) {
  return html`<button class=${'dmc' + (selected ? ' sel' : '') + (chosen ? ' chosen' : '')} disabled=${!enabled} aria-pressed=${selected}
    onClick=${onPick} onMouseEnter=${() => onHover(true)} onMouseLeave=${() => onHover(false)} onFocus=${() => onHover(true)} onBlur=${() => onHover(false)}>
    <${ModArt} art=${artOf(c.theme, c.kind, c.title)} />
    <div class="dmbody">
      <h4 class=${c.kind === 'event' && c.sub.length > 28 ? 'long' : ''}>${c.kind === 'event' ? c.sub || c.title : c.title}</h4>
      <div class="dtags"><${RiskGems} risk=${c.risk} /><span class=${'ty rk-' + c.tone}>${c.kind === 'event' ? c.title : c.sub}</span></div>
      ${c.kind === 'event' ? null
        : c.facts.slice(0, 3).map((l, i) => html`<div key=${i} class=${'dpl ' + (l.tone === 'benefit' ? 'p' : l.tone === 'cost' ? 'c' : 'n')}><i>${l.sign || '·'}</i>${l.text}</div>`)}
      <div class="dpay"><span>${chosen ? 'Chosen' : selected ? 'Selected' : ''}${c.votes ? (chosen || selected ? ' · ' : '') + c.votes + (c.votes === 1 ? ' vote' : ' votes') : ''}</span>${c.payout ? html`<b>${c.payout}<small> payout</small></b>` : null}</div>
    </div>
  </button>`;
}

/** @param {{inter: object, at: number}} props */
export function DeskIntermission({ inter, at: arrived }) {
  const draft = inter.draft || { cards: [], chosen: -1, myVote: -1, state: 0 };
  const choices = choicesOf(draft);
  const offer = draft.cards.map((c) => c.id).join('|') + '@' + inter.floor;
  const [view, setView] = useState({ offer, selected: draft.state === 2 ? draft.chosen : draft.myVote });
  const [hover, setHover] = useState(-1);
  const [pendingAt, setPendingAt] = useState(0);
  // A new offer starts from the player's own vote; a refresh of the same offer keeps the choice.
  let selected = view.selected;
  if (view.offer !== offer) { selected = draft.state === 2 ? draft.chosen : draft.myVote; setView({ offer, selected }); }
  useEffect(() => { setPendingAt(0); }, [inter]);
  useEffect(() => { if (!pendingAt) return undefined; const id = setTimeout(() => setPendingAt(0), 8000); return () => clearTimeout(id); }, [pendingAt]);

  const extras = inter.extras || {};
  const me = inter.members.find((m) => m.name === myName());
  const ready = me ? me.ready : false, cashOut = me ? me.cashOut : false;
  const open = draft.state === 1, idle = inter.countdown < 0, pending = pendingAt > 0;
  const n = choices.length;
  const inspected = hover >= 0 && hover < n ? hover : selected >= 0 && selected < n ? selected : n ? 0 : -1;
  const shown = inspected >= 0 ? choices[inspected] : null;
  const hint = inter.countdown >= 0 ? 'Next floor in ' : inter.message || (draft.state === 1 ? (pending ? 'Sending your vote...' : offerHint(draft.cards)) : draft.state === 2 ? 'Modifier chosen' : 'No modifier this floor');
  const confirm = () => { if (pending || selected < 0 || !open) return; setPendingAt(Date.now()); send('PICK_CARD', selected); };
  const canConfirm = open && idle && !pending && selected >= 0 && selected !== draft.myVote;

  return html`<div class="fd">
    <div class="dtop"><span class="logo">COBBLE <b>TOWERS</b></span><span class="dcrumb"><b>${clearedText(inter.floor, extras)}</b>${extras.tower ? ' · ' + extras.tower : ''}</span>
      <span class="sp2"></span><button class="tl" onClick=${() => sendFeature('run')}>Current run</button><button class="x" aria-label="Close" onClick=${closeGui}>×</button></div>
    <${Path} extras=${extras} />
    <div class="dbody" style="grid-template-columns:21fr 52fr 27fr;flex:1">
      <${Team} party=${extras.party ?? []} />
      <div style="display:flex;flex-direction:column;gap:calc(var(--u) * 1);min-height:0">
        <div class="dhint">${hint}${inter.countdown >= 0 ? html`<${Countdown} from=${inter.countdown} at=${arrived} />` : ''}</div>
        ${n === 0 ? html`<div class="dcard empty" style="flex:1;display:grid;place-items:center">No modifier is on offer this floor. You can still visit the vendor, ready up or cash out.</div>`
          : hasRoute(extras) ? html`<${RouteMap} route=${extras.route} choices=${choices} selected=${open ? selected : -1} chosen=${draft.state === 2 ? draft.chosen : -1}
            enabled=${open && idle && !pending} lead=${(extras.party && extras.party[0] && extras.party[0].species) || ''}
            onPick=${(card) => setView({ offer, selected: card })} onHover=${(card) => setHover(card)} />`
          : html`<div class="dchoices" style=${`grid-template-columns:repeat(${Math.min(n, 3)},1fr)`}>${choices.map((c) => html`<${Choice} key=${c.id} c=${c} selected=${open && c.index === selected}
            chosen=${draft.state === 2 && c.index === draft.chosen} enabled=${open && idle && !pending}
            onPick=${() => setView({ offer, selected: c.index })} onHover=${(on) => setHover(on ? c.index : -1)} />`)}</div>`}
      </div>
      <div class="dcard dcol dside">${shown ? html`<${ModArt} art=${artOf(shown.theme, shown.kind, shown.title)} wide />` : null}<div class="ch"><h3>${shown ? shown.title : 'Details'}</h3>${shown ? html`<span class=${'ty rk-' + shown.tone}>${shown.kind === 'event' ? 'Room' : shown.sub}</span>` : null}</div>
        <div class="dlines">${shown ? shown.lines.map((l, i) => html`<div key=${i} class=${'dpl ' + (l.tone === 'benefit' ? 'p' : l.tone === 'cost' ? 'c' : 'n')}><i>${l.sign || '·'}</i>${l.text}</div>`) : html`<div class="empty">Nothing to show.</div>`}</div>
        <div class="dmembers"><span class="lbl">Party</span>${inter.members.map((m) => html`<div key=${m.name} class="dmem"><span class=${'dot ' + (m.ready ? 'on' : '')}></span>${m.name}<small>${m.cashOut ? 'cashing out' : m.ready ? 'ready' : 'waiting'}</small></div>`)}</div></div>
    </div>
    <div class="dft"><button class="dbtn" disabled=${!canConfirm} onClick=${confirm}>${confirmLabel(draft, selected, pending)}</button>
      <button class="dbtn gh" onClick=${() => send('VENDOR')}>Vendor</button>
      <button class="dbtn gh" disabled=${!(ready || !open)} onClick=${() => send(ready ? 'UNREADY' : 'READY')}>${ready ? 'Not ready' : 'Ready'}</button>
      <button class="dbtn gh" onClick=${() => send(cashOut ? 'STAY' : 'CASH_OUT')}>${cashOut ? 'Keep going' : 'Cash out'}</button>
      <span class="st"></span></div></div>`;
}
