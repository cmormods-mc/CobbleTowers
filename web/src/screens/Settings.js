import { useState } from 'preact/hooks';
import { html } from '../html.js';
import { apply, load, save } from '../settings.js';
import { box, Label, NB, Panel, at } from '../components/native.js';
import { update } from '../store.js';
import { setLook } from '../reducer.js';

const TABS = ['General', 'Audio', 'Keybinds'];
const NOTES = {
  General: 'Reduced motion keeps every choice available. The plain font uses your system font instead of the pixel font. The Play page and the screen between floors can use the new Frontier Desk look; the other pages are still native. What your run carries is on the Current Run tab.',
  Audio: 'Game volume remains in Minecraft sound settings. The tower pages make no sounds of their own.',
  Keybinds: 'Tower keys are set in Minecraft\'s controls screen, under Miscellaneous.',
};

/** TowerOptionsScreen: presentation settings, kept in this browser. */
export function Settings() {
  const b = box();
  const [tab, setTab] = useState('General');
  const [s, setS] = useState(load());
  const setLookTo = (look) => { const next = { ...s, look }; setS(next); save(next); update((st) => setLook(st, look)); };
  const flip = (key) => { const next = { ...s, [key]: !s[key] }; setS(next); save(next); apply(next); };
  const rail = 70;
  return html`
    ${TABS.map((name, i) => html`<${NB} key=${name} x=${b.x} y=${b.y + 4 + i * 24} w=${rail} kind=${tab === name ? 'tab' : 'dark'} onClick=${() => setTab(name)}>${name}<//>`)}
    <${Panel} x=${b.x + rail + 6} y=${b.y + 4} w=${b.w - rail - 6} h=${b.bottom - b.y - 4} kind="dark" />
    ${tab === 'General' ? html`
      <${NB} x=${b.x + rail + 14} y=${b.y + 14} w=${b.w - rail - 22} kind=${s.motion ? 'primary' : 'dark'} onClick=${() => flip('motion')}>Animations: ${s.motion ? 'On' : 'Off'}<//>
      <${NB} x=${b.x + rail + 14} y=${b.y + 40} w=${b.w - rail - 22} kind=${s.pixelFont ? 'primary' : 'dark'} onClick=${() => flip('pixelFont')}>Pixel font: ${s.pixelFont ? 'On' : 'Off'}<//>
      <${NB} x=${b.x + rail + 14} y=${b.y + 66} w=${b.w - rail - 22} kind=${s.look === 'desk' ? 'primary' : 'dark'} onClick=${() => setLookTo(s.look === 'desk' ? 'native' : 'desk')}>Look: ${s.look === 'desk' ? 'Frontier Desk' : 'Native'}<//>` : null}
    <div class="n-lines lit" style=${at(b.x + rail + 14, b.y + (tab === 'General' ? 96 : 16), b.w - rail - 22, 80)}>${NOTES[tab]}</div>`;
}
