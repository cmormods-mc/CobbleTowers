// A small drawing per kind of stop on the route (and per kind of room), in the colour the page gives it.
import { html } from '../html.js';

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
