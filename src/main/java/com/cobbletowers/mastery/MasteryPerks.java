package com.cobbletowers.mastery;

import net.minecraft.resources.ResourceLocation;

/**
 * What a mastery level is called and what it gives (P31). One level per achievement unlocked in a tower. Perks are small, permanent,
 * cumulative, and apply only in runs of the tower they were earned in; each is read per player at the point of use (a vendor price, a wallet
 * credit, a Raid Points grant), so a team's mix of masteries never has to be averaged.
 *
 * <p>Since P37 the ranks and perks are data ({@link MasteryTracks}: the shipped {@code mastery_tracks/default.json}, which reproduces the
 * original table, plus any datapack or config additions) and a level can be any number, so a tower with more than 30 achievements keeps
 * climbing. The overloads without a tower use the track every tower shares.
 *
 * <p>Deliberately nothing repeatable on run start (a stipend could be farmed by starting and abandoning), and never a jersey
 * aspect (TDS #89).
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
