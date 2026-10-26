package com.cobbletowers.route;

import com.cobbletowers.encounter.EncounterSeed;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * The whole branching map of one cycle of a tower (P56): stops in rows, edges between adjacent rows, all drawn from the run
 * seed. Pure. Row 0 is the start, rows 1..{@code rows} are floors. Row 1 (the first floor has no choice) is one battle in the
 * middle lane, and so is a milestone row (a boss); every path funnels through them. Edges join the same or an adjacent lane, so LEFT never links to RIGHT, and no two edges of a row cross.
 */
public final class RouteMap {

    public static final int LANES = RouteGraph.LANES;
    public static final int MIDDLE = 1;

    /** Its own ordinal space, apart from every other draw (see {@code DraftDraw}). */
    static final int ORDINAL_BASE = 6_200_047;

    /** Paths walked up from the start; more paths means more stops and more merges. */
    static final int PATHS = 4;

    /** What a stop is. Separate from {@link RouteGraph.Kind} on purpose: adding values there would shift every existing seed. */
    public enum Stop { START, BATTLE, ELITE, REST, SHRINE, FORGE, LIBRARY, VAULT, POOL, MERCHANT, TRICK_OR_TREAT, BOSS }

    /** The stops that have gameplay today; a live run draws only from these. */
    public static final Set<Stop> PLAYABLE = EnumSet.of(Stop.START, Stop.BATTLE, Stop.REST, Stop.SHRINE, Stop.FORGE, Stop.LIBRARY,
            Stop.VAULT, Stop.POOL, Stop.BOSS);

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

        /** What stands on a lane of a row, or null where nothing does. */
        public Stop stopAt(int row, int lane) {
            for (Node n : nodes) if (n.row() == row && n.lane() == lane) return n.stop();
            return null;
        }
    }

    private RouteMap() {}

    /** The seed of the map for the cycle {@code cycle} of a run (0 is the first), so each cycle of an ascending tower differs. */
    public static long cycleSeed(long runSeed, int cycle) {
        return cycle == 0 ? runSeed : EncounterSeed.of(runSeed, cycle, ORDINAL_BASE + 200);
    }

    /**
     * @param rows floors in the cycle
     * @param eventTower whether the tower is an event tower, which may use event-only stops
     * @param milestone whether a row (1..rows) is a milestone, which is a lone boss stop
     * @param playable the stops that may be placed
     */
    public static Map generate(long seed, int rows, boolean eventTower, IntPredicate milestone, Set<Stop> playable) {
        List<Edge> edges = new ArrayList<>();
        TreeSet<Long> seen = new TreeSet<>();
        for (int path = 0; path < PATHS; path++) {
            // the first floor has no choice, so every path starts on its one stop and fans out from there
            int lane = MIDDLE;
            addEdge(edges, seen, 0, MIDDLE, lane);
            for (int row = 1; row < rows; row++) {
                int to = milestone.test(row + 1) ? MIDDLE : fanOut(seed, row, path, lane, edges);
                addEdge(edges, seen, row, lane, to);
                lane = to;
            }
        }
        TreeSet<Long> placed = new TreeSet<>();
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(0, MIDDLE, Stop.START));
        for (Edge e : edges) {
            int row = e.row() + 1;
            if (placed.add(key(row, e.toLane()))) {
                nodes.add(new Node(row, e.toLane(), milestone.test(row) ? Stop.BOSS : row == 1 ? Stop.BATTLE : stopFor(seed, row, e.toLane(), eventTower, milestone, playable)));
            }
        }
        nodes.sort((a, b) -> a.row() != b.row() ? Integer.compare(a.row(), b.row()) : Integer.compare(a.lane(), b.lane()));
        edges.sort((a, b) -> a.row() != b.row() ? Integer.compare(a.row(), b.row()) : a.fromLane() != b.fromLane() ? Integer.compare(a.fromLane(), b.fromLane()) : Integer.compare(a.toLane(), b.toLane()));
        return new Map(rows, List.copyOf(nodes), List.copyOf(edges));
    }

    private static int fanOut(long seed, int row, int path, int lane, List<Edge> edges) {
        int step = (int) Math.floorMod(EncounterSeed.of(seed, row, ORDINAL_BASE + 16 + path), 3) - 1;
        int to = Math.max(0, Math.min(LANES - 1, lane + step));
        return crosses(edges, row, lane, to) ? lane : to;
    }

    /**
     * What stands on a placed stop. This is the main tuning lever for how the map feels: how common each room is,
     * where elites may not appear, where a rest must come before the boss, and where the event-only stops fit.
     * Weights are untuned.
     */
    static Stop stopFor(long seed, int row, int lane, boolean eventTower, IntPredicate milestone, Set<Stop> playable) {
        if (milestone.test(row + 1) && playable.contains(Stop.REST)) return Stop.REST;
        Stop[] stops = Stop.values();
        int[] weights = new int[stops.length];
        int total = 0;
        for (Stop stop : stops) {
            weights[stop.ordinal()] = playable.contains(stop) ? weightOf(stop, row, eventTower) : 0;
            total += weights[stop.ordinal()];
        }
        if (total == 0) return Stop.BATTLE;
        long roll = Math.floorMod(EncounterSeed.of(seed, row, ORDINAL_BASE + 64 + lane), total);
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
