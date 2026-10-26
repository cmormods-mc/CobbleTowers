package com.cobbletowers.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.route.RouteMap.Edge;
import com.cobbletowers.route.RouteMap.Node;
import com.cobbletowers.route.RouteMap.Stop;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class RouteMapTest {

    private static final long[] SEEDS = {1, 42, -7, 123456789L, Long.MAX_VALUE};
    private static final IntPredicate MILESTONES = row -> row == 5 || row == 10;
    private static final Set<Stop> ALL = EnumSet.allOf(Stop.class);

    private static RouteMap.Map map(long seed) {
        return RouteMap.generate(seed, 10, false, MILESTONES, ALL);
    }

    @Test
    void sameSeedSameMap() {
        for (long seed : SEEDS) assertEquals(map(seed), map(seed));
    }

    @Test
    void cycleZeroKeepsTheRunSeedAndLaterCyclesDiffer() {
        assertEquals(42L, RouteMap.cycleSeed(42L, 0));
        assertNotEquals(RouteMap.cycleSeed(42L, 0), RouteMap.cycleSeed(42L, 1));
        assertNotEquals(RouteMap.cycleSeed(42L, 1), RouteMap.cycleSeed(42L, 2));
    }

    @Test
    void edgesJoinAdjacentLanesAndNeverCross() {
        for (long seed : SEEDS) {
            RouteMap.Map map = map(seed);
            for (Edge a : map.edges()) {
                assertTrue(Math.abs(a.fromLane() - a.toLane()) <= 1, "no jump across lanes: " + a);
                for (Edge b : map.edges()) {
                    if (b.row() != a.row()) continue;
                    boolean cross = (a.fromLane() < b.fromLane() && a.toLane() > b.toLane()) || (a.fromLane() > b.fromLane() && a.toLane() < b.toLane());
                    assertFalse(cross, "edges cross in row " + a.row() + ": " + a + " " + b);
                }
            }
        }
    }

    @Test
    void everyNodeIsReachableFromTheStartAndEveryNodeButTheLastRowLeads() {
        for (long seed : SEEDS) {
            RouteMap.Map map = map(seed);
            Set<String> up = new HashSet<>(Set.of("0:1"));
            for (int row = 0; row < map.rows(); row++) {
                for (Node n : map.nodes()) {
                    if (n.row() != row || !up.contains(row + ":" + n.lane())) continue;
                    for (int to : map.next(row, n.lane())) up.add((row + 1) + ":" + to);
                }
            }
            for (Node n : map.nodes()) {
                assertTrue(up.contains(n.row() + ":" + n.lane()), "unreachable from start: " + n + " seed " + seed);
                if (n.row() < map.rows()) assertFalse(map.next(n.row(), n.lane()).isEmpty(), "dead end: " + n + " seed " + seed);
                assertTrue(map.stopAt(n.row(), n.lane()) == n.stop());
            }
        }
    }

    @Test
    void milestoneRowsAreOneBossInTheMiddleAndTheRowBeforeRests() {
        for (long seed : SEEDS) {
            RouteMap.Map map = map(seed);
            assertEquals(new Node(0, 1, Stop.START), map.nodes().get(0));
            for (Node n : map.nodes()) {
                if (MILESTONES.test(n.row())) {
                    assertEquals(Stop.BOSS, n.stop());
                    assertEquals(1, n.lane());
                } else if (n.row() > 0) {
                    assertNotEquals(Stop.BOSS, n.stop(), "a boss only on a milestone row: " + n);
                }
                if (MILESTONES.test(n.row() + 1)) assertEquals(Stop.REST, n.stop(), "rest before a boss: " + n);
            }
            assertEquals(List.of(new Node(1, 1, Stop.BATTLE)), map.nodes().stream().filter(n -> n.row() == 1).toList(), "the first floor has no choice");
            assertEquals(1, map.nodes().stream().filter(n -> n.row() == 5).count());
            assertEquals(1, map.nodes().stream().filter(n -> n.row() == 10).count());
        }
    }

    @Test
    void placementRules() {
        for (long seed = 0; seed < 200; seed++) {
            for (Node n : map(seed).nodes()) {
                if (n.row() <= 2) assertFalse(n.stop() == Stop.ELITE, "no elite early: " + n);
                assertFalse(n.stop() == Stop.TRICK_OR_TREAT, "trick or treat is event only: " + n);
            }
        }
        boolean seen = false;
        for (long seed = 0; seed < 200; seed++) {
            for (Node n : RouteMap.generate(seed, 10, true, MILESTONES, ALL).nodes()) seen |= n.stop() == Stop.TRICK_OR_TREAT;
        }
        assertTrue(seen, "an event tower rolls trick or treat sometimes");
    }

    @Test
    void aLiveRunPlacesOnlyStopsThatHaveGameplay() {
        for (long seed = 0; seed < 200; seed++) {
            for (Node n : RouteMap.generate(seed, 10, true, MILESTONES, RouteMap.PLAYABLE).nodes()) {
                assertTrue(RouteMap.PLAYABLE.contains(n.stop()), "not playable yet: " + n);
            }
        }
    }
}
