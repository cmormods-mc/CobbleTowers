import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { html } from '../html.js';
import { RentalCard, adaptCard } from '../components/RentalCard.js';
import { act } from '../bridge.js';
import { RARITIES } from '../util.js';
import { at, Label, NB, Panel, screen, useScreen } from '../components/native.js';

const MAX_BIG = 2;
/** How big a card is drawn: in step with the GUI scale (so it grows with the window like the rest), five across, at most half the height. */
const cardScale = () => Math.max(0.8, Math.min(screen.g * 0.65, (window.innerWidth - 60) / (5 * 112 + 4 * 14), (window.innerHeight * 0.5) / 160));
const isBig = (card) => card.rarity === 'legendary' || card.rarity === 'mythic';

/** Cards the team has kept so far, across the finished packs. */
export function keptCards(draft) {
  const out = [];
  draft.packs.forEach((p) => (p.kept || []).forEach((i) => out.push(adaptCard(p.cards[i]))));
  return out;
}

function Dots({ current, complete, x, y }) {
  return html`<div class="dots" style=${at(x, y)}>${[0, 1, 2].map((i) => html`<i class=${(i < current || complete ? 'on' : '') + (i === current && !complete ? ' cur' : '')}></i>`)}</div>`;
}

/** sealed -> tearing -> revealing -> choosing, per pack. The server holds the packs and checks every pick; this only presents. */
function PackStage({ draft }) {
  const cs = cardScale();
  const cards = useMemo(() => draft.packs[draft.current].cards.map(adaptCard), [draft.current, draft.packs[draft.current]]);
  const [phase, setPhase] = useState('sealed');
  const [picks, setPicks] = useState([]);
  const timers = useRef([]);
  const later = (fn, ms) => timers.current.push(setTimeout(fn, ms));

  // A new pack (after a successful pick, or a restart) starts sealed again.
  useEffect(() => {
    setPhase('sealed'); setPicks([]);
    return () => { timers.current.forEach(clearTimeout); timers.current = []; };
  }, [draft.current]);

  const open = () => {
    if (phase !== 'sealed') return;
    setPhase('tearing');
    later(() => { setPhase('revealing'); later(() => setPhase((p) => (p === 'revealing' ? 'choosing' : p)), cards.length * 300 + 900); }, 900);
  };
  const kept = useMemo(() => keptCards(draft), [draft.packs]);
  const toggle = (i) => {
    if (picks.includes(i)) return setPicks(picks.filter((p) => p !== i));
    if (picks.length >= 2) return;
    const big = [...kept, ...picks.map((p) => cards[p]), cards[i]].filter(isBig).length;
    if (big > MAX_BIG) return;
    setPicks([...picks, i]);
  };

  if (phase === 'sealed' || phase === 'tearing') {
    return html`<button class=${'pack' + (phase === 'tearing' ? ' tear' : '')} onClick=${open} aria-label="Open the pack"><div class="ptop"></div><div class="body"></div></button>
      <div class="fl c-bronze">${phase === 'tearing' ? '…' : 'Click the pack to open it'}</div>
      <div class="fl c-muted">Pack ${draft.current + 1} of 3</div>`;
  }
  const revealing = phase === 'revealing';
  return html`
    <div style="display:flex;gap:14px;align-items:flex-end;margin-top:14px">${cards.map((c, i) => revealing
      ? html`<${RentalCard} card=${c} cs=${cs} flip delay=${i * 0.3} />`
      : html`<div style="position:relative"><${RentalCard} card=${c} cs=${cs} pick kept=${picks.includes(i)} onClick=${() => toggle(i)} /><div class="num">${i + 1}</div></div>`)}</div>
    <div style="display:flex;align-items:center;gap:20px">
      <span class="fl c-bronze">${revealing ? 'Click to skip the reveal' : `Choose two cards to keep (${picks.length} of 2)`}</span>
      ${revealing
        ? html`<${NB} flow w=${60} kind="dark" onClick=${() => setPhase('choosing')}>Skip<//>`
        : html`<${NB} flow w=${100} disabled=${picks.length !== 2} onClick=${() => act('RentalDraftAction', { action: 'PICK', a: picks[0], b: picks[1] })}>Keep these two<//>`}
    </div>
    <div class="fl c-muted">A team may keep at most ${MAX_BIG} legendary or mythic Pokémon.</div>`;
}

function Done({ draft }) {
  const team = useMemo(() => keptCards(draft), [draft.packs]);
  const cs = cardScale() * 0.73;
  return html`
    <div class="fl c-bronze">Your rental team</div>
    <div style="display:flex;gap:10px">${team.map((c) => html`<${RentalCard} card=${c} cs=${cs} />`)}</div>
    <div class="fl c-text">Your team is drafted.</div>
    <div style="display:flex;gap:12px">
      <${NB} flow w=${80} kind="dark" onClick=${() => act('RentalDraftAction', { action: 'RESTART', a: 0, b: 0 })}>Draft again<//>
      <${NB} flow w=${60} kind="dark" onClick=${() => act('RentalDraftAction', { action: 'LOBBY', a: 0, b: 0 })}>Lobby<//>
      <${NB} flow w=${80} onClick=${() => act('RentalDraftAction', { action: 'READY', a: 0, b: 0 })}>Ready up<//></div>`;
}

export function Draft({ draft }) {
  useScreen();
  const SW = screen.w, SH = screen.h;
  return html`
    <div class="ns" style=${`--g:${screen.g}px`}>
      <${Panel} x=${6} y=${6} w=${SW - 12} h=${31} />
      <${Label} x=${15} y=${12} color="bronze">RENTAL DRAFT<//>
      <${Label} x=${15} y=${25} w=${SW - 150} color="muted">Keep two cards from each of three packs.<//>
      <${Dots} current=${draft.current} complete=${draft.complete} x=${SW - 140} y=${17} />
      <${NB} x=${SW - 72} y=${10} w=${60} kind="dark" onClick=${() => act('RentalDraftAction', { action: 'RESTART', a: 0, b: 0 })}>Start over<//>
      <${Panel} x=${6} y=${40} w=${SW - 12} h=${SH - 46} />
      <div class="dstage" style=${at(12, 46, SW - 24, SH - 58)}>
        ${draft.complete ? html`<${Done} draft=${draft} />` : html`<${PackStage} draft=${draft} />`}</div>
    </div>`;
}

export { RARITIES };
