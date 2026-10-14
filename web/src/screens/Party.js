import { useState } from 'preact/hooks';
import { html } from '../html.js';
import { act } from '../bridge.js';
import { update } from '../store.js';
import { goBack } from '../reducer.js';
import { hint, pageCount, perPage, rows } from '../party.js';
import { Label, NB, Panel, screen, useScreen } from '../components/native.js';

const play = (action, argument = '') => act('PlayAction', { action, argument });

/** RegistrationScreen: every Pokemon the player owns, tick up to the limit. */
export function Party({ reg }) {
  useScreen();
  const SW = screen.w, SH = screen.h;
  const [asked, setPage] = useState(0);
  const per = perPage(SH), pages = pageCount(reg, per), page = Math.min(asked, pages - 1);
  const left = Math.floor(SW / 2) - 120;
  const list = rows(reg, page, per);
  const bottom = Math.max(40 + list.length * 22 + 6, 40 + per * 22 + 6);
  // Back to the lobby locally; REFRESH makes the server resend the lobby state.
  const done = () => { update(goBack); play('REFRESH'); };
  return html`
    <div class="ns" style=${`--g:${screen.g}px`}>
      <${Panel} x=${Math.floor(SW / 2) - 127} y=${35} w=${254} h=${SH - 73} />
      <${Label} x=${0} y=${12} w=${SW} center>Choose your party<//>
      <${Label} x=${0} y=${26} w=${SW} center color="muted">${hint(reg)}<//>
      ${list.map((r, i) => html`<${NB} key=${r.id} x=${left} y=${40 + i * 22} w=${240} kind=${r.chosen ? 'primary' : 'dark'} onClick=${() => play('TOGGLE_POKEMON', r.id)}>${r.label}<//>`)}
      <${NB} x=${left} y=${bottom} w=${30} kind="dark" disabled=${page === 0} onClick=${() => setPage(page - 1)}>${'<'}<//>
      <${NB} x=${left + 36} y=${bottom} w=${80} kind="dark" onClick=${() => play('CLEAR_CHOICE')}>Clear<//>
      <${NB} x=${left + 124} y=${bottom} w=${80} onClick=${done}>Done<//>
      <${NB} x=${left + 210} y=${bottom} w=${30} kind="dark" disabled=${page >= pages - 1} onClick=${() => setPage(page + 1)}>${'>'}<//>
      <${Label} x=${0} y=${SH - 28} w=${SW} center color="muted">Page ${page + 1}/${pages}<//>
    </div>`;
}
