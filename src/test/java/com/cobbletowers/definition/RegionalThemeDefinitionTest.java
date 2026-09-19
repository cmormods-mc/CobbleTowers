package com.cobbletowers.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Parsing a regional theme, and every rule that makes one invalid (P10). */
class RegionalThemeDefinitionTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "test");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static String fiveSignatures() {
        return """
                [{"species":"cobblemon:gyarados","aspects":["jersey"]},
                 {"species":"cobblemon:lanturn","aspects":["jersey"]},
                 {"species":"cobblemon:kingdra","aspects":["jersey"]},
                 {"species":"cobblemon:empoleon","aspects":["jersey"]},
                 {"species":"cobblemon:milotic","aspects":["jersey"]}]""";
    }

    @Test
    @DisplayName("a theme reads its doctrine and five jersey signatures")
    void parses() {
        RegionalThemeDefinition theme = RegionalThemeDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "Tideforge", "doctrine": "Momentum",
                 "jersey_weight_growth_percent_per_floor": 15,
                 "jersey_signatures": %s}""".formatted(fiveSignatures())));

        assertEquals("Tideforge", theme.displayName());
        assertEquals("Momentum", theme.doctrine());
        assertEquals(15, theme.jerseyWeightGrowthPercentPerFloor());
        assertEquals(5, theme.jerseySignatures().size());
        assertEquals(5, theme.jerseySpeciesIds().size());
        assertTrue(theme.isJerseySpecies(ResourceLocation.fromNamespaceAndPath("cobblemon", "gyarados")));
        assertFalse(theme.isJerseySpecies(ResourceLocation.fromNamespaceAndPath("cobblemon", "magikarp")));
    }

    @Test
    @DisplayName("a growth percent absent from the file is zero, not required")
    void growthDefaultsToZero() {
        RegionalThemeDefinition theme = RegionalThemeDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "Tideforge", "doctrine": "Momentum",
                 "jersey_signatures": %s}""".formatted(fiveSignatures())));

        assertEquals(0, theme.jerseyWeightGrowthPercentPerFloor());
    }

    @Test
    @DisplayName("a theme is refused for anything but exactly five jersey signatures")
    void wrongSignatureCount() {
        assertThrows(IllegalArgumentException.class, () -> RegionalThemeDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "Tideforge", "doctrine": "Momentum",
                 "jersey_signatures": [{"species":"cobblemon:gyarados"}]}""")));
        assertThrows(IllegalArgumentException.class, () -> RegionalThemeDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "Tideforge", "doctrine": "Momentum"}""")));
    }

    @Test
    @DisplayName("a theme is refused for a blank name, a blank doctrine or a newer schema")
    void themeValidation() {
        assertThrows(IllegalArgumentException.class, () -> RegionalThemeDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "  ", "doctrine": "Momentum",
                 "jersey_signatures": %s}""".formatted(fiveSignatures()))));
        assertThrows(IllegalArgumentException.class, () -> RegionalThemeDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "Tideforge", "doctrine": "  ",
                 "jersey_signatures": %s}""".formatted(fiveSignatures()))));
        assertThrows(IllegalArgumentException.class, () -> RegionalThemeDefinition.fromJson(ID, json("""
                {"schema_version": 2, "display_name": "Tideforge", "doctrine": "Momentum",
                 "jersey_signatures": %s}""".formatted(fiveSignatures()))));
    }

    @Test
    @DisplayName("a missing required field names itself")
    void missingFieldMessage() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> RegionalThemeDefinition.fromJson(ID, json("{\"schema_version\": 1}")));
        assertTrue(thrown.getMessage().contains("display_name"), thrown.getMessage());
    }

    @Test
    @DisplayName("jersey signature order is preserved, for a deterministic jerseySpeciesIds view")
    void signatureOrderPreserved() {
        RegionalThemeDefinition theme = RegionalThemeDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "Tideforge", "doctrine": "Momentum",
                 "jersey_signatures": %s}""".formatted(fiveSignatures())));

        assertEquals(List.of("cobblemon:gyarados", "cobblemon:lanturn", "cobblemon:kingdra",
                "cobblemon:empoleon", "cobblemon:milotic"),
                theme.jerseySpeciesIds().stream().map(ResourceLocation::toString).toList());
    }
}
