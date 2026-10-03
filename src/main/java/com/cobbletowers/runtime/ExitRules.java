package com.cobbletowers.runtime;

/**
 * Who may stay in the tower dimension (P20). One rule: nobody stays without a live place in a run.
 *
 * <p>Pure, from plain values and a clock reading, so the whole policy is unit-tested without a server. The
 * sweep that enforces it runs about once a second, which is why a single rule can cover a run ending, a
 * player leaving, a connection being dropped, a crash and a late login.
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
     * @param inTower whether the player is in the tower dimension right now
     * @param exempt  an operator in creative or spectator mode, who may be looking around on purpose
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
     * Whether a finished run's cell may be reset now. Never while someone is standing in it, except that
     * once the beat has passed the players have been sent home in the same sweep, so waiting longer would
     * only hold a cell for nothing.
     */
    public static boolean releaseDue(boolean anyoneInside, long endedAt, long now) {
        return !anyoneInside || now - endedAt >= BEAT_MILLIS;
    }
}
