import { useEffect } from 'preact/hooks';
import { html } from '../html.js';
import { closeGui } from '../bridge.js';
import { load } from '../settings.js';
import { DeskSpectator } from '../desk/DeskModals.js';

/** How long a spectator panel stays up with no update before it hides and the page closes. */
export const STALE_MS = 120_000;

/** The HUD page (opened with #hud): only the spectator panel, click-through. */
export function Hud({ spec, specAt }) {
  useEffect(() => {
    if (!spec) return undefined;
    const id = setTimeout(closeGui, Math.max(0, specAt + STALE_MS - Date.now()));
    return () => clearTimeout(id);
  }, [spec, specAt]);
  if (!spec) return null;
  if (load().look === 'desk') return html`<${DeskSpectator} spec=${spec} />`;
  return html`<div id="specbox" class="nine dark"><b class="gold">Following ${spec.teammateName}</b>
    <div>${spec.remainingCount}/${spec.totalCount} Pokemon remaining</div><div class="sub">Floor ${spec.floorIndex} - ${spec.runStateLabel}</div></div>`;
}
