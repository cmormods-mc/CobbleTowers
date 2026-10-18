// The route between floors (P50 stage 1) on the Frontier Desk: the stops of this step as buttons, what scouting shows of the steps
// after it, and the lines a party can walk. Choosing a stop is voting its card; the page sends the same PICK_CARD as for a draft.
import { html } from '../html.js';
import { DeskPano, DeskSprite } from './DeskHall.js';
import { regionOf } from '../lobby.js';
import { arenaName, colCenters, colTitle, kindName, nodeY, routeMap } from './routeMap.js';

const KIND_CLASS = (kind) => 'rk-' + String(kind).toLowerCase();

/** A small drawing per kind of stop (the same shapes the mockups used), in the kind's colour. */
export function KindIcon({ kind }) {
  const paths = {
    BATTLE: html`<path d="M4 4l16 16M20 4L4 20" stroke="currentColor" stroke-width="3" fill="none" stroke-linecap="round" />`,
    FORGE: html`<path d="M3 7h18l-3 5h-3v3h3v3H6v-3h3v-3H6z" fill="currentColor" />`,
    REST: html`<path d="M16 3a9 9 0 1 0 5 12 7 7 0 0 1-5-12z" fill="currentColor" />`,
    SHRINE: html`<path d="M12 3l9 17H3z" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linejoin="round" /><circle cx="12" cy="14.5" r="2" fill="currentColor" />`,
    GAMBLER: html`<rect x="4" y="4" width="16" height="16" rx="3" fill="none" stroke="currentColor" stroke-width="2.5" /><circle cx="9" cy="9" r="1.6" fill="currentColor" /><circle cx="15" cy="9" r="1.6" fill="currentColor" /><circle cx="9" cy="15" r="1.6" fill="currentColor" /><circle cx="15" cy="15" r="1.6" fill="currentColor" />`,
    VAULT: html`<circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" stroke-width="2.5" /><path d="M12 7v10M9.5 10h4a1.5 1.5 0 0 1 0 3h-3a1.5 1.5 0 0 0 0 3h4" fill="none" stroke="currentColor" stroke-width="1.8" />`,
    LIBRARY: html`<path d="M3 5h8v14H3zM13 5h8v14h-8z" fill="currentColor" />`,
    POOL: html`<path d="M3 15c3-3 5 3 9 0s6 3 9 0M3 10c3-3 5 3 9 0s6 3 9 0" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />`,
    RELIC: html`<path d="M3 19V8l5 5 4-8 4 8 5-5v11z" fill="currentColor" />`,
  };
  return html`<svg class="dkind" viewBox="0 0 24 24" aria-hidden="true">${paths[kind] || paths.BATTLE}</svg>`;
}

function Stop({ node, x, y, selected, chosen, enabled, onPick, onHover }) {
  const style = `left:${x}%;top:${y}%`;
  if (node.state === 'blocked') return html`<div class="dstop blocked" style=${style} title="Not reachable from where you stand"><span class="dkindname">—</span></div>`;
  if (node.state === 'milestone') return html`<div class="dstop ahead rk-relic" style=${style} title="A milestone pays a relic"><${KindIcon} kind="RELIC" /><span class="dkindname">Relic</span></div>`;
  if (node.state === 'ahead') return html`<div class=${'dstop ahead ' + KIND_CLASS(node.kind)} style=${style} title=${kindName(node.kind) + ' (scouted)'}><${KindIcon} kind=${node.kind} /><span class="dkindname">${kindName(node.kind)}</span></div>`;
  const c = node.choice;
  return html`<button class=${'dstop now ' + KIND_CLASS(node.kind) + (selected ? ' sel' : '') + (chosen ? ' chosen' : '')} style=${style} disabled=${!enabled} aria-pressed=${selected}
    onClick=${onPick} onMouseEnter=${() => onHover(true)} onMouseLeave=${() => onHover(false)} onFocus=${() => onHover(true)} onBlur=${() => onHover(false)}
    aria-label=${kindName(node.kind) + (c ? ': ' + c.title : '')}>
    <span class="dkindbox"><${KindIcon} kind=${node.kind} /></span>
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
