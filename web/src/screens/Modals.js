import { useState } from 'preact/hooks';
import { html } from '../html.js';
import { Button, Coin } from '../components/ui.js';
import { act } from '../bridge.js';
import { idTail, pretty } from '../util.js';

/** The Tower Rewards reveal. `label` is empty for ordinary items, so the item id is named instead. */
export function RewardModal({ reward, onClose }) {
  return html`
    <div class="veil"><div class="nine parch" style="width:560px">
      <div style="text-align:center"><h2>Tower Rewards</h2>
        <div style="font-size:20px;color:#8a5a14">REWARDS SECURED</div>
        <div class="sub">Through floor ${reward.floorIndex}</div></div>
      <div class="sep"></div>
      ${reward.grants.length
        ? reward.grants.map((g, i) => {
            const label = g.label || '';
            const coin = /dollar/i.test(g.item) || /dollar/i.test(label);
            const raid = /raid/i.test(label || g.item);
            return html`<div class="reward" style=${`--d:${i * 0.15}s`}>
              ${coin ? html`<${Coin} big />` : raid ? html`<img src="assets/gui/raid_points.png" alt="" />` : html`<span class="stub"></span>`}
              <span class="grow">${label || pretty(idTail(g.item))}</span><b>×${g.amount}</b></div>`;
          })
        : html`<div class="sub" style="text-align:center">No rewards in this grant</div>`}
      <div style="text-align:center;margin-top:14px"><${Button} onClick=${onClose}>Continue<//></div>
    </div></div>`;
}

export function VendorModal({ vendor, onClose }) {
  const [target, setTarget] = useState(0);
  const who = vendor.team[target] || vendor.team[0];
  return html`
    <div class="veil"><div class="nine parch" style="width:600px">
      <div class="row center"><div class="grow"><h2>Vendor</h2><div class="sub">Services for you or a teammate</div></div>
        <div style="font-size:22px"><${Coin} /> <b>${vendor.cobbleDollars}</b></div></div>
      <div class="sep"></div>
      <div class="row" style="flex-wrap:wrap;margin-bottom:6px">${vendor.team.map((t, i) => html`
        <${Button} small kind=${i === target ? 'on' : 'sec'} onClick=${() => setTarget(i)}>${t.name}${i === 0 ? ' (you)' : ''}${t.online ? '' : ' · offline'}<//>`)}</div>
      ${vendor.services.map((e) => {
        const soldOut = e.remainingPurchases === 0;
        const afford = vendor.cobbleDollars >= e.priceCobbleDollars;
        return html`<div class="eff"><span class="grow"><b>${e.displayName}</b>${e.remainingPurchases > 0 ? html` <span class="sub">(${e.remainingPurchases} left)</span>` : null}</span>
          <b>${e.priceCobbleDollars}</b>
          <${Button} small kind="sec" disabled=${soldOut || !afford || !who} onClick=${() => act('VendorPurchase', { serviceId: e.id, targetPlayerId: who.id })}>Buy<//></div>`;
      })}
      ${vendor.message ? html`<div class="sub" style="margin-top:6px">${vendor.message}</div>` : null}
      <div style="text-align:center;margin-top:12px"><${Button} onClick=${onClose}>Close<//></div>
    </div></div>`;
}
