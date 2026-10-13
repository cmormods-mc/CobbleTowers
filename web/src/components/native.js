// The native screens' chrome (TowerMenuScreen, PixelUi, TowerButton) rebuilt in the page. Everything is placed in the mod's own GUI pixels
// on a 427x240 screen at scale 3, so a page lines up with the native screen it replaces.
import { html } from '../html.js';
import { ICONS } from '../gen/icons.js';

export const G = 3;           // CSS px per GUI pixel
export const SW = 427, SH = 240;

/** Position and size in GUI pixels. */
export const at = (x, y, w, h) => `left:${x * G}px;top:${y * G}px;` + (w != null ? `width:${w * G}px;` : '') + (h != null ? `height:${h * G}px;` : '');
const border = (w, h) => `--b:${Math.max(1, Math.min(8, Math.floor(Math.min(w, h) / 2))) * G}px;`;

export function Icon({ name, x, y, size = 16 }) {
  const i = Math.max(0, ICONS.indexOf(name));
  const k = (size * G) / 16;
  return html`<i class="ic" style=${`left:${x * G}px;top:${y * G}px;width:${size * G}px;height:${size * G}px;background-size:${ICONS.length * 16 * k}px ${16 * k}px;background-position:${-i * 16 * k}px 0`}></i>`;
}

/** A framed box: kind is dark | parch. */
export const Panel = ({ x, y, w, h, kind = 'dark', class: cls = '', children }) =>
  html`<div class=${'n-fr n-' + kind + ' ' + cls} style=${at(x, y, w, h) + border(w, h)}>${children}</div>`;

/** Text placed at a GUI position; `w` clips it. */
export const Label = ({ x, y, w, color = 'text', center, children, class: cls = '' }) =>
  html`<div class=${'n-t c-' + color + (center ? ' ctr' : '') + ' ' + cls} style=${at(x, y, w, 9)}>${children}</div>`;

/**
 * TowerButton. kind: dark | primary | tab. A tab (the page you are on) is not clickable. An icon shows beside the label only when
 * the whole label still fits, as in the mod.
 */
export function NB({ x, y, w, h = 20, kind = 'primary', icon, disabled, onClick, children, label, tip }) {
  const cls = ['n-btn', 'n-fr', 'k-' + (disabled && kind !== 'tab' ? 'off' : kind), disabled ? 'off' : ''].join(' ');
  const showIcon = icon && h >= 18 && (label || '').length * 5 + 26 <= w;
  return html`<button class=${cls} title=${tip || ''} disabled=${disabled || kind === 'tab'} onClick=${onClick} style=${at(x, y, w, h) + border(w, h)}>
    ${showIcon ? html`<${Icon} name=${icon} x=${-4} y=${(h - 16) / 2 - 8} />` : null}
    <span class=${showIcon ? 'lb' : 'lb clip'} style=${showIcon ? `margin-left:${20 * G}px` : ''}>${children}</span></button>`;
}

const TABS = [['Tower Hall', 'tower', 'towers'], ['Trials', 'hourglass', 'trials'], ['Codex', 'book', null], ['Progress', 'stairs', 'progress'],
  ['Echoes', 'echo', null], ['Social', 'group', 'social'], ['Collection', 'cards', null]];

/** Header, tab rail and parchment sheet; `children` are the content, positioned in GUI pixels. */
export function Shell({ section, tab, onTab, onBack, onClose, children }) {
  return html`
    <div class="ns">
      <${Panel} x=${6} y=${6} w=${SW - 12} h=${31} />
      <${Label} x=${15} y=${12} color="bronze">COBBLE TOWERS<//>
      <${Label} x=${15} y=${25} color="muted">${section}<//>
      <${NB} x=${SW - 100} y=${10} w=${42} kind="dark" onClick=${onBack}>Back<//>
      <${NB} x=${SW - 54} y=${10} w=${42} kind="dark" onClick=${onClose}>Close<//>
      <${Panel} x=${6} y=${40} w=${94} h=${SH - 46} />
      ${TABS.map(([label, icon, id], i) => html`<${NB} key=${label} x=${9} y=${43 + i * 22} w=${86} kind=${id && id === tab && section === label ? 'tab' : 'dark'}
        icon=${icon} label=${label} disabled=${!id} tip=${id ? '' : 'Coming soon'} onClick=${() => onTab(id)}>${label}<//>`)}
      <${NB} x=${9} y=${SH - 30} w=${86} kind="dark" icon="gear" label="Settings" disabled tip="Coming soon">Settings<//>
      <${Panel} x=${103} y=${39} w=${317} h=${195} kind="parch" />
      ${children}
    </div>`;
}
