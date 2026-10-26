package com.cobbletowers.battle.cobblemon;

import com.cobbletowers.battle.cobblemon.CobblemonBattleAdapter.Binding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every index of the battles the tower has open, kept in step by one register and one forget. Free of Cobblemon types
 * so it can be tested. Server thread only.
 */
final class BattleIndex {

    final Map<UUID, Binding> byBattle = new LinkedHashMap<>();
    final Map<UUID, List<UUID>> byRun = new HashMap<>();
    /** When each battle last did something, for the watchdog (a start, a faint, a turn). */
    final Map<UUID, Long> lastActivity = new HashMap<>();
    /** When to look once at a battle that has not moved on from its first turn; one entry per floor battle. */
    final Map<UUID, Long> probeAt = new HashMap<>();
    final TurnTracker turns = new TurnTracker();

    void register(UUID battleId, Binding binding, long probeAfterMillis) {
        byBattle.put(battleId, binding);
        lastActivity.put(battleId, binding.startedAt());
        if (!binding.exhibition()) probeAt.put(battleId, binding.startedAt() + probeAfterMillis);
        byRun.computeIfAbsent(binding.runId(), key -> new ArrayList<>()).add(battleId);
    }

    /** Drops a battle from every index. @return its binding, null if it was not (or no longer) ours */
    Binding forget(UUID battleId) {
        lastActivity.remove(battleId);
        probeAt.remove(battleId);
        turns.forget(battleId);
        Binding binding = byBattle.remove(battleId);
        if (binding != null) {
            List<UUID> battles = byRun.get(binding.runId());
            if (battles != null) {
                battles.remove(battleId);
                if (battles.isEmpty()) byRun.remove(binding.runId());
            }
        }
        return binding;
    }

    void clear() {
        byBattle.clear();
        byRun.clear();
        lastActivity.clear();
        probeAt.clear();
        turns.clear();
    }
}
