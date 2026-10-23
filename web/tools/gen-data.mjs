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

  // Three foes to show on each tower's row (P51): the first entries of the pool its first floor draws from, if they have a sprite.
  const residents = {};
  for (const f of listJson(path.join(data, 'towers'))) {
    const id = f.replace(/\.json$/, '');
    const tower = readJson(path.join(data, 'towers', f));
    const floor = (tower.floors ?? [])[0];
    if (!floor) continue;
    const floorFile = path.join(data, 'floors', floor.split(':')[1] + '.json');
    if (!fs.existsSync(floorFile)) continue;
    const poolId = String(readJson(floorFile).encounter_pool ?? '').split(':').pop();
    const poolFile = path.join(data, 'encounter_pools', poolId + '.json');
    if (!fs.existsSync(poolFile)) continue;
    const spriteDir = path.join(root, 'src/main/resources/assets/cobbletowers/textures/gui/partners');
    const names = (readJson(poolFile).entries ?? []).map((e) => String(e.species).split(':').pop()).filter((s) => fs.existsSync(path.join(spriteDir, s + '.png')));
    residents[id] = [...new Set(names)].slice(0, 3);
  }

  const version = JSON.parse(fs.readFileSync(path.join(root, 'src/main/resources/fabric.mod.json'), 'utf8')).version;
  const guide = readJson(path.join(root, 'src/main/resources/data/cobbletowers/guide.json'));
  return { rental, species: [...species].sort(), residents, version, guide };
}
