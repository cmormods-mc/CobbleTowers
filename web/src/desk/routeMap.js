// Pure view model for the route map (P50 stage 1): columns of stops and the lines between them, from the intermission's
// `extras.route` and the draft's choices. No DOM.

export const LANES = 3;

const NAMES = { BATTLE: 'Battle', REST: 'Rest', SHRINE: 'Shrine', GAMBLER: 'Gambler', FORGE: 'Forge', LIBRARY: 'Library', VAULT: 'Vault', POOL: 'Mystic Pool' };

/** "Mystic Pool", "Battle"; a kind the page does not know reads as itself in capitals. */
export const kindName = (kind) => NAMES[kind] || String(kind);

/** Whether a path can run from a stop in lane `from` to a stop in lane `to` (a party that has taken no step can start anywhere). */
export const linked = (from, to) => from < 0 || Math.abs(from - to) <= 1;

/** "Rootvale" for "cobbletowers:rootvale": the place a detour goes to, or empty for the run's own tower. */
export const arenaName = (arena) => (arena ? String(arena).split(':').pop().replace(/^./, (c) => c.toUpperCase()) : '');

/** Whether the server sent a route step to choose from. */
export const hasRoute = (extras) => !!extras?.route && extras.route.now.length > 0;

/**
 * @param {{lane:number, now:{lane:number,card:number,kind:string}[], ahead:{floor:number,milestone:boolean,kinds:string[]}[]}} route
 * @param {{index:number}[]} choices the draft's choices, by card index
 * @returns {{cols: object[], edges: object[], lane: number}}
 */
export function routeMap(route, choices) {
  const cols = [];
  // the step on the table: one stop per lane; a lane the party cannot step to is shown blocked
  const now = [];
  for (let lane = 0; lane < LANES; lane++) {
    const node = route.now.find((n) => n.lane === lane);
    now.push(node ? { lane, kind: node.kind, card: node.card, arena: node.arena || '', choice: choices[node.card] || null, state: 'now' } : { lane, kind: '', card: -1, arena: '', choice: null, state: 'blocked' });
  }
  cols.push({ floor: null, milestone: false, nodes: now });
  for (const step of route.ahead) {
    if (step.milestone) cols.push({ floor: step.floor, milestone: true, nodes: [{ lane: 1, kind: 'RELIC', card: -1, choice: null, state: 'milestone' }] });
    else cols.push({ floor: step.floor, milestone: false, nodes: [0, 1, 2].map((lane) => ({ lane, kind: step.kinds[lane] || '', card: -1, choice: null, state: step.kinds[lane] ? 'ahead' : 'blocked' })) });
  }
  const edges = [];
  for (let c = 0; c + 1 < cols.length; c++) {
    for (const a of cols[c].nodes) {
      if (a.state === 'blocked') continue;
      for (const b of cols[c + 1].nodes) {
        if (b.state === 'blocked') continue;
        if (cols[c].milestone || cols[c + 1].milestone || linked(a.lane, b.lane)) edges.push({ from: c, fromLane: a.lane, to: c + 1, toLane: b.lane, active: c === 0 });
      }
    }
  }
  return { cols, edges, lane: route.lane };
}

/** Where each column's middle sits in the map box, in percent: the step on the table is wider than the steps scouting shows. */
export function colCenters(count, firstWeight = 3) {
  const weights = Array.from({ length: count }, (_, i) => (i === 0 ? firstWeight : 1));
  const total = weights.reduce((a, b) => a + b, 0);
  let used = 0;
  return weights.map((w) => { const c = ((used + w / 2) / total) * 100; used += w; return c; });
}

/** Lanes sit at a fifth, a half and four fifths of the box's height. */
export const nodeY = (lane) => [20, 50, 80][lane] ?? 50;

/** A column's heading: the step on the table, a floor to come, or a milestone. */
export const colTitle = (col, i) => (i === 0 ? 'Choose' : col.milestone ? 'F' + col.floor + ' relic' : 'After F' + col.floor);
