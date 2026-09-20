package com.cobbletowers.encounter;

/**
 * The number painted on a jersey signature's uniform (TDS #67).
 *
 * <p>Sports jersey range, 1-99. Pure arithmetic over the same seed {@link EncounterDraw} already
 * derives for this encounter -- TDS #67 A only asks that a restart cannot reroll it, and a second
 * random source here would be exactly the scattered generator P0's coding gate warns against. The
 * fourth "one place this arithmetic exists" class alongside {@link TowerLevelPolicy} and
 * {@link RegionalWeighting}.
 */
public final class JerseyNumbers {

    public static final int MIN = 1;
    public static final int MAX = 99;

    private JerseyNumbers() {}

    /** The jersey number for one encounter, from that encounter's own {@link EncounterSeed}. */
    public static int forEncounter(long encounterSeed) {
        return MIN + (int) Math.floorMod(encounterSeed, MAX - MIN + 1);
    }
}
