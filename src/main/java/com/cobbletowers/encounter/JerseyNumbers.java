package com.cobbletowers.encounter;

/**
 * The number on a jersey signature's uniform (TDS #67): 1-99, pure arithmetic over the encounter's seed from {@link
 * EncounterDraw}, so a restart cannot reroll it.
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
