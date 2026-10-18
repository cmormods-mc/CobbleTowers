// The Frontier Desk look for Progress (P51 slice 4): the mastery track of each tower and the season track, with claiming.
// Same requests as the native Tracks page (sendTrack); src/tracks.js decides what each tile is and what the buttons may do.
import { useEffect, useRef, useState } from 'preact/hooks';
import { html } from '../html.js';
import { update, useStore } from '../store.js';
import { goTo, selectNode, setLane } from '../reducer.js';
import { anyClaimable, CLAIMABLE, CLAIMED, currentIndex, detail, laneOf, LOCKED, subtitle } from '../tracks.js';
import { sendTrack } from '../screens/Tracks.js';
import { sendFeature } from '../screens/Features.js';
import { requestMastery } from '../screens/Mastery.js';
import { TopBar } from './DeskHall.js';
import { TileIcon } from './icons.js';
import { claimable, claimLine, laneRows, progressPercent, stateWord, tileLabel, timerText } from './progressView.js';

const MORE = [['Season & rewards', () => sendFeature('season')], ['Cosmetics & titles', () => sendFeature('cosmetics')], ['Contracts', () => sendFeature('contracts')],
  ['Hall of Fame', () => sendFeature('hall')], ['Last run report', () => sendFeature('report')],
  ['Mastery & boards', () => { update((s) => goTo(s, 'mastery')); requestMastery('', 'mastery'); }]];

function Tile({ node, unit, selected, current, onPick }) {
  const frame = node.state === CLAIMABLE ? 'open' : node.state === CLAIMED ? 'done' : node.state === LOCKED ? 'lock' : 'info';
  return html`<button class=${'dtile ' + frame + (selected ? ' sel' : '') + (current ? ' cur' : '')} onClick=${onPick} data-n=${node.number} aria-pressed=${selected}
    aria-label=${unit + ' ' + node.number + ' ' + stateWord(node) + ' ' + (node.reward || '')}>
    <span class="dn">${node.number}</span><span class="dpic"><${TileIcon} node=${node} /></span>
    <span class="dlab">${tileLabel(node)}</span>
    ${node.state === CLAIMABLE ? html`<span class="dbang">Ready</span>` : node.state === CLAIMED ? html`<span class="dtick" aria-hidden="true">✓</span>` : null}</button>`;
}

/** @param {{hall: object|null, busy: boolean}} props */
export function DeskProgress({ hall }) {
  const tracks = useStore((s) => s.tracks);
  const arrived = useStore((s) => s.tracksAt);
  const want = useStore((s) => s.lane);
  const sel = useStore((s) => s.tSel);
  const [now, setNow] = useState(Date.now());
  const gridRef = useRef(null);
  const { kind, data: lane } = tracks ? laneOf(tracks, want) : { kind: 'mastery', data: null };
  // the season timer counts down while the page is open
  useEffect(() => { if (kind !== 'season') return undefined; const id = setInterval(() => setNow(Date.now()), 30_000); return () => clearInterval(id); }, [kind]);
  // open on the tile the player is on
  useEffect(() => {
    const el = gridRef.current;
    if (!el || !lane) return;
    const cur = el.querySelector('.dtile.cur');
    if (cur) el.scrollTop = Math.max(0, cur.offsetTop - el.clientHeight / 2 + cur.clientHeight / 2);
  }, [kind, tracks && tracks.selectedTower, lane && lane.nodes.length]);

  if (!tracks || !lane) {
    return html`<div class="fd"><${TopBar} active="progress" /><div class="dbody" style="flex:1;grid-template-columns:1fr"><div class="dcard empty" style="display:grid;place-items:center">Loading your progress…</div></div></div>`;
  }
  const ready = claimable(tracks);
  const rows = laneRows(tracks, want, hall && hall.extras);
  const selected = sel[kind] != null && sel[kind] < lane.nodes.length ? sel[kind] : null;
  const node = selected == null ? null : lane.nodes[selected];
  const d = detail(lane, kind, node);
  const pct = progressPercent(lane);
  const timer = timerText(lane, arrived, now);
  const pick = (r) => (r.kind === 'season' ? update((s) => setLane(s, 'season')) : (update((s) => setLane(s, 'mastery')), r.id !== tracks.selectedTower ? sendTrack('refresh', '', r.id) : undefined));
  return html`<div class="fd"><${TopBar} active="progress" />
    <div class="dbody" style="grid-template-columns:27fr 73fr;flex:1">
      <div style="display:flex;flex-direction:column;gap:calc(var(--u) * .9);min-height:0">
        <div class=${'cont' + (ready.total ? ' gold' : '')}><div style="flex:1"><small>Ready to claim</small><b>${claimLine(ready.total)}</b></div>
          ${ready.mastery && kind !== 'mastery' ? html`<button class="dbtn dsm gh" onClick=${() => update((s) => setLane(s, 'mastery'))}>Mastery</button>` : ready.season && kind !== 'season' ? html`<button class="dbtn dsm gh" onClick=${() => update((s) => setLane(s, 'season'))}>Season</button>` : null}</div>
        <div class="rows">${rows.map((r) => html`<button key=${r.id} class=${'trow dl' + (r.selected ? ' sel' : '')} aria-pressed=${r.selected} onClick=${() => pick(r)}
          style="grid-template-columns:1fr"><div><b>${r.name}</b><small style="margin:0">${r.sub}</small></div></button>`)}
          <div class="dmore"><span class="lbl">More</span>${MORE.map(([name, run]) => html`<button key=${name} class="dbtn gh dsm" onClick=${run}>${name}</button>`)}</div></div>
      </div>
      <div class="dcard dcol" style="min-height:0">
        <div class="ch"><div><h3 style="font-size:calc(var(--u) * 2.2)">${lane.title}</h3><div class="dsub">${subtitle(tracks, lane, kind)}</div></div>
          <div style="display:flex;gap:calc(var(--u) * .6);align-items:center">${timer ? html`<span class="stp stp-y">${timer}</span>` : null}
            <button class="dbtn dsm gh" disabled=${!anyClaimable(lane)} onClick=${() => sendTrack('claim_all', kind, tracks.selectedTower, 0)}>Claim all</button></div></div>
        ${lane.need > 0 ? html`<div class="dprog" role="img" aria-label=${pct + ' percent to the next step'}><i style=${`width:${pct}%`}></i><span>${lane.into} / ${lane.need}</span></div>` : null}
        <div class="dtiles" ref=${gridRef}>${lane.nodes.map((n, i) => html`<${Tile} key=${n.number} node=${n} unit=${kind === 'season' ? 'Step' : 'Level'} selected=${selected === i} current=${i === currentIndex(lane)} onPick=${() => update((s) => selectNode(s, kind, i))} />`)}</div>
        <div class="ddetail"><div style="flex:1;min-width:0">${d.hint ? html`<div class="dsub">${d.body}</div>` : html`<b>${d.title}</b><div>${d.body}</div>${d.note ? html`<div class="dsub">${d.note}</div>` : null}`}</div>
          <button class="dbtn" disabled=${!d.claimable} onClick=${() => sendTrack('claim', kind, tracks.selectedTower, node.number)}>Claim</button></div>
      </div></div></div>`;
}
