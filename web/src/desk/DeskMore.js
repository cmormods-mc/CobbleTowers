// The Frontier Desk look for the rest of the Hall's pages (P51 slice 6): Mastery and boards, the party chooser, the guide and settings.
// Same requests as the native pages (requestMastery, requestBadge, TOGGLE_POKEMON, ...); the pure helpers decide what is shown.
import { useState } from 'preact/hooks';
import { html } from '../html.js';
import { act } from '../bridge.js';
import { update } from '../store.js';
import { goBack } from '../reducer.js';
import { badgeView, boardLines, TABS, towerLabel } from '../mastery.js';
import { guideLines } from '../screens/Guide.js';
import { requestBadge, requestMastery } from '../screens/Mastery.js';
import { apply, load, save, THEMES } from '../settings.js';
import { setLook } from '../reducer.js';
import { DeskSprite, TopBar } from './DeskHall.js';
import { chooserHint, monCards } from './partyView.js';

const back = () => update(goBack);
const play = (action, argument = '') => act('PlayAction', { action, argument });

/** Achievements and the four boards of a tower. */
export function DeskMastery({ state }) {
  const badge = badgeView(state.towers, state.selected);
  const m = state.mastery;
  const rank = (t) => (t.level > 0 ? t.rank + ' · Level ' + t.level : 'Level ' + t.level);
  return html`<div class="fd"><${TopBar} back=${back} active="progress" />
    <div class="dbody" style="grid-template-columns:24fr 76fr;flex:1">
      <div class="dcard dcol"><div class="ch"><h3>Towers</h3></div>
        <div class="rows">${state.towers.map((t) => html`<button key=${t.id} class=${'dentry' + (t.id === state.selected ? ' sel' : '')} aria-pressed=${t.id === state.selected}
          onClick=${() => t.id !== state.selected && requestMastery(t.id, state.tab)}><b>${t.displayName.replace(/ Tower$/, '')}</b><small>${rank(t)}</small></button>`)}</div>
        <div class="dbadge"><span class="lbl">Name badge</span><div>${badge.line}${badge.mode ? ' · ' + badge.mode : ''}</div>
          <button class="dbtn gh dsm" disabled=${badge.button.disabled} onClick=${() => badge.button.action !== 'none' && requestBadge(state.selected, badge.button.action)}>${badge.button.label}</button></div></div>
      <div class="dcard dcol"><div class="ch"><div class="seg">${TABS.map(([id, name]) => html`<button key=${id} class=${id === state.tab ? 'on' : ''} aria-pressed=${id === state.tab}
          onClick=${() => requestMastery(state.selected, id)}>${name}</button>`)}</div></div>
        ${state.tab === 'mastery' ? html`<div class="dsub" style="padding:0 calc(var(--u) * 1.4)">${m.progress}</div><div class="dsub" style="padding:0 calc(var(--u) * 1.4) calc(var(--u) * .8)">Perks: ${m.perks || 'none yet'}</div>
          <div class="dtiles wide">${m.achievements.length === 0 ? html`<div class="empty">No achievements for this tower.</div>` : m.achievements.map((a, i) => html`<div key=${i} class=${'dach' + (a.held ? ' held' : '')}>
            <b>${a.name}</b><span>${a.description}</span><em>${a.held ? 'Earned' : 'Not yet earned'}</em></div>`)}</div>`
          : html`<div class="dlines" style="flex:1">${boardLines(state.board).map((l, i) => html`<div key=${i} class=${'dboard ' + l.tone}>${l.text || ' '}</div>`)}</div>`}
      </div></div></div>`;
}

/** The party chooser: every Pokemon the player owns, tick up to the limit. */
export function DeskParty({ reg }) {
  const cards = monCards(reg);
  const done = () => { update(goBack); play('REFRESH'); };
  return html`<div class="fd"><${TopBar} back=${done} />
    <div class="dbody" style="grid-template-columns:1fr;flex:1"><div class="dcard dcol">
      <div class="ch"><div><h3>Choose your party</h3><div class="dsub">${chooserHint(reg)}</div></div>
        <div style="display:flex;gap:calc(var(--u) * .8)"><button class="dbtn gh dsm" onClick=${() => play('CLEAR_CHOICE')}>Clear</button><button class="dbtn dsm" onClick=${done}>Done</button></div></div>
      <div class="dtiles wide">${cards.length === 0 ? html`<div class="empty">You have no Pokémon yet.</div>` : cards.map((c) => html`<button key=${c.id} class=${'dmon' + (c.order ? ' on' : '') + (c.fainted ? ' out' : '')}
        aria-pressed=${!!c.order} onClick=${() => play('TOGGLE_POKEMON', c.id)}>
        ${c.order ? html`<span class="dord">${c.order}</span>` : null}<${DeskSprite} name=${c.species} w=${7} />
        <b>${c.name}</b><small>Lv ${c.level}${c.fainted ? ' · fainted' : ''}</small><small>${c.where}</small></button>`)}</div>
    </div></div></div>`;
}

/** How the tower works: the guide that /tower help prints. */
export function DeskGuide() {
  return html`<div class="fd"><${TopBar} back=${back} />
    <div class="dbody" style="grid-template-columns:1fr;flex:1"><div class="dcard dcol"><div class="dlines dguide" style="flex:1">${guideLines().map((l, i) => (l.kind === 'line' ? html`<p key=${i}>${l.text}</p>`
      : l.kind === 'title' ? html`<h2 key=${i}>${l.text}</h2>` : html`<h3 key=${i}>${l.text}</h3>`))}</div></div></div></div>`;
}

/** The page's own presentation settings, kept in this browser. */
export function DeskSettings() {
  const [s, setS] = useState(load());
  const flip = (key) => { const next = { ...s, [key]: !s[key] }; setS(next); save(next); apply(next); };
  const setLookTo = (look) => { const next = { ...s, look }; setS(next); save(next); update((st) => setLook(st, look)); };
  const setThemeTo = (theme) => { const next = { ...s, theme }; setS(next); save(next); apply(next); };
  const row = (title, sub, on, onClick, label) => html`<div class="dsetrow"><div><b>${title}</b><span>${sub}</span></div>
    <button class=${'dbtn dsm' + (on ? '' : ' gh')} aria-pressed=${on} onClick=${onClick}>${label}</button></div>`;
  return html`<div class="fd"><${TopBar} back=${back} />
    <div class="dbody" style="grid-template-columns:1fr;flex:1"><div class="dcard dcol"><div class="ch"><h3>Settings</h3><span class="lbl">kept in this browser</span></div>
      <div class="dlines" style="flex:1">
        ${row('Look', 'Frontier Desk is the new look for every page; Native is the original.', s.look === 'desk', () => setLookTo(s.look === 'desk' ? 'native' : 'desk'), s.look === 'desk' ? 'Frontier Desk' : 'Native')}
        <div class="dsetrow"><div><b>Theme</b><span>The Frontier Desk colours. All four are dark.</span></div>
          <div class="seg" role="group" aria-label="Theme">${Object.entries(THEMES).map(([id, name]) => html`<button key=${id} class=${s.theme === id ? 'on' : ''} aria-pressed=${s.theme === id} onClick=${() => setThemeTo(id)}>${name}</button>`)}</div></div>
        ${row('Animations', 'Reduced motion keeps every choice available.', s.motion, () => flip('motion'), s.motion ? 'On' : 'Off')}
        ${row('Pixel font', 'Only the Native look uses it.', s.pixelFont, () => flip('pixelFont'), s.pixelFont ? 'On' : 'Off')}
        <div class="dsub">Game volume stays in Minecraft's sound settings; the tower pages make no sounds of their own. Tower keys are set in Minecraft's controls screen, under Miscellaneous.</div>
      </div></div></div></div>`;
}
