// The Frontier Desk look for the pop-ups and the last full screens (P51 slice 7): the rental draft table, the reward reveal, the vendor, the
// scouting report, the spectator panel and the waiting page. Same requests and the same pure helpers as the native versions.
import { useState } from 'preact/hooks';
import { html } from '../html.js';
import { act, closeGui } from '../bridge.js';
import { update } from '../store.js';
import { closeScout } from '../reducer.js';
import { idTail, pretty } from '../util.js';
import { canBuy, grantIcon, stockLine } from '../rewards.js';
import { screen, useScreen } from '../components/native.js';
import { Done, PackStage } from '../screens/Draft.js';
import { DeskIcon, DeskItem } from './icons.js';

const step = (i, by, n) => (n ? (((i + by) % n) + n) % n : 0);

/** The picture of one reward: a coin, the raid-points crystal, or the item itself. */
export function GrantPicture({ grant, w = 8 }) {
  const kind = grantIcon(grant);
  if (kind === 'coin') return html`<${DeskIcon} name="coin" w=${w} />`;
  if (kind === 'raid') return html`<i class="dico" style=${`width:calc(var(--u) * ${w});height:calc(var(--u) * ${w});background-image:url(assets/gui/raid_points.png);background-size:100% 100%`}></i>`;
  return html`<${DeskItem} id=${grant.item} w=${w} />`;
}

/** The rental draft: three packs of five, keep two from each, on a dark table so the pixel cards keep their own look. */
export function DeskDraft({ draft }) {
  useScreen();
  const restart = () => act('RentalDraftAction', { action: 'RESTART', a: 0, b: 0 });
  return html`<div class="fd"><div class="dtop"><span class="logo">COBBLE <b>TOWERS</b></span><span class="dcrumb"><b>Rental draft</b> · Keep two cards from each of three packs</span>
      <div class="dots" role="img" aria-label=${'Pack ' + (draft.complete ? 3 : draft.current + 1) + ' of 3'}>${[0, 1, 2].map((i) => html`<i key=${i} class=${(i < draft.current || draft.complete ? 'on' : '') + (i === draft.current && !draft.complete ? ' cur' : '')}></i>`)}</div>
      <span class="sp2"></span><button class="tl" onClick=${restart}>Start over</button><button class="x" aria-label="Close" onClick=${closeGui}>×</button></div>
    <div class="dbody" style="grid-template-columns:1fr;flex:1"><div class="ns dtable" style=${`--g:${screen.g}px`}><div class="dstage2">
      ${draft.complete ? html`<${Done} draft=${draft} desk=${true} />` : html`<${PackStage} draft=${draft} desk=${true} />`}</div></div></div></div>`;
}

/** The reward reveal over whatever is on screen: one grant a page. */
export function DeskReward({ reward, onClose }) {
  const [page, setPage] = useState(0);
  const grants = reward.grants, at0 = Math.min(page, Math.max(0, grants.length - 1));
  const grant = grants[at0];
  const label = grant ? grant.label || pretty(idTail(grant.item)) : '';
  return html`<div class="fd dover" role="dialog" aria-label="Rewards secured"><div class="dmodal">
    <span class="lbl">Rewards secured</span><h3 style="font-size:calc(var(--u) * 2.4)">Through floor ${reward.floorIndex}</h3>
    ${grant ? html`<div class="dgrant" key=${at0}><${GrantPicture} grant=${grant} w=${9} /><b>${label}</b><span>× ${grant.amount}</span></div>` : html`<div class="empty">No rewards in this grant.</div>`}
    ${grants.length > 1 ? html`<div class="dpager"><button class="dbtn gh dsm" aria-label="Previous reward" onClick=${() => setPage(step(at0, -1, grants.length))}>‹</button><span>${at0 + 1} / ${grants.length}</span>
      <button class="dbtn gh dsm" aria-label="Next reward" onClick=${() => setPage(step(at0, 1, grants.length))}>›</button></div>` : null}
    <button class="dbtn" onClick=${onClose}>Continue</button></div></div>`;
}

/** The tower vendor: goods as cards, a recipient to cycle, the chosen one's details and Purchase. */
export function DeskVendor({ vendor, onClose }) {
  const [target, setTarget] = useState(0);
  const [sel, setSel] = useState(0);
  const team = vendor.team, services = vendor.services;
  const who = team[Math.min(target, team.length - 1)];
  const chosen = services[Math.min(sel, services.length - 1)];
  return html`<div class="fd dover" role="dialog" aria-label="Tower supplies"><div class="dmodal wide">
    <div class="dmhead"><div><span class="lbl">Tower supplies</span><h3 style="font-size:calc(var(--u) * 2.4)">Balance: ${vendor.cobbleDollars}</h3></div>
      ${who ? html`<button class="dbtn gh dsm" onClick=${() => setTarget(step(target, 1, team.length))}>For: ${who.name}${who.online ? '' : ' (offline)'}</button>` : null}</div>
    <div class="dgoods">${services.length === 0 ? html`<div class="empty">Nothing is for sale.</div>` : services.map((e, i) => html`<button key=${e.id} class=${'dgood' + (i === sel ? ' sel' : '')} aria-pressed=${i === sel} onClick=${() => setSel(i)}>
      <b>${e.displayName}</b><span>${e.priceCobbleDollars} CobbleDollars</span><small>${e.remainingPurchases < 0 ? 'Unlimited' : e.remainingPurchases + ' left'}</small></button>`)}</div>
    <div class="dmfoot"><div style="flex:1;min-width:0">${chosen ? html`<b>${chosen.displayName}</b><div class="dsub">${chosen.priceCobbleDollars} CobbleDollars · ${stockLine(chosen)}</div>` : null}
      ${vendor.message ? html`<div class="dsub" role="status">${vendor.message}</div>` : null}</div>
      ${chosen ? html`<button class="dbtn" disabled=${!canBuy(vendor, chosen, who)} onClick=${() => act('VendorPurchase', { serviceId: chosen.id, targetPlayerId: who.id })}>Purchase</button>` : null}
      <button class="dbtn gh" onClick=${onClose}>Close</button></div></div></div>`;
}

/** What the scouts learned about the floor: the categories as cards, the chosen one read in full. */
export function DeskScouting({ scout }) {
  const [sel, setSel] = useState(0);
  const cats = scout.categories;
  const chosen = cats[Math.min(sel, cats.length - 1)];
  const close = () => { update(closeScout); closeGui(); };
  return html`<div class="fd"><div class="dtop"><span class="logo">COBBLE <b>TOWERS</b></span><span class="dcrumb"><b>Scouting</b> · Floor ${scout.floorIndex}</span><span class="sp2"></span>
      <button class="x" aria-label="Close" onClick=${close}>×</button></div>
    <div class="dbody" style="grid-template-columns:64fr 36fr;flex:1">
      <div class="dcard dcol"><div class="ch"><h3>What the scouts saw</h3><span class="lbl">${cats.length} ${cats.length === 1 ? 'detail' : 'details'}</span></div>
        <div class="dtiles wide">${cats.length === 0 ? html`<div class="empty">Nothing revealed.</div>` : cats.map((c, i) => html`<button key=${i} class=${'dach' + (i === sel ? ' pick' : '')} aria-pressed=${i === sel} onClick=${() => setSel(i)}>
          <b>${c.name}</b><span>${c.value}</span></button>`)}</div></div>
      <div class="dcard dcol"><div class="ch"><h3>${chosen ? chosen.name : 'Details'}</h3></div><div class="dlines" style="flex:1;font-size:calc(var(--u) * 1.5);line-height:1.45">${chosen ? chosen.value : 'Nothing revealed.'}</div>
        <div class="dactions"><button class="dbtn" onClick=${close}>Close</button></div></div></div></div>`;
}

/** The spectator panel: who you are following and how their team is doing (click-through). */
export function DeskSpectator({ spec }) {
  return html`<div class="fd-hud"><b>Following ${spec.teammateName}</b><div>${spec.remainingCount}/${spec.totalCount} Pokémon remaining</div><small>Floor ${spec.floorIndex} · ${spec.runStateLabel}</small></div>`;
}

/** Shown before the server has sent anything. */
export function DeskWaiting() {
  return html`<div class="fd"><div class="dbody" style="grid-template-columns:1fr;flex:1"><div class="dcard empty" style="display:grid;place-items:center"><div style="text-align:center"><h3 style="font-size:calc(var(--u) * 3)">CobbleTowers</h3><div class="dsub">Waiting for the tower…</div></div></div></div></div>`;
}
