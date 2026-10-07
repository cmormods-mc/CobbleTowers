package com.cobbletowers.definition;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One season's identity (P36a): number, name and spotlight region, authored in {@code
 * data/<namespace>/cobbletowers/seasons/season_<n>.json}. A season with no file gets a generated one.
 * @param spotlight the regional tower featured this season
 */
public record SeasonDefinition(int number, String name, Optional<ResourceLocation> spotlight) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /** The regional towers a generated season rotates through, in order. */
    public static final List<ResourceLocation> REGIONS = List.of(
            ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge"),
            ResourceLocation.fromNamespaceAndPath("cobbletowers", "rootvale"),
            ResourceLocation.fromNamespaceAndPath("cobbletowers", "duskvale"));

    public SeasonDefinition {
        if (number < 1) throw new IllegalArgumentException("season number must be >= 1, got " + number);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("season name must not be blank");
    }

    /** The season used when none is authored: {@code Season N}, spotlight rotating through the regions. */
    public static SeasonDefinition generated(int number) {
        return new SeasonDefinition(number, "Season " + number, Optional.of(REGIONS.get((number - 1) % REGIONS.size())));
    }

    public static SeasonDefinition fromJson(ResourceLocation id, JsonObject root) {
        int version = TowerJson.requireInt(root, "schema_version");
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + version + " is not supported; expected " + SUPPORTED_SCHEMA_VERSION);
        }
        return new SeasonDefinition(TowerJson.requireInt(root, "number"), TowerJson.requireString(root, "name"),
                TowerJson.optionalId(root, "spotlight"));
    }
}
