// The mod's own pictures at any size, in the Frontier Desk's --u units: the 16-pixel icon sheet and the item sheet, sized with percentages.
import { html } from '../html.js';
import { ICONS } from '../gen/icons.js';
import { ITEMS } from '../gen/items.js';
import { itemPicture } from '../itemart.js';
import { tileIcon } from '../tracks.js';

/** One icon of the sheet by name; `w` is its width in --u. */
export function DeskIcon({ name, w = 3 }) {
  const i = Math.max(0, ICONS.indexOf(name));
  const n = ICONS.length;
  return html`<i class="dico" style=${`width:calc(var(--u) * ${w});height:calc(var(--u) * ${w});background-image:url(assets/gui/icons.png);background-size:${n * 100}% 100%;background-position:${n > 1 ? (i / (n - 1)) * 100 : 0}% 0`}></i>`;
}

/** The mod's own item picture, else a shape and colour matched to the item's name, else a gift. */
export function DeskItem({ id, w = 3 }) {
  const e = ITEMS.map[id];
  if (!e) return html`<${DeskIcon} name=${itemPicture(id) || 'gift'} w=${w} />`;
  const cols = ITEMS.width / e[2], rows = ITEMS.height / e[3];
  const x = e[0] / (ITEMS.width - e[2]), y = e[1] / (ITEMS.height - e[3]);
  return html`<i class="dico" style=${`width:calc(var(--u) * ${w});height:calc(var(--u) * ${w});background-image:url(assets/items.png);background-size:${cols * 100}% ${rows * 100}%;background-position:${x * 100}% ${y * 100}%`}></i>`;
}

/** The picture for a track tile: the item, a coin or star, the raid-points crystal, or nothing. */
export function TileIcon({ node, w = 3.4 }) {
  const icon = tileIcon(node);
  if (!icon) return null;
  if (icon.kind === 'raid') return html`<i class="dico" style=${`width:calc(var(--u) * ${w});height:calc(var(--u) * ${w});background-image:url(assets/gui/raid_points.png);background-size:100% 100%`}></i>`;
  if (icon.kind === 'ui') return html`<${DeskIcon} name=${icon.name} w=${w} />`;
  return html`<${DeskItem} id=${icon.id} w=${w} />`;
}
