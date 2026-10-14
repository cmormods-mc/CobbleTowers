import { useEffect } from 'preact/hooks';
import { html } from '../html.js';
import { closeGui } from '../bridge.js';
import { load } from '../settings.js';

/** The run-effects overlay (the HUD page, opened with #hud). Click-through; closes itself when the run is over. */
export function Hud({ fx, spec }) {
  const over = !fx || fx.floor === 0;
  useEffect(() => { if (fx && fx.floor === 0) closeGui(); }, [fx]);
  const panel = spec ? html`<div id="specbox" class="nine dark"><b class="gold">Following ${spec.teammateName}</b>
    <div>${spec.remainingCount}/${spec.totalCount} Pokemon remaining</div><div class="sub">Floor ${spec.floorIndex} - ${spec.runStateLabel}</div></div>` : null;
  if (over || !load().hud) return panel;
  return html`${panel}<div id="hudbox"><div class="nine dark">
    <div class="row1"><b>Run effects</b><span class="gold">floor ${fx.floor}</span></div>
    <div class="sub">risk ${fx.riskPercent}%</div>
    ${fx.items.length
      ? fx.items.map((i) => html`<div class="it">${i.name}${i.count > 1 ? ` ×${i.count}` : ''}${i.relic ? html` <img src="assets/gui/byzantine/seal.png" alt="" />` : null}
          ${i.lines && i.lines[0] ? html`<small>${i.lines[0]}</small>` : null}</div>`)
      : html`<div class="sub">No modifiers or relics yet.</div>`}
  </div></div>`;
}
