// The branching route map (P56) on the Frontier Desk, drawn as a cut face of the tower: a lit window per stop in each floor's slab,
// copper pipes for the links. Choosing a window is voting its card, as the route buttons do. Concept C ("Tower Elevation");
// the Halloween tower wears the Hollow's Eve skin (concept D).
import { html } from '../html.js';
import { kindPaths } from './kindIcon.js';
import { atlas, atlasHeight, laneX, rowY, stopName } from './atlasMap.js';
import { KindIcon } from './kindIcon.js';
import { ModThumb, artOf } from './modArt.js';

const W = 360;
const KIND = (kind) => 'rk-' + String(kind).toLowerCase();

/** The skin of a tower: the Halloween tower is Hollow's Eve, every other the plain stone elevation. */
export const skinOf = (tower) => (/hollow/i.test(tower || '') ? 'hollow' : 'stone');

function arch(x, y, w) {
  const top = y - 19;
  return `M${x - w} ${y + 17}V${top + w}A${w} ${w} 0 0 1 ${x + w} ${top + w}V${y + 17}Z`;
}

function Window({ n, rows, selected, chosen, hovered, enabled, title, onPick, onHover }) {
  const x = laneX(n.lane), y = rowY(n.row, rows), w = n.kind === 'BOSS' ? 21 : 17;
  const fog = n.state === 'fog', live = n.state === 'reach' && enabled && n.card >= 0;
  const named = n.state === 'current';
  const cls = `awin st-${n.state}${fog ? '' : ' ' + KIND(n.kind)}${selected ? ' sel' : ''}${chosen ? ' chosen' : ''}${hovered ? ' hov' : ''}`;
  const label = stopName(n.kind) + (title ? ': ' + title : '');
  const body = html`
    <path class="aglow" d=${arch(x, y, w + 3)} />
    <path class="awface" d=${arch(x, y, w)} />
    ${fog ? html`<text class="aq" x=${x} y=${y + 7} text-anchor="middle">?</text>`
      : html`<svg class="aicon" x=${x - 10} y=${y - 9} width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">${kindPaths(n.kind)}</svg>`}
    ${n.state === 'done' ? html`<g class="adone"><circle cx=${x + w - 2} cy=${y - 9} r="6" /><path d=${`M${x + w - 5} ${y - 9}l2.2 2.4 4-4.6`} /></g>` : null}
    ${n.state === 'current' ? html`<path class="ayou" d=${`M${x - 7} ${y - 31}h14l-7 9z`} />` : null}
    ${named ? html`<text class="aname" x=${x} y=${y + 33} text-anchor="middle">${stopName(n.kind)}</text>` : null}`;
  if (!live) return html`<g class=${cls}>${body}</g>`;
  const pick = () => onPick(n.card);
  return html`<g class=${cls} role="button" tabindex="0" aria-label=${label} aria-pressed=${selected}
    onClick=${pick} onKeyDown=${(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); } }}
    onMouseEnter=${() => onHover(true)} onMouseLeave=${() => onHover(false)} onFocus=${() => onHover(true)} onBlur=${() => onHover(false)}>${body}</g>`;
}

/** A stop the party can step to, as a card: the modifier's picture and name, the room it opens and the payout. Hovering or choosing it lights its window on the map. */
function Option({ n, choice, selected, chosen, hovered, enabled, onPick, onHover }) {
  const c = choice, art = c ? artOf(c.theme, c.kind, c.title) : null;
  const cls = `aopt ${KIND(n.kind)}${selected ? ' sel' : ''}${chosen ? ' chosen' : ''}${hovered ? ' hov' : ''}`;
  return html`<button class=${cls} disabled=${!enabled} aria-pressed=${selected} onClick=${() => onPick(n.card)}
    onMouseEnter=${() => onHover(true)} onMouseLeave=${() => onHover(false)} onFocus=${() => onHover(true)} onBlur=${() => onHover(false)}
    aria-label=${stopName(n.kind) + (c ? ': ' + c.title : '')}>
    <span class="dkindbox">${c ? html`<${ModThumb} art=${art} />` : html`<${KindIcon} kind=${n.kind} />`}
      ${c && art.cat !== 'ROOM' ? html`<span class="dkindchip"><${KindIcon} kind=${n.kind} /></span>` : null}</span>
    <span class="dstoptext"><b>${c ? c.title : stopName(n.kind)}</b><small>${stopName(n.kind)}${c && c.payout ? ' \u00b7 ' + c.payout : ''}${c && c.votes ? ' \u00b7 ' + c.votes + (c.votes === 1 ? ' vote' : ' votes') : ''}</small></span>
  </button>`;
}

/** Cobweb in a corner, for the Hollow's Eve skin. */
function Web({ x, y, sx, sy }) {
  const rays = [0, 1, 2, 3, 4].map((i) => `M${x} ${y}L${x + sx * 80 * Math.cos((i * Math.PI) / 8)} ${y + sy * 80 * Math.sin((i * Math.PI) / 8)}`).join('');
  let rings = '';
  for (let k = 1; k <= 4; k++) {
    const r = k * 18;
    let d = `M${x + sx * r} ${y}`;
    for (let i = 1; i <= 4; i++) d += `Q${x + sx * r * 0.82 * Math.cos(((i - 0.5) * Math.PI) / 8)} ${y + sy * r * 0.82 * Math.sin(((i - 0.5) * Math.PI) / 8)} ${x + sx * r * Math.cos((i * Math.PI) / 8)} ${y + sy * r * Math.sin((i * Math.PI) / 8)}`;
    rings += d;
  }
  return html`<path class="aweb" d=${rays + rings} />`;
}

/**
 * @param {{route: object, choices: object[], selected: number, chosen: number, enabled: boolean, skin: string, onPick: Function, onHover: Function}} props
 */
export function Atlas({ route, choices, selected, chosen, hovered, enabled, skin, onPick, onHover }) {
  const map = atlas(route.graph, route.now);
  const rows = map.rows, h = atlasHeight(rows);
  const reach = map.nodes.filter((n) => n.state === 'reach' && n.card >= 0).sort((a, b) => a.lane - b.lane);
  const titleOf = (card) => (card >= 0 && choices[card] ? choices[card].title : '');
  // the legend names what the party can see on the map, once each
  const seen = [...new Set(map.nodes.filter((n) => n.state !== 'fog' && n.kind !== 'START').map((n) => n.kind))];
  return html`<div class=${'datlas ' + (skin || 'stone')} role="group" aria-label="Choose the next stop on your route">
    <div class="aleft">
      <div class="aopts">${reach.map((n) => html`<${Option} key=${n.id} n=${n} choice=${choices[n.card] || null} selected=${n.card === selected} chosen=${n.card === chosen}
        hovered=${n.card === hovered} enabled=${enabled} onPick=${onPick} onHover=${(on) => onHover(on ? n.card : -1)} />`)}</div>
      <ul class="alegend" aria-label="Rooms on the map">${seen.map((kind) => html`<li key=${kind} class=${KIND(kind)}><svg viewBox="0 0 24 24" aria-hidden="true">${kindPaths(kind)}</svg>${stopName(kind)}</li>`)}</ul>
    </div>
    <svg viewBox=${`0 0 ${W} ${h}`} preserveAspectRatio="xMidYMid meet">
      <defs><pattern id="abricks" width="36" height="18" patternUnits="userSpaceOnUse"><rect class="abrick-a" width="36" height="18" /><path class="abrick-l" d="M0 .5H36M0 9.5H36M18 .5V9.5M0 9.5V18M36 9.5V18" fill="none" stroke-width="1.2" /></pattern></defs>
      <rect width=${W} height=${h} fill="url(#abricks)" />
      ${[0, 1, 2].map((lane) => html`<rect key=${lane} class="ashaft" x=${laneX(lane) - 30} y=${rowY(rows, rows) - 28} width="60" height=${rowY(0, rows) - rowY(rows, rows) + 56} />`)}
      ${Array.from({ length: rows + 1 }, (_, row) => html`<g key=${row} class="aslabg"><rect class="aslab" x="0" y=${rowY(row, rows) + 30} width=${W} height="7" /><text class="afloor" x="8" y=${rowY(row, rows) + 25}>${row === 0 ? 'GND' : 'F' + row}</text></g>`)}
      ${skin === 'hollow' ? html`<${Web} x=${0} y=${0} sx=${1} sy=${1} /><${Web} x=${W} y=${0} sx=${-1} sy=${1} />` : null}
      ${map.edges.map((e, i) => {
        const x1 = laneX(e.from), y1 = rowY(e.row, rows), x2 = laneX(e.to), y2 = rowY(e.row + 1, rows);
        return html`<g key=${i} class=${'aedge st-' + e.state}><line class="abg" x1=${x1} y1=${y1} x2=${x2} y2=${y2} /><line class="apipe" x1=${x1} y1=${y1} x2=${x2} y2=${y2} /></g>`;
      })}
      ${map.nodes.map((n) => html`<${Window} key=${n.id} n=${n} rows=${rows} selected=${n.card >= 0 && n.card === selected} chosen=${n.card >= 0 && n.card === chosen} hovered=${n.card >= 0 && n.card === hovered}
        enabled=${enabled} title=${titleOf(n.card)} onPick=${onPick} onHover=${(on) => onHover(on ? n.card : -1)} />`)}
    </svg>
  </div>`;
}
