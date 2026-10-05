package com.cobbletowers.season;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.armortrim.TrimPattern;

/**
 * The season armor trim templates (P36b): the finale of the season track. A trim is data (a pattern file naming its template item,
 * and two textures), but a pattern needs one registered item to be its template, so one is registered here for each of the first
 * {@value #MAX_SEASONS} season numbers. A later season is then only its pattern file, its textures and a language entry
 * ({@code tools/generate_season_trims.py}); nothing here changes until the thirteenth.
 *
 * <p>Items must exist before any datapack loads, which is why this is code and the pattern is not. A template is
 * {@code cobbletowers:season_trim_template_<n>} and the pattern it applies is {@code cobbletowers:season_<n>}.
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
