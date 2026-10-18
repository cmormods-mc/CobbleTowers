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

    /** At most six Pokemon, in party order. */
    public static Extras of(String runTower, List<Mon> party, List<Standing> standings) {
        return new Extras(runTower == null ? "" : runTower, party.size() > 6 ? List.copyOf(party.subList(0, 6)) : List.copyOf(party),
                List.copyOf(standings));
    }
}
