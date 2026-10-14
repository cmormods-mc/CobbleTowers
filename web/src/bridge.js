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

/** Starts listening. `onPayload(kind, data)` is called for every server payload. */
export function connect(onPayload) {
  const deliver = (envelope) => {
    if (!envelope || typeof envelope.kind !== 'string') return;
    // The server sends every emit to every open page of the player (GUI and HUD), so the HUD takes only what is meant for it.
    if (isHud && envelope.kind !== 'SpectatorPanel') return;
    try { onPayload(envelope.kind, envelope.data); } catch (e) { console.error('[tower] handler for ' + envelope.kind, e); }
  };
  if (typeof W.on === 'function') W.on('tower', deliver);
  else window.addEventListener('webgui:tower', (e) => deliver(e.detail));

  // Say the page has loaded (the server replies with what it missed), then keep proving it is open: WebGUI gives the server
  // no "closed" event, so silence means closed.
  post({ kind: 'hello', hud: isHud });
  setInterval(() => post({ kind: 'ping', hud: isHud }), 1500);
  window.addEventListener('pagehide', () => post({ kind: 'bye', hud: isHud }));

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
