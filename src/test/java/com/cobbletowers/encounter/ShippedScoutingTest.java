package com.cobbletowers.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.ScoutingProfileDefinition;
import com.cobbletowers.definition.ScoutingProfileDefinition.RevealCategory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The scouting profile this mod ships (P22 content): the regional towers hide typing and field conditions from
 * floor 7 and threat level from floor 9, and Neutral hides nothing. Read from the real resource files.
 */
class ShippedScoutingTest {

    private static JsonObject json(String path) {
        try (Reader reader = new InputStreamReader(ShippedScoutingTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception ex) {
            throw new AssertionError("could not load " + path + ": " + ex, ex);
        }
    }

    private static final String DATA = "/data/cobbletowers/cobbletowers/";

    private static ScoutingProfileDefinition regional() {
        return ScoutingProfileDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("cobbletowers", "regional"),
                json(DATA + "scouting_profiles/regional.json"));
    }

    private static RevealCategory category(String name) {
        return regional().categories().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("the regional profile names the three categories the reveal screen knows, and nothing else")
    void knownCategoriesOnly() {
        List<String> names = regional().categories().stream().map(RevealCategory::name).toList();

        assertEquals(List.of("typing", "threat_level", "field_conditions"), names);
    }

    @Test
    @DisplayName("typing and field conditions are visible through floor 6 and hidden from floor 7")
    void typingHidesAtSeven() {
        for (String name : List.of("typing", "field_conditions")) {
            assertTrue(ScoutingReveal.isRevealed(category(name), 6, 0), name + " on floor 6");
            assertFalse(ScoutingReveal.isRevealed(category(name), 7, 0), name + " on floor 7");
        }
    }

    @Test
    @DisplayName("threat level is the last thing to go: visible through floor 8, hidden from floor 9")
    void threatHidesAtNine() {
        assertTrue(ScoutingReveal.isRevealed(category("threat_level"), 8, 0));
        assertFalse(ScoutingReveal.isRevealed(category("threat_level"), 9, 0));
    }

    @Test
    @DisplayName("Keen Eye (+2) pushes every concealment two floors later, so the modifier is worth drafting")
    void keenEyeMatters() {
        assertFalse(ScoutingReveal.isRevealed(category("typing"), 7, 0));
        assertTrue(ScoutingReveal.isRevealed(category("typing"), 7, 2));
        assertTrue(ScoutingReveal.isRevealed(category("typing"), 8, 2));
        assertFalse(ScoutingReveal.isRevealed(category("typing"), 9, 2));
        assertTrue(ScoutingReveal.isRevealed(category("threat_level"), 10, 2), "even the top floor, with enough Keen Eyes");
    }

    @Test
    @DisplayName("each regional tower uses the profile, and Neutral names none so it reveals everything")
    void regionsConcealNeutralDoesNot() {
        for (String region : List.of("tideforge", "rootvale", "duskvale")) {
            assertEquals("cobbletowers:regional", json(DATA + "towers/" + region + ".json").get("scouting_profile").getAsString(), region);
        }
        assertFalse(json(DATA + "towers/neutral.json").has("scouting_profile"), "neutral hides nothing");
    }
}
