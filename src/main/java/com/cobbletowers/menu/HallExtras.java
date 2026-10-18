package com.cobbletowers.menu;

import com.cobbletowers.network.TowerHallStatePayload.Extras;
import com.cobbletowers.network.TowerHallStatePayload.Mon;
import com.cobbletowers.network.TowerHallStatePayload.Standing;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** What the Frontier Desk Hall (P51) shows beyond the native snapshot. Pure: the service reads the world, this shapes it. */
public final class HallExtras {

    private HallExtras() {}

    /**
     * One tower's standing. {@code nextRankAt} is the level where the next rank begins, or a negative number at the top;
     * the payload carries the gap, or -1 at the top.
     */
    public static Standing standing(ResourceLocation tower, int level, int ascension, String rank, int nextRankAt) {
        int shown = Math.max(0, level);
        return new Standing(tower, rank, shown, nextRankAt < 0 ? -1 : Math.max(0, nextRankAt - shown), Math.max(0, ascension));
    }

    /** The sprite file name for a species id path: lower case, letters and digits only ("nidoran_f" is nidoranf, "mr_mime" is mrmime). */
    public static String spriteName(String speciesIdPath) {
        return speciesIdPath.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Health as a whole percent, 0 to 100; anything above zero shows as at least 1 so a living Pokemon never reads as empty. */
    public static int hpPercent(int current, int max) {
        if (max <= 0 || current <= 0) return 0;
        return Math.min(100, Math.max(1, Math.round(current * 100f / max)));
    }

    /** At most six Pokemon, in party order. */
    public static Extras of(String runTower, List<Mon> party, List<Standing> standings) {
        return new Extras(runTower == null ? "" : runTower, party.size() > 6 ? List.copyOf(party.subList(0, 6)) : List.copyOf(party),
                List.copyOf(standings));
    }
}
