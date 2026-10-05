package com.cobbletowers.economy;

/**
 * Which AscensionLib enemy tier (enemy-tiers.json) a tower enemy is generated from (decided 2026-10-05): floors 1-4 are
 * {@code trial_rank_1}, 5-9 are {@code trial_rank_2}, and floor 10 on, which includes every later cycle of an ascending
 * tower, is {@code trial_rank_3}. A milestone boss (floor 5 and floor 10 of each cycle) uses the {@code boss} tier.
 *
 * <p>The floor is the run's floor, which keeps counting past a tower's last floor, so deeper cycles never fall back to
 * an easier tier.
 */
public final class ScoutingTiers {

    private ScoutingTiers() {}

    public static String tierFor(int runFloor, boolean milestoneBoss) {
        if (milestoneBoss) return "boss";
        if (runFloor >= 10) return "trial_rank_3";
        if (runFloor >= 5) return "trial_rank_2";
        return "trial_rank_1";
    }
}
