// The route between floors (P50 stage 1) on the Frontier Desk: the stops of this step as buttons, what scouting shows of the steps
// after it, and the lines a party can walk. Choosing a stop is voting its card; the page sends the same PICK_CARD as for a draft.
import { html } from '../html.js';
import { DeskPano, DeskSprite } from './DeskHall.js';
import { KindIcon } from './kindIcon.js';
import { ModThumb, artOf } from './modArt.js';
import { regionOf } from '../lobby.js';
import { arenaName, colCenters, colTitle, kindName, nodeY, routeMap } from './routeMap.js';

export { KindIcon };
const KIND_CLASS = (kind) => 'rk-' + String(kind).toLowerCase();

function Stop({ node, x, y, selected, chosen, enabled, onPick, onHover }) {
  const style = `left:${x}%;top:${y}%`;
  if (node.state === 'blocked') return html`<div class="dstop blocked" style=${style} title="Not reachable from where you stand"><span class="dkindname">—</span></div>`;
  if (node.state === 'milestone') return html`<div class="dstop ahead rk-relic" style=${style} title="A milestone pays a relic"><${KindIcon} kind="RELIC" /><span class="dkindname">Relic</span></div>`;
  if (node.state === 'ahead') return html`<div class=${'dstop ahead ' + KIND_CLASS(node.kind)} style=${style} title=${kindName(node.kind) + ' (scouted)'}><${KindIcon} kind=${node.kind} /><span class="dkindname">${kindName(node.kind)}</span></div>`;
  const c = node.choice;
  return html`<button class=${'dstop now ' + KIND_CLASS(node.kind) + (selected ? ' sel' : '') + (chosen ? ' chosen' : '')} style=${style} disabled=${!enabled} aria-pressed=${selected}
    onClick=${onPick} onMouseEnter=${() => onHover(true)} onMouseLeave=${() => onHover(false)} onFocus=${() => onHover(true)} onBlur=${() => onHover(false)}
    aria-label=${kindName(node.kind) + (c ? ': ' + c.title : '')}>
    <span class="dkindbox">${c ? html`<${ModThumb} art=${artOf(c.theme, c.kind, c.title)} />` : html`<${KindIcon} kind=${node.kind} />`}
      ${c && artOf(c.theme, c.kind, c.title).cat !== 'ROOM' ? html`<span class="dkindchip"><${KindIcon} kind=${node.kind} /></span>` : null}</span>
    <span class="dstoptext"><b>${c ? c.title : ''}</b><small>${kindName(node.kind)}${c && c.payout ? ' · ' + c.payout : ''}${c && c.votes ? ' · ' + c.votes + (c.votes === 1 ? ' vote' : ' votes') : ''}${node.arena ? ' · in ' + arenaName(node.arena) : ''}</small></span>
    ${node.arena ? html`<span class="darena" title=${'Fought in ' + arenaName(node.arena)}><${DeskPano} region=${regionOf(node.arena)} /></span>` : null}
  </button>`;
}

/**
 * @param {{route: object, choices: object[], selected: number, chosen: number, enabled: boolean, lead: string, onPick: Function, onHover: Function}} props
 */
export function RouteMap({ route, choices, selected, chosen, enabled, lead, onPick, onHover }) {
  const map = routeMap(route, choices);
  const centers = colCenters(map.cols.length);
  const at = (col, lane) => [centers[col], nodeY(lane)];
  const you = map.lane >= 0 ? map.lane : 1;
  return html`<div class="droute" role="group" aria-label="Choose the next stop on your route">
    <div class="dyou" style=${`top:${nodeY(you)}%`} title="Your party"><${DeskSprite} name=${lead} w=${5} /></div>
    <div class="droutein">
    <div class="dheads">${map.cols.map((col, i) => html`<span key=${i} class=${'dhead' + (i === 0 ? ' now' : '')} style=${`left:${centers[i]}%`}>${colTitle(col, i)}</span>`)}</div>
    <svg class="dedges" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">${map.edges.map((e, i) => {
      const [x1, y1] = at(e.from, e.fromLane), [x2, y2] = at(e.to, e.toLane), mid = (x1 + x2) / 2;
      return html`<path key=${i} class=${e.active ? 'on' : ''} d=${`M${x1} ${y1} C${mid} ${y1} ${mid} ${y2} ${x2} ${y2}`} vector-effect="non-scaling-stroke" />`;
    })}</svg>
    ${map.cols.map((col, ci) => col.nodes.map((node) => {
      const [x, y] = at(ci, node.lane);
      return html`<${Stop} key=${ci + ':' + node.lane} node=${node} x=${x} y=${y} selected=${node.card >= 0 && node.card === selected} chosen=${node.card >= 0 && node.card === chosen}
        enabled=${enabled} onPick=${() => onPick(node.card)} onHover=${(on) => onHover(on ? node.card : -1)} />`;
    }))}
    </div>
  </div>`;
}
