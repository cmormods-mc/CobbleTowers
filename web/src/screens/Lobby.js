import { useState } from 'preact/hooks';
import { html } from '../html.js';
import { Button, Chip } from '../components/ui.js';
import { act } from '../bridge.js';
import { update } from '../store.js';
import { goBack } from '../reducer.js';
import { lobbyView, partyLine, regionOf } from '../lobby.js';
import { Banner } from './Hall.js';

const play = (action, argument = '') => act('PlayAction', { action, argument });

function Invite() {
  const [name, setName] = useState('');
  const send = () => { if (name.trim()) { play('INVITE', name.trim()); setName(''); } };
  return html`<div class="row"><input class="field grow" maxlength="16" placeholder="Player name" value=${name} onInput=${(e) => setName(e.target.value)}
    onKeyDown=${(e) => { e.stopPropagation(); if (e.key === 'Enter') send(); }} /><${Button} small onClick=${send}>Invite<//></div>`;
}

export function Lobby({ state }) {
  const lobby = state.lobby;
  const v = lobbyView(state);
  return html`
    <div class="row center">
      <${Button} small kind="sec" onClick=${() => update(goBack)}>Back<//>
      <div class="grow"><h1>Expedition Lobby</h1><div class="sub">Your party: ${partyLine(state.partyLevels)}</div></div>
    </div>
    <div class="row grow">
      <div class="col grow">
        <div class="nine dark grow scroll"><h3>Towers</h3>
          <div class="grid2" style="margin-top:8px">${state.towers.map((t) => html`
            <button key=${t.id} class=${'tile sm' + (t.id === lobby.selected ? ' sel' : '')} disabled=${!v.canChoose} onClick=${() => play('SELECT_TOWER', t.id)}>
              <${Banner} region=${regionOf(t.id)} /><div class="cap"><b>${t.displayName}</b>${t.id === lobby.selected ? html`<${Chip} color="var(--green)">SELECTED<//>` : null}</div></button>`)}</div>
        </div>
      </div>
      <div class="col" style="width:430px">
        <div class="nine dark" style="flex:none;min-height:150px">
          <h3>Team status</h3>
          ${v.team.length ? v.team.map((m) => html`<div class="eff"><span class="grow">${m.name}</span><${Chip} color=${m.tone === 'good' ? 'var(--green)' : 'var(--brown)'}>${m.status.toUpperCase()}<//></div>`)
            : html`<div class="sub">You are not in a lobby yet. Select a tower to start one.</div>`}
          ${v.waitingForHost ? html`<div class="sub" style="margin-top:6px">Waiting for the host to confirm the mode.</div>` : null}
        </div>
        ${v.invite || v.modes || v.depth ? html`<div class="nine dark grow scroll">
          ${v.invite ? html`<div ><${Invite} /></div>` : null}
          ${v.modes ? html`<div ><div class="sub">Mode</div><div class="wrap">${v.modes.ids.map((id, i) => html`
            <${Button} small kind=${id === v.modes.chosen ? 'on' : ''} disabled=${!v.modes.enabled} onClick=${() => play('SET_PLAYLIST', id)}>${v.modes.names[i]}<//>`)}</div></div>` : null}
          ${v.depth ? html`<div ><${Button} small kind="sec" disabled=${!v.depth.enabled} onClick=${() => play('SET_ASCENSION', String(v.depth.next))}>
            Start at: ${v.depth.label}${v.depth.none ? ' (none reached)' : ''}<//></div>` : null}
        </div>` : null}
        <div class="nine dark">
          <div class="col" style="gap:8px">
            ${v.confirm ? html`<${Button} kind="sec" onClick=${() => play('CONFIRM_MODE')}>Confirm mode<//>` : null}
            ${v.draft ? html`<div class="row"><${Button} class="grow" kind="sec" onClick=${() => act('RentalDraftAction', { action: 'OPEN', a: 0, b: 0 })}>Draft your team<//>
              <${Button} class="grow" kind="sec" disabled=${!v.ready.enabled} onClick=${() => play(v.ready.mine ? 'UNREADY' : 'READY')}>${v.ready.mine ? 'Unready' : 'Ready up'}<//></div>` : null}
            ${v.answer ? html`<div class="row"><${Button} class="grow" onClick=${() => play('ACCEPT', lobby.hostName)}>Accept<//><${Button} kind="sec" class="grow" onClick=${() => play('DECLINE', lobby.hostName)}>Decline<//></div>` : null}
            <div class="row">${v.start ? html`<${Button} class="grow" disabled=${!v.start.enabled} onClick=${() => play('START')}>${v.start.label}<//>` : null}
              ${v.leave ? html`<${Button} class="grow" kind="sec" onClick=${() => play('LEAVE')}>${v.leave}<//>` : null}</div>
          </div>
        </div>
      </div>
    </div>`;
}
