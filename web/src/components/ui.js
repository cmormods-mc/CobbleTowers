import { html } from '../html.js';
import { ATLAS } from '../gen/atlas.js';

/** A partner sprite from the shared sheet. `s` scales it (the sprites are drawn at 1x = their own pixels). */
export function Sprite({ name, s = 1, class: cls = '', at: centre }) {
  const e = ATLAS.map[name];
  if (!e) return html`<span class=${cls} style="font-size:20px">?</span>`;
  const [x, y, w, h, bx, by, bw, bh] = e;
  // `at` puts the middle of the drawn pixels (not of the canvas) on that point
  const pos = centre ? `;left:${(centre[0] - (bx + bw / 2) * s).toFixed(2)}px;top:${(centre[1] - (by + bh / 2) * s).toFixed(2)}px` : '';
  return html`<span class=${'sprite ' + cls} style=${`--x:${x};--y:${y};--w:${w};--h:${h};--aw:${ATLAS.width};--ah:${ATLAS.height};--s:${s}${pos}`}></span>`;
}

export const Coin = ({ big }) => html`<i class=${'coin' + (big ? ' big' : '')}></i>`;

export const Chip = ({ color, children }) => html`<span class="chip" style=${`--c:${color}`}>${children}</span>`;

export function Button({ children, onClick, disabled, kind = '', small, class: cls = '' }) {
  const c = ['btn', kind, small ? 'sm' : '', cls].filter(Boolean).join(' ');
  return html`<button class=${c} disabled=${disabled} onClick=${onClick}>${children}</button>`;
}

export function Toast({ notice }) {
  // Keyed on the notice id so each message replays the slide-in.
  return html`<div class=${'toast' + (notice ? ' show' : '')}>${notice ? html`<div key=${notice.id} class="nine dark" style="font-size:18px">${notice.text}</div>` : null}</div>`;
}
