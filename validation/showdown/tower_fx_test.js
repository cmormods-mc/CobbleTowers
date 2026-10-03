#!/usr/bin/env node
// Tests tower-fx.js (P23) against the REAL Showdown simulator Cobblemon unbundles, with no Minecraft server.
//
//   node validation/showdown/tower_fx_test.js --showdown-dir L:/claude-cobbleraids-work/testserver-full/showdown
//
// Showdown is plain CommonJS, so the patch can be installed into it and driven through BattleStream with the same
// `>start` / `>player` lines Cobblemon writes. Each test asserts what the simulator actually did -- the weather the
// field holds, the stat stage, the HP, the damage a move dealt -- never what the patch claims it asked for.
//
// Not part of ci_local.sh: it needs the unbundled simulator, which lives in a server directory the CI has no
// business downloading. Run it after touching tower-fx.js, and after a Cobblemon upgrade (Showdown can change).

const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {spawnSync} = require('child_process');

const args = process.argv.slice(2);
const dirArg = args.indexOf('--showdown-dir');
const raidArg = args.indexOf('--raid-patch');
const SHOWDOWN = path.resolve(dirArg >= 0 ? args[dirArg + 1] : (process.env.SHOWDOWN_DIR || 'showdown'));
// The CobbleRaids raid-patch.js to test the extension loader against. The one in a server's showdown/ directory is only
// replaced when the server next starts, so after changing the loader pass the source copy from the CobbleRaids repo.
const RAID_PATCH = raidArg >= 0 ? path.resolve(args[raidArg + 1]) : path.join(SHOWDOWN, 'raid-patch.js');
const FX_FILE = path.resolve(__dirname, '../../src/main/resources/assets/cobbletowers/showdown/tower-fx.js');
if (!fs.existsSync(path.join(SHOWDOWN, 'sim', 'battle.js'))) {
  console.error('no Showdown simulator at ' + SHOWDOWN + ' (pass --showdown-dir)');
  process.exit(2);
}

globalThis.__TOWER_FX_TEST__ = true;
const BS = require(path.join(SHOWDOWN, 'sim/battle-stream'));
const {Battle} = require(path.join(SHOWDOWN, 'sim/battle'));
const fx = require(FX_FILE);

// ---- driving a battle ----------------------------------------------------------------------------------------

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

/** Cobblemon's packed team: name|species|uuid|currentHealth|status|statusDuration|item|ability|moves|pp|nature|... */
function pack(species, ability, moves, level) {
  return [species, species, '00000000-0000-0000-0000-00000000000' + species.length, '', '', '', 'none', ability,
    moves.join(','), moves.map(() => '16/16').join(','), 'Hardy', '', '', '', '', String(level), ''].join('|');
}

const BLASTOISE = () => pack('Blastoise', 'torrent', ['hydropump', 'icebeam', 'surf', 'earthquake'], 50);
const CHARIZARD = () => pack('Charizard', 'blaze', ['flamethrower', 'airslash', 'dragonclaw', 'roost'], 50);

/**
 * Runs a battle to the end of turn one. `towerFx` goes on the format object exactly where CobbleRaids' format
 * provider would put it. Returns the log, the stream's battle, and anything the stream threw.
 */
async function battle({towerFx, moves = ['move 2', 'move 4'], extraFormat = {}, turns = 1} = {}) {
  const stream = new BS.BattleStream();
  const lines = [];
  const errors = [];
  (async () => {
    try {
      for await (const chunk of stream) lines.push(...String(chunk).split('\n'));
    } catch (err) {
      errors.push(err);
    }
  })();
  const format = Object.assign({mod: 'gen9', gameType: 'singles', gen: 9, ruleset: [], effectType: 'Format'}, extraFormat);
  if (towerFx !== undefined) format.towerFx = towerFx;
  stream.write('>start ' + JSON.stringify({format, seed: [1, 2, 3, 4]}));
  stream.write('>player p1 ' + JSON.stringify({name: 'A', team: BLASTOISE()}));
  stream.write('>player p2 ' + JSON.stringify({name: 'B', team: CHARIZARD()}));
  await sleep(250);
  for (let turn = 0; turn < turns && !stream.battle.ended; turn++) {
    stream.write('>p1 ' + moves[0]);
    stream.write('>p2 ' + moves[1]);
    await sleep(250);
  }
  return {lines, stream, battle: stream.battle, errors};
}

const hp = (b, side) => b.battle.sides[side].active[0].hp;
const maxhp = (b, side) => b.battle.sides[side].active[0].maxhp;
const stage = (b, side, stat) => b.battle.sides[side].active[0].boosts[stat];
const started = b => b.lines.some(line => line.startsWith('|switch|'));
const said = (b, pattern) => b.lines.some(line => pattern.test(line));

// ---- tests ---------------------------------------------------------------------------------------------------

const tests = [];
const test = (name, fn) => tests.push({name, fn});

let baseline;   // computed BEFORE the patch is installed, so "identical when absent" compares against the real engine

test('a battle with no towerFx is identical to one run with the patch absent (same seed, same log)', async () => {
  const b = await battle({});
  const strip = log => log.filter(line => !line.startsWith('|t:') && !line.startsWith('|request|'));
  assert.deepStrictEqual(strip(b.lines), strip(baseline.lines));
  assert.strictEqual(hp(b, 1), hp(baseline, 1));
  assert.strictEqual(hp(b, 0), hp(baseline, 0));
});

test('weather: sets what the field actually holds, with no expiry by default', async () => {
  const b = await battle({towerFx: [{op: 'weather', id: 'raindance', sides: ['p1']}]});
  assert.strictEqual(b.battle.field.weather, 'raindance');
  assert.strictEqual(b.battle.field.weatherState.duration, 0);
  assert.ok(said(b, /\|-weather\|RainDance/), 'the log shows the weather');
});

test('weather: a duration is honoured and clamped', async () => {
  const short = await battle({towerFx: [{op: 'weather', id: 'sunnyday', duration: 3}], turns: 0});
  assert.strictEqual(short.battle.field.weatherState.duration, 3);
  const huge = await battle({towerFx: [{op: 'weather', id: 'sunnyday', duration: 9999}], turns: 0});
  assert.strictEqual(huge.battle.field.weatherState.duration, 20);
});

test('terrain: sets the terrain the field holds', async () => {
  const b = await battle({towerFx: [{op: 'terrain', id: 'electricterrain', sides: ['p1']}]});
  assert.strictEqual(b.battle.field.terrain, 'electricterrain');
});

test('boost: raises a stat stage on the named side only', async () => {
  const b = await battle({towerFx: [{op: 'boost', sides: ['p1'], stat: 'atk', stages: 2}]});
  assert.strictEqual(stage(b, 0, 'atk'), 2);
  assert.strictEqual(stage(b, 1, 'atk'), 0);
  assert.ok(said(b, /\|-boost\|.*\|atk\|2/));
});

test('boost: stages are clamped to +-6 and a zero or non-number does nothing', async () => {
  const high = await battle({towerFx: [{op: 'boost', sides: ['p1'], stat: 'spe', stages: 99}]});
  assert.strictEqual(stage(high, 0, 'spe'), 6);
  const none = await battle({towerFx: [{op: 'boost', sides: ['p1'], stat: 'spe', stages: 'lots'}]});
  assert.strictEqual(stage(none, 0, 'spe'), 0);
});

test('hp: lowers the named side to a percentage and never raises it', async () => {
  const b = await battle({towerFx: [{op: 'hp', sides: ['p2'], percent: 50}], moves: ['move 4', 'move 4']});
  const full = maxhp(b, 1);
  assert.ok(hp(b, 1) <= Math.floor(full / 2) + 1, `foe at ${hp(b, 1)}/${full}`);
  assert.strictEqual(hp(b, 0), maxhp(b, 0), 'the other side is untouched');
  const noRaise = await battle({towerFx: [{op: 'hp', sides: ['p2'], percent: 100}]});
  assert.ok(hp(noRaise, 1) <= maxhp(noRaise, 1));
});

test('status: puts the status on the named side', async () => {
  const b = await battle({towerFx: [{op: 'status', sides: ['p2'], status: 'par'}], turns: 0});
  assert.strictEqual(b.battle.sides[1].active[0].status, 'par');
  assert.strictEqual(b.battle.sides[0].active[0].status, '');
});

test("status: the simulator's own immunities are respected (a Fire type cannot be burned)", async () => {
  const b = await battle({towerFx: [{op: 'status', sides: ['p2'], status: 'brn'}], turns: 0});
  assert.strictEqual(b.battle.sides[1].active[0].status, '', 'Charizard stays unburned, and the battle carried on');
  assert.ok(started(b));
  const blastoise = await battle({towerFx: [{op: 'status', sides: ['p1'], status: 'brn'}], turns: 0});
  assert.strictEqual(blastoise.battle.sides[0].active[0].status, 'brn');
});

test('sidecondition: adds Tailwind to the named side', async () => {
  const b = await battle({towerFx: [{op: 'sidecondition', sides: ['p1'], id: 'tailwind', duration: 4}]});
  assert.ok(b.battle.sides[0].sideConditions.tailwind, 'p1 has tailwind');
  assert.ok(!b.battle.sides[1].sideConditions.tailwind);
});

test('damage: scales what the named side deals, only for the named type', async () => {
  const base = await battle({});
  const baseLoss = maxhp(base, 1) - hp(base, 1);
  const boosted = await battle({towerFx: [{op: 'damage', sides: ['p1'], type: 'Ice', percent: 150}]});
  const boostedLoss = maxhp(boosted, 1) - hp(boosted, 1);
  assert.ok(Math.abs(boostedLoss - Math.floor(baseLoss * 1.5)) <= 3, `loss ${baseLoss} -> ${boostedLoss}`);
  const wrongType = await battle({towerFx: [{op: 'damage', sides: ['p1'], type: 'Fire', percent: 150}]});
  assert.strictEqual(hp(wrongType, 1), hp(base, 1), 'a Fire boost does nothing to a Water move');
  const any = await battle({towerFx: [{op: 'damage', sides: ['p1'], type: 'any', percent: 50}]});
  assert.ok(maxhp(any, 1) - hp(any, 1) < baseLoss, '"any" applies to every move');
});

test('resist: scales what the named side takes', async () => {
  const base = await battle({});
  const baseLoss = maxhp(base, 1) - hp(base, 1);
  const tough = await battle({towerFx: [{op: 'resist', sides: ['p2'], type: 'any', percent: 50}]});
  const toughLoss = maxhp(tough, 1) - hp(tough, 1);
  assert.ok(Math.abs(toughLoss - Math.floor(baseLoss / 2)) <= 3, `loss ${baseLoss} -> ${toughLoss}`);
  assert.strictEqual(hp(tough, 0), hp(base, 0), 'damage dealt to p1 is not changed by p2\'s resistance');
});

test('a damage rule is local to its battle: a later battle without it is unaffected', async () => {
  await battle({towerFx: [{op: 'damage', sides: ['p1'], type: 'any', percent: 300}]});
  const after = await battle({});
  assert.strictEqual(hp(after, 1), hp(baseline, 1));
});

test('several operations together all apply', async () => {
  const b = await battle({towerFx: [
    {op: 'weather', id: 'raindance'}, {op: 'boost', sides: ['p1'], stat: 'spa', stages: 1},
    {op: 'status', sides: ['p2'], status: 'par'}, {op: 'sidecondition', sides: ['p1'], id: 'reflect'}]});
  assert.strictEqual(b.battle.field.weather, 'raindance');
  assert.strictEqual(stage(b, 0, 'spa'), 1);
  assert.strictEqual(b.battle.sides[1].active[0].status, 'par');
  assert.ok(b.battle.sides[0].sideConditions.reflect);
});

test('one bad operation is reported and skipped; the ones around it still apply', async () => {
  const b = await battle({towerFx: [
    {op: 'boost', sides: ['p1'], stat: 'atk', stages: 1},
    {op: 'weather', id: 'notaweather'},
    {op: 'boost', sides: ['p1'], stat: 'def', stages: 1}]});
  assert.strictEqual(stage(b, 0, 'atk'), 1);
  assert.strictEqual(stage(b, 0, 'def'), 1);
  assert.ok(said(b, /could not be applied/), 'the failure is said out loud in the log');
});

test('hostile input: the battle always starts, nothing throws, nothing unexpected is applied', async () => {
  const hostile = [
    'not an array', 42, null, {op: 'weather'}, [], [null], [42], ['weather'], [[]],
    [{}], [{op: 7}], [{op: '__proto__'}], [{op: 'constructor'}], [{op: 'toString'}], [{op: 'eval', code: '1+1'}],
    [{op: 'boost', sides: 'p1', stat: 'atk', stages: 1}], [{op: 'boost', sides: [1, null, {}], stat: 'atk', stages: 1}],
    [{op: 'boost', sides: ['p1'], stat: '__proto__', stages: 1}], [{op: 'boost', sides: ['p9'], stat: 'atk', stages: 1}],
    [{op: 'weather', id: '../../etc/passwd'}], [{op: 'weather', id: {}}], [{op: 'weather', id: 'raindance', duration: 'x'}],
    [{op: 'status', sides: ['p1'], status: 'fnt'}], [{op: 'sidecondition', sides: ['p1'], id: 'stealthrock'}],
    [{op: 'damage', sides: ['p1'], type: 'Pizza', percent: 150}], [{op: 'damage', sides: ['p1'], percent: 1e308}],
    [{op: 'damage', sides: ['p1'], percent: -50}], [{op: 'hp', sides: ['p1'], percent: 'NaN'}],
    Array.from({length: 1000}, () => ({op: 'boost', sides: ['p1'], stat: 'atk', stages: 1})),
    [{op: 'boost', sides: ['p1'], stat: 'atk', stages: 1, extra: {deep: {deeper: [1, 2, 3]}}}],
  ];
  for (const payload of hostile) {
    const b = await battle({towerFx: payload});
    assert.ok(started(b), 'the battle started for ' + JSON.stringify(payload).slice(0, 80));
    assert.deepStrictEqual(b.errors, [], 'nothing threw for ' + JSON.stringify(payload).slice(0, 80));
    assert.ok(stage(b, 0, 'atk') <= 6 && stage(b, 0, 'atk') >= -6, 'stages stay in range');
  }
});

test('a thousand operations apply only the first 32 and say so', async () => {
  const b = await battle({towerFx: Array.from({length: 1000}, () => ({op: 'boost', sides: ['p1'], stat: 'atk', stages: 1}))});
  assert.strictEqual(stage(b, 0, 'atk'), 6, 'clamped at +6 long before 32 raises');
  assert.ok(said(b, /Only the first 32 tower effects/));
});

test('damage multipliers are bounded: 1e308 percent is clamped to 300, never infinite or NaN', async () => {
  const base = await battle({});
  const b = await battle({towerFx: [{op: 'damage', sides: ['p1'], type: 'any', percent: 1e308}]});
  const loss = maxhp(b, 1) - hp(b, 1);
  assert.ok(Number.isFinite(hp(b, 1)) && hp(b, 1) >= 0);
  assert.ok(loss <= (maxhp(base, 1) - hp(base, 1)) * 3 + 3, 'at most three times the damage');
});

test('towerFx on a battle that is a restored (deserialized) battle is not applied a second time', async () => {
  const b = await battle({towerFx: [{op: 'boost', sides: ['p1'], stat: 'atk', stages: 1}]});
  b.battle.towerFxApplied = true;   // the guard a restored battle relies on
  fx.applyTowerFx(b.battle);
  assert.strictEqual(stage(b, 0, 'atk'), 1, 'not applied twice');
});

test('the extension loader: a module that throws is skipped, a good one still loads, other files are ignored', async () => {
  const raidPatch = RAID_PATCH;
  if (!fs.existsSync(raidPatch) || !/ext-\[a-z0-9\]/.test(fs.readFileSync(raidPatch, 'utf8'))) {
    console.log('    (skipped: no raid-patch.js with the extension loader at ' + raidPatch + '; pass --raid-patch)');
    return;
  }
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'towerfx-loader-'));
  try {
    for (const name of ['sim', 'data', 'lib', 'config', 'tools']) {
      if (fs.existsSync(path.join(SHOWDOWN, name))) fs.symlinkSync(path.join(SHOWDOWN, name), path.join(tmp, name), 'junction');
    }
    fs.copyFileSync(raidPatch, path.join(tmp, 'raid-patch.js'));
    fs.writeFileSync(path.join(tmp, 'ext-aaa-bad.js'), "throw new Error('this extension is broken');\n");
    fs.writeFileSync(path.join(tmp, 'ext-zzz-good.js'), "globalThis.__extGood = true;\n");
    fs.writeFileSync(path.join(tmp, 'ext-Upper.js'), "globalThis.__extUpper = true;\n");
    fs.writeFileSync(path.join(tmp, 'extra.js'), "globalThis.__extExtra = true;\n");
    const result = spawnSync(process.execPath, ['-e',
      "require('./raid-patch.js'); console.log(JSON.stringify({good: !!globalThis.__extGood, upper: !!globalThis.__extUpper, extra: !!globalThis.__extExtra}));"],
    {cwd: tmp, encoding: 'utf8'});
    assert.strictEqual(result.status, 0, 'raid-patch.js loaded despite the broken extension: ' + result.stderr);
    assert.match(result.stdout, /ext-aaa-bad\.js failed to load and was skipped/);
    assert.match(result.stdout, /\{"good":true,"upper":false,"extra":false\}/);
  } finally {
    fs.rmSync(tmp, {recursive: true, force: true});
  }
});

test('the full production chain: raid-patch.js loads the extension, which then applies effects to an ordinary battle', async () => {
  const raidPatch = RAID_PATCH;
  if (!fs.existsSync(raidPatch) || !/ext-\[a-z0-9\]/.test(fs.readFileSync(raidPatch, 'utf8'))) {
    console.log('    (skipped: no raid-patch.js with the extension loader; pass --raid-patch)');
    return;
  }
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'towerfx-chain-'));
  try {
    for (const name of ['sim', 'data', 'lib', 'config', 'tools']) {
      if (fs.existsSync(path.join(SHOWDOWN, name))) fs.symlinkSync(path.join(SHOWDOWN, name), path.join(tmp, name), 'junction');
    }
    fs.copyFileSync(raidPatch, path.join(tmp, 'raid-patch.js'));
    // Installed under the id CobbleTowers registers, with no test flag, exactly as CobbleRaids' installer writes it.
    fs.copyFileSync(FX_FILE, path.join(tmp, 'ext-cobbletowers-fx.js'));
    fs.writeFileSync(path.join(tmp, 'chain.js'), `
      require('./raid-patch.js');
      const BS = require('./sim/battle-stream');
      const pack = ${pack.toString()};
      async function run(towerFx) {
        const stream = new BS.BattleStream();
        (async () => { for await (const c of stream) {} })();
        const format = {mod: 'gen9', gameType: 'singles', gen: 9, ruleset: [], effectType: 'Format'};
        if (towerFx) format.towerFx = towerFx;
        stream.write('>start ' + JSON.stringify({format, seed: [1, 2, 3, 4]}));
        stream.write('>player p1 ' + JSON.stringify({name: 'A', team: pack('Blastoise', 'torrent', ['icebeam'], 50)}));
        stream.write('>player p2 ' + JSON.stringify({name: 'B', team: pack('Charizard', 'blaze', ['roost'], 50)}));
        await new Promise(r => setTimeout(r, 250));
        stream.write('>p1 move 1'); stream.write('>p2 move 1');
        await new Promise(r => setTimeout(r, 250));
        const b = stream.battle;
        return {weather: b.field.weather, foeHp: b.sides[1].active[0].hp, atk: b.sides[0].active[0].boosts.atk};
      }
      (async () => {
        const plain = await run(undefined);
        const fx = await run([{op: 'weather', id: 'raindance'}, {op: 'boost', sides: ['p1'], stat: 'atk', stages: 1},
                              {op: 'damage', sides: ['p1'], type: 'any', percent: 200}]);
        console.log(JSON.stringify({plain, fx}));
        process.exit(0);
      })();
    `);
    const result = spawnSync(process.execPath, ['chain.js'], {cwd: tmp, encoding: 'utf8'});
    assert.strictEqual(result.status, 0, result.stderr || result.stdout);
    const line = result.stdout.split('\n').filter(l => l.startsWith('{"plain"'))[0];
    assert.ok(line, 'the chain script reported: ' + result.stdout.slice(0, 300));
    const {plain, fx: withFx} = JSON.parse(line);
    assert.strictEqual(plain.weather, '', 'an ordinary battle through the same chain has no weather');
    assert.strictEqual(plain.atk, 0);
    assert.strictEqual(withFx.weather, 'raindance', 'the extension loaded by raid-patch.js applied weather');
    assert.strictEqual(withFx.atk, 1);
    assert.ok(withFx.foeHp < plain.foeHp, 'and doubled damage: foe at ' + withFx.foeHp + ' vs ' + plain.foeHp);
  } finally {
    fs.rmSync(tmp, {recursive: true, force: true});
  }
});

// ---- run -----------------------------------------------------------------------------------------------------

(async () => {
  // The reference battle is run BEFORE the patch is installed, so the first test compares against the real engine.
  baseline = await battle({});
  fx.install({Battle});

  let failed = 0;
  for (const {name, fn} of tests) {
    try {
      await fn();
      console.log('  [PASS] ' + name);
    } catch (err) {
      failed++;
      console.log('  [FAIL] ' + name + '\n         ' + String((err && err.stack) || err).split('\n').slice(0, 4).join('\n         '));
    }
  }
  console.log(`\n${tests.length - failed}/${tests.length} checks passed`);
  process.exit(failed ? 1 : 0);
})();
