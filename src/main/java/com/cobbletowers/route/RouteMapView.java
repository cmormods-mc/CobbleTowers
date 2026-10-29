package com.cobbletowers.route;

import com.cobbletowers.network.IntermissionStatePayload.Route.Graph;
import com.cobbletowers.route.RouteGraph.Kind;
import com.cobbletowers.route.RouteMap.Stop;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Where a run stands on its {@link RouteMap} and what the page draws of it (P56). Pure. */
public final class RouteMapView {

    private RouteMapView() {}

    /**
     * The map of the cycle the next floor is in, and the way the party came.
     * @param trail the lane of the party at each row from the start (row 0) to where it stands now; the last entry is its stop
     */
    public record Spot(RouteMap.Map map, List<Integer> trail) {
        /** The row of the stop the party stands on (0 is the start); the next step offers row + 1. */
        public int row() {
            return trail.size() - 1;
        }

        public int lane() {
            return trail.get(trail.size() - 1);
        }

        /** The lanes the party can step to, ascending. */
        public List<Integer> reachable() {
            return map.next(row(), lane());
        }
    }

    /**
     * @param floor the run floor just cleared (0 before the first)
     * @param rows floors in one cycle of the tower
     * @param laneAtFloor the lane taken at the step after a run floor, or -1 when none was recorded
     */
    public static Spot at(long runSeed, int floor, int rows, boolean eventTower, IntPredicate milestone, IntUnaryOperator laneAtFloor) {
        int row = floor % rows, cycleStart = floor - row;
        RouteMap.Map map = RouteMap.generate(RouteMap.cycleSeed(runSeed, floor / rows), rows, eventTower, milestone, RouteMap.PLAYABLE);
        List<Integer> trail = new ArrayList<>(row + 1);
        trail.add(RouteMap.MIDDLE);
        for (int r = 1; r <= row; r++) {
            // a forced row (the first floor, a milestone, the floor after one) stands in the middle lane; any other row stands where the party went
            int lane = RouteMap.forced(milestone, r) ? RouteMap.MIDDLE : laneAtFloor.applyAsInt(cycleStart + r - 1);
            if (lane < 0 || map.stopAt(r, lane) == null) lane = RouteMap.MIDDLE;
            trail.add(lane);
        }
        return new Spot(map, List.copyOf(trail));
    }

    /** The kind a placed stop is drawn as by the rest of the run: one of the stops with a room today, else a plain battle. */
    public static Kind kindOf(Stop stop) {
        if (stop == null) return Kind.BATTLE;
        return switch (stop) {
            case REST -> Kind.REST;
            case SHRINE -> Kind.SHRINE;
            case FORGE -> Kind.FORGE;
            case LIBRARY -> Kind.LIBRARY;
            case VAULT -> Kind.VAULT;
            case POOL -> Kind.POOL;
            case TRICK_OR_TREAT -> Kind.TRICK_OR_TREAT;
            default -> Kind.BATTLE;
        };
    }

    /**
     * The whole map as the page draws it.
     * @param reveal the highest row whose kinds the party can see (a boss row always shows)
     */
    public static Graph graph(Spot spot, int reveal) {
        List<Graph.Stop> nodes = new ArrayList<>();
        for (RouteMap.Node n : spot.map().nodes()) nodes.add(new Graph.Stop(n.row(), n.lane(), n.stop().name()));
        List<Graph.Link> links = new ArrayList<>();
        for (RouteMap.Edge e : spot.map().edges()) links.add(new Graph.Link(e.row(), e.fromLane(), e.toLane()));
        return new Graph(spot.map().rows(), reveal, spot.trail(), nodes, links);
    }
}
