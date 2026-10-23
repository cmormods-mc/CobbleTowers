// The page's own presentation settings (the native TowerUiSettings): kept in the browser, applied as classes on <html>.

const KEY = 'cobbletowers.ui';
/** The Frontier Desk's colour themes: all dark. Midnight is the base styling, so it needs no class on the root. */
export const THEMES = Object.freeze({ midnight: 'Midnight', graphite: 'Graphite', harbor: 'Harbor', dusk: 'Dusk' });
export const DEFAULTS = Object.freeze({ motion: true, pixelFont: true, look: 'desk', theme: 'midnight' });

/** Reads what is stored; anything unreadable or missing falls back to the default. */
export function load(storage = globalThis.localStorage) {
  try {
    const saved = JSON.parse(storage.getItem(KEY) || '{}');
    return { motion: typeof saved.motion === 'boolean' ? saved.motion : DEFAULTS.motion, pixelFont: typeof saved.pixelFont === 'boolean' ? saved.pixelFont : DEFAULTS.pixelFont, look: saved.look === 'native' ? 'native' : 'desk', theme: Object.hasOwn(THEMES, saved.theme) ? saved.theme : DEFAULTS.theme };
  } catch (e) {
    return { ...DEFAULTS };
  }
}

export function save(settings, storage = globalThis.localStorage) {
  try { storage.setItem(KEY, JSON.stringify(settings)); } catch (e) { /* private mode or no storage: the setting lasts until the page closes */ }
}

/** Turns the settings into classes on the root element. */
export function apply(settings, root = document.documentElement) {
  root.classList.toggle('calm', !settings.motion);
  root.classList.toggle('plainfont', !settings.pixelFont);
  for (const id of Object.keys(THEMES)) if (id !== DEFAULTS.theme) root.classList.toggle('th-' + id, settings.theme === id);
}
