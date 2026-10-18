import test from 'node:test';
import assert from 'node:assert/strict';
import { arenaName, colCenters, colTitle, hasRoute, kindName, linked, nodeY, routeMap } from '../src/desk/routeMap.js';

const choices = [{ index: 0, title: 'Far Sight' }, { index: 1, title: 'Empty Pockets' }, { index: 2, title: 'Electric Terrain' }];
const route = (lane, lanes, ahead) => ({ lane, now: lanes.map(([l, kind], i) => ({ lane: l, card: i, kind })), ahead });

test('a first step has a stop in every lane and every stop is clickable', () => {
  const m = routeMap(route(-1, [[0, 'BATTLE'], [1, 'FORGE'], [2, 'REST']], []), choices);
  assert.equal(m.cols.length, 1);
  assert.deepEqual(m.cols[0].nodes.map((n) => n.state), ['now', 'now', 'now']);
  assert.deepEqual(m.cols[0].nodes.map((n) => n.choice.title), ['Far Sight', 'Empty Pockets', 'Electric Terrain']);
});

test('a lane the party cannot reach is blocked and has no choice', () => {
  const m = routeMap(route(0, [[0, 'BATTLE'], [1, 'REST']], []), choices.slice(0, 2));
  assert.deepEqual(m.cols[0].nodes.map((n) => n.state), ['now', 'now', 'blocked']);
  assert.equal(m.cols[0].nodes[2].choice, null);
  assert.equal(m.cols[0].nodes[2].card, -1);
});

test('the card index follows the stop, not the lane', () => {
  const m = routeMap(route(2, [[1, 'REST'], [2, 'SHRINE']], []), choices.slice(0, 2));
  assert.equal(m.cols[0].nodes[1].card, 0);
  assert.equal(m.cols[0].nodes[2].card, 1);
  assert.equal(m.cols[0].nodes[0].state, 'blocked');
});

test('scouted steps follow, a milestone is one relic stop in the middle lane', () => {
  const m = routeMap(route(1, [[0, 'BATTLE'], [1, 'REST'], [2, 'FORGE']], [{ floor: 3, milestone: false, kinds: ['SHRINE', 'BATTLE', 'VAULT'] }, { floor: 5, milestone: true, kinds: [] }]), choices);
  assert.equal(m.cols.length, 3);
  assert.deepEqual(m.cols[1].nodes.map((n) => n.kind), ['SHRINE', 'BATTLE', 'VAULT']);
  assert.deepEqual(m.cols[2].nodes.map((n) => [n.lane, n.kind, n.state]), [[1, 'RELIC', 'milestone']]);
});

test('lines join neighbouring lanes only, and only the first set is lit', () => {
  const m = routeMap(route(-1, [[0, 'BATTLE'], [1, 'REST'], [2, 'FORGE']], [{ floor: 3, milestone: false, kinds: ['SHRINE', 'BATTLE', 'VAULT'] }]), choices);
  assert.equal(m.edges.length, 7); // 0-0 0-1, 1-0 1-1 1-2, 2-1 2-2
  assert.ok(!m.edges.some((e) => Math.abs(e.fromLane - e.toLane) > 1));
  assert.ok(m.edges.every((e) => e.active));
});

test('lines to and from a milestone reach it from every lane; blocked stops have no lines', () => {
  const m = routeMap(route(0, [[0, 'BATTLE'], [1, 'REST']], [{ floor: 2, milestone: true, kinds: [] }]), choices.slice(0, 2));
  assert.equal(m.edges.length, 2);
  assert.ok(m.edges.every((e) => e.toLane === 1 && e.fromLane !== 2));
});

test('linked, kind names and positions', () => {
  assert.ok(linked(-1, 2) && linked(0, 1) && !linked(0, 2) && linked(2, 2));
  assert.equal(kindName('POOL'), 'Mystic Pool');
  assert.equal(kindName('BATTLE'), 'Battle');
  assert.equal(kindName('ODD'), 'ODD');
  assert.deepEqual(colCenters(1), [50]);
  const c = colCenters(4);
  assert.equal(c[0], (1.5 / 6) * 100);
  assert.ok(c[1] < c[2] && c[2] < c[3] && c[3] < 100 && c[0] < c[1]);
  assert.equal(Math.round(c[3] - c[2]), Math.round(c[2] - c[1]));
  assert.deepEqual([0, 1, 2].map(nodeY), [20, 50, 80]);
});

test('a route is only drawn when a step is on the table', () => {
  assert.equal(hasRoute(undefined), false);
  assert.equal(hasRoute({ route: { lane: -1, now: [], ahead: [{ floor: 3, milestone: false, kinds: [] }] } }), false);
  assert.equal(hasRoute({ route: { lane: -1, now: [{ lane: 0, card: 0, kind: 'REST' }], ahead: [] } }), true);
  assert.equal(colTitle({ milestone: false }, 0), 'Choose');
  assert.equal(colTitle({ milestone: true, floor: 5 }, 2), 'F5 relic');
  assert.equal(colTitle({ milestone: false, floor: 3 }, 1), 'After F3');
});

test('a stop carries the place its battle is fought in, and the name reads well', () => {
  const m = routeMap({ lane: -1, now: [{ lane: 0, card: 0, kind: 'BATTLE', arena: '' }, { lane: 1, card: 1, kind: 'REST', arena: 'cobbletowers:rootvale' }], ahead: [] }, choices.slice(0, 2));
  assert.equal(m.cols[0].nodes[0].arena, '');
  assert.equal(m.cols[0].nodes[1].arena, 'cobbletowers:rootvale');
  assert.equal(m.cols[0].nodes[2].arena, '');
  assert.equal(arenaName('cobbletowers:rootvale'), 'Rootvale');
  assert.equal(arenaName(''), '');
  assert.equal(arenaName(undefined), '');
  // an older server sends no arena at all
  assert.equal(routeMap({ lane: -1, now: [{ lane: 0, card: 0, kind: 'BATTLE' }], ahead: [] }, choices).cols[0].nodes[0].arena, '');
});
