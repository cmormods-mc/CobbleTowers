import { useEffect } from 'preact/hooks';
import { html } from '../html.js';
import { Button, Chip } from '../components/ui.js';
import { act } from '../bridge.js';
import { update } from '../store.js';
import { begin, goBack, goTo, setTab, timeout } from '../reducer.js';
import { canSelect, regionOf } from '../lobby.js';

const TABS = [['towers', 'Towers'], ['trials', 'Trials'], ['social', 'Social'], ['progress', 'Progress']];
const WAIT_MS = 8000;

/** One server request at a time: buttons wait for the next state, or give up after 8 s. */
export function request(action, argument = '', revision = '') {
  update((s) => begin(s, Date.now()));
  act('TowerHallAction', { action, argument, revision });
}

/** `ready` cards open a screen that exists; the rest are shown as "Soon" until their slice lands. */
const CARDS = {
  social: [['Party & invites', 'Gather your team', true, () => request('lobby')], ['Club lodge', 'Your community'], ['Spectate', 'Watch challengers'], ['Run codes', 'Scout an expedition']],
  progress: [['Season & rewards', 'Your next milestone'], ['Cosmetics & titles', 'Wear your victories'], ['Contracts', 'Accept a challenge'],
    ['Hall of Fame', 'Tower legends'], ['Last run report', 'Review your run'], ['Mastery & boards', 'Measure your growth']],
};

export function Banner({ region, tall }) {
  return html`<div class=${'banner r-' + region + (tall ? ' tall' : '')}><i class="tw"></i></div>`;
}

function Tile({ region, title, sub, onClick, disabled, chip }) {
  return html`<button class=${'tile'} disabled=${disabled} onClick=${onClick}>
    <${Banner} region=${region} />
    <div class="cap"><b>${title}</b>${chip ? html`<${Chip} color="var(--brown)">${chip}<//>` : null}</div><div class="sub">${sub}</div></button>`;
}

function TowerGrid({ hall, busy }) {
  const open = (t) => update((s) => goTo(s, 'tower', t.id));
  return html`
    <div class="grid3 scroll">${hall.towers.map((t) => html`<${Tile} key=${t.id} region=${regionOf(t.id)} title=${t.name} sub=${t.floors + ' floors / cycle'} onClick=${() => open(t)} />`)}</div>
    <div class="grid3" style="flex:none">
      ${hall.trials.map((t) => html`<${Tile} key=${t.kind} region=${regionOf(t.tower)} title=${t.kind === 'daily' ? 'Daily Tower' : 'Weekly Tower'}
        sub=${t.title} onClick=${() => update((s) => goTo(s, 'trial', t.kind))} />`)}
    </div>`;
}

function TrialGrid({ hall }) {
  return html`<div class="grid3 scroll">${hall.trials.length ? hall.trials.map((t) => html`<${Tile} key=${t.kind} region=${regionOf(t.tower)}
      title=${t.kind === 'daily' ? 'Daily Tower' : 'Weekly Tower'} sub=${t.title} onClick=${() => update((s) => goTo(s, 'trial', t.kind))} />`)
    : html`<div class="nine dark"><b class="gold">No trial is running right now.</b></div>`}</div>`;
}

function CardGrid({ tab, busy }) {
  return html`<div class="grid2">${CARDS[tab].map(([title, sub, ready, run]) => html`
    <button class="nine dark card-tab" disabled=${!ready || busy} onClick=${run}>
      <b>${title}</b>${ready ? null : html`<${Chip} color="var(--brown)">SOON<//>`}<div class="sub">${sub}</div></button>`)}</div>`;
}

export function Hall({ hall, tab, busy }) {
  const status = hall.runStatus || (hall.play.lobby.role === 0 ? 'Choose a destination to begin.' : 'Your expedition lobby is available.');
  return html`
    <div class="row center">
      <div class="grow"><h1>Tower Hall</h1><div class="sub">Every ascent begins here.</div></div>
      <div class="row" style="gap:8px">${TABS.map(([id, label]) => html`<${Button} small kind=${tab === id ? 'on' : ''} onClick=${() => update((s) => setTab(s, id))}>${label}<//>`)}</div>
    </div>
    <div class="col grow">
      ${tab === 'towers' ? html`<${TowerGrid} hall=${hall} busy=${busy} />` : tab === 'trials' ? html`<${TrialGrid} hall=${hall} />` : html`<${CardGrid} tab=${tab} busy=${busy} />`}
    </div>
    <div class="row center">
      <div class="grow sub" style="font-size:17px">${status}</div>
      <${Button} kind="sec" disabled=${busy} onClick=${() => request(hall.canResume ? 'resume' : 'lobby')}>${hall.canResume ? 'Return to run' : 'Lobby'}<//>
      <${Button} kind="sec" disabled=${busy} onClick=${() => request('refresh')}>Refresh<//>
    </div>`;
}

export function TowerBriefing({ hall, id, busy }) {
  const t = hall.towers.find((x) => x.id === id);
  const lines = t ? [t.floors + ' floors per cycle / ' + t.milestones + ' milestones', t.ascends ? 'Ascension continues into harder cycles.' : 'The tower ends after its final floor.',
    'Select this tower to create or update your lobby. Review mode, party registration and invitations before starting.'] : ['This tower is no longer available. Refresh the Hall.'];
  return html`<${Briefing} region=${regionOf(id)} title=${t ? t.name : 'Tower'} lines=${lines} busy=${busy}
    action=${t ? ['Select tower & open lobby', () => request('tower', id), canSelect(hall, busy)] : null} />`;
}

export function TrialBriefing({ hall, kind, busy }) {
  const t = hall.trials.find((x) => x.kind === kind);
  const lines = t ? [...t.rules, 'Preparing does not launch a run. Trial rules will be checked again by the server.'] : ['No ' + kind + ' trial is currently available.'];
  return html`<${Briefing} region=${t ? regionOf(t.tower) : 'tideforge'} title=${t ? t.title : 'Trial'} lines=${lines} busy=${busy}
    action=${t ? ['Prepare trial lobby', () => request('trial', kind, t.revision), canSelect(hall, busy)] : null} />`;
}

function Briefing({ region, title, lines, action }) {
  return html`
    <div class="row center">
      <${Button} small kind="sec" onClick=${() => update(goBack)}>Back<//>
      <div class="grow"><h1>${title}</h1></div>
    </div>
    <div class="col grow">
      <${Banner} region=${region} tall />
      <div class="nine parch grow scroll">${lines.map((l, i) => html`<p class=${i === 0 ? 'lead' : ''}>${l}</p>`)}</div>
      ${action ? html`<${Button} disabled=${!action[2]} onClick=${action[1]}>${action[0]}<//>` : null}
    </div>`;
}

/** Gives up waiting for the server after a while, so a lost reply does not leave the buttons dead. */
export function useBusyTimeout(busyAt) {
  useEffect(() => {
    if (!busyAt) return undefined;
    const id = setTimeout(() => update(timeout), Math.max(0, busyAt + WAIT_MS - Date.now()));
    return () => clearTimeout(id);
  }, [busyAt]);
}
