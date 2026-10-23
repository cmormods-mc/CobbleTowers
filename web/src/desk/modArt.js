// The picture on a modifier card (and its small version on the route map). The server names what a modifier does with a theme key
// (ModifierArt: "weather:raindance", "enemy:tough", "reward_up:hoard" ...); this turns the key into a scene: sky and ground colours, one
// big glyph, and a Pokemon standing in it. Art never implies an effect the modifier does not have: the key says only what it does.
import { useEffect, useRef } from 'preact/hooks';
import { html } from '../html.js';
import { DeskSprite } from './DeskHall.js';
import { KindIcon } from './kindIcon.js';
import { GLYPHS, Pix, boxOf } from './pix.js';
import { artOf, paintScene, roomKindOf } from './modArtData.js';

export { artOf, paintScene, roomKindOf };

const SCENES = new Map();
function sceneData(art, w, h) {
  const key = art.look.join() + art.overlay + w + 'x' + h;
  let d = SCENES.get(key);
  if (!d) { d = new ImageData(paintScene(art, w, h), w, h); SCENES.set(key, d); }
  return d;
}

/** The picture as a card shows it: scene, glyph, and the Pokemon. `wide` is the banner shape of the details panel. */
export function ModArt({ art, wide = false }) {
  const ref = useRef(null);
  const w = wide ? 120 : 96, h = wide ? 40 : 56;
  useEffect(() => { ref.current.getContext('2d').putImageData(sceneData(art, w, h), 0, 0); }, [art.lookKey, art.overlay, w, h]);
  return html`<div class=${'dart' + (wide ? ' wide' : '') + (art.at === 'ground' ? ' low' : '')} style=${`--k:${art.k}`}>
    <canvas ref=${ref} width=${w} height=${h}></canvas>
    ${art.glyph ? html`<span class=${'dglyph' + (art.flip ? ' flip' : '')}><${Pix} name=${art.glyph} w=${boxOf(GLYPHS[art.glyph])[0] * (wide ? 0.42 : 0.52)} slash=${art.slash} /></span>`
      : art.room ? html`<span class="dglyph room"><${KindIcon} kind=${art.room} /></span>` : null}
    ${art.sprite ? html`<span class="dsprite"><${DeskSprite} name=${art.sprite} w=${wide ? 6.4 : 7.6} /></span>` : null}
    <span class="dcat">${art.cat}</span></div>`;
}

/** A square thumbnail of a modifier for the route map: its sky and its glyph, drawn with CSS only. */
export function ModThumb({ art }) {
  const [sky, low] = art.look;
  return html`<span class="dthumb" style=${`background:linear-gradient(${sky} 0 45%,${low} 45% 100%)`}>${art.glyph ? html`<${Pix} name=${art.glyph} w=${2.9} slash=${art.slash} />` : art.room ? html`<${KindIcon} kind=${art.room} />` : null}</span>`;
}

/** How risky a card is as gems: one, two or three lit (minor, moderate, severe); nothing for a room or a relic (risk below zero). */
export function RiskGems({ risk }) {
  if (risk < 0) return null;
  return html`<span class=${'dgems g' + risk} role="img" aria-label=${['Minor', 'Moderate', 'Severe'][risk] + ' risk'}>${[0, 1, 2].map((i) => html`<i key=${i} class=${i <= risk ? 'on' : ''}></i>`)}</span>`;
}
