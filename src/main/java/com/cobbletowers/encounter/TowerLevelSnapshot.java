package com.cobbletowers.encounter;

import java.util.Collection;
import java.util.OptionalInt;

/**
 * The level a tower encounter's enemies fight at, taken once per encounter (TDS #45).
 *
 * <p>A flat mean over every registered Pokemon of every participant, fainted ones included. Flat
 * rather than per player, so a six-Pokemon party and a one-Pokemon party are weighed by what they
 * bring; fainted ones included, so a party cannot lower the enemy by walking in half dead.
 *
 * <p>The only place tower level maths lives (TDS #45: do not scatter level formulas). Free of
 * Minecraft types so it is tested without a server.
 */
public final class TowerLevelSnapshot {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 100;

    private TowerLevelSnapshot() {}

    /** Rounded half up and clamped to [MIN_LEVEL, MAX_LEVEL]; empty when there is nothing to average. */
    public static OptionalInt of(Collection<Integer> levels) {
        if (levels == null) return OptionalInt.empty();
        long total = 0;
        int counted = 0;
        for (Integer level : levels) {
            if (level == null) continue;
            total += level;
            counted++;
        }
        if (counted == 0) return OptionalInt.empty();
        long rounded = Math.round(total / (double) counted);
        return OptionalInt.of((int) Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, rounded)));
    }
}
