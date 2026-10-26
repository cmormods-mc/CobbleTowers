package com.cobbletowers.battle.cobblemon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Notices when a battle's turn counter moves: a fight with no faint is still a fight. Server thread only. */
final class TurnTracker {

    private final Map<UUID, Integer> last = new HashMap<>();

    /** True when {@code turn} differs from the previous reading; the first reading is only remembered. */
    boolean advanced(UUID battleId, int turn) {
        Integer before = last.put(battleId, turn);
        return before != null && before != turn;
    }

    void forget(UUID battleId) {
        last.remove(battleId);
    }

    void clear() {
        last.clear();
    }

    int size() {
        return last.size();
    }
}
