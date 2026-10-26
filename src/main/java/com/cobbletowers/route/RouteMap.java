package com.cobbletowers.route;

import com.cobbletowers.encounter.EncounterSeed;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The whole branching map of a run (P56): stops in rows, edges between adjacent rows, all drawn from the run seed. Pure.
 * Row 0 is the start, rows 1..{@code rows} are floors, the row after is the boss. Edges join the same or an adjacent lane,
 * so LEFT never links to RIGHT, and no two edges of a row cross.
 */
public final class RouteMap {

    public static final int LANES = RouteGraph.LANES;

    /** Its own ordinal space, apart from every other draw (see {@code DraftDraw}). */
    static final int ORDINAL_BASE = 6_200_047;

    /** Paths walked up from the start; more paths means more stops and more merges. */
    static final int PATHS = 4;

    /** What a stop is. Separate from {@link RouteGraph.Kind} on purpose: adding values there would shift every existing seed. */
    public enum Stop { START, BATTLE, ELITE, REST, SHRINE, FORGE, LIBRARY, VAULT, POOL, MERCHANT, TRICK_OR_TREAT, BOSS }

    public record Node(int row, int lane, Stop stop) {}

    /** A link from lane {@code fromLane} of row {@code row} to lane {@code toLane} of the row above. */
    public record Edge(int row, int fromLane, int toLane) {}

    public record Map(int rows, List<Node> nodes, List<Edge> edges) {
        /** The lanes of {@code row + 1} reachable from a stop on {@code lane} of {@code row}, ascending. */
        public List<Integer> next(int row, int lane) {
            List<Integer> out = new ArrayList<>(LANES);
            for (Edge e : edges) if (e.row() == row && e.fromLane() == lane) out.add(e.toLane());
            out.sort(null);
            return out;
        }
    }

    private RouteMap() {}

    /**
     * @param rows floors in the tower (the boss sits on the row after)
     * @param eventTower whether the tower is an event tower, which may use event-only stops
     */
    public static Map generate(long runSeed, int rows, boolean eventTower) {
        List<Edge> edges = new ArrayList<>();
        TreeSet<Long> seen = new TreeSet<>();
        for (int path = 0; path < PATHS; path++) {
            int lane = (int) Math.floorMod(EncounterSeed.of(runSeed, 1, ORDINAL_BASE + path), LANES);
            addEdge(edges, seen, 0, 1, lane);
            for (int row = 1; row < rows; row++) {
                int step = (int) Math.floorMod(EncounterSeed.of(runSeed, row, ORDINAL_BASE + 16 + path), 3) - 1;
                int to = Math.max(0, Math.min(LANES - 1, lane + step));
                if (crosses(edges, row, lane, to)) to = lane;
                addEdge(edges, seen, row, lane, to);
                lane = to;
            }
            addEdge(edges, seen, rows, lane, 1);
        }
        TreeSet<Long> placed = new TreeSet<>();
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(0, 1, Stop.START));
        for (Edge e : edges) {
            if (e.row() + 1 <= rows && placed.add(key(e.row() + 1, e.toLane()))) {
                nodes.add(new Node(e.row() + 1, e.toLane(), stopFor(runSeed, e.row() + 1, e.toLane(), rows, eventTower)));
            }
        }
        nodes.add(new Node(rows + 1, 1, Stop.BOSS));
        nodes.sort((a, b) -> a.row() != b.row() ? Integer.compare(a.row(), b.row()) : Integer.compare(a.lane(), b.lane()));
        edges.sort((a, b) -> a.row() != b.row() ? Integer.compare(a.row(), b.row()) : a.fromLane() != b.fromLane() ? Integer.compare(a.fromLane(), b.fromLane()) : Integer.compare(a.toLane(), b.toLane()));
        return new Map(rows, List.copyOf(nodes), List.copyOf(edges));
    }

    /**
     * What stands on a placed stop. This is the main tuning lever for how the map feels: how common each room is,
     * where elites may not appear, where a rest must come before the boss, and where the event-only stops fit.
     * Weights are untuned.
     */
    static Stop stopFor(long runSeed, int row, int lane, int rows, boolean eventTower) {
        if (row == rows) return Stop.REST;
        Stop[] stops = Stop.values();
        int[] weights = new int[stops.length];
        for (Stop stop : stops) weights[stop.ordinal()] = weightOf(stop, row, eventTower);
        int total = 0;
        for (int w : weights) total += w;
        long roll = Math.floorMod(EncounterSeed.of(runSeed, row, ORDINAL_BASE + 64 + lane), total);
        for (Stop stop : stops) {
            roll -= weights[stop.ordinal()];
            if (roll < 0) return stop;
        }
        return Stop.BATTLE;
    }

    /** How likely a stop is on a row; 0 where it may not stand. */
    static int weightOf(Stop stop, int row, boolean eventTower) {
        return switch (stop) {
            case BATTLE -> 40;
            case ELITE -> row >= 3 ? 12 : 0;
            case REST -> row >= 2 ? 12 : 0;
            case SHRINE, FORGE, LIBRARY -> 8;
            case VAULT, POOL -> row >= 2 ? 6 : 0;
            case MERCHANT -> row >= 2 ? 10 : 0;
            case TRICK_OR_TREAT -> eventTower ? 5 : 0;
            case START, BOSS -> 0;
        };
    }

    private static void addEdge(List<Edge> edges, TreeSet<Long> seen, int row, int from, int to) {
        if (seen.add(key(row, from * 10 + to))) edges.add(new Edge(row, from, to));
    }

    private static long key(int a, int b) {
        return a * 1000L + b;
    }

    /** Whether an edge from {@code from} to {@code to} in {@code row} would cross one already there. */
    private static boolean crosses(List<Edge> edges, int row, int from, int to) {
        for (Edge e : edges) {
            if (e.row() != row) continue;
            if ((e.fromLane() < from && e.toLane() > to) || (e.fromLane() > from && e.toLane() < to)) return true;
        }
        return false;
    }
}
