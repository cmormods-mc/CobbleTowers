// The Frontier Desk look for the tower and trial briefings and the Trials and Social pages (P51 slice 5).
// Same requests as the native Hall: a tower or trial is selected with request('tower'|'trial'), the Social cards open the lobby or a feature menu.
import { html } from '../html.js';
import { update } from '../store.js';
import { goBack, goTo } from '../reducer.js';
import { regionOf } from '../lobby.js';
import { request } from '../screens/Hall.js';
import { sendFeature } from '../screens/Features.js';
import { DeskPano, DeskSprite, TopBar } from './DeskHall.js';
import { rankTone, standingText } from './standings.js';
import { SOCIAL, towerBrief, trialLines, whyNot } from './briefing.js';

const back = () => update(goBack);

/** A tower's briefing: its landscape, what is in it, and the way into its lobby. */
export function DeskTowerBrief({ hall, id, busy }) {
  const b = towerBrief(hall, id);
  const why = whyNot(hall, busy);
  if (!b.found) {
    return html`<div class="fd"><${TopBar} back=${back} /><div class="dbody" style="grid-template-columns:1fr;flex:1"><div class="dcard empty" style="display:grid;place-items:center">This tower is no longer available. Refresh the Hall.</div></div></div>`;
  }
  return html`<div class="fd"><${TopBar} back=${back} />
    <div class="dbody" style="grid-template-columns:62fr 38fr;flex:1">
      <div class="dcard dcol"><div class="dbanner"><${DeskPano} region=${regionOf(id)} /></div>
        <div class="ch" style="padding-top:calc(var(--u) * 1.4)"><h3 style="font-size:calc(var(--u) * 3)">${b.name}</h3><span class=${'medal m-' + rankTone(b.standing)}>${standingText(b.standing)}</span></div>
        <dl class="dkv">${b.facts.map(([k, v]) => html`<dt key=${k}>${k}</dt><dd>${v}</dd>`)}</dl>
        ${b.residents.length ? html`<div style="padding:0 calc(var(--u) * 1.4) calc(var(--u) * 1.4)"><span class="lbl">Foes you may meet</span><div class="res" style="margin-top:calc(var(--u) * .6)">${b.residents.map((s) => html`<${DeskSprite} key=${s} name=${s} w=${7} />`)}</div></div>` : null}</div>
      <div class="dcard dcol" style="padding:calc(var(--u) * 1.6);gap:calc(var(--u) * 1.2)"><h3>Start an expedition</h3>
        <div class="dsub">Selecting this tower creates or updates your lobby. Review the mode, your party and invitations there before starting.</div>
        <button class="dbtn" disabled=${!!why} onClick=${() => request('tower', id)}>Select tower & open lobby</button>
        ${why ? html`<div class="elig" style="margin:0;background:#fff4d6;color:#7a5200">${why}</div>` : null}</div></div></div>`;
}

/** A trial's briefing: its rules, and the way into a lobby for it. */
export function DeskTrialBrief({ hall, kind, busy }) {
  const t = hall.trials.find((x) => x.kind === kind);
  const why = whyNot(hall, busy);
  const lines = trialLines(t);
  return html`<div class="fd"><${TopBar} back=${back} active="trials" />
    <div class="dbody" style="grid-template-columns:62fr 38fr;flex:1">
      ${t ? html`<div class="dcard dcol"><div class="dbanner"><${DeskPano} region=${regionOf(t.tower)} /></div>
        <div class="ch" style="padding-top:calc(var(--u) * 1.4)"><h3 style="font-size:calc(var(--u) * 2.6)">${t.title}</h3><span class="lbl">${kind}</span></div>
        <div class="dlines" style="flex:1">${lines.facts.map((l, i) => html`<div key=${i} class="dpl n"><i>·</i>${l}</div>`)}</div></div>`
        : html`<div class="dcard empty" style="display:grid;place-items:center">No ${kind} trial is currently available.</div>`}
      <div class="dcard dcol" style="padding:calc(var(--u) * 1.6);gap:calc(var(--u) * 1.2)"><h3>Take the trial</h3>
        <div class="dsub">${lines.note || 'Preparing opens a lobby; nothing launches yet.'}</div>
        <button class="dbtn" disabled=${!t || !!why} onClick=${() => t && request('trial', kind, t.revision)}>Prepare trial lobby</button>
        ${why && t ? html`<div class="elig" style="margin:0;background:#fff4d6;color:#7a5200">${why}</div>` : null}</div></div></div>`;
}

/** The Trials page: today's and this week's trial, each with its rules at a glance. */
export function DeskTrials({ hall }) {
  const card = (kind, title) => {
    const t = hall.trials.find((x) => x.kind === kind);
    const lines = trialLines(t);
    return html`<div class="dcard dcol">
      <div class="dbanner short">${t ? html`<${DeskPano} region=${regionOf(t.tower)} />` : null}</div>
      <div class="ch" style="padding-top:calc(var(--u) * 1.2)"><h3>${title}</h3><span class="lbl">${t ? t.title.replace(/^(Daily|Weekly) Trial: /, '') : 'None right now'}</span></div>
      <div class="dlines" style="flex:1">${lines.facts.slice(0, 5).map((l, i) => html`<div key=${i} class="dpl n"><i>·</i>${l}</div>`)}</div>
      <div style="padding:calc(var(--u) * 1.2) calc(var(--u) * 1.4) calc(var(--u) * 1.4)"><button class="dbtn" disabled=${!t} onClick=${() => update((s) => goTo(s, 'trial', kind))}>Open briefing</button></div></div>`;
  };
  return html`<div class="fd"><${TopBar} active="trials" /><div class="dbody" style="grid-template-columns:1fr 1fr;flex:1">${card('daily', 'Daily Tower')}${card('weekly', 'Weekly Tower')}</div></div>`;
}

/** The Social page: the lobby, the club, spectating and run codes. */
export function DeskSocial() {
  const open = (what) => (what === 'lobby' ? request('lobby') : sendFeature(what));
  return html`<div class="fd"><${TopBar} active="social" />
    <div class="dbody" style="grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;flex:1">${SOCIAL.map((c) => html`<button key=${c.title} class="dcard dtilebtn" onClick=${() => open(c.open)}>
      <b>${c.title}</b><span>${c.sub}</span></button>`)}</div></div>`;
}
