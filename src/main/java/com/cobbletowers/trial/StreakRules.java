package com.cobbletowers.trial;

import java.util.ArrayList;
import java.util.List;

/**
 * The daily streak (P32) as pure rules over epoch days. A day qualifies when the daily attempt cleared enough floors.
 * A missed day spends a freeze if held, otherwise the streak restarts. A freeze is earned at each multiple of seven,
 * at most two held. Milestones pay once.
 */
public final class StreakRules {

    public static final int[] MILESTONES = {3, 7, 14, 30, 60, 100};
    public static final int FREEZE_EVERY = 7;
    public static final int MAX_FREEZES = 2;
    /** One-time CobbleDollars for each milestone, in the same order. */
    public static final int[] MILESTONE_REWARDS = {50, 100, 200, 400, 800, 1500};

    /**
     * A player's streak.
     * @param lastDay last qualifying day (epoch day), or {@link Long#MIN_VALUE} for never
     * @param streak consecutive qualifying days ending at {@code lastDay}
     * @param freezes freezes held
     * @param best longest streak
     * @param rewarded highest milestone paid (0 for none)
     */
    public record State(long lastDay, int streak, int freezes, int best, int rewarded) {
        public static final State NEW = new State(Long.MIN_VALUE, 0, 0, 0, 0);
    }

    /** What recording a qualifying day did. */
    public record Outcome(State state, List<Integer> milestones, boolean freezeSpent, boolean freezeEarned, boolean alreadyCounted) {}

    /** How the streak stands on a given day, before anything is recorded. */
    public enum Standing { NONE, SAFE, AT_RISK, FROZEN, BROKEN }

    private StreakRules() {}

    /** Records that {@code day} qualified. Idempotent for a day already counted. */
    public static Outcome qualify(State state, long day) {
        if (state.lastDay() == day) return new Outcome(state, List.of(), false, false, true);
        if (state.lastDay() != Long.MIN_VALUE && day < state.lastDay()) return new Outcome(state, List.of(), false, false, true);

        int streak;
        int freezes = state.freezes();
        boolean spent = false;
        if (state.lastDay() == Long.MIN_VALUE) {
            streak = 1;
        } else {
            long missed = day - state.lastDay() - 1;
            if (missed == 0) {
                streak = state.streak() + 1;
            } else if (missed <= freezes) {
                freezes -= (int) missed;
                spent = true;
                streak = state.streak() + 1;
            } else {
                streak = 1;
            }
        }
        boolean earned = false;
        if (streak > 0 && streak % FREEZE_EVERY == 0 && freezes < MAX_FREEZES && streak > state.streak()) {
            freezes++;
            earned = true;
        }
        int best = Math.max(state.best(), streak);

        List<Integer> reached = new ArrayList<>();
        int rewarded = state.rewarded();
        for (int milestone : MILESTONES) {
            if (streak >= milestone && milestone > rewarded) {
                reached.add(milestone);
                rewarded = milestone;
            }
        }
        return new Outcome(new State(day, streak, freezes, best, rewarded), List.copyOf(reached), spent, earned, false);
    }

    /**
     * What the streak is worth to show on {@code today}: counted already, still to do today, held by a freeze, or
     * gone.
     */
    public static Standing standing(State state, long today) {
        if (state.lastDay() == Long.MIN_VALUE) return Standing.NONE;
        long gap = today - state.lastDay();
        if (gap <= 0) return Standing.SAFE;
        if (gap == 1) return Standing.AT_RISK;
        long missed = gap - 1;
        return missed <= state.freezes() + 0 ? Standing.FROZEN : Standing.BROKEN;
    }

    /** The streak a player can show today: its length unless it is broken. */
    public static int effectiveStreak(State state, long today) {
        return standing(state, today) == Standing.BROKEN ? 0 : state.streak();
    }

    /** The reward for a milestone, or 0 if it is not one. */
    public static int rewardFor(int milestone) {
        for (int i = 0; i < MILESTONES.length; i++) if (MILESTONES[i] == milestone) return MILESTONE_REWARDS[i];
        return 0;
    }
}
