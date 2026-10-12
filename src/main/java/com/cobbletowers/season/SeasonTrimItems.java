package com.cobbletowers.season;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The season armor trim templates (P36b), the season track's finale. A pattern needs a registered template item, so
 * one is registered per season up to {@value #MAX_SEASONS}; later seasons need only a pattern file, textures and a
 * lang entry ({@code tools/generate_season_trims.py}).
 */
public final class SeasonTrimItems {

    public static final String NAMESPACE = "cobbletowers";
    /** How many seasons' templates are registered. */
    public static final int MAX_SEASONS = 12;

    private static boolean registered;

    private SeasonTrimItems() {}

    /** {@code cobbletowers:season_trim_template_3}. */
    public static ResourceLocation templateId(int season) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "season_trim_template_" + season);
    }

    /** {@code cobbletowers:season_3}, the pattern that season's template applies. */
    public static ResourceLocation patternId(int season) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "season_" + season);
    }

    /** Registers every template. Idempotent: a second call (a test, a reload) does nothing. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        for (int season = 1; season <= MAX_SEASONS; season++) {
            ResourceLocation id = templateId(season);
            // Server-only (P43): clients see the sentry template; the pattern is found through the datapack's template_item.
            Registry.register(BuiltInRegistries.ITEM, templateId(season),
                    new SimplePolymerItem(new Item.Properties(), Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE) {
                        @Override
                        public net.minecraft.network.chat.Component getName(net.minecraft.world.item.ItemStack stack) {
                            return com.cobbletowers.ServerLang.itemName(id);
                        }
                    });
        }
    }
}
