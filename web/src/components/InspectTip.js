// The stat tooltip a rental card opens from its inspect band, in the look of CobblemonExtras' Pokeshout: name and level, types, ability,
// nature, IVs and EVs with a badge per stat, moves in their type colours, plus the AscensionLib tier when the server sent one.
import { STATS, lift, natureEffect, tierOf, tierOfAscension, typeHex } from '../cardTheme.js';

const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
const sum = (a) => a.reduce((x, y) => x + y, 0);
const pct = (v, max, digits) => { const x = (v / max) * 100; return x === 100 ? '100' : x.toFixed(digits); };
const tc = (t) => lift(typeHex(t), 0.25);
const badges = (arr) => STATS.map(([n, c], i) => `<span class="rc-sb" style="--sc:${c}">${n}</span><b style="color:${c}">${arr[i]}</b>`).join('');

/** The tooltip's inside, from a card as adaptCard returns it. */
export function tipHtml(card) {
  const out = [`<div class="rc-th"><b>${esc(card.name)}</b><span>Lv.${card.level}</span></div>`];
  if (card.types.length) out.push(`<div class="rc-tt">${card.types.map((t) => `<span style="color:${tc(t)}">${esc(t.charAt(0).toUpperCase() + t.slice(1))}</span>`).join('<em> / </em>')}</div>`);
  const asc = card.ascension ? tierOfAscension(card.ascension) : null;
  if (asc) out.push(`<div class="rc-ar"><u>Ascension:</u> <b style="color:${asc.c}">${asc.n}</b><i style="color:${asc.c}">${'★'.repeat(asc.st)}</i></div>`);
  if (card.ability) out.push(`<div><u>Ability:</u> ${esc(card.ability)}</div>`);
  if (card.item) out.push(`<div><u>Item:</u> ${esc(card.item)}</div>`);
  if (card.nature) {
    const fx = natureEffect(card.nature);
    out.push(`<div><u>Nature:</u> ${esc(card.nature)}${fx ? ` <em>(<s class="rc-up">+${fx[0]}</s> / <s class="rc-dn">-${fx[1]}</s>)</em>` : ''}</div>`);
  }
  if (card.ivs) out.push(`<div class="rc-gap"><u>IVs:</u> ${sum(card.ivs)}/186 <em>(<s class="rc-pg">${pct(sum(card.ivs), 186, 0)}%</s>)</em></div><div class="rc-sr">${badges(card.ivs)}</div>`);
  if (card.evs) out.push(`<div class="rc-gap"><u>EVs:</u> ${sum(card.evs)}/510 <em>(<s class="rc-pe">${pct(sum(card.evs), 510, 2)}%</s>)</em></div><div class="rc-sr">${badges(card.evs)}</div>`);
  if (card.moves.length) out.push(`<div class="rc-gap"><u>Moves:</u></div><div class="rc-mvl">${card.moves.map((m) => `<span style="color:${tc(m.type)}">${esc(m.name)}</span>`).join('<em> - </em>')}</div>`);
  return out.join('');
}

let tip = null;
function element() {
  if (!tip) {
    tip = document.createElement('div');
    tip.id = 'rc-tip'; tip.setAttribute('role', 'tooltip');
    document.body.appendChild(tip);
    document.addEventListener('pointerdown', (e) => { if (!e.target.closest || !e.target.closest('.rc-insp')) hideTip(); });
  }
  return tip;
}

/** Puts the tooltip beside (x, y), above it when there is room, and inside the window. */
export function moveTip(x, y) {
  const el = element(), w = el.offsetWidth, h = el.offsetHeight;
  let tx = x + 16, ty = y - h - 12;
  if (tx + w > innerWidth - 8) tx = x - w - 16;
  if (tx < 8) tx = 8;
  if (ty < 8) ty = y + 20;
  if (ty + h > innerHeight - 8) ty = Math.max(8, innerHeight - h - 8);
  el.style.transform = `translate(${tx}px,${ty}px)`;
}

export function showTip(card, x, y) {
  const el = element();
  el.innerHTML = tipHtml(card);
  el.style.setProperty('--rc', tierOf(card.rarity).c);
  moveTip(x, y);
  el.classList.add('rc-on');
}

export const hideTip = () => { if (tip) tip.classList.remove('rc-on'); };
export const tipOpen = () => !!tip && tip.classList.contains('rc-on');
