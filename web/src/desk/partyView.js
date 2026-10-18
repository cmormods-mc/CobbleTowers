// Pure view model for the Frontier Desk party chooser (P51 slice 6), over RegistrationState.

/** A species display name as its sprite file name: lower case, letters and digits only ("Mr. Mime" is mrmime). */
export const spriteOf = (name) => String(name).toLowerCase().replace(/[^a-z0-9]/g, '');

/** Every Pokemon the player owns as a card: its sprite, where it is, and its place in the registered team (0 when not chosen). */
export function monCards(reg) {
  return reg.pokemon.map((p) => ({ id: p.id, name: p.name, species: spriteOf(p.name), level: p.level, fainted: p.fainted, where: p.where, order: reg.chosen.indexOf(p.id) + 1 }));
}

/** "2 of 6 registered", or what happens when nothing is chosen. */
export const chooserHint = (reg) => (reg.chosen.length === 0 ? 'Nothing chosen: your current party will be used' : reg.chosen.length + ' of ' + reg.max + ' registered');

/** Whether one more can be chosen. */
export const hasRoom = (reg) => reg.chosen.length < reg.max;
