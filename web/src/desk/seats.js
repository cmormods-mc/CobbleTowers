// Pure view model for the Frontier Desk lobby (P51 slice 3): the party's seats, from the lobby view and who is looking.

/** Most seats drawn even when nobody has been invited yet. */
export const MIN_SEATS = 4;

const PILL = { ready: 'g', drafting: 'y', invited: 'n', host: 'b', accepted: 'g' };

/**
 * @param {object} view lobbyView(play)
 * @param {string} me the viewer's name
 * @returns {{kind:'player'|'open', name?:string, host?:boolean, me?:boolean, status?:string, pill?:string}[]}
 */
export function seatsOf(view, me) {
  const seats = view.team.map((m) => ({ kind: 'player', name: m.name, host: m.host, me: m.name === me, status: m.status === 'host' ? (m.host ? 'host' : '') : m.status,
    pill: PILL[m.status] || 'n' }));
  while (seats.length < MIN_SEATS) seats.push({ kind: 'open' });
  return seats;
}

/** The Pokémon a teammate is bringing, from the server's rosters (none for you, for someone not on the team yet, or from an older server). */
export const rosterOf = (play, name) => (play.lobby.options.rosters ?? []).find((r) => r.name === name)?.team ?? [];

/** What a seat says under the name: the role, then the state. */
export function seatLine(seat) {
  if (seat.kind === 'open') return 'Open seat';
  const role = seat.host ? 'Host' : 'Guest';
  const state = seat.status && seat.status !== 'host' ? seat.status.charAt(0).toUpperCase() + seat.status.slice(1) : '';
  return state ? role + ' · ' + state : role;
}

/** The mode the lobby is on, by name: "Standard" when none is chosen. */
export function modeName(play) {
  const modes = play.lobby.options.modes;
  const i = modes.ids.indexOf(modes.chosen);
  return i >= 0 ? modes.names[i] : 'Standard';
}

/** The sentence under the Start button: why it is not ready, or nothing. */
export function startHint(view, play) {
  if (!view.start) return '';
  if (view.start.enabled) return 'Everything is ready.';
  if (play.lobby.countdown >= 0) return 'The countdown is running.';
  if (play.lobby.selected === '') return 'Choose a tower first.';
  if (!play.lobby.options.modes.readiness.confirmed) return 'Confirm the mode to continue.';
  return 'Waiting.';
}
