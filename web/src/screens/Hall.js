import { useEffect, useRef } from 'preact/hooks';
import { html } from '../html.js';
import { act, closeGui } from '../bridge.js';
import { get, update, useStore } from '../store.js';
import { begin, goBack, goTo, setPage, setRecords, setTab, timeout } from '../reducer.js';
import { canSelect, regionOf } from '../lobby.js';
import { Tracks, sendTrack } from './Tracks.js';
import { Settings } from './Settings.js';
import { requestMastery } from './Mastery.js';
import { Features, sectionTitle, sendFeature, stepBack } from './Features.js';
import { at, box, Card, Label, NB, Panel, screen, Shell, useScreen } from '../components/native.js';
import { paintPanorama } from '../art/panorama.js';

const WAIT_MS = 8000;
/** One server request at a time: buttons wait for the next state, or give up after 8 s. */
export function request(action, argument = '', revision = '') {
  update((s) => begin(s, Date.now()));
  act('TowerHallAction', { action, argument, revision });
}

/** Cards of the Social and Progress tabs; `run` marks the ones whose screen exists in the web UI. */
const CARDS = {
  social: [['Party & invites', 'Gather your team', 'group', () => request('lobby')], ['Club lodge', 'Your community', 'flag', () => sendFeature('club')],
    ['Spectate', 'Watch challengers', 'eye', () => sendFeature('watch')], ['Run codes', 'Scout an expedition', 'scroll', () => sendFeature('codes')]],
  progress: [['Season & rewards', 'Your next milestone', 'star', () => sendFeature('season')], ['Cosmetics & titles', 'Wear your victories', 'crown', () => sendFeature('cosmetics')],
    ['Contracts', 'Accept a challenge', 'scroll', () => sendFeature('contracts')], ['Hall of Fame', 'Tower legends', 'trophy', () => sendFeature('hall')],
    ['Last run report', 'Review your run', 'book', () => sendFeature('report')], ['Mastery & boards', 'Measure your growth', 'stairs', () => { update((st) => goTo(st, 'mastery')); requestMastery('', 'mastery'); }]],
};

const PANOS = new Map();
/** A landscape painted once per region and size into a canvas. */
export function Pano({ region, x, y, w, h }) {
  const ref = useRef(null);
  useEffect(() => {
    const key = region + '|' + w + 'x' + h;
    let data = PANOS.get(key);
    if (!data) { data = new ImageData(paintPanorama(region, w, h), w, h); PANOS.set(key, data); if (PANOS.size > 24) PANOS.delete(PANOS.keys().next().value); }
    ref.current.getContext('2d').putImageData(data, 0, 0);
  }, [region, w, h]);
  return html`<canvas ref=${ref} class="pano" width=${w} height=${h} style=${at(x, y, w, h)}></canvas>`;
}

/** DestinationButton: a dark frame holding a landscape and the tower's name. */
function Tile({ x, y, w, h, region, title, sub, onClick }) {
  return html`<button class="n-btn n-fr n-dark tile" title=${title + '\n' + sub} style=${at(x, y, w, h)} onClick=${onClick}>
    <${Pano} region=${region} x=${-5} y=${-5} w=${w - 6} h=${Math.max(10, h - 18)} />
    <${Label} x=${-3} y=${h - 20} w=${w - 10}>${title}<//><i class="brk"></i></button>`;
}

function Header({ lines, x, y, w, h = 28 }) {
  return html`<${Panel} x=${x} y=${y} w=${w} h=${h}><${Label} x=${-2} y=${-3} color="bronze">${lines[0]}<//><${Label} x=${-2} y=${9} color="muted">${lines[1]}<//><//>`;
}

function Footer({ hall, busy, status }) {
  const b = box();
  const resume = hall.canResume;
  return html`
    <${Label} x=${b.x} y=${b.status} w=${b.w} color="soft">${status}<//>
    <${NB} x=${b.x} y=${b.foot} w=${Math.min(95, Math.floor(b.w / 2))} kind="dark" icon="door" label=${resume ? 'Return to run' : 'Lobby'} disabled=${busy} onClick=${() => request(resume ? 'resume' : 'lobby')}>${resume ? 'Return to run' : 'Lobby'}<//>
    <${NB} x=${b.x + b.w - 58} y=${b.foot} w=${58} kind="dark" disabled=${busy} onClick=${() => request('refresh')}>Refresh<//>`;
}

function Towers({ hall, page }) {
  const b = box();
  const pages = Math.max(1, Math.ceil(hall.towers.length / b.perPage));
  const cur = Math.min(page, pages - 1);
  const cw = Math.floor((b.w - 6) / 2);
  const trial = (kind) => hall.trials.find((t) => t.kind === kind);
  const ty = b.y + 38 + b.rows * b.rowH;
  return html`
    <${Pano} region="tideforge" x=${b.x} y=${b.y} w=${b.w} h=${32} />
    <div class="shade" style=${at(b.x, b.y, Math.floor((b.w * 46) / 100), 32)}></div>
    <${Label} x=${b.x + 7} y=${b.y + 12} w=${b.w - 14}>Every ascent begins here.<//>
    ${hall.towers.slice(cur * b.perPage, cur * b.perPage + b.perPage).map((t, i) => html`<${Tile} key=${t.id} x=${b.x + (i % 2) * (cw + 6)} y=${b.y + 38 + Math.floor(i / 2) * b.rowH} w=${cw} h=${b.rowH - 4}
      region=${regionOf(t.id)} title=${t.name} sub=${t.floors + ' floors / cycle'} onClick=${() => update((s) => goTo(s, 'tower', t.id))} />`)}
    <${NB} x=${b.x} y=${ty} w=${cw} kind="dark" icon="sun" label="Daily Tower" disabled=${!trial('daily')} onClick=${() => update((s) => goTo(s, 'trial', 'daily'))}>Daily Tower<//>
    <${NB} x=${b.x + cw + 6} y=${ty} w=${cw} kind="dark" icon="calendar" label="Weekly Tower" disabled=${!trial('weekly')} onClick=${() => update((s) => goTo(s, 'trial', 'weekly'))}>Weekly Tower<//>
    ${pages > 1 ? html`
      <${NB} x=${b.x} y=${b.bottom} w=${20} kind="dark" onClick=${() => update((s) => setPage(s, (cur - 1 + pages) % pages))}>${'<'}<//>
      <${NB} x=${b.x + 24} y=${b.bottom} w=${20} kind="dark" onClick=${() => update((s) => setPage(s, (cur + 1) % pages))}>${'>'}<//>
      <${Label} x=${b.x + 51} y=${screen.h - 58} color="ink">${cur + 1} / ${pages}<//>` : null}`;
}

function Trials() {
  const b = box();
  const entry = (kind, title, sub, region, y) => html`<${Tile} x=${b.x} y=${y} w=${b.w} h=${49} region=${region}
    title=${title} sub=${sub} onClick=${() => update((s) => goTo(s, 'trial', kind))} />`;
  return html`
    <${Panel} x=${b.x} y=${b.y} w=${b.w} h=${28} />
    ${entry('daily', 'Daily Tower', 'A new daily challenge', 'tideforge', b.y + 34)}
    ${entry('weekly', 'Weekly Tower', 'The week’s expedition', 'duskvale', b.y + 88)}`;
}

function Cards({ tab }) {
  const b = box();
  const tracks = useStore((s) => s.tracks);
  const social = tab === 'social';
  const list = CARDS[tab];
  const cw = Math.floor((b.w - 6) / 2);
  const cardH = Math.min(57, Math.floor((screen.h - 47 - (b.y + 34)) / (social ? 2 : 3)));
  return html`
    <${Header} x=${b.x} y=${b.y} w=${b.w} lines=${social ? ['LINK LOUNGE', 'Great battles begin with good company.'] : ['TRAINER RECORD', 'Every victory leaves a mark.']} />
    ${!social && tracks ? html`<${NB} x=${b.x + b.w - 64} y=${b.y + 5} w=${58} h=${18} kind="dark" icon="stairs" label="Tracks" onClick=${() => update((s) => setRecords(s, false))}>Tracks<//>` : null}
    ${list.map(([title, sub, icon, run], i) => html`<${Card} key=${title} x=${b.x + (i % 2) * (cw + 6)} y=${b.y + 34 + Math.floor(i / 2) * cardH} w=${cw} h=${cardH - 4}
      title=${title} sub=${sub} icon=${icon} soon=${!run} onClick=${run} />`)}`;
}

export function Hall({ hall, tab, page, busy, view, pick }) {
  useScreen();
  const feature = useStore((s) => s.feature);
  const tracks = useStore((s) => s.tracks);
  const records = useStore((s) => s.records);
  const fSection = useStore((s) => s.fSection);
  const onTab = (id) => {
    if (id === 'codex') return sendFeature('modifiers');
    if (id === 'echoes') return sendFeature('echoes');
    if (id === 'collection') return sendFeature('collection');
    update((s) => setRecords(setTab(s, id), false));
    if (id === 'progress') sendTrack('refresh');
    return undefined;
  };
  const status = !hall ? '' : hall.runStatus || (hall.play.lobby.role === 0 ? 'Choose a destination to begin.' : 'Your expedition lobby is available.');
  const section = view === 'settings' ? 'Settings' : view === 'feature' ? sectionTitle(feature, fSection) : view === 'tower' ? 'Tower briefing' : view === 'trial' ? 'Trial briefing' : { towers: 'Tower Hall', trials: 'Trials', social: 'Social', progress: 'Progress' }[tab];
  return html`<${Shell} section=${section} tab=${tab} onTab=${onTab} onBack=${() => (view === 'hall' ? closeGui() : view === 'feature' ? stepBack(get().fui) : update(goBack))} onClose=${closeGui} onSettings=${() => update((s) => (s.view === 'settings' ? s : goTo(s, 'settings')))}>
    ${view === 'settings' ? html`<${Settings} />`
      : view === 'feature' ? html`<${Features} />`
      : view === 'tower' ? html`<${TowerBriefing} hall=${hall} id=${pick} busy=${busy} />`
      : view === 'trial' ? html`<${TrialBriefing} hall=${hall} kind=${pick} busy=${busy} />`
      : tab === 'towers' ? html`<${Towers} hall=${hall} page=${page} />`
      : tab === 'trials' ? html`<${Trials} />`
      : tab === 'progress' && tracks && !records ? html`<${Tracks} onRecords=${() => update((s) => setRecords(s, true))} />` : html`<${Cards} tab=${tab} />`}
    ${view === 'feature' || view === 'settings' ? null : html`<${Footer} hall=${hall} busy=${busy} status=${status} />`}
  <//>`;
}

function Briefing({ region, lines, action }) {
  const b = box();
  return html`
    <${Pano} region=${region} x=${b.x} y=${b.y} w=${b.w} h=${28} />
    <div class="n-lines" style=${at(b.x + 7, b.y + 35, b.w - 14, b.bottom - b.y - 40)}>${lines.map((l) => html`<p>${l}</p>`)}</div>
    ${action ? html`<${NB} x=${b.x} y=${b.bottom} w=${b.w} kind="dark" label=${action[0]} disabled=${!action[2]} onClick=${action[1]}>${action[0]}<//>` : null}`;
}

function TowerBriefing({ hall, id, busy }) {
  const t = hall.towers.find((x) => x.id === id);
  const lines = t ? [t.name, t.floors + ' floors per cycle / ' + t.milestones + ' milestones', t.ascends ? 'Ascension continues into harder cycles.' : 'The tower ends after its final floor.',
    'Select this tower to create or update your lobby. Review mode, party registration and invitations before starting.'] : ['This tower is no longer available. Refresh the Hall.'];
  return html`<${Briefing} region=${regionOf(id)} lines=${lines} action=${t ? ['Select tower & open lobby', () => request('tower', id), canSelect(hall, busy)] : null} />`;
}

function TrialBriefing({ hall, kind, busy }) {
  const t = hall.trials.find((x) => x.kind === kind);
  const lines = t ? [t.title, ...t.rules, 'Preparing does not launch a run. Trial rules will be checked again by the server.'] : ['No ' + kind + ' trial is currently available.'];
  return html`<${Briefing} region=${t ? regionOf(t.tower) : 'tideforge'} lines=${lines} action=${t ? ['Prepare trial lobby', () => request('trial', kind, t.revision), canSelect(hall, busy)] : null} />`;
}

/** Gives up waiting for the server after a while, so a lost reply does not leave the buttons dead. */
export function useBusyTimeout(busyAt) {
  useEffect(() => {
    if (!busyAt) return undefined;
    const id = setTimeout(() => update(timeout), Math.max(0, busyAt + WAIT_MS - Date.now()));
    return () => clearTimeout(id);
  }, [busyAt]);
}
