package com.cobbletowers.reward;

import java.util.Random;

/**
 * Diminishing returns on the tower's own loot for repeat completions in one trial day: the first {@value #FULL_COMPLETIONS} pay in full, the next
 * {@value #FULL_COMPLETIONS} at {@value #REDUCED_PERCENT} percent, and every one after at {@value #LOW_PERCENT} percent. A reduced completion keeps each
 * rolled item with that probability (amounts are never rounded down to nothing). It applies to the tower's table only, never to AscensionLib's pile.
 * Pure.
 */
public final class DailyCompletionBudget {

    public static final int FULL_COMPLETIONS = 3;
    public static final int REDUCED_PERCENT = 50;
    public static final int LOW_PERCENT = 25;

    private DailyCompletionBudget() {}

    /** The share of the loot paid when the player has already completed {@code completionsToday} towers today. */
    public static int percentFor(int completionsToday) {
        if (completionsToday < FULL_COMPLETIONS) return 100;
        return completionsToday < 2 * FULL_COMPLETIONS ? REDUCED_PERCENT : LOW_PERCENT;
    }

    /** Whether one rolled item is kept at {@code percent}. Seeded, so a retry keeps the same items. */
    public static boolean keeps(long seed, int percent) {
        if (percent >= 100) return true;
        return new Random(seed).nextInt(100) < percent;
    }
}
