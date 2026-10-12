// The whole page state as a pure function of what the server sent. No DOM, no timers: unit-tested in test/reducer.test.js.
// The rules mirror docs/design/webgui-bridge.md ("What a payload does when it arrives").

export const initial = Object.freeze({
  screen: null,      // 'intermission' | 'draft' | 'hall' | null (nothing to show yet)
  inter: null, interAt: 0,
  draft: null,
  reward: null,      // shown over any screen until dismissed
  vendor: null,      // the vendor catalog; vendorOpen says whether it is showing
  vendorOpen: false,
  fx: null,          // RunEffects, for the HUD page
  hall: null,        // TowerHallState
  play: null,        // PlayState (also carried inside TowerHallState)
  view: 'hall',      // inside the hall screen: 'hall' | 'tower' | 'trial' | 'lobby'
  tab: 'towers',     // hall tab: 'towers' | 'trials' | 'social' | 'progress'
  pick: '',          // the tower id or trial kind a briefing shows
  stack: [],         // views to go back to
  busyAt: 0,         // when a hall request went out and was not answered yet (0 = none)
  notice: null,      // {text, id}: the last message the server attached to a payload
  noticeId: 0,
});

function withNotice(state, text) {
  if (!text) return state;
  return { ...state, notice: { text, id: state.noticeId + 1 }, noticeId: state.noticeId + 1 };
}

/** @param {object} state @param {string} kind the payload kind @param {object} data @param {number} now ms */
export function reduce(state, kind, data, now) {
  switch (kind) {
    case 'IntermissionState': {
      const screen = data.open || state.screen === 'intermission' ? 'intermission' : state.screen;
      return { ...state, inter: data, interAt: now, screen };
    }
    case 'RentalDraft':
      return withNotice({ ...state, draft: data, screen: 'draft' }, data.message);
    case 'RewardReveal':
      return { ...state, reward: data, vendorOpen: false };
    case 'VendorCatalog':
      return withNotice({ ...state, vendor: data, vendorOpen: !state.reward }, data.message);
    case 'TowerHallState':
      return withNotice({ ...state, hall: data, play: data.play, screen: 'hall', busyAt: 0, view: state.screen === 'hall' ? state.view : 'hall', stack: state.screen === 'hall' ? state.stack : [] }, data.play.message);
    case 'PlayState': {
      // Only a flagged state opens the lobby; an unflagged one just refreshes it.
      const base = { ...state, play: data, busyAt: 0 };
      return withNotice(data.open ? { ...showLobby(base) } : base, data.message);
    }
    case 'RunEffects':
      return { ...state, fx: data };
    default:
      return state;
  }
}

/** The player dismissed the reward reveal. */
export const closeReward = (state) => ({ ...state, reward: null });
export const openVendor = (state, open) => ({ ...state, vendorOpen: open });

function showLobby(state) {
  if (state.screen === 'hall' && state.view === 'lobby') return state;
  const stack = state.screen === 'hall' ? [...state.stack, state.view] : [];
  return { ...state, screen: 'hall', view: 'lobby', stack };
}

/** Moves inside the hall screen. `pick` names the tower or trial a briefing shows. */
export const goTo = (state, view, pick = '') => ({ ...state, view, pick, stack: [...state.stack, state.view] });
export const goBack = (state) => (state.stack.length
  ? { ...state, view: state.stack[state.stack.length - 1], stack: state.stack.slice(0, -1) } : { ...state, view: 'hall' });
export const setTab = (state, tab) => ({ ...state, tab });
/** A hall request went out; the next hall or lobby state (or the timeout) clears it. */
export const begin = (state, now) => (state.busyAt ? state : { ...state, busyAt: now });
export const timeout = (state) => (state.busyAt ? { ...state, busyAt: 0, notice: { text: 'No response. Refresh to check the latest state.', id: state.noticeId + 1 }, noticeId: state.noticeId + 1 } : state);
