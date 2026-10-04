'use strict';
// CobbleTowers' Showdown extension (P23, docs/design/P23-showdown-battle-effects.md).
//
// Installed by CobbleRaids as showdown/ext-cobbletowers-fx.js and require()d by raid-patch.js, which is why the
// relative require('./sim/battle') below resolves to the simulator. This file never edits any Showdown file.
//
// WHAT IT DOES. A tower battle can carry `towerFx`, an array of declarative operations, on the format object of
// its >start payload (CobbleRaids' ShowdownExtensions API puts it there). At battle start each operation is applied
// to the battle: weather, terrain, stat stages, HP, a status, a side condition, or a damage multiplier.
//
// WHY IT IS STABLE.
//   * It does nothing unless asked. It wraps exactly one method, Battle.prototype.start, and returns straight
//     away unless battle.format.towerFx is an array. Every other battle is untouched.
//   * It is declarative. Operations are data with validated parameters; nothing is evaluated, and no string is
//     ever interpolated into code.
//   * A damage multiplier wraps modifyDamage on THIS battle's own `actions` object, not on a prototype, so no
//     other battle can see it and a simulator mod that overrides the method on the instance is wrapped too.
//   * Every operation runs in its own try/catch. A bad one is reported into the battle log and skipped, the rest
//     still apply, and the battle always starts. The damage wrapper itself fails open to the engine's own number.
//   * Everything is bounded: at most MAX_OPS operations, percentages clamped, stages clamped, ids matched against
//     fixed patterns and checked against the battle's own dex. An unknown operation is ignored, not guessed at.
//   * Applying something and then saying what the simulator ACTUALLY holds, never what was asked for.

const MAX_OPS = 32;
const STATS = ['atk', 'def', 'spa', 'spd', 'spe', 'accuracy', 'evasion'];
const STATUSES = ['brn', 'par', 'psn', 'tox', 'slp', 'frz'];
// Fixed lists rather than asking the dex what kind of condition an id is: terrains carry no effectType in this
// simulator (only weathers do), so the dex cannot tell us, and a whitelist is the safer answer anyway. The primal
// weathers are left out on purpose: they cannot be removed by the other side and would make a fight unwinnable.
const WEATHERS = ['raindance', 'sunnyday', 'sandstorm', 'hail', 'snowscape'];
const TERRAINS = ['electricterrain', 'grassyterrain', 'mistyterrain', 'psychicterrain'];
const SIDE_CONDITIONS = ['tailwind', 'reflect', 'lightscreen', 'auroraveil', 'safeguard', 'mist'];
const SIDE_ID = /^p[1-9]$/;
// Over-the-cap EVs (P30). Cobblemon refuses more than 252 per stat, and Showdown clamps a team member's EVs to 255 when
// it builds one, but neither limit applies to a Pokemon that already exists in a battle: the stat formula only ever
// computes floor(ev / 4). So Ascension scaling is applied here, to the battle's own copy, and nothing is ever written
// back to a player's Pokemon.
const EV_STATS = ['hp', 'atk', 'def', 'spa', 'spd', 'spe'];
const MAX_EV_AMOUNT = 2000;   // one operation
const MAX_EV_TOTAL = 4000;    // per stat, however many operations: already +1000 stat points before level scaling

function clamp(value, low, high, fallback) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(low, Math.min(high, Math.trunc(n)));
}

/** A line in the battle log the player can read; never throws. */
function note(battle, text) {
  try {
    battle.add('-message', String(text).slice(0, 200));
  } catch (err) {
    /* a log line is never worth losing a battle over */
  }
}

/** The concrete sides an operation names, silently dropping any that do not exist. */
function sidesOf(battle, op) {
  const wanted = Array.isArray(op.sides) ? op.sides : [];
  const sides = [];
  for (const id of wanted) {
    if (typeof id !== 'string' || !SIDE_ID.test(id)) continue;
    const side = battle.sides.find(candidate => candidate && candidate.id === id);
    if (side && !sides.includes(side)) sides.push(side);
  }
  return sides;
}

function activesOf(sides) {
  const out = [];
  for (const side of sides) {
    for (const pokemon of side.active) if (pokemon && !pokemon.fainted) out.push(pokemon);
  }
  return out;
}

function validType(battle, name) {
  if (name === undefined || name === null || name === 'any') return 'any';
  if (typeof name !== 'string') return null;
  const type = battle.dex.types.get(name);
  return type && type.exists ? type.name : null;
}

// ---- the operations --------------------------------------------------------------------------------------------

function applyWeather(battle, op) {
  const id = String(op.id || '');
  if (!WEATHERS.includes(id)) throw new Error(`${id} is not a supported weather`);
  const condition = battle.dex.conditions.get(id);
  if (!condition.exists) throw new Error(`${id} is not known to this simulator`);
  const source = activesOf(sidesOf(battle, op))[0] || activesOf(battle.sides.filter(Boolean))[0] || null;
  battle.field.setWeather(id, source);
  // 0 is Showdown's "does not expire", which is the default for an armor bonus the player chose to wear.
  if (battle.field.weatherState) battle.field.weatherState.duration = clamp(op.duration, 0, 20, 0);
  if (battle.field.weather !== condition.id) throw new Error(`${id} could not be set`);
}

function applyTerrain(battle, op) {
  const id = String(op.id || '');
  if (!TERRAINS.includes(id)) throw new Error(`${id} is not a supported terrain`);
  const condition = battle.dex.conditions.get(id);
  if (!condition.exists) throw new Error(`${id} is not known to this simulator`);
  const source = activesOf(sidesOf(battle, op))[0] || activesOf(battle.sides.filter(Boolean))[0] || null;
  battle.field.setTerrain(id, source);
  if (battle.field.terrainState) battle.field.terrainState.duration = clamp(op.duration, 0, 20, 0);
  if (battle.field.terrain !== condition.id) throw new Error(`${id} could not be set`);
}

function applyBoost(battle, op) {
  if (!STATS.includes(op.stat)) throw new Error('boost needs a stat');
  const stages = clamp(op.stages, -6, 6, 0);
  if (stages === 0) return;
  for (const pokemon of activesOf(sidesOf(battle, op))) {
    battle.boost({[op.stat]: stages}, pokemon, pokemon, null, false, true);
  }
}

/** Lowers the leads' HP to a percentage. Never raises it: this is a handicap, not a heal. */
function applyHp(battle, op) {
  const percent = clamp(op.percent, 1, 100, 100);
  for (const pokemon of activesOf(sidesOf(battle, op))) {
    const target = Math.max(1, Math.floor(pokemon.maxhp * percent / 100));
    const difference = pokemon.hp - target;
    if (difference > 0) battle.directDamage(difference, pokemon);
  }
}

function applyStatus(battle, op) {
  if (!STATUSES.includes(op.status)) throw new Error('status needs one of ' + STATUSES.join(', '));
  for (const pokemon of activesOf(sidesOf(battle, op))) pokemon.setStatus(op.status, null, null, false);
}

function applySideCondition(battle, op) {
  const id = String(op.id || '');
  if (!SIDE_CONDITIONS.includes(id)) throw new Error(`${id} is not a supported side condition`);
  for (const side of sidesOf(battle, op)) {
    const source = side.active.find(pokemon => pokemon && !pokemon.fainted) || null;
    side.addSideCondition(id, source);
    if (side.sideConditions[id] && typeof op.duration === 'number') {
      side.sideConditions[id].duration = clamp(op.duration, 0, 20, 5);
    }
  }
}

/** Records a damage rule; the wrapper that enforces it is installed once, after every operation has run. */
function addDamageRule(battle, op, rules, key) {
  const type = validType(battle, op.type);
  if (type === null) throw new Error(`${op.type} is not a type`);
  const sides = sidesOf(battle, op).map(side => side.id);
  if (sides.length === 0) return;
  rules[key].push({sides, type, percent: clamp(op.percent, 1, 300, 100)});
}

/**
 * Raises every Pokemon on the named sides (the whole team, not only the leads, so a switch-in is as strong) by `amount`
 * EVs in one stat or in all six, then recomputes its stats the way the simulator itself does. Runs BEFORE the battle
 * starts, so the HP the first switch-in line reports is already the real one; run afterwards, the client would see a
 * max HP change under its feet.
 */
function applyEvs(battle, op) {
  const amount = clamp(op.amount, 1, MAX_EV_AMOUNT, 0);
  if (amount === 0) throw new Error('evs needs a positive amount');
  let stats = EV_STATS;
  if (op.stat !== undefined && op.stat !== null && op.stat !== 'all') {
    if (!EV_STATS.includes(op.stat)) throw new Error(`${op.stat} is not a stat`);
    stats = [op.stat];
  }
  for (const side of sidesOf(battle, op)) {
    for (const pokemon of side.pokemon) {
      if (!pokemon || !pokemon.set || !pokemon.species) continue;
      const before = pokemon.maxhp;
      for (const stat of stats) {
        pokemon.set.evs[stat] = Math.min(MAX_EV_TOTAL, (Number(pokemon.set.evs[stat]) || 0) + amount);
      }
      restat(battle, pokemon);
      (battle.towerFxEvLog = battle.towerFxEvLog || []).push(`${side.id}:${pokemon.species.name} +${amount} hp ${before}->${pokemon.maxhp} atk ${pokemon.storedStats.atk}`);
    }
  }
}

/** Recomputes a Pokemon's stored stats from its (possibly raised) EVs, keeping its HP at the same fraction. */
function restat(battle, pokemon) {
  const stats = battle.spreadModify(pokemon.species.baseStats, pokemon.set);
  pokemon.baseStoredStats = Object.assign({}, stats);
  for (const name of Object.keys(pokemon.storedStats)) {
    pokemon.storedStats[name] = stats[name];
    if (pokemon.modifiedStats) pokemon.modifiedStats[name] = stats[name];
  }
  pokemon.speed = pokemon.storedStats.spe;
  if (pokemon.species.maxHP) return;   // a fixed-HP species (Shedinja) keeps its one HP
  const oldMax = pokemon.maxhp || 1;
  const newMax = stats.hp;
  pokemon.hp = pokemon.hp > 0 ? Math.max(1, Math.min(newMax, Math.round(pokemon.hp * newMax / oldMax))) : 0;
  pokemon.baseMaxhp = newMax;
  pokemon.maxhp = newMax;
}

/** Operations that must be in place before the first switch-in is announced. */
const PRE_START = {
  evs: (battle, op) => applyEvs(battle, op),
};

function applyPreStart(battle) {
  const fx = battle.format && battle.format.towerFx;
  if (!Array.isArray(fx)) return;
  let applied = 0;
  for (const op of fx.slice(0, MAX_OPS)) {
    const name = op && typeof op === 'object' ? op.op : undefined;
    if (typeof name !== 'string' || !Object.prototype.hasOwnProperty.call(PRE_START, name)) continue;
    try {
      PRE_START[name](battle, op);
      applied++;
    } catch (err) {
      note(battle, `A tower effect (${name}) could not be applied: ${err && err.message}`);
    }
  }
  battle.towerFxPreApplied = applied;
  // Said in the server log, like the main summary: only this proves the raised stats are the ones in the battle.
  try {
    if (battle.towerFxEvLog) console.log(`[CobbleTowers] EVs raised: ${battle.towerFxEvLog.join('; ')}`);
  } catch (err) {
    /* logging is never worth a battle */
  }
}

const OPERATIONS = {
  weather: (battle, op) => applyWeather(battle, op),
  terrain: (battle, op) => applyTerrain(battle, op),
  boost: (battle, op) => applyBoost(battle, op),
  hp: (battle, op) => applyHp(battle, op),
  status: (battle, op) => applyStatus(battle, op),
  sidecondition: (battle, op) => applySideCondition(battle, op),
  damage: (battle, op, rules) => addDamageRule(battle, op, rules, 'dealt'),
  resist: (battle, op, rules) => addDamageRule(battle, op, rules, 'taken'),
};

function matches(rule, sideId, type) {
  return rule.sides.includes(sideId) && (rule.type === 'any' || rule.type === type);
}

/**
 * Scales damage for this one battle. Wrapped on the battle's own actions object so nothing else can see it; fails
 * open to the engine's number on anything unexpected, because a wrong multiplier is a small bug and a thrown
 * exception inside damage calculation is a hung battle.
 */
function wrapDamage(battle, rules) {
  const actions = battle.actions;
  const original = actions && actions.modifyDamage;
  if (typeof original !== 'function') {
    note(battle, 'Tower damage effects are not available in this simulator.');
    return;
  }
  actions.modifyDamage = function (baseDamage, pokemon, target, move) {
    const damage = original.apply(this, arguments);
    try {
      if (typeof damage !== 'number' || !(damage > 0) || !pokemon || !target) return damage;
      const type = move && move.type;
      let scaled = damage;
      for (const rule of rules.dealt) {
        if (matches(rule, pokemon.side.id, type)) scaled = Math.max(1, battle.modify(scaled, rule.percent, 100));
      }
      for (const rule of rules.taken) {
        if (matches(rule, target.side.id, type)) scaled = Math.max(1, battle.modify(scaled, rule.percent, 100));
      }
      return scaled;
    } catch (err) {
      return damage;
    }
  };
}

/** Applies every operation in battle.format.towerFx once. Exported for the tests. */
function applyTowerFx(battle) {
  const fx = battle.format && battle.format.towerFx;
  if (!Array.isArray(fx) || battle.towerFxApplied) return;
  battle.towerFxApplied = true;
  if (fx.length > MAX_OPS) note(battle, `Only the first ${MAX_OPS} tower effects were applied.`);

  const rules = {dealt: [], taken: []};
  let applied = 0;
  for (const op of fx.slice(0, MAX_OPS)) {
    const name = op && typeof op === 'object' ? op.op : undefined;
    const handler = typeof name === 'string' && Object.prototype.hasOwnProperty.call(OPERATIONS, name) ? OPERATIONS[name] : null;
    if (!handler) continue;   // an operation this version does not know is ignored, never guessed at (pre-start ones already ran)
    try {
      handler(battle, op, rules);
      applied++;
    } catch (err) {
      note(battle, `A tower effect (${name}) could not be applied: ${err && err.message}`);
    }
  }
  if (rules.dealt.length || rules.taken.length) wrapDamage(battle, rules);
  applied += battle.towerFxPreApplied || 0;
  if (applied > 0) note(battle, 'Tower effects are in play.');
  // To the server log, not just the battle log: the Java side logs what it HANDED to Showdown, and only this line
  // proves the JavaScript received it and applied it (it did not, for a whole phase, and nothing said so).
  try {
    console.log(`[CobbleTowers] Applied ${applied} of ${Math.min(fx.length, MAX_OPS)} tower effect(s) to a battle.`);
  } catch (err) {
    /* logging is never worth a battle */
  }
}

function install(sim) {
  const Battle = sim.Battle;
  const oldStart = Battle.prototype.start;
  Battle.prototype.start = function () {
    // Over-the-cap EVs go in before anything is announced; a battle that has started or been restored is left alone.
    if (!this.started && !this.deserialized) {
      try {
        applyPreStart(this);
      } catch (err) {
        note(this, 'Tower effects could not be prepared.');
      }
    }
    const result = oldStart.apply(this, arguments);
    if (this.deserialized) return result;   // a restored battle must not re-apply what already happened
    try {
      applyTowerFx(this);
    } catch (err) {
      note(this, 'Tower effects could not be applied.');
    }
    return result;
  };
}

module.exports = {install, applyTowerFx, applyPreStart, MAX_OPS};

// Loaded by Showdown (through CobbleRaids' extension loader): install against the simulator beside this file. The
// tests set the flag and call install() themselves against a simulator they loaded.
if (!globalThis.__TOWER_FX_TEST__) install({Battle: require('./sim/battle').Battle});
