package com.cobbletowers.season;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.armortrim.TrimPattern;

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
            ResourceKey<TrimPattern> pattern = ResourceKey.create(Registries.TRIM_PATTERN, patternId(season));
            Registry.register(BuiltInRegistries.ITEM, templateId(season), SmithingTemplateItem.createArmorTrimTemplate(pattern));
        }
    }
}
