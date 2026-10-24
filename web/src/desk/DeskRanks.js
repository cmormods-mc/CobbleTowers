// The Ranks tab: two regions side by side, stat by stat, with who leads and each region's champions.
import { html } from '../html.js';
import { act } from '../bridge.js';
import { TopBar } from './DeskHall.js';
import { memberText, pairAfterPick, pickerOptions, regionName, verdict } from './ranksView.js';

const ask = (left, right) => act('RanksRequest', { left, right });

function Picker({ ranks, side }) {
  return html`<div class="rvside" role="group" aria-label=${side === 'left' ? 'Left region' : 'Right region'}>
    ${pickerOptions(ranks, side).map((o) => html`<button key=${o.id} class=${'rvpick rv-' + o.id} aria-pressed=${o.chosen}
      onClick=${() => !o.chosen && (({ left, right }) => ask(left, right))(pairAfterPick(ranks, side, o.id))}>
      <b>${o.name}</b><small>${o.mine ? 'Your region · ' : ''}${memberText(o.members)}</small></button>`)}</div>`;
}

function Row({ row, left, right }) {
  return html`<div class="rvrow">
    <div class=${'rvv l rv-' + left + (row.lead < 0 ? ' lead' : '')}><b>${row.leftText}</b><i style=${`width:${row.leftBar}%`}></i></div>
    <div class="rvnm">${row.label}</div>
    <div class=${'rvv rv-' + right + (row.lead > 0 ? ' lead' : '')}><b>${row.rightText}</b><i style=${`width:${row.rightBar}%`}></i></div></div>`;
}

function Champions({ ranks, id, list }) {
  return html`<div class=${'rvchamps rv-' + id}><span class="lbl">${regionName(ranks, id)} champions</span>
    ${list.length === 0 ? html`<div class="empty">No clears yet.</div>`
      : html`<ol>${list.map((c) => html`<li key=${c.name}><span>${c.name}</span><b>${c.points}</b></li>`)}</ol>`}</div>`;
}

/** @param {{ranks: object|null}} props the RanksState payload, null until the first reply */
export function DeskRanks({ ranks }) {
  if (!ranks) return html`<div class="fd"><${TopBar} active="ranks" /><div class="dbody" style="flex:1"><div class="empty">Loading the standings...</div></div></div>`;
  const v = verdict(ranks);
  return html`<div class="fd"><${TopBar} active="ranks" />
    <div class="dbody rvbody">
      <div class="dcard rvpicker"><${Picker} ranks=${ranks} side="left" /><span class="rvvs">VS</span><${Picker} ranks=${ranks} side="right" /></div>
      <div class="dcard rvduel">${ranks.rows.map((row) => html`<${Row} key=${row.key} row=${row} left=${ranks.left} right=${ranks.right} />`)}</div>
      <div class="dcard rvverdict"><div><span class="lbl">Rivalry standing</span><strong>${v.text}</strong></div>
        <span class="rvscore">${v.left} : ${v.right}</span></div>
      <div class="dcard rvheroes"><${Champions} ranks=${ranks} id=${ranks.left} list=${ranks.leftChampions} /><${Champions} ranks=${ranks} id=${ranks.right} list=${ranks.rightChampions} /></div>
    </div>
    <div class="dft"><span class="st">${ranks.message || ranks.scope + ' · clears in a rival region\'s tower count as away conquests.'}</span></div></div>`;
}
