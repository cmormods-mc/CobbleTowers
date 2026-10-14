import { useState } from 'preact/hooks';
import { html } from '../html.js';
import { act } from '../bridge.js';
import { idTail, pretty } from '../util.js';
import { canBuy, grantIcon, stockLine } from '../rewards.js';
import { at, Icon, ItemIcon, Label, NB, Panel, screen, useScreen } from '../components/native.js';

const step = (i, by, n) => (n ? (((i + by) % n) + n) % n : 0);

/** One picture, `size` GUI pixels, with its left-top at x, y. */
function Picture({ grant, x, y, size = 16 }) {
  const kind = grantIcon(grant);
  if (kind === 'item') return html`<${ItemIcon} id=${grant.item} x=${x} y=${y} size=${size} />`;
  return kind === 'raid' ? html`<i class="ic raid" style=${at(x, y, size, size)}></i>` : html`<${Icon} name=${kind} x=${x} y=${y} size=${size} />`;
}

/** RewardRevealScreen: a parchment label in a wooden frame, one grant a page. `label` is empty for ordinary items. */
export function RewardModal({ reward, onClose }) {
  useScreen();
  const SW = screen.w, SH = screen.h;
  const [page, setPage] = useState(0);
  const grants = reward.grants, at0 = Math.min(page, Math.max(0, grants.length - 1));
  const w = Math.min(250, SW - 40), h = Math.min(145, SH - 84), x = Math.floor((SW - w) / 2), y = Math.floor((SH - h) / 2) - 8;
  const grant = grants[at0];
  const label = grant ? grant.label || pretty(idTail(grant.item)) : '';
  return html`
    <div class="ns over" style=${`--g:${screen.g}px`}>
      <${Panel} x=${x - 6} y=${y - 6} w=${w + 12} h=${h + 12} />
      <${Panel} x=${x} y=${y} w=${w} h=${h} kind="parch" />
      <${Label} x=${0} y=${y + 12} w=${SW} center color="burg">REWARDS SECURED<//>
      <${Label} x=${0} y=${y + 27} w=${SW} center color="ink">Through floor ${reward.floorIndex}<//>
      ${grant ? html`
        <${Picture} grant=${grant} x=${Math.floor(SW / 2) - 16} y=${y + 45} size=${32} />
        <${Label} x=${x + 12} y=${y + h - 34} w=${w - 24} color="ink">${label} x${grant.amount}<//>
        <${Label} x=${0} y=${y + h - 16} w=${SW} center color="ink">${at0 + 1} / ${grants.length}<//>`
      : html`<${Label} x=${0} y=${y + 60} w=${SW} center color="ink">No rewards in this grant<//>`}
      <${NB} x=${Math.floor(SW / 2) - 50} y=${SH - 34} w=${100} h=${22} onClick=${onClose}>Continue<//>
      ${grants.length > 1 ? html`
        <${NB} x=${Math.floor(SW / 2) - 86} y=${SH - 34} w=${28} h=${22} onClick=${() => setPage(step(at0, -1, grants.length))}>${'<'}<//>
        <${NB} x=${Math.floor(SW / 2) + 58} y=${SH - 34} w=${28} h=${22} onClick=${() => setPage(step(at0, 1, grants.length))}>${'>'}<//>` : null}
    </div>`;
}

function ServiceCard({ x, y, w, e, selected, onPick }) {
  return html`<button class=${'n-btn n-fr n-dark card' + (selected ? ' sel' : '')} style=${at(x, y, w, 44)} onClick=${onPick}>
    <${Label} x=${-2} y=${-2} w=${w - 30} color="text">${e.displayName}<//>
    <${Label} x=${-2} y=${11} w=${w - 30} color="bronze">${e.priceCobbleDollars} CobbleDollars<//>
    <${Label} x=${-2} y=${22} w=${w - 30} color="muted">${e.remainingPurchases < 0 ? '' : e.remainingPurchases + ' left'}<//>
    <${Icon} name="pouch" x=${w - 28} y=${12} /><i class="brk"></i></button>`;
}

/** VendorScreen: goods on planks at the left, the chosen one on a parchment at the right, a recipient to cycle. */
export function VendorModal({ vendor, onClose }) {
  useScreen();
  const SW = screen.w, SH = screen.h;
  const [target, setTarget] = useState(0);
  const [asked, setPage] = useState(0);
  const [sel, setSel] = useState(0);
  const team = vendor.team, services = vendor.services;
  const who = team[Math.min(target, team.length - 1)];
  const cw = SW - Math.max(108, Math.floor(SW / 4)) - 30, sx = cw + 22, sw = SW - sx - 10;
  const per = Math.max(1, Math.floor((SH - 110) / 48)) * 2, pages = Math.max(1, Math.ceil(services.length / per));
  const p = Math.min(asked, pages - 1), chosen = services[Math.min(sel, services.length - 1)];
  const tile = Math.floor((cw - 6) / 2);
  return html`
    <div class="ns top" style=${`--g:${screen.g}px`}>
      <${Panel} x=${8} y=${6} w=${96} h=${20} kind="dark"><${Label} x=${0} y=${-1} w=${86} color="bronze">TOWER SUPPLIES<//><//>
      <${Label} x=${sx} y=${15} w=${sw} color="text">Balance: ${vendor.cobbleDollars}<//>
      ${who ? html`<${NB} x=${12} y=${35} w=${cw} h=${22} onClick=${() => setTarget(step(target, 1, team.length))}>For: ${who.name}<//>` : null}
      ${services.slice(p * per, (p + 1) * per).map((e, i) => html`<${ServiceCard} key=${e.id} x=${12 + (i % 2) * (tile + 6)} y=${64 + Math.floor(i / 2) * 48} w=${tile} e=${e}
        selected=${p * per + i === sel} onPick=${() => setSel(p * per + i)} />`)}
      <${Panel} x=${sx} y=${35} w=${sw} h=${SH - 73} kind="parch">
        ${chosen ? html`<div class="n-lines" style=${at(0, 4, sw - 20, SH - 100)}><b>${chosen.displayName}</b><br /><br /><span class="c-burg">${chosen.priceCobbleDollars} CobbleDollars</span><br /><br />${stockLine(chosen)}</div>` : null}
      <//>
      ${chosen ? html`<${NB} x=${sx + 6} y=${SH - 62} w=${sw - 12} h=${24} disabled=${!canBuy(vendor, chosen, who)} onClick=${() => act('VendorPurchase', { serviceId: chosen.id, targetPlayerId: who.id })}>Purchase<//>` : null}
      ${pages > 1 ? html`<${NB} x=${12} y=${SH - 36} w=${70} h=${22} kind="dark" disabled=${p === 0} onClick=${() => setPage(p - 1)}>Previous<//>
        <${NB} x=${86} y=${SH - 36} w=${60} h=${22} kind="dark" disabled=${p >= pages - 1} onClick=${() => setPage(p + 1)}>Next<//>` : null}
      <${Label} x=${12} y=${SH - 48} w=${cw} color="text">${vendor.message}<//>
      <${NB} x=${sx + 6} y=${SH - 34} w=${sw - 12} h=${22} onClick=${onClose}>Close<//>
    </div>`;
}
