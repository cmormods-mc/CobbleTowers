package com.cobbletowers.trial;

/**
 * A trial attempt's score (P32), one integer, higher is better, and a pure function of what happened.
 *
 * <p>Floors dominate, so a wipe on floor four of five still outranks a clean run that quit on floor two. On top of that: a bonus
 * for finishing, a bonus for speed (against a par of three minutes a floor), a penalty per fainted Pokemon, and ten points per
 * point of difficulty (the playlist, the mutators and the party size are all in the difficulty score).
 */
public final class TrialScoring {

    public static final int PER_FLOOR = 1000;
    public static final int COMPLETION_BONUS = 500;
    public static final long PAR_MILLIS_PER_FLOOR = 3 * 60 * 1000L;
    public static final int FAINT_PENALTY = 100;
    public static final int PER_DIFFICULTY_POINT = 10;

    private TrialScoring() {}

    /**
     * @param floorsCleared floors cleared before the run ended
     * @param floors        the trial's length
     * @param activeMillis  time spent fighting (intermissions do not count)
     * @param faints        player Pokemon that fainted
     * @param difficulty    the run's difficulty score
     */
    public static int score(int floorsCleared, int floors, long activeMillis, int faints, int difficulty) {
        int cleared = Math.max(0, Math.min(floorsCleared, floors));
        long score = (long) cleared * PER_FLOOR;
        if (cleared >= floors) {
            score += COMPLETION_BONUS;
            long par = floors * PAR_MILLIS_PER_FLOOR;
            score += Math.max(0, par - Math.max(0, activeMillis)) / 1000;   // up to 900 for a five-floor trial, one point per second saved
        }
        score -= (long) Math.max(0, faints) * FAINT_PENALTY;
        score += (long) Math.max(0, difficulty) * PER_DIFFICULTY_POINT;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, score));
    }
}
