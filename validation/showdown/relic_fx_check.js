#!/usr/bin/env node
// Runs every shipped modifier and relic that has battle effects through the real Showdown simulator, one at a time, the way the mod hands them
// over (side becomes sides): node validation/showdown/relic_fx_check.js --showdown-dir <rig>/showdown
// A relic whose operation is dropped or does nothing in the simulator fails here. Not in ci_local.sh; run after changing a relic or tower-fx.js.

const fs = require('fs');
const path = require('path');

const args = process.argv.slice(2);
const dirArg = args.indexOf('--showdown-dir');
const SHOWDOWN = path.resolve(dirArg >= 0 ? args[dirArg + 1] : (process.env.SHOWDOWN_DIR || 'showdown'));
const FX_FILE = path.resolve(__dirname, '../../src/main/resources/assets/cobbletowers/showdown/tower-fx.js');
const MODIFIERS = path.resolve(__dirname, '../../src/main/resources/data/cobbletowers/cobbletowers/modifiers');
if (!fs.existsSync(path.join(SHOWDOWN, 'sim', 'battle.js'))) {
  console.error('no Showdown simulator at ' + SHOWDOWN + ' (pass --showdown-dir)');
  process.exit(2);
}

globalThis.__TOWER_FX_TEST__ = true;
const BS = require(path.join(SHOWDOWN, 'sim/battle-stream'));
const {Battle} = require(path.join(SHOWDOWN, 'sim/battle'));
const fx = require(FX_FILE);
fx.install({Battle});

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const pack = (species, ability, moves, level) => [species, species, '00000000-0000-0000-0000-00000000000' + species.length, '', '', '', 'none', ability,
  moves.join(','), moves.map(() => '16/16').join(','), 'Hardy', '', '', '', '', String(level), ''].join('|');

/** The mod's own resolve(): the player is p1, the foe p2. */
function resolve(ops) {
  return ops.map((op) => {
    const out = Object.assign({}, op);
    if (out.side !== undefined) {
      out.sides = out.side === 'self' ? ['p1'] : out.side === 'foe' ? ['p2'] : ['p1', 'p2'];
      delete out.side;
    }
    return out;
  });
}

async function run(towerFx) {
  const stream = new BS.BattleStream();
  const lines = [];
  (async () => { for await (const chunk of stream) lines.push(...String(chunk).split('\n')); })().catch(() => {});
  const format = {mod: 'gen9', gameType: 'singles', gen: 9, ruleset: [], effectType: 'Format', towerFx};
  stream.write('>start ' + JSON.stringify({format, seed: [1, 2, 3, 4]}));
  stream.write('>player p1 ' + JSON.stringify({name: 'A', team: pack('Blastoise', 'torrent', ['hydropump', 'icebeam', 'surf', 'earthquake'], 50)}));
  stream.write('>player p2 ' + JSON.stringify({name: 'B', team: pack('Charizard', 'blaze', ['flamethrower', 'airslash', 'dragonclaw', 'roost'], 50)}));
  await sleep(200);
  stream.write('>p1 move 2');
  stream.write('>p2 move 4');
  await sleep(200);
  return {lines, battle: stream.battle};
}

(async () => {
  let checked = 0;
  let failed = 0;
  const seen = [];
  const realLog = console.log;
  for (const file of fs.readdirSync(MODIFIERS).filter((f) => f.endsWith('.json')).sort()) {
    const json = JSON.parse(fs.readFileSync(path.join(MODIFIERS, file), 'utf8'));
    const ops = ((json.effect || {}).battle) || [];
    if (!ops.length) continue;
    const logged = [];
    console.log = (...a) => logged.push(a.join(' '));
    let result;
    try {
      result = await run(resolve(ops));
    } finally {
      console.log = realLog;
    }
    const line = logged.find((l) => /Applied \d+ of \d+/.test(l)) || '';
    const m = /Applied (\d+) of (\d+)/.exec(line);
    const ok = !!m && Number(m[1]) === ops.length && Number(m[2]) === ops.length && result.lines.some((l) => l.startsWith('|switch|'));
    checked++;
    if (!ok) failed++;
    seen.push(`${ok ? 'PASS' : 'FAIL'} ${file.replace('.json', '')} (${ops.map((o) => o.op).join(', ')}) ${ok ? '' : line || 'no apply line'}`);
  }
  seen.forEach((s) => realLog('  ' + s));
  realLog(`\n${checked - failed}/${checked} battle-effect modifiers applied in full`);
  process.exit(failed ? 1 : 0);
})();
