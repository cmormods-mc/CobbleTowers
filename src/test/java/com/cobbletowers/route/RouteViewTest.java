package com.cobbletowers.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.network.IntermissionStatePayload.Route;
import com.cobbletowers.route.RouteGraph.Rules;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteViewTest {

    @Test
    void theTableIsTheCardsLanesAndKinds() {
        Route route = RouteView.of(7, 2, 1, List.of(0, 1, 2), List.of("BATTLE", "FORGE", "REST"), 0, Rules.NONE, f -> false);
        assertEquals(1, route.lane());
        assertEquals(3, route.now().size());
        assertEquals(new Route.Node(1, 1, "FORGE", ""), route.now().get(1));
        assertTrue(route.ahead().isEmpty());
    }

    @Test
    void aStopCarriesTheBuildingItsBattleIsFoughtIn() {
        Route route = RouteView.of(7, 2, -1, List.of(0, 1), List.of("BATTLE", "REST"), List.of("", "cobbletowers:rootvale"), 0, Rules.NONE, f -> false);
        assertEquals("", route.now().get(0).arena());
        assertEquals("cobbletowers:rootvale", route.now().get(1).arena());
    }

    @Test
    void aheadIsTheSameKindsTheServerWillOfferThen() {
        Route route = RouteView.of(7, 2, -1, List.of(), List.of(), 3, Rules.NONE, f -> false);
        assertTrue(route.now().isEmpty());
        assertEquals(3, route.ahead().size());
        for (int i = 0; i < 3; i++) {
            Route.Step step = route.ahead().get(i);
            assertEquals(3 + i, step.floor());
            assertEquals(RouteGraph.kindsAt(7, 3 + i, Rules.NONE).stream().map(Enum::name).toList(), step.kinds());
        }
    }

    @Test
    void aMilestoneStepIsMarkedAndHasNoKinds() {
        Route route = RouteView.of(7, 3, 0, List.of(), List.of(), 3, Rules.NONE, f -> f == 5);
        assertEquals(List.of(false, true, false), route.ahead().stream().map(Route.Step::milestone).toList());
        assertTrue(route.ahead().get(1).kinds().isEmpty());
        assertEquals(3, route.ahead().get(0).kinds().size());
    }

    @Test
    void howFarScoutingSeesIsBoundedAndNeverNegative() {
        assertEquals(RouteView.MAX_AHEAD, RouteView.of(1, 1, -1, List.of(), List.of(), 99, Rules.NONE, f -> false).ahead().size());
        assertEquals(0, RouteView.of(1, 1, -1, List.of(), List.of(), -4, Rules.NONE, f -> false).ahead().size());
    }

    @Test
    void ruleChangesReachTheKindsShown() {
        Rules noRest = new Rules(true, false, java.util.Optional.empty());
        for (int floor = 1; floor <= 60; floor++) {
            for (Route.Step step : RouteView.of(9, floor, -1, List.of(), List.of(), 2, noRest, f -> false).ahead()) {
                assertTrue(!step.kinds().contains("REST"), step.toString());
            }
        }
    }
}
