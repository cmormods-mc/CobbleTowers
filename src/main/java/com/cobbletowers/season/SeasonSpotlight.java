package com.cobbletowers.season;

import com.cobbletowers.armor.ArmorSetItems;
import com.cobbletowers.definition.RewardTableDefinition;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceLocation;

/**
 * What the season's spotlight does (P36c): the featured region drops its own armor more often. Drops only (owner
 * decision): opponents, run codes and trials are unaffected. Applies to that region's tower while its season is
 * active. Pure apart from {@link #weights}.
 */
public final class SeasonSpotlight {

    /** The slice a spotlight armor piece gets, in percent of its normal weight: twice as wide. */
    public static final int ARMOR_PERCENT = 200;

    private static final List<String> SLOTS = List.of("helmet", "chestplate", "leggings", "boots");

    private SeasonSpotlight() {}

    /**
     * Whether {@code item} is a piece of the armor set that belongs to {@code tower} (Tideforge's tower, the
     * tideforge set).
     */
    public static boolean isRegionArmor(ResourceLocation tower, ResourceLocation item) {
        if (!ArmorSetItems.NAMESPACE.equals(item.getNamespace()) || !ArmorSetItems.SETS.contains(tower.getPath())) return false;
        for (String slot : SLOTS) {
            if (item.getPath().equals(tower.getPath() + "_" + slot)) return true;
        }
        return false;
    }

    /**
     * The percent to scale one entry's weight by: {@value #ARMOR_PERCENT} for the spotlight region's own armor in its
     * own tower, else 100.
     */
    public static int weightPercent(Optional<ResourceLocation> spotlight, ResourceLocation tower, ResourceLocation item) {
        return spotlight.isPresent() && spotlight.get().equals(tower) && isRegionArmor(tower, item) ? ARMOR_PERCENT : 100;
    }

    /**
     * The weights for a run of {@code tower} now: the identity unless a season is running with this tower in the
     * spotlight.
     */
    public static ToIntFunction<RewardTableDefinition.Entry> weights(ResourceLocation tower) {
        Optional<ResourceLocation> spotlight = Seasons.activeNumber().map(Seasons::definition).flatMap(definition -> definition.spotlight());
        return entry -> weightPercent(spotlight, tower, entry.item());
    }
}
