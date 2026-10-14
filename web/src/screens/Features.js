import { useEffect } from 'preact/hooks';
import { html } from '../html.js';
import { act, closeGui } from '../bridge.js';
import { get, update, useStore } from '../store.js';
import { beginFeature, featureDelayed, goBack, leaveFeature, setFui } from '../reducer.js';
import { actionPlan, featureView, iconOf, runPage } from '../features.js';
import { parseLine } from '../lines.js';
import { at, box, Card, Label, NB, Panel, screen } from '../components/native.js';

const WAIT_MS = 8000;

/** Asks the server for a menu section, or for an action in it. One request at a time (the buttons are off while one is out). */
export function sendFeature(section, action = 'open', argument = '') {
  const id = get().fReq + 1;
  update((s) => beginFeature(s, section, Date.now()));
  act('TowerFeatureRequest', { requestId: id, section, action, argument });
}

/** A section's own title is only known once its reply arrives; until then the section id stands in. */
export const sectionTitle = (feature, section) => (feature && feature.section === section ? feature.title : section);

/** Esc-like back: close a review, then an open entry, then leave the menu. Returns true when it left the menu. */
export function stepBack(ui) {
  if (ui.review) { update((s) => setFui(s, { review: null, typed: '' })); return false; }
  if (ui.sel != null) { update((s) => setFui(s, { sel: null })); return false; }
  if (get().stack.length) update(goBack);
  else if (get().ret) update((s) => leaveFeature(s));
  else closeGui();
  return true;
}

function choose(section, action) {
  const plan = actionPlan(action);
  if (plan === 'lobby') act('TowerHallAction', { action: 'lobby', argument: '', revision: '' });
  else if (plan === 'page') sendFeature(action.argument);
  else if (plan === 'review') update((s) => setFui(s, { review: action, typed: '' }));
  else sendFeature(section, action.id, action.argument);
}

/** The Current Run tab: what the run carries, at a glance. */
function RunPage({ data, b, H }) {
  const v = runPage(data);
  const empty = (list, text) => (list.length ? null : html`<p class="ln muted">${text}</p>`);
  const block = (name, list, none) => html`
    <p class="ln head">${name} (${list.length})</p>
    ${empty(list, none)}
    ${list.map((m) => html`<p class="ln title">${m.title}</p>${m.lines.map(parseLine).map((l) => html`<p class=${'ln ' + l.tone}>${l.sign ? l.sign + ' ' : ''}${l.text}</p>`)}`)}`;
  if (v.none) return html`<${Panel} x=${b.x} y=${b.y} w=${b.w} h=${40}><${Label} x=${0} y=${2} w=${b.w - 14}>${v.none.title}<//><//>
    <div class="n-lines lit" style=${at(b.x + 7, b.y + 48, b.w - 14, 40)}>${v.none.lines.join(' ')}</div>`;
  return html`
    <${Panel} x=${b.x} y=${b.y} w=${b.w} h=${62}>
      <div class="n-lines lit" style=${at(0, 0, b.w - 16, 56)}>${(v.summary ? v.summary.lines : []).map(parseLine).map((l) => html`<p class=${'ln ' + l.tone}>${l.sign ? l.sign + ' ' : ''}${l.text}</p>`)}</div>
    <//>
    <${Panel} x=${b.x} y=${b.y + 66} w=${b.w} h=${H - 46 - (b.y + 66)} />
    <div class="scroll n-lines lit run-scroll" style=${at(b.x + 7, b.y + 71, b.w - 14, H - 56 - (b.y + 66))}>
      ${block('Modifiers', v.modifiers, 'No modifiers yet.')}
      ${block('Relics', v.relics, 'No relics yet.')}
      ${v.bonuses.length ? block('Set bonuses', v.bonuses, '') : null}
    </div>`;
}

function Lines({ lines, x, y, w, h }) {
  return html`<div class="n-lines" style=${at(x, y, w, h)}>${lines.map(parseLine).map((l) => html`<p class=${'ln-' + l.tone}>${l.sign ? html`<i>${l.sign}</i> ` : null}${l.text}</p>`)}</div>`;
}

export function Features() {
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

  const b = box();
  const H = screen.h;
  const ready = !pending && !uncertain;
  const status = pending ? 'Waiting for the server...' : uncertain ? 'Response delayed. Refresh before another action.' : (data && data.message) || '';
  const footer = html`
    <${Label} x=${b.x} y=${H - 40} w=${b.w} color="soft">${status}<//>
    <${NB} x=${b.x} y=${b.foot} w=${Math.min(90, Math.floor(b.w / 2))} kind="dark" disabled=${!!pending} onClick=${() => update((s) => setFui(s, { sel: null, review: null, typed: '' }))}>Overview<//>
    <${NB} x=${b.x + b.w - 58} y=${b.foot} w=${58} kind="dark" disabled=${!!pending} onClick=${() => sendFeature(section)}>Refresh<//>`;
  if (!data || data.section !== section) {
    return html`<${Panel} x=${b.x} y=${b.y} w=${b.w} h=${26} /><${Label} x=${b.x + 7} y=${b.y + 9}>Loading...<//>${footer}`;
  }

  if (data.section === 'run') return html`<${RunPage} data=${data} b=${b} H=${H} />${footer}`;
  const v = featureView(data, ui, H, b.y);
  return html`
    <${Panel} x=${b.x} y=${b.y} w=${b.w} h=${26} />
    <${Label} x=${b.x + 7} y=${b.y + 9} w=${b.w - 14}>${v.heading}<//>
    ${v.mode === 'list' ? html`
      ${v.items.map((it, i) => html`<${Card} key=${it.kind + it.id} x=${b.x} y=${b.y + 34 + i * 24} w=${b.w} h=${22} title=${it.label} sub="" icon=${iconOf(data.section)}
        onClick=${() => (ready ? (it.kind === 'entry' ? update((s) => setFui(s, { sel: it.id })) : choose(data.section, it.action)) : null)} />`)}
      ${v.pages > 1 ? html`
        <${NB} x=${b.x} y=${H - 62} w=${24} kind="dark" disabled=${!ready} onClick=${() => update((s) => setFui(s, { page: (v.page - 1 + v.pages) % v.pages }))}>${'<'}<//>
        <${NB} x=${b.x + 28} y=${H - 62} w=${24} kind="dark" disabled=${!ready} onClick=${() => update((s) => setFui(s, { page: (v.page + 1) % v.pages }))}>${'>'}<//>
        <${Label} x=${b.x + 60} y=${H - 56} color="ink">${v.page + 1} / ${v.pages}<//>` : null}`
    : html`
      <${Lines} lines=${v.lines} x=${b.x + 7} y=${b.y + 34} w=${b.w - 14} h=${H - (v.needsInput ? 110 : 89) - b.y - 34} />
      ${ui.review ? html`
        ${v.needsInput ? html`<input class="n-edit" style=${at(b.x + 2, H - 87, b.w - 4, 20)} maxlength="256" placeholder=${ui.review.inputHint} value=${ui.typed} disabled=${!ready}
          onInput=${(e) => update((s) => setFui(s, { typed: e.target.value }))} onKeyDown=${(e) => e.stopPropagation()} />` : null}
        <${NB} x=${b.x} y=${H - 62} w=${Math.floor((b.w - 6) / 2)} kind="dark" disabled=${!ready} onClick=${() => sendFeature(data.section, ui.review.id, v.needsInput ? ui.typed.trim() : ui.review.argument)}>Confirm<//>
        <${NB} x=${b.x + Math.floor((b.w + 6) / 2)} y=${H - 62} w=${Math.floor((b.w - 6) / 2)} kind="dark" disabled=${!!pending} onClick=${() => update((s) => setFui(s, { review: null, typed: '' }))}>Cancel<//>`
        : v.primary ? html`<${NB} x=${b.x} y=${H - 62} w=${b.w} kind="dark" disabled=${!ready} onClick=${() => choose(data.section, v.primary)}>${v.primary.label}<//>` : null}`}
    ${footer}`;
}
