// The server's one-line messages in the Frontier Desk look (the native Toast is dark oak and would clash on the light pages).
import { html } from '../html.js';

/** @param {{notice: {text: string, id: number}|null}} props */
export function DeskToast({ notice }) {
  // Keyed on the notice id so each message replays the slide-in.
  return html`<div class=${'fd-toast' + (notice ? ' show' : '')} role="status" aria-live="polite">${notice ? html`<div key=${notice.id} class="fd-toast-box">${notice.text}</div>` : null}</div>`;
}
