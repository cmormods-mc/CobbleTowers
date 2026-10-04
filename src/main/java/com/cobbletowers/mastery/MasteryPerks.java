package com.cobbletowers.mastery;

/**
 * What a mastery level is called and what it gives (P31). One level per achievement unlocked in a tower, 0 to 30. Perks are
 * small, permanent, cumulative, and apply only in runs of the tower they were earned in; each is read per player at the point
 * of use (a vendor price, a wallet credit, a Raid Points grant), so a team's mix of masteries never has to be averaged.
 *
 * <p>Deliberately nothing repeatable on run start (a stipend could be farmed by starting and abandoning), and never a jersey
 * aspect (TDS #89).
 */
public final class MasteryPerks {

    public static final int MAX_LEVEL = 30;

    /** The perks at a level, as percentages. */
    public record Perks(int vendorDiscountPercent, int cobbleDollarBonusPercent, int raidPointsBonusPercent) {
        public static final Perks NONE = new Perks(0, 0, 0);
    }

    private MasteryPerks() {}

    /** The rank name for a level. */
    public static String rankOf(int level) {
        int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
        if (clamped >= MAX_LEVEL) return "Champion";
        if (clamped >= 25) return "Master";
        if (clamped >= 20) return "Diamond";
        if (clamped >= 15) return "Platinum";
        if (clamped >= 10) return "Gold";
        if (clamped >= 5) return "Silver";
        if (clamped >= 1) return "Bronze";
        return "Unranked";
    }

    /** The level at which the next rank begins, or -1 at the top. */
    public static int nextRankAt(int level) {
        for (int threshold : new int[] {1, 5, 10, 15, 20, 25, MAX_LEVEL}) {
            if (level < threshold) return threshold;
        }
        return -1;
    }

    /** The perks a level has earned, cumulatively. */
    public static Perks at(int level) {
        int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
        int vendor = clamped >= MAX_LEVEL ? 10 : clamped >= 15 ? 6 : clamped >= 5 ? 3 : 0;
        int dollars = clamped >= MAX_LEVEL ? 15 : clamped >= 20 ? 10 : clamped >= 10 ? 5 : 0;
        int raid = clamped >= 25 ? 10 : 0;
        return new Perks(vendor, dollars, raid);
    }
}
