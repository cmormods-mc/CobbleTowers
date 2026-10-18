package com.cobbletowers.route;

import com.cobbletowers.network.IntermissionStatePayload.Route;
import com.cobbletowers.route.RouteGraph.Kind;
import com.cobbletowers.route.RouteGraph.Rules;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/** What the page draws of the route (P50): the stops on the table and the kinds of the steps scouting can see. Pure. */
public final class RouteView {

    /** Steps ahead the page is ever sent, however far the party can scout. */
    public static final int MAX_AHEAD = 6;

    private RouteView() {}

    /**
     * @param floor the floor just cleared; the open draft is the step after it
     * @param lastLane the lane the party stands on, or -1
     * @param lanes lane of each card of the open route draft, in card order (empty when there is none)
     * @param kinds kind name of each card, same order
     * @param ahead how many steps past this one scouting shows (clamped to {@link #MAX_AHEAD})
     * @param milestone whether a floor is a milestone (those steps are not choices; they pay a relic)
     */
    public static Route of(long seed, int floor, int lastLane, List<Integer> lanes, List<String> kinds, int ahead, Rules rules, IntPredicate milestone) {
        return of(seed, floor, lastLane, lanes, kinds, List.of(), ahead, rules, milestone);
    }

    /** As above, with the building each stop's battle is fought in (empty strings, or an empty list, for the run's own). */
    public static Route of(long seed, int floor, int lastLane, List<Integer> lanes, List<String> kinds, List<String> arenas, int ahead, Rules rules, IntPredicate milestone) {
        List<Route.Node> now = new ArrayList<>();
        for (int i = 0; i < lanes.size() && i < kinds.size(); i++) now.add(new Route.Node(lanes.get(i), i, kinds.get(i), i < arenas.size() ? arenas.get(i) : ""));
        List<Route.Step> steps = new ArrayList<>();
        int depth = Math.max(0, Math.min(MAX_AHEAD, ahead));
        for (int f = floor + 1; f <= floor + depth; f++) {
            if (milestone.test(f)) {
                steps.add(new Route.Step(f, true, List.of()));
            } else {
                List<String> names = new ArrayList<>();
                for (Kind kind : RouteGraph.kindsAt(seed, f, rules)) names.add(kind.name());
                steps.add(new Route.Step(f, false, names));
            }
        }
        return new Route(lastLane, now, steps);
    }
}
