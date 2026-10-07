package com.cobbletowers.mastery;

import net.minecraft.resources.ResourceLocation;

/**
 * What a mastery level is called and gives (P31): one level per achievement unlocked in a tower. Perks are small,
 * permanent, cumulative and apply only in that tower's runs. Ranks and perks are data ({@link MasteryTracks});
 * overloads without a tower use the shared track. Nothing repeatable on run start and never a jersey aspect (TDS
 * #89).
 */
public final class MasteryPerks {

    /** The perks at a level, as percentages. */
    public record Perks(int vendorDiscountPercent, int cobbleDollarBonusPercent, int raidPointsBonusPercent) {
        public static final Perks NONE = new Perks(0, 0, 0);
    }

    private MasteryPerks() {}

    /** The rank name for a level. */
    public static String rankOf(int level) {
        return rankOf(null, level);
    }

    public static String rankOf(ResourceLocation tower, int level) {
        return MasteryTracks.forTower(tower).rankOf(level);
    }

    /** The level at which the next rank begins, or -1 at the top. */
    public static int nextRankAt(int level) {
        return nextRankAt(null, level);
    }

    public static int nextRankAt(ResourceLocation tower, int level) {
        return MasteryTracks.forTower(tower).nextRankAt(level);
    }

    /** The perks a level has earned, cumulatively. */
    public static Perks at(int level) {
        return at(null, level);
    }

    public static Perks at(ResourceLocation tower, int level) {
        return MasteryTracks.forTower(tower).perksAt(level);
    }
}
