package com.cobbletowers.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.route.RouteGraph.Kind;
import com.cobbletowers.route.RouteMap.Stop;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class RouteMapViewTest {

    private static final IntPredicate MILESTONES = row -> row == 5 || row == 10;

    @Test
    void theRunStartsAtTheStartAndCanStepToRowOne() {
        var spot = RouteMapView.at(42, 0, 10, false, MILESTONES, f -> -1);
        assertEquals(0, spot.row());
        assertEquals(RouteMap.MIDDLE, spot.lane());
        assertFalse(spot.reachable().isEmpty());
        for (int lane : spot.reachable()) assertTrue(spot.map().stopAt(1, lane) != null);
    }

    @Test
    void thePartyStandsWhereItWentAndReachesWhatThatStopLinksTo() {
        var first = RouteMapView.at(42, 0, 10, false, MILESTONES, f -> -1);
        int lane = first.reachable().get(0);
        var spot = RouteMapView.at(42, 1, 10, false, MILESTONES, f -> lane);
        assertEquals(1, spot.row());
        assertEquals(lane, spot.lane());
        assertEquals(first.map().next(1, lane), spot.reachable());
    }

    @Test
    void aMilestoneStandsInTheMiddleWhateverLaneWasRecorded() {
        var spot = RouteMapView.at(42, 5, 10, false, MILESTONES, f -> 0);
        assertEquals(5, spot.row());
        assertEquals(RouteMap.MIDDLE, spot.lane());
        assertEquals(Stop.BOSS, spot.map().stopAt(5, 1));
    }

    @Test
    void theNextCycleStartsOverFromTheStartOnAFreshMap() {
        var end = RouteMapView.at(42, 10, 10, false, MILESTONES, f -> -1);
        assertEquals(0, end.row());
        assertFalse(RouteMapView.at(42, 0, 10, false, MILESTONES, f -> -1).map().equals(end.map()));
    }

    @Test
    void stopsWithoutARoomYetReadAsABattle() {
        assertEquals(Kind.BATTLE, RouteMapView.kindOf(Stop.ELITE));
        assertEquals(Kind.BATTLE, RouteMapView.kindOf(Stop.MERCHANT));
        assertEquals(Kind.BATTLE, RouteMapView.kindOf(Stop.TRICK_OR_TREAT));
        assertEquals(Kind.BATTLE, RouteMapView.kindOf(null));
        assertEquals(Kind.FORGE, RouteMapView.kindOf(Stop.FORGE));
        assertEquals(Kind.REST, RouteMapView.kindOf(Stop.REST));
    }

    @Test
    void theGraphCarriesEveryStopAndLink() {
        var spot = RouteMapView.at(7, 3, 10, false, MILESTONES, f -> -1);
        var graph = RouteMapView.graph(spot, 5);
        assertEquals(10, graph.rows());
        assertEquals(spot.map().nodes().size(), graph.stops().size());
        assertEquals(spot.map().edges().size(), graph.links().size());
    }
}
