// The whole page state as a pure function of what the server sent. No DOM, no timers: unit-tested in test/reducer.test.js.
// The rules mirror docs/design/webgui-bridge.md ("What a payload does when it arrives").

export const initial = Object.freeze({
  screen: null,      // 'intermission' | 'draft' | null (nothing to show yet)
  inter: null, interAt: 0,
  draft: null,
  reward: null,      // shown over any screen until dismissed
  vendor: null,      // the vendor catalog; vendorOpen says whether it is showing
  vendorOpen: false,
  fx: null,          // RunEffects, for the HUD page
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
    case 'RunEffects':
      return { ...state, fx: data };
    default:
      return state;
  }
}

/** The player dismissed the reward reveal. */
export const closeReward = (state) => ({ ...state, reward: null });
export const openVendor = (state, open) => ({ ...state, vendorOpen: open });
