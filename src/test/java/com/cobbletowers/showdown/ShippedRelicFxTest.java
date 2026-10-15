package com.cobbletowers.showdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every battle effect a shipped modifier or relic carries is one the mod will actually send to Showdown (P48). */
class ShippedRelicFxTest {

    @Test
    @DisplayName("no shipped battle operation is dropped by the validator, and none is clamped to a different value")
    void everyShippedOperationSurvivesValidation() throws Exception {
        Path folder = Paths.get("src/main/resources/data/cobbletowers/cobbletowers/modifiers");
        int checked = 0;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!json.has("effect") || !json.getAsJsonObject("effect").has("battle")) continue;
                for (JsonElement op : json.getAsJsonObject("effect").getAsJsonArray("battle")) {
                    var clean = TowerBattleFx.validate(op);
                    assertTrue(clean.isPresent(), file.getFileName() + " has a battle operation the mod would drop: " + op);
                    for (var entry : op.getAsJsonObject().entrySet()) {
                        if (entry.getKey().equals("percent") || entry.getKey().equals("stages") || entry.getKey().equals("amount")) {
                            assertEquals(entry.getValue(), clean.get().get(entry.getKey()),
                                    file.getFileName() + " " + entry.getKey() + " is clamped by the validator");
                        }
                    }
                    checked++;
                }
            }
        }
        assertTrue(checked >= 30, "only " + checked + " operations found");
    }
}
