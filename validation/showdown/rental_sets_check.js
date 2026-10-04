#!/usr/bin/env node
// Checks every rental set (P33) against the real data it will meet:
//
//   node validation/showdown/rental_sets_check.js --showdown-dir <rig>/showdown --cobblemon-jar <Cobblemon.jar>
//
// For each set: the species exists in Cobblemon's own species files, Showdown knows the species, the ability is one the species can
// have, every move exists and the species (or something it evolves from) can learn it, the held item is a Cobblemon item, and the
// EVs are legal. A set that fails here would build wrongly (or not at all) in game, which is a bad way to find out.

const fs = require('fs');
const path = require('path');
const {spawnSync} = require('child_process');

const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const i = args.indexOf('--' + name);
  return i >= 0 ? args[i + 1] : fallback;
};
const SHOWDOWN = path.resolve(opt('showdown-dir', process.env.SHOWDOWN_DIR || 'showdown'));
const JAR = opt('cobblemon-jar', process.env.COBBLEMON_JAR || '');
const SETS = path.resolve(__dirname, '../../src/main/resources/data/cobbletowers/cobbletowers/rental_sets');

const {Dex} = require(path.join(SHOWDOWN, 'sim/dex'));

function jarEntries() {
  if (!JAR) return null;
  const out = spawnSync('unzip', ['-Z1', JAR], {encoding: 'utf8', maxBuffer: 64 * 1024 * 1024});
  if (out.status !== 0) return null;
  return out.stdout.split('\n');
}

const entries = jarEntries();
const species = new Set();
const items = new Set();
if (entries) {
  for (const e of entries) {
    let m = /^data\/cobblemon\/species\/[^/]+\/([a-z0-9_]+)\.json$/.exec(e);
    if (m) species.add(m[1]);
    m = /^assets\/cobblemon\/models\/item\/([a-z0-9_]+)\.json$/.exec(e);
    if (m) items.add(m[1]);
  }
}

function canLearn(speciesId, moveId) {
  const seen = new Set();
  for (let s = Dex.species.get(speciesId); s && s.exists && !seen.has(s.id); s = s.prevo ? Dex.species.get(s.prevo) : null) {
    seen.add(s.id);
    const data = Dex.data.Learnsets[s.id];
    if (data && data.learnset && data.learnset[moveId]) return true;
    if (s.baseSpecies && s.baseSpecies !== s.name) {
      const base = Dex.data.Learnsets[Dex.species.get(s.baseSpecies).id];
      if (base && base.learnset && base.learnset[moveId]) return true;
    }
  }
  return false;
}

let bad = 0;
let count = 0;
const speciesSeen = new Map();
for (const file of fs.readdirSync(SETS).filter(f => f.endsWith('.json')).sort()) {
  const set = JSON.parse(fs.readFileSync(path.join(SETS, file), 'utf8'));
  count++;
  const problems = [];
  const id = set.species;
  const mon = Dex.species.get(id);
  if (!mon.exists) problems.push(`Showdown does not know the species ${id}`);
  if (entries && !species.has(id)) problems.push(`Cobblemon has no species file for ${id}`);
  if (speciesSeen.has(id)) problems.push(`species ${id} is also in ${speciesSeen.get(id)}`);
  speciesSeen.set(id, file);
  if (mon.exists) {
    const abilities = Object.values(mon.abilities).map(a => a.toLowerCase().replace(/[^a-z0-9]/g, ''));
    if (!abilities.includes(set.ability)) problems.push(`${id} cannot have ${set.ability} (has ${abilities.join(', ')})`);
    for (const move of set.moves) {
      const m = Dex.moves.get(move);
      if (!m.exists) problems.push(`no such move ${move}`);
      else if (m.id !== move) problems.push(`move ${move} is written ${m.id} in Showdown`);
      else if (!canLearn(id, move)) problems.push(`${id} cannot learn ${move}`);
    }
  }
  if (!Dex.natures.get(set.nature).exists) problems.push(`no such nature ${set.nature}`);
  if (mon.exists) {
    const want = mon.types.map(t => t.toLowerCase());
    if (JSON.stringify(set.types || []) !== JSON.stringify(want)) problems.push(`types ${JSON.stringify(set.types)} but Showdown has ${JSON.stringify(want)}`);
  }
  if (set.item) {
    const name = set.item.replace(/^cobblemon:/, '');
    if (!set.item.startsWith('cobblemon:')) problems.push(`item ${set.item} is not a Cobblemon item`);
    else if (entries && !items.has(name)) problems.push(`Cobblemon has no item ${name}`);
  }
  const total = set.evs.reduce((a, b) => a + b, 0);
  if (total > 510 || set.evs.some(v => v > 252)) problems.push(`illegal EVs ${set.evs}`);
  if (problems.length) {
    bad++;
    console.log(`  [FAIL] ${file}: ${problems.join('; ')}`);
  }
}
console.log(`\n${count - bad}/${count} rental sets are valid${entries ? '' : ' (Cobblemon files were not checked: no --cobblemon-jar)'}`);
process.exit(bad ? 1 : 0);
