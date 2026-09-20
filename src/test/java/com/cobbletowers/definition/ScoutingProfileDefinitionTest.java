package com.cobbletowers.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Parsing a scouting profile: a reveal-threshold config, not a new source of gameplay data (TDS #22). */
class ScoutingProfileDefinitionTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "standard");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    @DisplayName("a profile reads its categories and their concealment floors")
    void parses() {
        ScoutingProfileDefinition profile = ScoutingProfileDefinition.fromJson(ID, json("""
                {"schema_version": 1, "categories": [
                  {"name": "typing", "concealed_from_floor": -1},
                  {"name": "threat_level", "concealed_from_floor": 6},
                  {"name": "field_conditions", "concealed_from_floor": 8}]}"""));

        assertEquals(3, profile.categories().size());
        assertEquals(-1, profile.categories().get(0).concealedFromFloor());
        assertEquals(6, profile.categories().get(1).concealedFromFloor());
    }

    @Test
    @DisplayName("concealed_from_floor defaults to -1: never concealed unless a profile says otherwise")
    void neverConcealedByDefault() {
        ScoutingProfileDefinition profile = ScoutingProfileDefinition.fromJson(ID, json("""
                {"schema_version": 1, "categories": [{"name": "typing"}]}"""));

        assertEquals(-1, profile.categories().get(0).concealedFromFloor());
    }

    @Test
    @DisplayName("a profile needs at least one category")
    void needsACategory() {
        assertThrows(IllegalArgumentException.class,
                () -> ScoutingProfileDefinition.fromJson(ID, json("{\"schema_version\": 1, \"categories\": []}")));
    }
}
