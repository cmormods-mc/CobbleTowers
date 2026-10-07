package com.cobbletowers.runtime;

/**
 * Who may stay in the tower dimension (P20): nobody without a live place in a run. Pure, from plain values and a
 * clock reading; enforced by a once-a-second sweep.
 */
public final class ExitRules {

    /** Long enough to read a reward screen or a wipe message before being moved. */
    public static final long BEAT_MILLIS = 5_000L;

    private ExitRules() {}

    /** How a player stands with the run they were last in. */
    public record Standing(Kind kind, long endedAt) {
        public enum Kind {
            /** A member of a run that is still live. */
            ACTIVE,
            /** Their run reached a terminal state at {@code endedAt}. */
            ENDED,
            /** They left the run on purpose, or there is no run for them at all. */
            NONE
        }

        public static Standing active() {
            return new Standing(Kind.ACTIVE, 0);
        }

        public static Standing ended(long at) {
            return new Standing(Kind.ENDED, at);
        }

        public static Standing none() {
            return new Standing(Kind.NONE, 0);
        }
    }

    public enum Verdict {
        /** Nothing to do. */
        STAY,
        /** Should leave, but the beat has not passed yet. */
        WAIT,
        /** Send them home now. */
        LEAVE
    }

    /**
     * @param inTower whether the player is in the tower dimension
     * @param exempt an operator in creative or spectator mode (see {@link #exempt})
     */
    public static Verdict decide(boolean inTower, boolean exempt, Standing standing, long now) {
        if (!inTower || exempt) return Verdict.STAY;
        return switch (standing.kind()) {
            case ACTIVE -> Verdict.STAY;
            case ENDED -> now - standing.endedAt() >= BEAT_MILLIS ? Verdict.LEAVE : Verdict.WAIT;
            case NONE -> Verdict.LEAVE;
        };
    }

    /**
     * Whether a player in the tower is exempt from being sent home: an operator in creative or spectator exploring on
     * purpose, but not one whose own run just ended ({@code leavingAfterOwnRun}).
     */
    public static boolean exempt(boolean operatorInCreativeOrSpectator, boolean leavingAfterOwnRun) {
        return operatorInCreativeOrSpectator && !leavingAfterOwnRun;
    }

    /**
     * Whether a participant still in a finished run's cell may be sent home before it is reset; not one with a live
     * place in another run.
     */
    public static boolean mayEvacuateAtRelease(Standing standing) {
        return standing.kind() != Standing.Kind.ACTIVE;
    }

    /**
     * Whether a finished run's cell may be reset now: never while someone is inside, unless the beat has passed and
     * they have been sent home.
     */
    public static boolean releaseDue(boolean anyoneInside, long endedAt, long now) {
        return !anyoneInside || now - endedAt >= BEAT_MILLIS;
    }
}
