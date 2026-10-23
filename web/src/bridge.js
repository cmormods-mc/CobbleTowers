// The page's side of the CobbleTowers <-> WebGUI bridge (docs/design/webgui-bridge.md).
// Inside the game: WebGUI's `tower` event in, `cobbletowers` channel out. In a plain browser: `?mock=Kind1,Kind2` replays fixtures.js.

const CHANNEL = 'cobbletowers';
const W = (window.webgui = window.webgui || {});

// WebGUI puts `assetsBase` / `isHud` on window.webgui before any page script runs; either is proof we are in the game.
const insideGame = () => typeof window.cefQuery === 'function' || typeof W.assetsBase === 'string' || typeof W.isHud === 'boolean';

export const isHud = (location.hash || '').indexOf('hud') === 1 || W.isHud === true;
export const inGame = insideGame();
/** Messages sent while not inside the game, for a test to read. */
export const sent = [];

function post(message) {
  message.channel = CHANNEL;
  if (typeof W.postToGame === 'function' && insideGame()) W.postToGame(message);
  else sent.push(message);
}

/**
 * Tells AscensionLib a full-screen page is up, so its overlay does not open over it (WebGUI's openHud destroys an open GUI page).
 * Nothing listens when AscensionLib is absent.
 */
function beatLib() {
  if (isHud || typeof W.postToGame !== 'function' || !insideGame()) return;
  W.postToGame({ channel: 'ascension_gui_beat' });
}

/** page -> server */
export function act(kind, fields) {
  post({ kind, ...fields });
}

export function myName() {
  return (W.client && W.client.username) || '';
}

export function closeGui() {
  if (typeof W.closeGui === 'function') W.closeGui();
}

let lastHeard = Date.now();

/** True when a page in the game has heard nothing from the server for `limit` ms (the server sends a Pong every few seconds). */
export const silentFor = (limit, now = Date.now()) => insideGame() && now - lastHeard > limit;

/** Calls `onChange(true|false)` when the page starts or stops being cut off from the server. */
export function watchServer(onChange, limit = 12000) {
  let was = false;
  setInterval(() => {
    const now = silentFor(limit) && document.visibilityState !== 'hidden';
    if (now !== was) { was = now; onChange(now); }
  }, 2000);
}

/** Starts listening. `onPayload(kind, data)` is called for every server payload. */
export function connect(onPayload) {
  const deliver = (envelope) => {
    if (!envelope || typeof envelope.kind !== 'string') return;
    lastHeard = Date.now();
    // The server sends every emit to every open page of the player (GUI and HUD), so the HUD takes only what is meant for it.
    if (isHud && envelope.kind !== 'SpectatorPanel') return;
    try { onPayload(envelope.kind, envelope.data); } catch (e) { console.error('[tower] handler for ' + envelope.kind, e); }
  };
  if (typeof W.on === 'function') W.on('tower', deliver);
  else window.addEventListener('webgui:tower', (e) => deliver(e.detail));

  // Say the page has loaded (the server replies with what it missed), then keep proving it is open: WebGUI gives the server
  // no "closed" event, so silence means closed.
  post({ kind: 'hello', hud: isHud });
  // A page closed with Esc may stay alive but hidden: it stops pinging (and says goodbye) while hidden, so the server can tell.
  setInterval(() => { if (document.visibilityState !== 'hidden') { post({ kind: 'ping', hud: isHud }); beatLib(); } }, 1500);
  beatLib();
  window.addEventListener('pagehide', () => post({ kind: 'bye', hud: isHud }));
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') post({ kind: 'bye', hud: isHud }); });

  const mock = /[?#&|]mock=([A-Za-z,]+)/.exec(decodeURIComponent(location.search + location.hash));
  if (mock && !insideGame()) replay(mock[1].split(','), deliver);
}

function replay(kinds, deliver) {
  import('./fixtures-loader.js').then(({ fixtures }) => {
    kinds.forEach((kind, i) => {
      if (!fixtures[kind]) { console.warn('[tower] no fixture for ' + kind); return; }
      setTimeout(() => deliver(JSON.parse(JSON.stringify(fixtures[kind]))), 30 + i * 80);
    });
  });
}
