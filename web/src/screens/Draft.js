import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { html } from '../html.js';
import { Button } from '../components/ui.js';
import { RentalCard, adaptCard } from '../components/RentalCard.js';
import { act } from '../bridge.js';
import { RARITIES } from '../util.js';

const MAX_BIG = 2;
const isBig = (card) => card.rarity === 'legendary' || card.rarity === 'mythic';

/** Cards the team has kept so far, across the finished packs. */
export function keptCards(draft) {
  const out = [];
  draft.packs.forEach((p) => (p.kept || []).forEach((i) => out.push(adaptCard(p.cards[i]))));
  return out;
}

function Dots({ current, complete }) {
  return html`<div class="dots">${[0, 1, 2].map((i) => html`<i class=${(i < current || complete ? 'on' : '') + (i === current && !complete ? ' cur' : '')}></i>`)}</div>`;
}

/** sealed -> tearing -> revealing -> choosing, per pack. The server holds the packs and checks every pick; this only presents. */
function PackStage({ draft }) {
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
    return html`<div class="grow" style="display:grid;place-items:center"><div style="text-align:center">
      <button class=${'pack' + (phase === 'tearing' ? ' tear' : '')} onClick=${open} aria-label="Open the pack"><div class="ptop"></div><div class="body"></div></button>
      <div style="margin-top:14px;font-size:20px" class="gold">${phase === 'tearing' ? '…' : 'Click the pack to open it'}</div>
      <div class="sub">Pack ${draft.current + 1} of 3</div></div></div>`;
  }
  const revealing = phase === 'revealing';
  return html`<div class="grow" style="display:flex;flex-direction:column;align-items:center;justify-content:center;gap:28px">
    <div style="display:flex;gap:14px;align-items:flex-end;margin-top:14px">${cards.map((c, i) => revealing
      ? html`<${RentalCard} card=${c} cs=${1.8} flip delay=${i * 0.3} />`
      : html`<div style="position:relative"><${RentalCard} card=${c} cs=${1.8} pick kept=${picks.includes(i)} onClick=${() => toggle(i)} /><div class="num">${i + 1}</div></div>`)}</div>
    <div style="display:flex;align-items:center;gap:20px;margin-top:6px">
      <span style="font-size:20px" class="gold">${revealing ? 'Click to skip the reveal' : `Choose two cards to keep (${picks.length} of 2)`}</span>
      ${revealing
        ? html`<${Button} kind="sec" onClick=${() => setPhase('choosing')}>Skip<//>`
        : html`<${Button} disabled=${picks.length !== 2} onClick=${() => act('RentalDraftAction', { action: 'PICK', a: picks[0], b: picks[1] })}>Keep these two<//>`}
    </div>
    <div class="sub">A team may keep at most ${MAX_BIG} legendary or mythic Pokémon.</div></div>`;
}

function Done({ draft }) {
  const team = useMemo(() => keptCards(draft), [draft.packs]);
  return html`<div class="grow" style="display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px">
    <h2 class="gold">Your rental team</h2>
    <div style="display:flex;gap:10px">${team.map((c) => html`<${RentalCard} card=${c} cs=${1.38} />`)}</div>
    <div style="font-size:18px">Your team is drafted.</div>
    <div style="display:flex;gap:12px">
      <${Button} kind="sec" onClick=${() => act('RentalDraftAction', { action: 'RESTART', a: 0, b: 0 })}>Draft again<//>
      <${Button} kind="sec" onClick=${() => act('RentalDraftAction', { action: 'LOBBY', a: 0, b: 0 })}>Lobby<//>
      <${Button} onClick=${() => act('RentalDraftAction', { action: 'READY', a: 0, b: 0 })}>Ready up<//></div></div>`;
}

export function Draft({ draft }) {
  return html`
    <div class="row center"><div class="grow"><h2>Rental Draft</h2>
      <div class="sub">Everyone drafts a borrowed team from three packs: keep two cards from each. Your own Pokémon stay safe in your boxes.</div></div>
      <${Dots} current=${draft.current} complete=${draft.complete} />
      <${Button} kind="sec" onClick=${() => act('RentalDraftAction', { action: 'RESTART', a: 0, b: 0 })}>Start over<//></div>
    <div class="nine dark grow" style="display:flex;flex-direction:column;min-height:0;padding:0">
      ${draft.complete ? html`<${Done} draft=${draft} />` : html`<${PackStage} draft=${draft} />`}</div>`;
}

export { RARITIES };
