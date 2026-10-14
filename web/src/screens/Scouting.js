import { useState } from 'preact/hooks';
import { html } from '../html.js';
import { closeGui } from '../bridge.js';
import { update } from '../store.js';
import { closeScout } from '../reducer.js';
import { at, Label, NB, Panel, screen, useScreen } from '../components/native.js';

/** ScoutingScreen: the revealed categories as cards on one sheet, the chosen one read on the right. */
export function Scouting({ scout }) {
  useScreen();
  const SW = screen.w, SH = screen.h;
  const [page, setPage] = useState(0);
  const [sel, setSel] = useState(0);
  const per = Math.max(1, Math.floor((SH - 88) / 38)) * 2;
  const cats = scout.categories, pages = Math.max(1, Math.ceil(cats.length / per)), p = Math.min(page, pages - 1);
  const cw = Math.floor((SW - 150) / 2);
  const chosen = cats[Math.min(sel, cats.length - 1)];
  const close = () => { update(closeScout); closeGui(); };
  return html`
    <div class="ns" style=${`--g:${screen.g}px`}>
      <${Label} x=${12} y=${18} w=${SW - 24}>SCOUTING / FLOOR ${scout.floorIndex}<//>
      <${Panel} x=${6} y=${40} w=${SW - 138} h=${SH - 78} kind="parch" />
      ${cats.slice(p * per, (p + 1) * per).map((c, i) => html`<${NB} key=${p * per + i} x=${12 + (i % 2) * (cw + 4)} y=${50 + Math.floor(i / 2) * 38} w=${cw - 4} h=${30}
        kind=${p * per + i === sel ? 'primary' : 'dark'} tip=${c.name + ': ' + c.value} onClick=${() => setSel(p * per + i)}>${c.name}<//>`)}
      <${Panel} x=${SW - 126} y=${43} w=${114} h=${SH - 83} kind="parch">
        <div class="n-lines" style=${at(10, 12, 94, SH - 105)}>${chosen ? html`<b>${chosen.name}</b><br /><br />${chosen.value}` : 'Nothing revealed'}</div>
      <//>
      ${pages > 1 ? html`<${NB} x=${12} y=${SH - 32} w=${100} h=${22} onClick=${() => setPage((p + 1) % pages)}>Next page<//>` : null}
      <${NB} x=${SW - 110} y=${SH - 32} w=${98} h=${22} onClick=${close}>Close<//>
    </div>`;
}
