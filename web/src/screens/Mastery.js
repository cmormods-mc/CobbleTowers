import { useState } from 'preact/hooks';
import { html } from '../html.js';
import { act } from '../bridge.js';
import { update } from '../store.js';
import { goBack } from '../reducer.js';
import { badgeView, boardLines, nodesPerPage, TABS, tabWidth, towerLabel } from '../mastery.js';
import { at, Icon, Label, NB, Panel, screen, useScreen } from '../components/native.js';

export const requestMastery = (tower, tab) => act('MasteryRequest', { tower, tab });
/** Pins the name badge to a tower, or (auto) back to the highest level; the reply is the mastery view again. */
export const requestBadge = (tower, action) => act('MasteryRequest', { tower, tab: 'badge:' + action });

/** AchievementNode: a card with the name, a line of description and a mark when earned. */
function Node({ x, y, w, node, selected, onPick }) {
  return html`<button class=${'n-btn n-fr n-dark card' + (selected ? ' sel' : '')} style=${at(x, y, w, 41)} onClick=${onPick}>
    <${Label} x=${-2} y=${-2} w=${w - 26} color=${node.held ? 'sage' : 'text'}>${node.name}<//>
    <${Label} x=${-2} y=${11} w=${w - 26} color="muted">${node.held ? 'Earned' : 'Not yet earned'}<//>
    <${Icon} name=${node.held ? 'check' : 'lock'} x=${w - 26} y=${12} /><i class="brk"></i></button>`;
}

function Achievements({ state, SW, SH }) {
  const [page, setPage] = useState(0);
  const [sel, setSel] = useState(0);
  const m = state.mastery, nodes = m.achievements, per = nodesPerPage(SH);
  const pages = Math.max(1, Math.ceil(nodes.length / per)), p = Math.min(page, pages - 1);
  const nw = Math.floor((SW - 234) / 2);
  const node = nodes[Math.min(sel, nodes.length - 1)];
  return html`
    <${Label} x=${112} y=${34} w=${SW - 124} color="bronze">${m.progress}<//>
    <${Label} x=${112} y=${47} w=${SW - 124} color="muted">Perks: ${m.perks}<//>
    ${nodes.slice(p * per, (p + 1) * per).map((n, i) => html`<${Node} key=${p * per + i} x=${112 + (i % 2) * (nw + 4)} y=${66 + Math.floor(i / 2) * 45} w=${nw} node=${n} selected=${p * per + i === sel} onPick=${() => setSel(p * per + i)} />`)}
    ${pages > 1 ? html`<${NB} x=${112} y=${SH - 27} w=${26} h=${18} kind="dark" disabled=${p === 0} onClick=${() => setPage(p - 1)}>${'<'}<//>
      <${NB} x=${142} y=${SH - 27} w=${26} h=${18} kind="dark" disabled=${p >= pages - 1} onClick=${() => setPage(p + 1)}>${'>'}<//>` : null}
    ${node ? html`<${Panel} x=${SW - 112} y=${63} w=${104} h=${SH - 97}>
      <div class="n-lines lit" style=${at(7, 9, 91, SH - 110)}><b>${node.name}</b><br /><br /><span class=${node.held ? "c-sage" : "c-muted"}>${node.held ? 'EARNED' : 'NOT YET EARNED'}</span><br /><br />${node.description}</div>
    <//>` : null}`;
}

function Board({ state, SW, SH }) {
  const lines = boardLines(state.board);
  return html`<div class="scroll" style=${at(112, 33, SW - 120, SH - 62)}>
    ${lines.map((l, i) => html`<div key=${i} class=${'n-row c-' + l.tone}>${l.text || ' '}</div>`)}</div>`;
}

/** MasteryScreen: towers on the left, tabs on top (achievements, then the four boards). */
export function Mastery({ state }) {
  useScreen();
  const SW = screen.w, SH = screen.h;
  const badge = badgeView(state.towers, state.selected);
  return html`
    <div class="ns" style=${`--g:${screen.g}px`}>
      <${Panel} x=${6} y=${27} w=${101} h=${SH - 62} />
      <${Label} x=${12} y=${SH - 88} w=${90} color="muted">Name badge: ${badge.line}<//>
      ${badge.mode ? html`<${Label} x=${12} y=${SH - 77} w=${90} color="muted">${badge.mode}<//>` : null}
      <${NB} x=${10} y=${SH - 56} w=${94} kind="dark" disabled=${badge.button.disabled} onClick=${() => badge.button.action !== 'none' && requestBadge(state.selected, badge.button.action)}>${badge.button.label}<//>
      ${state.towers.map((t, i) => html`<${NB} key=${t.id} x=${10} y=${30 + i * 22} w=${94} kind="dark" disabled=${t.id === state.selected} onClick=${() => requestMastery(t.id, state.tab)}>${towerLabel(t, state.selected)}<//>`)}
      <${NB} x=${10} y=${SH - 28} w=${94} onClick=${() => update(goBack)}>Close<//>
      ${(() => { let x = 112; return TABS.map(([id, name]) => { const w = tabWidth(name), at0 = x; x += w + 2;
        return html`<${NB} key=${id} x=${at0} y=${8} w=${w} h=${18} kind=${id === state.tab ? 'tab' : 'dark'} onClick=${() => requestMastery(state.selected, id)}>${name}<//>`; }); })()}
      ${state.tab === 'mastery' ? html`<${Achievements} key=${state.selected} state=${state} SW=${SW} SH=${SH} />` : html`<${Board} state=${state} SW=${SW} SH=${SH} />`}
    </div>`;
}
