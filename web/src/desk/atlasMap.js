// Pure view model for the branching route map (P56): every stop of the tower's cycle with a state, and every link with a state,
// from the intermission's `extras.route.graph`. No DOM.

export const LANES = 3;
export const MIDDLE = 1;

const NAMES = { START: 'Start', BATTLE: 'Battle', ELITE: 'Elite', REST: 'Rest', SHRINE: 'Shrine', FORGE: 'Forge', LIBRARY: 'Library', VAULT: 'Vault', POOL: 'Mystic Pool', MERCHANT: 'Merchant', TRICK_OR_TREAT: 'Trick or Treat', BOSS: 'Boss' };

/** "Mystic Pool", "Trick or Treat"; a stop the page does not know reads as itself. */
export const stopName = (kind) => NAMES[kind] || String(kind);

/** Whether the server sent a branching map. */
export const hasGraph = (extras) => !!extras?.route?.graph && extras.route.graph.rows > 0;

/**
 * @param {{rows:number, reveal:number, trail:number[], stops:{row:number,lane:number,kind:string}[], links:{row:number,from:number,to:number}[]}} graph
 * @param {{lane:number, card:number}[]} now the stops of the step on the table (their cards), by lane
 * @returns {{rows:number, row:number, lane:number, nodes:object[], edges:object[]}}
 */
export function atlas(graph, now) {
  const trail = graph.trail && graph.trail.length ? graph.trail : [MIDDLE];
  const row = trail.length - 1, lane = trail[row];
  const out = graph.links.filter((l) => l.row === row && l.from === lane).map((l) => l.to);
  const nodes = graph.stops.map((s) => {
    let state;
    if (s.row <= row) state = s.row === row && s.lane === lane ? 'current' : trail[s.row] === s.lane ? 'done' : 'passed';
    else if (s.row === row + 1) state = out.includes(s.lane) ? 'reach' : 'seen';
    else state = s.row <= graph.reveal || s.kind === 'BOSS' ? 'seen' : 'fog';
    const hit = state === 'reach' ? (now || []).find((n) => n.lane === s.lane) : null;
    return { id: s.row + ':' + s.lane, row: s.row, lane: s.lane, kind: s.kind, state, card: hit ? hit.card : -1 };
  });
  const edges = graph.links.map((l) => {
    let state = 'future';
    if (l.row < row) state = trail[l.row] === l.from && trail[l.row + 1] === l.to ? 'taken' : 'dead';
    else if (l.row === row) state = l.from === lane ? 'open' : 'dead';
    return { row: l.row, from: l.from, to: l.to, state };
  });
  return { rows: graph.rows, row, lane, nodes, edges };
}

/** Lanes sit at a quarter, a half and three quarters of the width. */
export const laneX = (lane) => [90, 180, 270][lane] ?? 180;

/** Row 0 is the bottom; the pitch leaves room for a floor slab between rows. */
export const PITCH = 54;
export const PAD = 36;
export const rowY = (row, rows) => PAD + (rows - row) * PITCH;
export const atlasHeight = (rows) => PAD * 2 + rows * PITCH;
