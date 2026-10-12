import { useEffect, useState } from 'preact/hooks';
import { initial, reduce } from './reducer.js';

let state = initial;
const listeners = new Set();

export const get = () => state;

export function update(fn) {
  const next = fn(state);
  if (next === state) return;
  state = next;
  for (const l of listeners) l();
}

export const dispatch = (kind, data) => update((s) => reduce(s, kind, data, Date.now()));

/** Subscribes a component to one slice; it re-renders only when that slice's value changes. */
export function useStore(select) {
  const [value, setValue] = useState(() => select(state));
  useEffect(() => {
    const check = () => {
      const next = select(state);
      setValue((old) => (Object.is(old, next) ? old : next));
    };
    listeners.add(check);
    check();
    return () => listeners.delete(check);
  }, []);
  return value;
}
