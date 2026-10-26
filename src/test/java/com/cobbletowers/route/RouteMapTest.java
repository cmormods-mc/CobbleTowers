package com.cobbletowers.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.route.RouteMap.Edge;
import com.cobbletowers.route.RouteMap.Node;
import com.cobbletowers.route.RouteMap.Stop;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RouteMapTest {

    private static final long[] SEEDS = {1, 42, -7, 123456789L, Long.MAX_VALUE};

    @Test
    void sameSeedSameMap() {
        for (long seed : SEEDS) assertEquals(RouteMap.generate(seed, 10, false), RouteMap.generate(seed, 10, false));
    }

    @Test
    void edgesJoinAdjacentRowsAndAdjacentLanesAndNeverCross() {
        for (long seed : SEEDS) {
            RouteMap.Map map = RouteMap.generate(seed, 10, false);
            for (Edge a : map.edges()) {
                assertTrue(Math.abs(a.fromLane() - a.toLane()) <= 1 || a.row() == 0 || a.row() == map.rows(), "no jump across lanes: " + a);
                if (a.row() == 0 || a.row() == map.rows()) continue;
                for (Edge b : map.edges()) {
                    if (b.row() != a.row()) continue;
                    boolean cross = (a.fromLane() < b.fromLane() && a.toLane() > b.toLane()) || (a.fromLane() > b.fromLane() && a.toLane() < b.toLane());
                    assertFalse(cross, "edges cross in row " + a.row() + ": " + a + " " + b);
                }
            }
        }
    }

    @Test
    void everyNodeIsReachableFromTheStartAndReachesTheBoss() {
        for (long seed : SEEDS) {
            RouteMap.Map map = RouteMap.generate(seed, 10, false);
            Set<String> up = new HashSet<>(Set.of("0:1"));
            for (int row = 0; row <= map.rows(); row++) {
                for (Node n : map.nodes()) {
                    if (n.row() != row || !up.contains(row + ":" + n.lane())) continue;
                    for (int to : map.next(row, n.lane())) up.add((row + 1) + ":" + to);
                }
            }
            Set<String> down = new HashSet<>(Set.of((map.rows() + 1) + ":1"));
            for (int row = map.rows(); row >= 0; row--) {
                for (Edge e : map.edges()) if (e.row() == row && down.contains((row + 1) + ":" + e.toLane())) down.add(row + ":" + e.fromLane());
            }
            for (Node n : map.nodes()) {
                String id = n.row() + ":" + n.lane();
                assertTrue(up.contains(id), "unreachable from start: " + n + " seed " + seed);
                assertTrue(down.contains(id), "dead end: " + n + " seed " + seed);
            }
        }
    }

    @Test
    void startBossAndRestBeforeBoss() {
        for (long seed : SEEDS) {
            RouteMap.Map map = RouteMap.generate(seed, 10, false);
            assertEquals(new Node(0, 1, Stop.START), map.nodes().get(0));
            assertEquals(new Node(11, 1, Stop.BOSS), map.nodes().get(map.nodes().size() - 1));
            for (Node n : map.nodes()) if (n.row() == 10) assertEquals(Stop.REST, n.stop());
        }
    }

    @Test
    void placementRules() {
        for (long seed = 0; seed < 200; seed++) {
            for (Node n : RouteMap.generate(seed, 10, false).nodes()) {
                if (n.row() <= 2) assertFalse(n.stop() == Stop.ELITE, "no elite early: " + n);
                assertFalse(n.stop() == Stop.TRICK_OR_TREAT, "trick or treat is event only: " + n);
            }
        }
        boolean seen = false;
        for (long seed = 0; seed < 200; seed++) {
            for (Node n : RouteMap.generate(seed, 10, true).nodes()) seen |= n.stop() == Stop.TRICK_OR_TREAT;
        }
        assertTrue(seen, "an event tower rolls trick or treat sometimes");
    }
}
