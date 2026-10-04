package com.cobbletowers.mastery;

import com.cobbletowers.api.modifier.RiskTier;
import java.util.List;

/**
 * A run's difficulty as one transparent integer (P31, TDS #90): the risk points of the modifiers it held, five per Ascension,
 * and a handicap for fewer players. Stored with every record so it can be re-derived and compared.
 *
 * <p>Minor modifiers are worth 1, moderate 3, severe 6. A full party of four holding nothing at the base cycle scores 0; a
 * solo run holding nothing scores 15.
 */
public final class DifficultyScore {

    public static final int MINOR = 1;
    public static final int MODERATE = 3;
    public static final int SEVERE = 6;
    public static final int PER_ASCENSION = 5;
    public static final int PER_MISSING_PLAYER = 5;
    /** A full party; one fewer player is one more handicap step. */
    public static final int FULL_PARTY = 4;

    private DifficultyScore() {}

    public static int pointsOf(RiskTier risk) {
        return switch (risk) {
            case MINOR -> MINOR;
            case MODERATE -> MODERATE;
            case SEVERE -> SEVERE;
        };
    }

    /**
     * @param risks      the risk tier of every modifier held, a locked-in one listed twice
     * @param ascension  the Ascension of the cycle
     * @param partySize  how many players the run started with
     */
    public static int of(List<RiskTier> risks, int ascension, int partySize) {
        int score = 0;
        for (RiskTier risk : risks) score += pointsOf(risk);
        score += PER_ASCENSION * Math.max(0, ascension);
        score += PER_MISSING_PLAYER * Math.max(0, FULL_PARTY - Math.max(1, partySize));
        return score;
    }

    /** How many of these modifiers are severe. */
    public static int severeIn(List<RiskTier> risks) {
        int severe = 0;
        for (RiskTier risk : risks) if (risk == RiskTier.SEVERE) severe++;
        return severe;
    }
}
