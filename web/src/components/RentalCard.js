import { html } from '../html.js';
import { Sprite } from './ui.js';
import { TYPES } from '../data/tables.js';
import { adaptCard } from '../cards.js';

const Gem = ({ type, cat }) => html`<i class="gem" style=${`--c:${Math.max(0, TYPES.indexOf(type))};--r:${cat}`}></i>`;

export { adaptCard };

/** One Byzantine rental card. `flip` plays the reveal; `pick` makes it a button; `kept` lifts and seals it. */
export function RentalCard({ card, cs = 2, flip, delay = 0, pick, kept, onClick }) {
  const face = html`
    <img class="f-field" src=${`assets/gui/byzantine/field_${card.rarity}.png`} alt="" />
    <${Sprite} class="f-sprite" name=${card.species} s=${cs * 1.5} at=${[56 * cs, 39 * cs]} />
    <img class="f-frame" src=${`assets/gui/byzantine/frame_${card.rarity}.png`} alt="" />
    <div class="t lv">Lv ${card.level}</div>
    <div class="t ty">${card.types.map((t) => html`<${Gem} type=${t} cat=${0} />`)}</div>
    <div class="t nm">${card.name}</div>
    <div class="t l1">${card.ability}</div><div class="t l2">${card.item}</div>
    <div class="t mv">${card.moves.map((m) => html`<div><${Gem} type=${m.type} cat=${m.cat} />${m.name}</div>`)}</div>
    ${kept ? html`<img class="kmark" src="assets/gui/byzantine/seal.png" alt="" />` : null}`;
  if (flip) return html`<div class="card flip" style=${`--cs:${cs};--d:${delay}s`}><div class="face">${face}</div><div class="backc"></div></div>`;
  const cls = 'card' + (pick ? ' pick' : '') + (kept ? ' kept' : '');
  return pick
    ? html`<div class=${cls} style=${`--cs:${cs}`} onClick=${onClick} role="button" tabindex="0">${face}</div>`
    : html`<div class=${cls} style=${`--cs:${cs}`}>${face}</div>`;
}
