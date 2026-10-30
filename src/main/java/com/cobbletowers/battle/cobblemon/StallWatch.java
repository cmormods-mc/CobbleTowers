package com.cobbletowers.battle.cobblemon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notices a battle that nobody can move in: every actor idle (no request, nothing to choose, no answer waiting) on one
 * turn for a couple of seconds. A healthy battle is idle for milliseconds between a choice and the next request, so this
 * only ever sees the Cobblemon stall where the opponent's choice was never written to Showdown. Server thread only.
 */
final class StallWatch {

    /** How long every actor must have been idle on one turn before the opponent is told to choose. */
    static final long IDLE_MILLIS = 2_000;
    /** How long to wait before telling it again, and how many times in all, for one turn. */
    static final long REPEAT_MILLIS = 3_000;
    static final int MAX_NUDGES = 4;

    private record Idle(int turn, long since, int nudges, long lastNudge) {}

    private final Map<UUID, Idle> idle = new HashMap<>();

    /**
     * @param allIdle whether every actor of the battle is idle right now
     * @return true when the opponent should be told to choose now
     */
    boolean observe(UUID battleId, int turn, boolean allIdle, long now) {
        if (!allIdle) {
            idle.remove(battleId);
            return false;
        }
        Idle seen = idle.get(battleId);
        if (seen == null || seen.turn() != turn) {
            idle.put(battleId, new Idle(turn, now, 0, 0));
            return false;
        }
        if (now - seen.since() < IDLE_MILLIS || seen.nudges() >= MAX_NUDGES) return false;
        if (seen.nudges() > 0 && now - seen.lastNudge() < REPEAT_MILLIS) return false;
        idle.put(battleId, new Idle(turn, seen.since(), seen.nudges() + 1, now));
        return true;
    }

    void forget(UUID battleId) {
        idle.remove(battleId);
    }

    void clear() {
        idle.clear();
    }

    int size() {
        return idle.size();
    }
}
