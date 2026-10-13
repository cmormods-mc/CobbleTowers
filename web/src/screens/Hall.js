import { useEffect } from 'preact/hooks';
import { html } from '../html.js';
import { act, closeGui } from '../bridge.js';
import { update } from '../store.js';
import { begin, goBack, goTo, setPage, setTab, timeout } from '../reducer.js';
import { canSelect, regionOf } from '../lobby.js';
import { at, Icon, Label, NB, Panel, Shell } from '../components/native.js';

const WAIT_MS = 8000;
const CONTENT_X = 108, CONTENT_Y = 43, CONTENT_W = 307, BOTTOM = 176;
const PER_PAGE = 2; // one row of two, as at the mod's smallest screen

/** One server request at a time: buttons wait for the next state, or give up after 8 s. */
export function request(action, argument = '', revision = '') {
  update((s) => begin(s, Date.now()));
  act('TowerHallAction', { action, argument, revision });
}

/** Cards of the Social and Progress tabs; `run` marks the ones whose screen exists in the web UI. */
const CARDS = {
  social: [['Party & invites', 'Gather your team', 'group', () => request('lobby')], ['Club lodge', 'Your community', 'flag'], ['Spectate', 'Watch challengers', 'eye'], ['Run codes', 'Scout an expedition', 'scroll']],
  progress: [['Season & rewards', 'Your next milestone', 'star'], ['Cosmetics & titles', 'Wear your victories', 'crown'], ['Contracts', 'Accept a challenge', 'scroll'],
    ['Hall of Fame', 'Tower legends', 'trophy'], ['Last run report', 'Review your run', 'book'], ['Mastery & boards', 'Measure your growth', 'stairs']],
};

/** A landscape strip: `kind` tile | hero | strip (the sizes the mod draws), cropped to the box. */
export const Pano = ({ region, kind, x, y, w, h }) =>
  html`<div class=${'pano k-' + kind} style=${at(x, y, w, h) + `background-image:url(assets/gui/pano/${region}_${kind}.png)`}></div>`;

/** DestinationButton: a dark frame holding a landscape and the tower's name. */
function Tile({ x, y, w, h, region, title, sub, onClick }) {
  return html`<button class="n-btn n-fr n-dark tile" title=${title + '\n' + sub} style=${at(x, y, w, h)} onClick=${onClick}>
    <${Pano} region=${region} kind=${w > 200 ? 'hero' : 'tile'} x=${-5} y=${-5} w=${w - 6} h=${Math.max(10, h - 18)} />
    <${Label} x=${-3} y=${h - 20} w=${w - 10}>${title}<//><i class="brk"></i></button>`;
}

/** TrainerCardButton: a dark card with a title, a caption and a picture. */
function Card({ x, y, w, h, title, sub, icon, onClick, soon }) {
  return html`<button class=${'n-btn n-fr n-dark card' + (soon ? ' soon' : '')} disabled=${soon} title=${soon ? 'Coming soon' : ''} style=${at(x, y, w, h)} onClick=${onClick}>
    <${Label} x=${-2} y=${-2} w=${w - 30} color="text">${title}<//>
    <${Label} x=${-2} y=${11} w=${w - 30} color="muted">${soon ? 'Coming soon' : sub}<//>
    <${Icon} name=${icon} x=${w - 30} y=${h / 2 - 16} /><i class="brk"></i></button>`;
}

function Header({ lines, x = CONTENT_X, y = CONTENT_Y, w = CONTENT_W, h = 28 }) {
  return html`<${Panel} x=${x} y=${y} w=${w} h=${h}><${Label} x=${-2} y=${-3} color="bronze">${lines[0]}<//><${Label} x=${-2} y=${9} color="muted">${lines[1]}<//><//>`;
}

function Footer({ hall, busy, status }) {
  const resume = hall.canResume;
  return html`
    <${Label} x=${CONTENT_X} y=${200} w=${CONTENT_W} color="soft">${status}<//>
    <${NB} x=${CONTENT_X} y=${211} w=${95} kind="dark" icon="door" label=${resume ? 'Return to run' : 'Lobby'} disabled=${busy} onClick=${() => request(resume ? 'resume' : 'lobby')}>${resume ? 'Return to run' : 'Lobby'}<//>
    <${NB} x=${CONTENT_X + CONTENT_W - 58} y=${211} w=${58} kind="dark" disabled=${busy} onClick=${() => request('refresh')}>Refresh<//>`;
}

function Towers({ hall, busy, page }) {
  const pages = Math.max(1, Math.ceil(hall.towers.length / PER_PAGE));
  const cur = Math.min(page, pages - 1);
  const cw = (CONTENT_W - 6) / 2;
  const trial = (kind) => hall.trials.find((t) => t.kind === kind);
  return html`
    <${Pano} region="tideforge" kind="hero" x=${CONTENT_X} y=${CONTENT_Y} w=${CONTENT_W} h=${32} />
    <div class="shade" style=${at(CONTENT_X, CONTENT_Y, Math.floor((CONTENT_W * 46) / 100), 32)}></div>
    <${Label} x=${CONTENT_X + 7} y=${CONTENT_Y + 12} w=${CONTENT_W - 14}>Every ascent begins here.<//>
    ${hall.towers.slice(cur * PER_PAGE, cur * PER_PAGE + PER_PAGE).map((t, i) => html`<${Tile} key=${t.id} x=${CONTENT_X + i * (cw + 6)} y=${CONTENT_Y + 38} w=${cw} h=${60}
      region=${regionOf(t.id)} title=${t.name} sub=${t.floors + ' floors / cycle'} onClick=${() => update((s) => goTo(s, 'tower', t.id))} />`)}
    <${NB} x=${CONTENT_X} y=${CONTENT_Y + 102} w=${cw} kind="dark" icon="sun" label="Daily Tower" disabled=${!trial('daily')} onClick=${() => update((s) => goTo(s, 'trial', 'daily'))}>Daily Tower<//>
    <${NB} x=${CONTENT_X + cw + 6} y=${CONTENT_Y + 102} w=${cw} kind="dark" icon="calendar" label="Weekly Tower" disabled=${!trial('weekly')} onClick=${() => update((s) => goTo(s, 'trial', 'weekly'))}>Weekly Tower<//>
    ${pages > 1 ? html`
      <${NB} x=${CONTENT_X} y=${BOTTOM} w=${20} kind="dark" onClick=${() => update((s) => setPage(s, (cur - 1 + pages) % pages))}>${'<'}<//>
      <${NB} x=${CONTENT_X + 24} y=${BOTTOM} w=${20} kind="dark" onClick=${() => update((s) => setPage(s, (cur + 1) % pages))}>${'>'}<//>
      <${Label} x=${CONTENT_X + 51} y=${182} color="ink">${cur + 1} / ${pages}<//>` : null}`;
}

function Trials() {
  const entry = (kind, title, sub, region, y) => html`<${Tile} x=${CONTENT_X} y=${y} w=${CONTENT_W} h=${49} region=${region}
    title=${title} sub=${sub} onClick=${() => update((s) => goTo(s, 'trial', kind))} />`;
  return html`
    <${Panel} x=${CONTENT_X} y=${CONTENT_Y} w=${CONTENT_W} h=${28} />
    ${entry('daily', 'Daily Tower', 'A new daily challenge', 'tideforge', CONTENT_Y + 34)}
    ${entry('weekly', 'Weekly Tower', 'The week’s expedition', 'duskvale', CONTENT_Y + 88)}`;
}

function Cards({ tab }) {
  const social = tab === 'social';
  const list = CARDS[tab];
  const cw = (CONTENT_W - 6) / 2;
  const cardH = Math.min(57, Math.floor((240 - 47 - (CONTENT_Y + 34)) / (social ? 2 : 3)));
  return html`
    <${Header} lines=${social ? ['LINK LOUNGE', 'Great battles begin with good company.'] : ['TRAINER RECORD', 'Every victory leaves a mark.']} />
    ${list.map(([title, sub, icon, run], i) => html`<${Card} key=${title} x=${CONTENT_X + (i % 2) * (cw + 6)} y=${CONTENT_Y + 34 + Math.floor(i / 2) * cardH} w=${cw} h=${cardH - 4}
      title=${title} sub=${sub} icon=${icon} soon=${!run} onClick=${run} />`)}`;
}

export function Hall({ hall, tab, page, busy, view, pick }) {
  const onTab = (id) => update((s) => setTab(s, id));
  const onBack = () => { update(goBack); };
  const status = hall.runStatus || (hall.play.lobby.role === 0 ? 'Choose a destination to begin.' : 'Your expedition lobby is available.');
  const section = view === 'tower' ? 'Tower briefing' : view === 'trial' ? 'Trial briefing' : { towers: 'Tower Hall', trials: 'Trials', social: 'Social', progress: 'Progress' }[tab];
  return html`<${Shell} section=${section} tab=${tab} onTab=${onTab} onBack=${() => (view === 'hall' ? closeGui() : onBack())} onClose=${closeGui}>
    ${view === 'tower' ? html`<${TowerBriefing} hall=${hall} id=${pick} busy=${busy} />`
      : view === 'trial' ? html`<${TrialBriefing} hall=${hall} kind=${pick} busy=${busy} />`
      : tab === 'towers' ? html`<${Towers} hall=${hall} busy=${busy} page=${page} />`
      : tab === 'trials' ? html`<${Trials} />` : html`<${Cards} tab=${tab} />`}
    <${Footer} hall=${hall} busy=${busy} status=${status} />
  <//>`;
}

function Briefing({ region, lines, action }) {
  return html`
    <${Pano} region=${region} kind="strip" x=${CONTENT_X} y=${CONTENT_Y} w=${CONTENT_W} h=${28} />
    <div class="n-lines" style=${at(CONTENT_X + 7, CONTENT_Y + 35, CONTENT_W - 14, BOTTOM - CONTENT_Y - 40)}>${lines.map((l) => html`<p>${l}</p>`)}</div>
    ${action ? html`<${NB} x=${CONTENT_X} y=${BOTTOM} w=${CONTENT_W} kind="dark" label=${action[0]} disabled=${!action[2]} onClick=${action[1]}>${action[0]}<//>` : null}`;
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
