// The native screens' chrome (TowerMenuScreen, PixelUi, TowerButton) rebuilt in the page. Everything is placed in the mod's own GUI pixels
// on a 427x240 screen at scale 3, so a page lines up with the native screen it replaces.
import { useEffect, useState } from 'preact/hooks';
import { html } from '../html.js';
import { ICONS } from '../gen/icons.js';
import { ITEMS } from '../gen/items.js';
import { itemPicture } from '../itemart.js';

// The screen is as big as the window, in whole GUI scales like Minecraft's own "auto": the largest scale that leaves at least 320x240 GUI pixels.
export const screen = { w: 427, h: 240, g: 3 };
export function measure() {
  const g = Math.max(1, Math.min(Math.floor(window.innerWidth / 320), Math.floor(window.innerHeight / 240)));
  screen.g = g; screen.w = Math.ceil(window.innerWidth / g); screen.h = Math.ceil(window.innerHeight / g);
}
/** Call from a component that draws a native screen: keeps `screen` current and re-renders on resize. */
export function useScreen() {
  const [, tick] = useState(0);
  measure();
  useEffect(() => {
    const on = () => tick((n) => n + 1);
    window.addEventListener('resize', on);
    return () => window.removeEventListener('resize', on);
  }, []);
  return screen;
}

const u = (n) => `calc(var(--g) * ${n})`;
/** Position and size in GUI pixels. */
export const at = (x, y, w, h) => (x != null ? `left:${u(x)};` : '') + (y != null ? `top:${u(y)};` : '') + (w != null ? `width:${u(w)};` : '') + (h != null ? `height:${u(h)};` : '');
const border = (w, h) => `--b:${u(Math.max(1, Math.min(8, Math.floor(Math.min(w, h) / 2))))};`;

export function Icon({ name, x, y, size = 16 }) {
  const i = Math.max(0, ICONS.indexOf(name));
  const k = size / 16;
  return html`<i class="ic" style=${`left:${u(x)};top:${u(y)};width:${u(size)};height:${u(size)};background-size:${u(ICONS.length * 16 * k)} ${u(16 * k)};background-position:${u(-i * 16 * k)} 0`}></i>`;
}

/** The mod's own item picture, else a shape and colour matched to the item's name, else a gift. `size` is in GUI pixels. */
export function ItemIcon({ id, x, y, size = 16 }) {
  const e = ITEMS.map[id];
  if (!e) return html`<${Icon} name=${itemPicture(id) || 'gift'} x=${x} y=${y} size=${size} />`;
  const k = size / 16;
  return html`<i class="ic item" style=${`left:${u(x)};top:${u(y)};width:${u(size)};height:${u(size)};background-size:${u(ITEMS.width * k)} ${u(ITEMS.height * k)};background-position:${u(-e[0] * k)} ${u(-e[1] * k)}`}></i>`;
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
export function NB({ x, y, w, h = 20, kind = 'primary', icon, iconOnly, disabled, onClick, children, label, tip, flow }) {
  const cls = ['n-btn', 'n-fr', 'k-' + (disabled && kind !== 'tab' ? 'off' : kind), disabled ? 'off' : '', flow ? 'flow' : ''].join(' ');
  const showIcon = icon && h >= 18 && (label || '').length * 5 + 26 <= w;
  return html`<button class=${cls} title=${tip || ''} disabled=${disabled || kind === 'tab'} onClick=${onClick} style=${at(x, y, w, h) + border(w, h)}>
    ${iconOnly ? html`<${Icon} name=${iconOnly} x=${(w - 16) / 2 - Math.min(8, Math.floor(Math.min(w, h) / 2))} y=${(h - 16) / 2 - Math.min(8, Math.floor(Math.min(w, h) / 2))} />` : null}
    ${showIcon ? html`<${Icon} name=${icon} x=${-4} y=${(h - 16) / 2 - 8} />` : null}
    <span class=${showIcon ? 'lb' : 'lb clip'} style=${showIcon ? `margin-left:${u(20)}` : ''}>${children}</span></button>`;
}

const TABS = [['Tower Hall', 'tower', 'towers'], ['Trials', 'hourglass', 'trials'], ['Current Run', 'flag', 'run'], ['Codex', 'book', 'codex'], ['Progress', 'stairs', 'progress'],
  ['Echoes', 'echo', 'echoes'], ['Social', 'group', 'social'], ['Collection', 'cards', 'collection']];

/** Header, tab rail and parchment sheet; `children` are the content, positioned in GUI pixels. */
export function Shell({ section, tab, active, onTab, onBack, onClose, onSettings, children }) {
  const SW = screen.w, SH = screen.h, rail = SW < 400 ? 80 : 96;
  return html`
    <div class="ns" style=${`--g:${screen.g}px`}>
      <${Panel} x=${6} y=${6} w=${SW - 12} h=${31} />
      <${Label} x=${15} y=${12} color="bronze">COBBLE TOWERS<//>
      <${Label} x=${15} y=${25} color="muted">${section}<//>
      <${NB} x=${SW - 100} y=${10} w=${42} kind="dark" onClick=${onBack}>Back<//>
      <${NB} x=${SW - 54} y=${10} w=${42} kind="dark" onClick=${onClose}>Close<//>
      <${Panel} x=${6} y=${40} w=${rail - 2} h=${SH - 46} />
      ${TABS.map(([label, icon, id], i) => html`<${NB} key=${label} x=${9} y=${43 + i * 20} h=${18} w=${rail - 10} kind=${id && (active !== undefined ? id === active : id === tab && section === label) ? 'tab' : 'dark'}
        icon=${icon} label=${label} disabled=${!id} tip=${id ? '' : 'Coming soon'} onClick=${() => onTab(id)}>${label}<//>`)}
      <${NB} x=${9} y=${SH - 30} w=${rail - 10} kind=${section === 'Settings' ? 'tab' : 'dark'} icon="gear" label="Settings" onClick=${onSettings}>Settings<//>
      <${Panel} x=${rail + 7} y=${39} w=${SW - rail - 14} h=${SH - 45} kind="parch" />
      ${children}
    </div>`;
}

/** TowerMenuScreen's content box and the rows below it, for the current screen size. */
export function box() {
  const x = (screen.w < 400 ? 80 : 96) + 12, y = 43, w = screen.w - x - 12, contentH = screen.h - y - 58;
  const rows = Math.max(1, Math.min(2, Math.floor((contentH - 75) / 52)));
  return { x, y, w, rows, perPage: rows * 2, rowH: Math.max(38, Math.floor((contentH - 75) / rows)), bottom: screen.h - 64, status: screen.h - 40, foot: screen.h - 29 };
}

/** TrainerCardButton: a dark card with a title, a caption and a picture. */
export function Card({ x, y, w, h, title, sub, icon, onClick, soon }) {
  return html`<button class=${'n-btn n-fr n-dark card' + (soon ? ' soon' : '')} disabled=${soon} title=${soon ? 'Coming soon' : ''} style=${at(x, y, w, h)} onClick=${onClick}>
    <${Label} x=${-2} y=${-2} w=${w - 30} color="text">${title}<//>
    ${soon || sub ? html`<${Label} x=${-2} y=${11} w=${w - 30} color="muted">${soon ? 'Coming soon' : sub}<//>` : null}
    <${Icon} name=${icon} x=${w - 30} y=${h / 2 - 16} /><i class="brk"></i></button>`;
}

