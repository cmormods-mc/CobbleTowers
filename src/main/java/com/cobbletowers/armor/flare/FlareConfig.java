package com.cobbletowers.armor.flare;

import com.cobbletowers.TowerLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The {@code "flares"} section of {@code config/cobbletowers-armor.json}: the particles around back attachments. Vanilla particles
 * only, so they need no client mod or pack; on by default.
 *
 * <pre>{"flares": {"enabled": true, "max_wearers": 24}}</pre>
 *
 * {@code max_wearers} caps how many wearers get a pass each time, rotating through everyone, so a crowd costs a bounded amount.
 */
public record FlareConfig(boolean enabled, int maxWearers) {

    public static final FlareConfig DEFAULT = new FlareConfig(true, 24);

    public static FlareConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-armor.json");
        if (!Files.exists(file)) return DEFAULT;
        try {
            return parse(Files.readString(file));
        } catch (IOException | RuntimeException ex) {
            TowerLog.error("{} is not usable ({}); armor flares use the defaults", file, ex.toString());
            return DEFAULT;
        }
    }

    public static FlareConfig parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("flares")) return DEFAULT;
        JsonObject f = root.getAsJsonObject("flares");
        boolean enabled = f.has("enabled") ? f.get("enabled").getAsBoolean() : DEFAULT.enabled;
        int max = f.has("max_wearers") ? f.get("max_wearers").getAsInt() : DEFAULT.maxWearers;
        if (max < 1 || max > 200) throw new IllegalArgumentException("max_wearers must be 1..200");
        return new FlareConfig(enabled, max);
    }
}
