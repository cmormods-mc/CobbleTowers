// Wheel scrolling that moves a whole row a notch, at once. The game's browser draws off-screen and uploads a texture for every frame that
// changes; the browser's smooth wheel scrolling makes about ten of them per notch. One jump per notch is one frame, so a long grid (the reward
// track, the party chooser) scrolls without dragging the game's frame rate down. Dragging the scrollbar and the keys still scroll as usual.
import { useEffect } from 'preact/hooks';

/** How many whole steps a wheel event is worth: `acc` carries the part of a step left over (a trackpad sends many small events). */
export function wheelSteps(acc, delta, threshold = 100) {
  const total = acc + delta;
  const steps = Math.trunc(total / threshold);
  return { steps, acc: total - steps * threshold };
}

/** The distance of one step in a grid: its first child's height plus the row gap; 0 when there is nothing to measure. */
export function rowPitch(el) {
  const first = el.firstElementChild;
  if (!first) return 0;
  const gap = parseFloat(getComputedStyle(el).rowGap) || 0;
  return first.getBoundingClientRect().height + gap;
}

/** Makes the wheel scroll `el` a row at a time (no animation); returns the function that undoes it. */
export function attachStepScroll(el) {
  let acc = 0;
  const onWheel = (e) => {
    if (e.ctrlKey || e.deltaY === 0) return;
    const pitch = rowPitch(el);
    if (pitch <= 0) return;
    e.preventDefault();
    const delta = e.deltaMode === 1 ? e.deltaY * 40 : e.deltaMode === 2 ? e.deltaY * el.clientHeight : e.deltaY;
    const next = wheelSteps(Math.sign(acc) === Math.sign(delta) ? acc : 0, delta);
    acc = next.acc;
    if (next.steps) el.scrollTop += next.steps * pitch;
  };
  el.addEventListener('wheel', onWheel, { passive: false });
  return () => el.removeEventListener('wheel', onWheel);
}

/** Hook form: `ref` is the scrolling element's ref. The element is not there on the first render, so this looks again after every one. */
export function useStepScroll(ref) {
  useEffect(() => (ref.current ? attachStepScroll(ref.current) : undefined));
}
