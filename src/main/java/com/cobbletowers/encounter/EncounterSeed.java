package com.cobbletowers.encounter;

import java.util.UUID;

/**
 * The seed one encounter is drawn from (TDS #29).
 *
 * <p>An opponent is <b>derived</b>, never stored. A run keeps the seed it started with; everything
 * about who it meets on floor 3 comes from arithmetic over that seed, the floor and the opponent's
 * ordinal. So a crash cannot reroll an encounter -- there is no roll to lose -- and recovering one
 * costs nothing to read.
 *
 * <p>Mixed rather than added. Adding the parts would make floor 2 opponent 3 and floor 3 opponent 2
 * the same encounter, which is exactly the pattern a player would notice.
 */
public final class EncounterSeed {

    /** Odd 64-bit constants from the SplitMix64 family; any odd multiplier avoids collapsing bits. */
    private static final long FLOOR_MIX = 0x9E3779B97F4A7C15L;
    private static final long ORDINAL_MIX = 0xBF58476D1CE4E5B9L;

    private EncounterSeed() {}

    /** The seed for one opponent on one floor of one run. */
    public static long of(long runSeed, int floorIndex, int ordinal) {
        long mixed = runSeed ^ (floorIndex * FLOOR_MIX) ^ (ordinal * ORDINAL_MIX);
        return scramble(mixed);
    }

    /** SplitMix64's finaliser: cheap, and it spreads neighbouring inputs to unrelated outputs. */
    private static long scramble(long value) {
        long x = value + 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /** A run's own seed, for a run that has to be given one rather than restoring it. */
    public static long forNewRun(UUID runId) {
        return scramble(runId.getMostSignificantBits() ^ Long.rotateLeft(runId.getLeastSignificantBits(), 32));
    }
}
