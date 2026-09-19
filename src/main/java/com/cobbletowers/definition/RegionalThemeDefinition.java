package com.cobbletowers.definition;

import com.cobbletowers.api.regional.RegionalThemeView;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * What an event tower's regional theme actually is: a doctrine to show and five jersey signatures to
 * weight toward as a themed pool's floors deepen (TDS #73, #76, #80).
 *
 * <p>Deliberately not a second roster. The ~ten core supporting species and the expanded pool TDS #76
 * mentions for Ascension are authored straight into a themed tower's own encounter pools, the same
 * way every non-jersey opponent already is -- a second list here would be two sources of truth for
 * one fact, the mistake P9 avoided by pricing rewards off {@code LedgerEntry.Kind} instead of
 * re-deriving it. Neutral names no theme at all (TDS #75); this record only exists for a tower that
 * does.
 *
 * @param doctrine display-only (TDS #80); nothing in this codebase reads it to change behavior
 * @param jerseyWeightGrowthPercentPerFloor how much a jersey entry's weight in a pool that names this
 *                                          theme rises per floor of depth (TDS #73), the same growth-
 *                                          step shape {@code RewardTableDefinition} already uses
 */
public record RegionalThemeDefinition(
        ResourceLocation id,
        int schemaVersion,
        int revision,
        String displayName,
        String doctrine,
        List<JerseySignature> jerseySignatures,
        int jerseyWeightGrowthPercentPerFloor) implements RegionalThemeView {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /** Each event tower has exactly five permanent jersey signatures (TDS #76). */
    public static final int JERSEY_SIGNATURE_COUNT = 5;

    /** One signature Cobblemon, by species and the aspect(s) that make it a jersey (TDS #54, #85). */
    public record JerseySignature(ResourceLocation species, List<String> aspects) {
        public JerseySignature {
            Objects.requireNonNull(species, "species");
            aspects = List.copyOf(aspects);
        }
    }

    public RegionalThemeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(doctrine, "doctrine");
        jerseySignatures = List.copyOf(jerseySignatures);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1, got " + revision);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display_name must not be blank");
        }
        if (jerseySignatures.size() != JERSEY_SIGNATURE_COUNT) {
            throw new IllegalArgumentException("a regional theme needs exactly " + JERSEY_SIGNATURE_COUNT
                    + " jersey signatures, got " + jerseySignatures.size());
        }
        if (jerseyWeightGrowthPercentPerFloor < 0) {
            throw new IllegalArgumentException("jersey_weight_growth_percent_per_floor must be >= 0, got "
                    + jerseyWeightGrowthPercentPerFloor);
        }
    }

    /** Whether {@code species} is one of this theme's five jerseys. */
    public boolean isJerseySpecies(ResourceLocation species) {
        for (JerseySignature signature : jerseySignatures) {
            if (signature.species().equals(species)) return true;
        }
        return false;
    }

    @Override
    public List<ResourceLocation> jerseySpeciesIds() {
        return jerseySignatures.stream().map(JerseySignature::species).toList();
    }

    public static RegionalThemeDefinition fromJson(ResourceLocation id, JsonObject root) {
        List<JerseySignature> signatures = new ArrayList<>();
        if (root.has("jersey_signatures")) {
            if (!root.get("jersey_signatures").isJsonArray()) {
                throw new IllegalArgumentException("field 'jersey_signatures' must be an array");
            }
            for (JsonElement element : root.getAsJsonArray("jersey_signatures")) {
                JsonObject signature = element.getAsJsonObject();
                signatures.add(new JerseySignature(
                        TowerJson.requireId(signature, "species"),
                        TowerJson.strings(signature, "aspects")));
            }
        }
        return new RegionalThemeDefinition(
                id,
                TowerJson.requireInt(root, "schema_version"),
                TowerJson.integer(root, "revision", 1),
                TowerJson.requireString(root, "display_name"),
                TowerJson.requireString(root, "doctrine"),
                signatures,
                TowerJson.integer(root, "jersey_weight_growth_percent_per_floor", 0));
    }
}
