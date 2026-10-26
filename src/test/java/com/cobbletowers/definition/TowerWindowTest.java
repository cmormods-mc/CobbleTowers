package com.cobbletowers.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** An event tower opens at its start and closes at its end; every other tower never closes (P54). */
class TowerWindowTest {

    private static final long START = TowerWindow.parse("2026-10-31T00:00:00Z", "2026-11-03T00:00:00Z").startMillis();
    private static final long END = TowerWindow.parse("2026-10-31T00:00:00Z", "2026-11-03T00:00:00Z").endMillis();

    private static TowerDefinition tower(String windowJson) {
        String extra = windowJson.isEmpty() ? "" : ", \"window\": " + windowJson;
        return TowerDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("cobbletowers", "halloween"),
                JsonParser.parseString("{\"schema_version\":1,\"display_name\":\"H\",\"ruleset\":\"cobbletowers:standard\","
                        + "\"reward_table\":\"cobbletowers:halloween\",\"floors\":[\"cobbletowers:f1\"]" + extra + "}")
                        .getAsJsonObject());
    }

    @Test
    @DisplayName("open from the start instant, closed from the end instant")
    void edges() {
        TowerDefinition tower = tower("{\"start\":\"2026-10-31T00:00:00Z\",\"end\":\"2026-11-03T00:00:00Z\"}");
        assertFalse(tower.isOpen(START - 1));
        assertTrue(tower.isOpen(START));
        assertTrue(tower.isOpen(END - 1));
        assertFalse(tower.isOpen(END));
    }

    @Test
    @DisplayName("a tower with no window is always open")
    void noWindow() {
        TowerDefinition tower = tower("");
        assertTrue(tower.window().isEmpty());
        assertTrue(tower.isOpen(0));
        assertTrue(tower.isOpen(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("a backwards or unparseable window is rejected with a message")
    void rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> tower("{\"start\":\"2026-11-03T00:00:00Z\",\"end\":\"2026-10-31T00:00:00Z\"}"));
        assertThrows(IllegalArgumentException.class, () -> tower("{\"start\":\"soon\",\"end\":\"later\"}"));
        assertEquals(1, tower("").floorCount());
    }
}
