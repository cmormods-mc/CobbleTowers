import { useEffect, useRef, useState } from 'preact/hooks';
import { html } from '../html.js';
import { Sprite } from './ui.js';
import { ATLAS } from '../gen/atlas.js';
import { adaptCard } from '../cards.js';
import { glyph, tierOf, typeHex } from '../cardTheme.js';
import { hideTip, moveTip, showTip, tipOpen } from './InspectTip.js';

export { adaptCard };

const reduced = () => (typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches) || (typeof document !== 'undefined' && document.documentElement.classList.contains('calm'));
/** A whole-number size that fits the sprite's drawn pixels into the art box (a crisp pixel grid). */
const spriteScale = (species) => {
  const e = ATLAS.map[species];
  return e ? Math.max(4, Math.min(11, Math.floor(Math.min(228 / e[6], 232 / e[7])))) : 8;
};

/** Square sparks flung from the landing point; one animation frame loop that stops when the last spark is gone. */
const loops = new WeakMap();
function sparks(cv, tier) {
  if (!cv || !tier.parts) return;
  const d = window.devicePixelRatio || 1;
  if (cv.width !== 420 * d) { cv.width = 420 * d; cv.height = 520 * d; }
  const ctx = cv.getContext('2d');
  ctx.setTransform(d, 0, 0, d, 0, 0);
  cancelAnimationFrame(loops.get(cv));
  const ps = [];
  for (let i = 0; i < tier.parts; i++) {
    const a = Math.random() * Math.PI * 2, v = 90 + Math.random() * (180 + tier.sh * 16);
    ps.push({ x: 210, y: 228, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 60, l: 0, L: 0.7 + Math.random() * 0.9, s: 2 + ((Math.random() * 3) | 0), h: tier.rainbow ? Math.random() * 360 : null });
  }
  let t0 = performance.now();
  const step = (t) => {
    const dt = Math.min(0.05, (t - t0) / 1000); t0 = t;
    ctx.clearRect(0, 0, 420, 520); ctx.globalCompositeOperation = 'lighter';
    let alive = 0;
    for (const p of ps) {
      p.l += dt; if (p.l > p.L) continue; alive++;
      p.vy += 380 * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= 0.99;
      ctx.globalAlpha = 1 - p.l / p.L; ctx.fillStyle = p.h != null ? `hsl(${p.h},100%,65%)` : tier.c;
      ctx.fillRect(Math.round(p.x), Math.round(p.y), p.s, p.s);
    }
    if (alive) loops.set(cv, requestAnimationFrame(step)); else ctx.clearRect(0, 0, 420, 520);
  };
  loops.set(cv, requestAnimationFrame(step));
}

/**
 * One rental card. `flip` plays the reveal (the back charges for as long as the rarity deserves, flips, and lands with the tier's
 * effects); `pick` makes it a button; `kept` lifts and seals it. Hover tilts it, and the bottom band opens the stat tooltip.
 */
export function RentalCard({ card, cs = 2, flip, delay = 0, pick, kept, onClick }) {
  const T = tierOf(card.rarity);
  const [stage, setStage] = useState(flip ? 'back' : 'rest');
  const [kick, setKick] = useState('');
  const [fxing, setFxing] = useState(false);
  const scene = useRef(null), tilt = useRef(null), cv = useRef(null), touched = useRef(false);

  useEffect(() => {
    if (!flip) return undefined;
    const ids = [];
    const later = (fn, ms) => ids.push(setTimeout(fn, ms));
    const charge = T.ct * 0.8, start = delay * 1000;
    later(() => setStage('charge'), start);
    later(() => setStage('flip'), start + charge);
    later(() => {
      setStage('live');
      if (reduced()) return;
      setFxing(true); later(() => setFxing(false), 2600);
      if (T.sh) {
        setKick(T.punch ? 'rc-punch' : 'rc-shake');
        if (T.punch) later(() => setKick('rc-shake'), 300);
        later(() => setKick(''), 900);
      }
      sparks(cv.current, T);
    }, start + charge + 560);
    return () => ids.forEach(clearTimeout);
  }, []);

  const move = (e) => {
    const s = scene.current, r = s.getBoundingClientRect(), x = (e.clientX - r.left) / r.width, y = (e.clientY - r.top) / r.height;
    s.classList.add('rc-hot');
    s.style.setProperty('--mx', (x * 100).toFixed(1)); s.style.setProperty('--my', (y * 100).toFixed(1));
    s.style.setProperty('--px', (x - 0.5).toFixed(3)); s.style.setProperty('--py', (y - 0.5).toFixed(3));
    tilt.current.style.transform = `rotateY(${((x - 0.5) * 18).toFixed(2)}deg) rotateX(${(-(y - 0.5) * 18).toFixed(2)}deg)`;
  };
  const leave = () => {
    const s = scene.current;
    s.classList.remove('rc-hot'); tilt.current.style.transform = '';
    ['--px', '--py'].forEach((v) => s.style.setProperty(v, 0)); s.style.setProperty('--mx', 50);
  };
  const band = {
    onPointerEnter: (e) => { if (e.pointerType !== 'touch') showTip(card, e.clientX, e.clientY); },
    onPointerMove: (e) => { if (e.pointerType !== 'touch' && tipOpen()) moveTip(e.clientX, e.clientY); },
    onPointerLeave: hideTip,
    // a tap on the band opens or closes the tooltip and must not also pick the card
    onPointerDown: (e) => { if (e.pointerType === 'touch') { touched.current = true; if (tipOpen()) hideTip(); else showTip(card, e.clientX, e.clientY); } },
    onClick: (e) => { if (touched.current) { touched.current = false; e.stopPropagation(); } },
  };
  const key = (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick && onClick(e); } };
  const focusTip = () => { const r = scene.current.getBoundingClientRect(); showTip(card, r.left + r.width / 2, r.top + 40); };

  const sc = spriteScale(card.species);
  const sprite = (cls) => html`<${Sprite} class=${cls} name=${card.species} s=${sc} at=${[140, 118]} />`;
  const [t1, t2] = [card.types[0] || 'normal', card.types[1] || card.types[0] || 'normal'];
  const flipped = stage === 'flip' || stage === 'live' || stage === 'rest';
  const cls = ['rcard', pick && 'rc-pick', kept && 'rc-kept', fxing && 'rc-fxing'].filter(Boolean).join(' ');
  const pickProps = pick ? { role: 'button', tabIndex: 0, onClick, onKeyDown: key, onFocus: focusTip, onBlur: hideTip } : {};

  return html`<div class=${cls} style=${`--cs:${cs}`} ...${pickProps}>
    <div class=${'rc-fit' + (stage === 'charge' ? ' rc-charging' : '')} data-r=${card.rarity} style=${`--sh:${T.sh}`}>
      <div class="rc-aura"></div>
      <div ref=${scene} class=${'rc-scene rc-a3' + (stage === 'live' ? ' rc-live' : '') + (kick ? ' ' + kick : '')} data-r=${card.rarity} onPointerMove=${move} onPointerLeave=${leave}>
        <div ref=${tilt} class="rc-tilt"><div class=${'rc-inner' + (flipped ? ' rc-flipped' : '')}>
          <div class="rc-face rc-back"><div class="rc-ball"></div></div>
          <div class="rc-face rc-front" style=${`--acc:${typeHex(t1)}`}>
            <div class="rc-burst"><div class="rc-bg"></div></div>
            <div class="rc-vig"></div>
            <div class="rc-px" style="--pd:-14"><span class="rc-giant rc-l">${card.name.toUpperCase()}</span><span class="rc-giant rc-r">${t1.toUpperCase()}</span></div>
            <div class="rc-slashes"></div>
            <div class="rc-beams">${[8, 22, 36, 50, 64, 78, 90].map((l, i) => html`<i key=${i} style=${`--rl:${l}%;--bw:${[10, 26, 14, 30, 12, 22, 9][i]}px;--ri:${i}`}></i>`)}</div>
            <div class="rc-rings"><i style="--ri:0"></i><i style="--ri:1"></i><i style="--ri:2"></i><i style="--ri:3"></i></div>
            <div class="rc-px" style="--pd:16"><div class="rc-sbox">
              ${T.rainbow ? html`<div class="rc-gh rc-r">${sprite('rc-spr')}</div><div class="rc-gh rc-c">${sprite('rc-spr')}</div>` : null}
              <div class="rc-bobber">${sprite('rc-spr rc-pops')}</div>
            </div></div>
            <div class="rc-pacc"></div>
            <div class="rc-plate"></div>
            <div class="rc-lvb"><small>LV</small>${card.level}</div>
            <div class="rc-nm">${card.name}</div>
            <div class="rc-tyb"><i class="rc-gem" style=${`--rg:${typeHex(t1)}`}></i>${t2 !== t1 ? html`<i class="rc-gem" style=${`--rg:${typeHex(t2)}`}></i>` : null}</div>
            <div class="rc-insp" ...${band}>
              <div class="rc-med" style=${`--tc:${typeHex(t1)}`} dangerouslySetInnerHTML=${{ __html: glyph(t1) }}></div>
              <div class="rc-mid"><svg viewBox="0 0 24 24" aria-hidden="true"><path fill="none" stroke="currentColor" stroke-width="2" d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/><circle cx="12" cy="12" r="3.4" fill="currentColor"/></svg><b>INSPECT</b><small>hover for stats</small></div>
              <div class="rc-med" style=${`--tc:${typeHex(t2)}`} dangerouslySetInnerHTML=${{ __html: glyph(t2) }}></div>
            </div>
            <div class="rc-rframe"></div>
            <div class="rc-rbadge">${T.n}</div><div class="rc-rstars">${'★'.repeat(T.st)}</div>
            <div class="rc-rbanner">${T.n.toUpperCase()}</div>
            <div class="rc-prism"></div><div class="rc-holo"></div>
            <div class="rc-flash"></div>
            ${kept ? html`<div class="rc-kmark"><svg viewBox="0 0 24 24" aria-hidden="true"><path fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="square" d="M4 13l5 5L20 6"/></svg></div>` : null}
          </div>
        </div></div>
      </div>
      ${flip ? html`<canvas ref=${cv} class="rc-fx" width="420" height="520" aria-hidden="true"></canvas>` : null}
    </div>
  </div>`;
}
