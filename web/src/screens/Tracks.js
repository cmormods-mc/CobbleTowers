import { useEffect, useLayoutEffect, useRef, useState } from 'preact/hooks';
import { html } from '../html.js';
import { act } from '../bridge.js';
import { update, useStore } from '../store.js';
import { selectNode, setLane, setRecords } from '../reducer.js';
import { anyClaimable, CLAIMABLE, CLAIMED, centreOn, currentIndex, detail, laneOf, LOCKED, neighbour, subtitle, tileIcon, timeLeft } from '../tracks.js';
import { at, box, Icon, ItemIcon, Label, NB, Panel, screen } from '../components/native.js';

const TILE_W = 38, TILE_H = 46, GAP = 4, PITCH = TILE_W + GAP;

/** Asks the server for the tracks (optionally another mastery tower) or for a claim. */
export function sendTrack(action, lane = '', tower = '', number = 0) {
  act('TrackAction', { action, lane, tower, number });
}

function Tile({ node, selected, current, onPick }) {
  const icon = tileIcon(node);
  const frame = node.state === CLAIMABLE ? 'open' : node.state === CLAIMED ? 'done' : 'shut';
  return html`<button class=${'tk tk-' + frame + (selected ? ' sel' : '') + (current ? ' cur' : '') + (node.state === LOCKED ? ' lock' : '')}
    style=${at(0, 0, TILE_W, TILE_H)} data-i=${node.number} onClick=${onPick} tabindex="-1">
    <span class="num">${node.number}</span>
    ${icon ? (icon.kind === 'raid' ? html`<i class="ic raid" style=${at(3, 7, 16, 16)}></i>` : icon.kind === 'ui' ? html`<${Icon} name=${icon.name} x=${3} y=${7} />` : icon.kind === 'item' ? html`<${ItemIcon} id=${icon.id} x=${3} y=${7} />` : html`<${Icon} name="gift" x=${3} y=${7} />`) : null}
    ${node.state === CLAIMED ? html`<${Icon} name="check" x=${11} y=${19} />` : null}
    ${node.state === LOCKED ? html`<${Icon} name="lock" x=${11} y=${19} />` : null}
    ${node.state === CLAIMABLE ? html`<span class="bang">!</span>` : null}
    <i class="brk"></i></button>`;
}

/** A row of tiles that scrolls sideways: wheel, drag, Left and Right. Opens on the current tile. */
function Strip({ x, y, w, h, lane, kind, selected, resetKey }) {
  const ref = useRef(null);
  const drag = useRef(null);
  const g = screen.g;
  const jump = (smooth) => {
    const el = ref.current;
    if (!el) return;
    const left = centreOn(currentIndex(lane), PITCH, TILE_W, el.clientWidth / g, lane.nodes.length) * g;
    if (smooth) el.scrollTo({ left, behavior: 'smooth' }); else el.scrollLeft = left;
  };
  useLayoutEffect(() => jump(false), [kind, resetKey]);
  useEffect(() => { window.TowerTracksJump = () => jump(true); return () => { window.TowerTracksJump = null; }; });
  const pick = (i) => update((s) => selectNode(s, kind, i));
  const reveal = (i) => {
    const el = ref.current;
    const left = i * PITCH * g, right = left + TILE_W * g;
    if (left < el.scrollLeft) el.scrollTo({ left: Math.max(0, left - 4 * g), behavior: 'smooth' });
    else if (right > el.scrollLeft + el.clientWidth) el.scrollTo({ left: right - el.clientWidth + 4 * g, behavior: 'smooth' });
  };
  const step = (d) => { const i = Math.max(0, Math.min(lane.nodes.length - 1, selected == null ? currentIndex(lane) : selected + d)); pick(i); reveal(i); };
  return html`<div class="strip" ref=${ref} style=${at(x, y, w, h)} tabindex="0"
    onWheel=${(e) => { e.preventDefault(); ref.current.scrollLeft += (Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY); }}
    onKeyDown=${(e) => { if (e.key === 'ArrowLeft') { e.preventDefault(); step(-1); } else if (e.key === 'ArrowRight') { e.preventDefault(); step(1); } }}
    onPointerDown=${(e) => { drag.current = { x: e.clientX, left: ref.current.scrollLeft, moved: false }; }}
    onPointerMove=${(e) => { const d = drag.current; if (!d || !(e.buttons & 1)) return; if (Math.abs(e.clientX - d.x) > 3 * g) { d.moved = true; ref.current.scrollLeft = d.left - (e.clientX - d.x); } }}
    onPointerUp=${() => { setTimeout(() => { drag.current = null; }, 0); }}
    onClickCapture=${(e) => { if (drag.current && drag.current.moved) { e.stopPropagation(); e.preventDefault(); } }}>
    <div class="rail" style=${`width:calc(var(--g) * ${lane.nodes.length * PITCH - GAP})`}>
      ${lane.nodes.map((n, i) => html`<div class="slot" key=${n.number} style=${`left:calc(var(--g) * ${i * PITCH})`}>
        <${Tile} node=${n} selected=${selected === i} current=${i === currentIndex(lane)} onPick=${() => pick(i)} /></div>`)}
    </div></div>`;
}

export function Tracks({ onRecords }) {
  const tracks = useStore((s) => s.tracks);
  const arrived = useStore((s) => s.tracksAt);
  const want = useStore((s) => s.lane);
  const sel = useStore((s) => s.tSel);
  const [, tick] = useState(0);
  const { kind, data: lane } = laneOf(tracks, want);
  useEffect(() => {
    if (kind !== 'season') return undefined;
    const id = setInterval(() => tick((n) => n + 1), 30_000);
    return () => clearInterval(id);
  }, [kind]);

  const b = box();
  const H = screen.h;
  const season = kind === 'season';
  const multi = !season && tracks.towers.length > 1;
  const bw = Math.min(64, Math.floor((b.w - 8) / 3));
  const selected = sel[kind] != null && sel[kind] < lane.nodes.length ? sel[kind] : null;
  const node = selected == null ? null : lane.nodes[selected];
  const d = detail(lane, kind, node);
  const dy = b.y + 105, dh = Math.max(36, H - 46 - dy);
  const left = arrived + lane.endsInMillis - Date.now();
  const rightPad = season ? 96 : multi ? 40 : 0;
  const prev = neighbour(tracks, -1), next = neighbour(tracks, 1);

  return html`
    <${Panel} x=${b.x} y=${b.y} w=${b.w} h=${28} />
    <${Label} x=${b.x + 8} y=${b.y + 5} w=${b.w - 16 - rightPad} color="bronze">${lane.title}<//>
    <${Label} x=${b.x + 8} y=${b.y + 16} w=${b.w - 16 - rightPad} color="muted">${subtitle(tracks, lane, kind)}<//>
    ${season ? html`
      <${Panel} x=${b.x + b.w - 92} y=${b.y + 3} w=${88} h=${22} kind="bframe" />
      <${Label} x=${b.x + b.w - 86} y=${b.y + 5} w=${78} color="muted">${lane.timerLabel || 'ENDS IN'}<//>
      <${Label} x=${b.x + b.w - 86} y=${b.y + 14} w=${78}>${timeLeft(left)}<//>
      ${lane.need > 0 ? html`<div class="tbar" style=${at(b.x + 8, b.y + 24, b.w - 16 - rightPad, 2)}><i style=${`width:${Math.min(100, (100 * Math.min(lane.into, lane.need)) / lane.need)}%`}></i></div>` : null}` : null}
    ${multi ? html`
      <${NB} x=${b.x + b.w - 38} y=${b.y + 6} w=${16} h=${16} kind="dark" iconOnly="left" label="" tip="Previous tower" onClick=${() => sendTrack('refresh', '', prev)}><//>
      <${NB} x=${b.x + b.w - 20} y=${b.y + 6} w=${16} h=${16} kind="dark" iconOnly="right" label="" tip="Next tower" onClick=${() => sendTrack('refresh', '', next)}><//>` : null}

    <${NB} x=${b.x} y=${b.y + 32} w=${bw} h=${16} kind=${season ? 'dark' : 'tab'} icon="trophy" label="Mastery" onClick=${() => update((s) => setLane(s, 'mastery'))}>Mastery<//>
    ${tracks.season.present ? html`<${NB} x=${b.x + bw + 4} y=${b.y + 32} w=${bw} h=${16} kind=${season ? 'tab' : 'dark'} icon="star" label="Season" onClick=${() => update((s) => setLane(s, 'season'))}>Season<//>` : null}
    <${NB} x=${b.x + b.w - bw} y=${b.y + 32} w=${bw} h=${16} kind="dark" icon="book" label="Records" onClick=${onRecords}>Records<//>

    <${Strip} x=${b.x} y=${b.y + 51} w=${b.w} h=${50} lane=${lane} kind=${kind} selected=${selected} resetKey=${tracks.selectedTower} />

    <${Panel} x=${b.x} y=${dy} w=${b.w} h=${dh} />
    ${d.hint ? html`<div class="n-lines hint" style=${at(b.x + 8, dy + 6, b.w - 76, dh - 8)}><p>${d.body}</p></div>` : html`
      <${Label} x=${b.x + 8} y=${dy + 5} w=${b.w - 76} color=${d.claimable ? 'bronze' : 'text'}>${d.title}<//>
      <div class="n-lines light" style=${at(b.x + 8, dy + 16, b.w - 76, dh - 18)}>${d.body ? html`<p>${d.body}</p>` : null}${d.note ? html`<p class="mu">${d.note}</p>` : null}</div>`}
    <${NB} x=${b.x + b.w - 64} y=${dy + 4} w=${58} h=${16} kind="primary" icon="gift" label="Claim" disabled=${!d.claimable}
      onClick=${() => sendTrack('claim', kind, tracks.selectedTower, node.number)}>Claim<//>
    <${NB} x=${b.x + b.w - 64} y=${dy + Math.min(24, dh - 20)} w=${58} h=${16} kind="dark" label="Claim all" disabled=${!anyClaimable(lane)}
      onClick=${() => sendTrack('claim_all', kind, tracks.selectedTower, 0)}>Claim all<//>
    ${dh >= 60 ? html`<${NB} x=${b.x + b.w - 64} y=${dy + Math.min(44, dh - 1)} w=${58} h=${16} kind="dark" label="Now" onClick=${() => window.TowerTracksJump && window.TowerTracksJump()}>Now<//>` : null}`;
}

export { setRecords };
