package com.cobbletowers.armor;

import com.google.gson.JsonArray;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything a player's worn armor switches on, summed (P24). Immutable; an equal value means nothing changed, which
 * is how the periodic check knows whether to touch attributes at all.
 *
 * @param attributes the attribute modifiers to hold, each with the stable key it is applied under
 * @param battleEffects logical P23 operations for the wearer's tower battles
 */
public record ActiveBonuses(
        List<Attribute> attributes,
        int xpPercent,
        int catchRatePercent,
        int shinyPercent,
        JsonArray battleEffects,
        int vendorDiscountPercent,
        int raidPointsPercent) {

    public static final ActiveBonuses NONE = new ActiveBonuses(List.of(), 0, 0, 0, new JsonArray(), 0, 0);

    /** @param key stable and unique per set and bonus: {@code cobbletowers:armor_set/<set>/<index>} */
    public record Attribute(ResourceLocation key, ResourceLocation attribute, String operation, double amount) {}

    public ActiveBonuses {
        attributes = List.copyOf(attributes);
        battleEffects = battleEffects.deepCopy();
    }

    public boolean isNone() {
        return equals(NONE);
    }

    /** A copy: callers merge and resolve these, and must not be able to change what is cached. */
    @Override
    public JsonArray battleEffects() {
        return battleEffects.deepCopy();
    }
}
