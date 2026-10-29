package com.cobbletowers.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.route.RouteGraph.Kind;
import com.cobbletowers.route.RouteGraph.Rules;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RouteGraphTest {

    @Test
    void sameSeedSameKindsAndTheyAreDistinct() {
        for (long seed : new long[] {1, 42, -7, Long.MAX_VALUE}) {
            for (int floor = 1; floor <= 40; floor++) {
                List<Kind> a = RouteGraph.kindsAt(seed, floor, Rules.NONE);
                assertEquals(a, RouteGraph.kindsAt(seed, floor, Rules.NONE));
                assertEquals(RouteGraph.LANES, a.size());
                assertEquals(RouteGraph.LANES, new HashSet<>(a).size(), "kinds in a step are distinct: " + a);
            }
        }
    }

    @Test
    void differentFloorsGiveDifferentSteps() {
        Map<List<Kind>, Integer> seen = new HashMap<>();
        for (int floor = 1; floor <= 200; floor++) seen.merge(RouteGraph.kindsAt(99, floor, Rules.NONE), 1, Integer::sum);
        assertTrue(seen.size() > 20, "a seed does not repeat one step: " + seen.size());
    }

    @Test
    void everyKindShowsUpAndBattleIsCommon() {
        Map<Kind, Integer> counts = new HashMap<>();
        for (int floor = 1; floor <= 3000; floor++) for (Kind k : RouteGraph.kindsAt(5, floor, Rules.NONE)) counts.merge(k, 1, Integer::sum);
        for (Kind k : Kind.values()) {
            if (k == Kind.TRICK_OR_TREAT) continue;   // only an event tower's route offers it, tested below
            assertTrue(counts.getOrDefault(k, 0) > 100, k + " appears: " + counts);
        }
        assertTrue(counts.get(Kind.BATTLE) > counts.get(Kind.POOL), counts.toString());
    }

    @Test
    void theTrickOrTreatVaultIsOnlyOfferedWhereTheRulesSayAnEventTower() {
        Rules event = new Rules(false, false, Optional.empty(), true);
        Map<Kind, Integer> counts = new HashMap<>();
        for (int floor = 1; floor <= 3000; floor++) {
            assertFalse(RouteGraph.kindsAt(5, floor, Rules.NONE).contains(Kind.TRICK_OR_TREAT), "an ordinary tower never has it");
            for (Kind k : RouteGraph.kindsAt(5, floor, event)) counts.merge(k, 1, Integer::sum);
        }
        assertTrue(counts.getOrDefault(Kind.TRICK_OR_TREAT, 0) > 100, "an event tower has it: " + counts);
    }

    @Test
    void ruleNoRestRemovesRest() {
        for (int floor = 1; floor <= 500; floor++) assertFalse(RouteGraph.kindsAt(8, floor, new Rules(true, false, Optional.empty())).contains(Kind.REST));
    }

    @Test
    void relicFloorsRuleMakesEverySecondStepRelicRoomsOnly() {
        Rules rules = new Rules(false, true, Optional.empty());
        for (int floor = 2; floor <= 200; floor += 2) {
            for (Kind k : RouteGraph.kindsAt(3, floor, rules)) assertTrue(k == Kind.SHRINE || k == Kind.FORGE || k == Kind.LIBRARY, floor + " " + k);
        }
        boolean battle = false;
        for (int floor = 1; floor <= 200; floor += 2) battle |= RouteGraph.kindsAt(3, floor, rules).contains(Kind.BATTLE);
        assertTrue(battle, "odd floors are not restricted");
    }

    @Test
    void biasMakesTheLeanedRoomMoreCommon() {
        int plain = 0, leaned = 0;
        for (int floor = 1; floor <= 3000; floor++) {
            if (RouteGraph.kindsAt(11, floor, Rules.NONE).contains(Kind.POOL)) plain++;
            if (RouteGraph.kindsAt(11, floor, new Rules(false, false, Optional.of("pool"))).contains(Kind.POOL)) leaned++;
        }
        assertTrue(leaned > plain * 1.3, plain + " vs " + leaned);
    }

    @Test
    void lanesLinkToTheirNeighboursAndAStartCanGoAnywhere() {
        assertEquals(List.of(0, 1, 2), RouteGraph.reachable(-1));
        assertEquals(List.of(0, 1), RouteGraph.reachable(0));
        assertEquals(List.of(0, 1, 2), RouteGraph.reachable(1));
        assertEquals(List.of(1, 2), RouteGraph.reachable(2));
        assertTrue(RouteGraph.linked(0, 1));
        assertFalse(RouteGraph.linked(0, 2));
    }

    @Test
    void kindsMapToRoomsAndBack() {
        assertEquals(Optional.empty(), Kind.BATTLE.room());
        for (Kind kind : Kind.values()) assertEquals(kind, Kind.of(kind.room().orElse(null)));
        assertEquals(Kind.BATTLE, Kind.parse("nonsense"));
        assertEquals(Kind.FORGE, Kind.parse("FORGE"));
    }

    @Test
    void detoursAreDeterministicRareAndOnOneLane() {
        int detours = 0;
        for (int floor = 1; floor <= 3000; floor++) {
            int lane = RouteGraph.detourLane(21, floor);
            assertEquals(lane, RouteGraph.detourLane(21, floor));
            assertTrue(lane >= -1 && lane < RouteGraph.LANES);
            if (lane >= 0) detours++;
        }
        assertTrue(detours > 800 && detours < 1200, "about a third of steps: " + detours);
        int[] lanes = new int[3];
        for (int floor = 1; floor <= 3000; floor++) {
            int lane = RouteGraph.detourLane(21, floor);
            if (lane >= 0) lanes[lane]++;
        }
        for (int count : lanes) assertTrue(count > 200, java.util.Arrays.toString(lanes));
    }

    @Test
    void aDetourPlaceIsInRangeAndNoneWithoutPlaces() {
        assertEquals(-1, RouteGraph.detourPlace(5, 3, 0));
        for (int floor = 1; floor <= 200; floor++) {
            int p = RouteGraph.detourPlace(5, floor, 3);
            assertTrue(p >= 0 && p < 3);
        }
    }
}
