// The party chooser's rules, as pure functions of RegistrationState (mirrors the native RegistrationScreen).

const MAX_PER_PAGE = 8;

/** Rows that fit above the controls on a screen `height` GUI pixels tall. */
export const perPage = (height) => Math.max(3, Math.min(MAX_PER_PAGE, Math.floor((height - 98) / 22)));
export const pageCount = (reg, per) => Math.max(1, Math.ceil(reg.pokemon.length / per));

/** The rows of one page: the label, and whether it is chosen (with its 1-based order). */
export function rows(reg, page, per) {
  return reg.pokemon.slice(page * per, (page + 1) * per).map((p) => {
    const order = reg.chosen.indexOf(p.id);
    return { id: p.id, chosen: order >= 0, label: (order >= 0 ? '[' + (order + 1) + '] ' : '[ ] ') + p.name + '  Lv ' + p.level + (p.fainted ? '  (fainted)' : '') + '  - ' + p.where };
  });
}

export const hint = (reg) => (reg.chosen.length === 0 ? 'Nothing chosen: your current party will be used' : reg.chosen.length + ' of ' + reg.max + ' registered');
