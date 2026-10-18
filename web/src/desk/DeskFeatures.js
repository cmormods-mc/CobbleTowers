// The Frontier Desk look for the feature menus (P51 slice 5): club, season, cosmetics, contracts, Hall of Fame, run report, codex, echoes,
// collection, spectate, run codes and the Current Run page. src/features.js decides what is shown and which buttons work; the requests are
// the native menu's (sendFeature, choose).
import { useEffect } from 'preact/hooks';
import { html } from '../html.js';
import { get, update, useStore } from '../store.js';
import { featureDelayed, setFui } from '../reducer.js';
import { actionPlan, runPage } from '../features.js';
import { parseLine } from '../lines.js';
import { choose, sectionTitle, sendFeature, stepBack } from '../screens/Features.js';
import { TopBar } from './DeskHall.js';
import { entryList, featureMode, pillFor, previewOf } from './featureView.js';

const WAIT_MS = 8000;

const Line = ({ l }) => html`<div class=${'dpl ' + (l.tone === 'benefit' ? 'p' : l.tone === 'cost' ? 'c' : 'n')}><i>${l.sign || '·'}</i>${l.text}</div>`;
const parsed = (lines) => lines.map(parseLine);

function RunPage({ data }) {
  const v = runPage(data);
  const block = (name, list, none) => html`<div class="dcard dcol"><div class="ch"><h3>${name}</h3><span class="lbl">${list.length}</span></div>
    <div class="dlines" style="flex:1">${list.length === 0 ? html`<div class="empty">${none}</div>` : list.map((m, i) => html`<div key=${i} class="dentrybox"><b>${m.title}</b>${parsed(m.lines).map((l, j) => html`<${Line} key=${j} l=${l} />`)}</div>`)}</div></div>`;
  if (v.none) return html`<div class="dbody" style="grid-template-columns:1fr;flex:1"><div class="dcard empty" style="display:grid;place-items:center">${v.none.title}. ${v.none.lines.join(' ')}</div></div>`;
  return html`<div class="dbody" style="grid-template-columns:1fr 1fr 1fr;grid-template-rows:auto 1fr;flex:1">
    <div class="dcard dcol" style="grid-column:1 / 4"><div class="ch"><h3>This run</h3></div><div class="dlines">${parsed(v.summary ? v.summary.lines : []).map((l, i) => html`<${Line} key=${i} l=${l} />`)}</div></div>
    ${block('Modifiers', v.modifiers, 'No modifiers yet.')}${block('Relics', v.relics, 'No relics yet.')}${block('Set bonuses', v.bonuses, 'No set bonus yet.')}</div>`;
}

export function DeskFeatures() {
  const data = useStore((s) => s.feature);
  const ui = useStore((s) => s.fui);
  const section = useStore((s) => s.fSection);
  const pending = useStore((s) => s.fPending);
  const uncertain = useStore((s) => s.fUncertain);
  useEffect(() => {
    if (!pending) return undefined;
    const id = setTimeout(() => update(featureDelayed), Math.max(0, pending + WAIT_MS - Date.now()));
    return () => clearTimeout(id);
  }, [pending]);

  const ready = !pending && !uncertain;
  const status = pending ? 'Waiting for the server…' : uncertain ? 'Response delayed. Refresh before another action.' : (data && data.message) || '';
  const bar = html`<${TopBar} active=${pillFor(section)} back=${() => stepBack(get().fui)} />`;
  const foot = html`<div class="dft"><button class="dbtn gh dsm" disabled=${!!pending} onClick=${() => update((s) => setFui(s, { sel: null, review: null, typed: '' }))}>Overview</button>
    <button class="dbtn gh dsm" disabled=${!!pending} onClick=${() => sendFeature(section)}>Refresh</button><span class="st">${status}</span></div>`;
  if (!data || data.section !== section) {
    return html`<div class="fd">${bar}<div class="dbody" style="grid-template-columns:1fr;flex:1"><div class="dcard empty" style="display:grid;place-items:center">Loading…</div></div>${foot}</div>`;
  }
  if (data.section === 'run') return html`<div class="fd">${bar}<${RunPage} data=${data} />${foot}</div>`;

  const mode = featureMode(data, ui);
  const items = entryList(data);
  return html`<div class="fd">${bar}
    <div class="dbody" style="grid-template-columns:${mode.kind === 'list' ? '1fr' : '34fr 66fr'};flex:1">
      ${mode.kind === 'list' ? null : html`<div class="dcard dcol"><div class="ch"><h3>${sectionTitle(data, section)}</h3></div>
        <div class="rows">${items.map((it) => html`<button key=${it.kind + it.id} class=${'dentry' + (it.id === ui.sel ? ' sel' : '')} disabled=${!ready}
          onClick=${() => (it.kind === 'entry' ? update((s) => setFui(s, { sel: it.id, review: null })) : choose(data.section, it.action))}><b>${it.label}</b></button>`)}</div></div>`}
      <div class="dcard dcol">
        <div class="ch"><h3>${mode.heading}</h3>${mode.kind === 'list' ? html`<span class="lbl">${items.length} ${items.length === 1 ? 'item' : 'items'}</span>` : null}</div>
        ${mode.kind === 'list' ? html`<div class="rows" style="padding:calc(var(--u) * .4) calc(var(--u) * 1.2) calc(var(--u) * 1.2)">${items.length === 0 ? html`<div class="empty">Nothing here yet.</div>` : items.map((it) => html`<button key=${it.kind + it.id}
          class=${'dentry wide' + (it.kind === 'action' ? ' act' : '')} disabled=${!ready}
          onClick=${() => (it.kind === 'entry' ? update((s) => setFui(s, { sel: it.id })) : choose(data.section, it.action))}><b>${it.label}</b>${it.kind === 'entry' ? html`<small>${previewOf(data, it.id)}</small>` : null}</button>`)}</div>`
        : html`<div class="dlines" style="flex:1">${parsed(mode.lines).map((l, i) => html`<${Line} key=${i} l=${l} />`)}
          ${mode.review && mode.needsInput ? html`<input class="dedit" style="margin-top:calc(var(--u) * 1)" maxlength="256" placeholder=${mode.review.inputHint} value=${ui.typed} disabled=${!ready} aria-label=${mode.review.inputHint}
            onInput=${(e) => update((s) => setFui(s, { typed: e.target.value }))} onKeyDown=${(e) => e.stopPropagation()} />` : null}</div>
          <div class="dactions">${mode.review ? html`<button class="dbtn" disabled=${!ready} onClick=${() => sendFeature(data.section, mode.review.id, mode.needsInput ? ui.typed.trim() : mode.review.argument)}>Confirm</button>
              <button class="dbtn gh" disabled=${!!pending} onClick=${() => update((s) => setFui(s, { review: null, typed: '' }))}>Cancel</button>`
            : mode.primary ? html`<button class="dbtn" disabled=${!ready} onClick=${() => choose(data.section, mode.primary)}>${mode.primary.label}</button>` : null}</div>`}
      </div></div>${foot}</div>`;
}
