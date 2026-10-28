package com.cobbletowers.encounter;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The level a tower encounter's enemies fight at, taken once per encounter (TDS #45): the mean of the two highest levels among every
 * registered Pokemon of every participant, fainted included, so a party cannot lower the enemy by walking in half dead, nor by padding
 * the bench or the team with low-level Pokemon. The only place tower level maths lives. Free of Minecraft types.
 */
public final class TowerLevelSnapshot {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 100;

    private TowerLevelSnapshot() {}

    /** How many of the highest levels set the enemy's. */
    public static final int TOP_COUNT = 2;

    /** The two highest levels' mean, rounded half up and clamped to [MIN_LEVEL, MAX_LEVEL]; empty when there are none. */
    public static OptionalInt of(Collection<Integer> levels) {
        if (levels == null) return OptionalInt.empty();
        List<Integer> highest = levels.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).limit(TOP_COUNT).toList();
        if (highest.isEmpty()) return OptionalInt.empty();
        long total = 0;
        for (int level : highest) total += level;
        long rounded = Math.round(total / (double) highest.size());
        return OptionalInt.of((int) Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, rounded)));
    }
}
