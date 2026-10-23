// The wordmark with the tower medallion in front of it: the same mark on every screen.
import { html } from '../html.js';
import { Emblem } from './emblem.js';

export function Logo() {
  return html`<span class="logo"><${Emblem} tower="neutral" w=${2.9} /><span>COBBLE <b>TOWERS</b></span></span>`;
}
