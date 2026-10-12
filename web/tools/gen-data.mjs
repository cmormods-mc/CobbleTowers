// Reads the mod's own data files (the single source) and returns what the pages need to know that the server does not send.
import fs from 'node:fs';
import path from 'node:path';

const pretty = (s) => s.split(/[_\s]+/).map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));
const listJson = (dir) => fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => f.endsWith('.json')).sort() : [];

export function genData(root) {
  const data = path.join(root, 'src/main/resources/data/cobbletowers/cobbletowers');
  const species = new Set();

  const rental = listJson(path.join(data, 'rental_sets')).map((f) => {
    const r = readJson(path.join(data, 'rental_sets', f));
    species.add(r.species);
    return {
      species: r.species, name: pretty(r.species), level: r.level, types: r.types, rarity: r.rarity,
      item: r.item ? pretty(r.item.split(':')[1]) : 'No item',
    };
  });

  for (const f of listJson(path.join(data, 'encounter_pools'))) {
    for (const e of readJson(path.join(data, 'encounter_pools', f)).entries ?? []) species.add(String(e.species).split(':').pop());
  }
  for (const f of listJson(path.join(data, 'regional_themes'))) {
    for (const j of readJson(path.join(data, 'regional_themes', f)).jersey_signatures ?? []) species.add(String(j.species).split(':').pop());
  }

  const version = readJson(path.join(root, 'src/main/resources/fabric.mod.json')).version;
  return { rental, species: [...species].sort(), version };
}
