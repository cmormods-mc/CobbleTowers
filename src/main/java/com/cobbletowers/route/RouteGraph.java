package com.cobbletowers.route;

import com.cobbletowers.encounter.EncounterSeed;
import com.cobbletowers.intermission.IntermissionEvents.Room;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The route between floors (P50): three lanes, a kind of stop on each, drawn from the run seed. Pure. The kind of a stop is the
 * room visited before the next battle ({@link Kind#BATTLE} is none); the modifier that rides on it is drawn when the step is
 * reached, from the pool and the rules that apply then, so only kinds are known ahead of time.
 */
public final class RouteGraph {

    /** The lanes of a step. */
    public static final int LANES = 3;

    /** Its own ordinal space, like every other draw (see {@code DraftDraw}). */
    static final int ORDINAL_BASE = 6_000_041;

    /** How likely a plain battle stop is, against the rooms' own weights (untuned). */
    static final int BATTLE_WEIGHT = 40;

    /** Its own ordinal space for the detours. */
    static final int DETOUR_ORDINAL_BASE = 6_100_043;

    /** A step has a detour (one lane fights in another region's building) one time in this many. */
    static final int DETOUR_ONE_IN = 3;

    private RouteGraph() {}

    /** What a stop is. */
    public enum Kind {
        BATTLE(null), REST(Room.REST), SHRINE(Room.SHRINE), GAMBLER(Room.GAMBLER), FORGE(Room.FORGE),
        LIBRARY(Room.LIBRARY), VAULT(Room.VAULT), POOL(Room.POOL);

        private final Room room;

        Kind(Room room) {
            this.room = room;
        }

        /** The intermission room this stop opens; empty for a plain battle. */
        public Optional<Room> room() {
            return Optional.ofNullable(room);
        }

        public static Kind of(Room room) {
            for (Kind kind : values()) if (kind.room == room) return kind;
            return BATTLE;
        }

        /** Parses a stored name; anything unknown reads as a plain battle. */
        public static Kind parse(String name) {
            for (Kind kind : values()) if (kind.name().equals(name)) return kind;
            return BATTLE;
        }
    }

    /**
     * What a weekly rule or a trial changes about the stops.
     * @param noRest Rest stops are taken away
     * @param relicFloors every second floor offers only relic rooms (Shrine, Forge, Library)
     * @param bias the room a rotating trial leans toward (lower case), if any
     */
    public record Rules(boolean noRest, boolean relicFloors, Optional<String> bias) {
        public static final Rules NONE = new Rules(false, false, Optional.empty());
    }

    /** Whether a path can run from a stop in {@code from} to a stop in {@code to}; a run with no stop yet can start anywhere. */
    public static boolean linked(int from, int to) {
        return from < 0 || Math.abs(from - to) <= 1;
    }

    /** The lanes a party standing on {@code from} can step to (all of them when it stands on none). */
    public static List<Integer> reachable(int from) {
        List<Integer> lanes = new ArrayList<>(LANES);
        for (int lane = 0; lane < LANES; lane++) if (linked(from, lane)) lanes.add(lane);
        return lanes;
    }

    /** The kinds of the {@link #LANES} stops of the step after {@code floorIndex}: distinct, in lane order. */
    public static List<Kind> kindsAt(long runSeed, int floorIndex, Rules rules) {
        boolean relicOnly = rules.relicFloors() && floorIndex % 2 == 0;
        List<Kind> pool = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            if (kind == Kind.REST && rules.noRest()) continue;
            if (relicOnly && kind != Kind.SHRINE && kind != Kind.FORGE && kind != Kind.LIBRARY) continue;
            pool.add(kind);
        }
        List<Kind> out = new ArrayList<>(LANES);
        for (int lane = 0; lane < LANES && !pool.isEmpty(); lane++) {
            int total = 0;
            for (Kind kind : pool) total += weightOf(kind, rules);
            long roll = Math.floorMod(EncounterSeed.of(runSeed, floorIndex, ORDINAL_BASE + lane), total);
            Kind picked = pool.get(pool.size() - 1);
            for (Kind kind : pool) {
                roll -= weightOf(kind, rules);
                if (roll < 0) {
                    picked = kind;
                    break;
                }
            }
            out.add(picked);
            pool.remove(picked);
        }
        return List.copyOf(out);
    }

    /**
     * The lane of the step after {@code floorIndex} whose battle is fought in another region's building (a detour), or -1 for none.
     * One lane at most, and only one step in {@link #DETOUR_ONE_IN}: a building is a heavy thing to put up, so this is rare.
     */
    public static int detourLane(long runSeed, int floorIndex) {
        if (Math.floorMod(EncounterSeed.of(runSeed, floorIndex, DETOUR_ORDINAL_BASE), DETOUR_ONE_IN) != 0) return -1;
        return (int) Math.floorMod(EncounterSeed.of(runSeed, floorIndex, DETOUR_ORDINAL_BASE + 1), LANES);
    }

    /** Which of {@code places} a detour at this step goes to; -1 when there is none to go to. */
    public static int detourPlace(long runSeed, int floorIndex, int places) {
        return places <= 0 ? -1 : (int) Math.floorMod(EncounterSeed.of(runSeed, floorIndex, DETOUR_ORDINAL_BASE + 2), places);
    }

    static int weightOf(Kind kind, Rules rules) {
        return kind == Kind.BATTLE ? BATTLE_WEIGHT : com.cobbletowers.intermission.IntermissionEvents.weightOf(kind.room, rules.bias());
    }
}
