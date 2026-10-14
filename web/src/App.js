import { useEffect } from 'preact/hooks';
import { html } from './html.js';
import { useStore, update } from './store.js';
import { closeReward, openVendor } from './reducer.js';
import { act, isHud } from './bridge.js';
import { Toast } from './components/ui.js';
import { Intermission } from './screens/Intermission.js';
import { Draft } from './screens/Draft.js';
import { Hud } from './screens/Hud.js';
import { Hall, useBusyTimeout } from './screens/Hall.js';
import { Lobby } from './screens/Lobby.js';
import { Party } from './screens/Party.js';
import { Mastery } from './screens/Mastery.js';
import { Scouting } from './screens/Scouting.js';
import { RewardModal, VendorModal } from './screens/Modals.js';

/** Fits the 1280x720 stage to the window with a single scale. */
function useFit() {
  useEffect(() => {
    const fit = () => document.getElementById('stage')?.style.setProperty('--k', Math.min(window.innerWidth / 1280, window.innerHeight / 720));
    window.addEventListener('resize', fit);
    fit();
    return () => window.removeEventListener('resize', fit);
  }, []);
}

/** The server's one-line messages show for a few seconds. */
function useNoticeTimeout(notice, clear) {
  useEffect(() => {
    if (!notice) return undefined;
    const id = setTimeout(clear, 4000);
    return () => clearTimeout(id);
  }, [notice]);
}

function Waiting() {
  return html`<div class="grow" style="display:grid;place-items:center"><div class="nine dark" style="text-align:center"><h2>CobbleTowers</h2><div class="sub">Waiting for the tower…</div></div></div>`;
}

export function App() {
  const screen = useStore((s) => s.screen);
  const inter = useStore((s) => s.inter);
  const interAt = useStore((s) => s.interAt);
  const draft = useStore((s) => s.draft);
  const reward = useStore((s) => s.reward);
  const vendor = useStore((s) => s.vendor);
  const vendorOpen = useStore((s) => s.vendorOpen);
  const notice = useStore((s) => s.notice);
  const hall = useStore((s) => s.hall);
  const play = useStore((s) => s.play);
  const reg = useStore((s) => s.reg);
  const mast = useStore((s) => s.mast);
  const scout = useStore((s) => s.scout);
  const view = useStore((s) => s.view);
  const tab = useStore((s) => s.tab);
  const pick = useStore((s) => s.pick);
  const page = useStore((s) => s.page);
  const busyAt = useStore((s) => s.busyAt);
  useBusyTimeout(busyAt);
  useFit();
  const native = screen === 'scout' || screen === 'intermission' || screen === 'draft' || screen === 'hall' && (view === 'lobby' ? !!play : view === 'party' ? !!reg : view === 'mastery' ? !!mast : !!hall || view === 'feature');
  useEffect(() => { document.documentElement.classList.toggle('native', native); }, [native]);
  useNoticeTimeout(notice, () => update((s) => (s.notice === notice ? { ...s, notice: null } : s)));

  const closeRewardNow = () => { update(closeReward); act('IntermissionAction', { action: 'REFRESH', argument: 0 }); };

  return html`
    <div class="main">
      ${screen === 'scout' && scout ? html`<${Scouting} scout=${scout} />`
        : screen === 'intermission' && inter ? html`<${Intermission} inter=${inter} at=${interAt} />`
        : screen === 'draft' && draft ? html`<${Draft} draft=${draft} />`
        : screen === 'hall' && view === 'mastery' && mast ? html`<${Mastery} state=${mast} />`
        : screen === 'hall' && view === 'party' && reg ? html`<${Party} reg=${reg} />`
        : screen === 'hall' && view === 'lobby' && play ? html`<${Lobby} state=${play} />`
        : screen === 'hall' && (hall || view === 'feature') ? html`<${Hall} hall=${hall} tab=${tab} page=${page} view=${view} pick=${pick} busy=${!!busyAt} />`
        : html`<${Waiting} />`}
      ${reward ? html`<${RewardModal} reward=${reward} onClose=${closeRewardNow} />`
        : vendorOpen && vendor ? html`<${VendorModal} vendor=${vendor} onClose=${() => update((s) => openVendor(s, false))} />` : null}
      <${Toast} notice=${notice} />
    </div>`;
}

export function HudApp() {
  const spec = useStore((s) => s.spec);
  const specAt = useStore((s) => s.specAt);
  return html`<${Hud} spec=${spec} specAt=${specAt} />`;
}

export { isHud };
