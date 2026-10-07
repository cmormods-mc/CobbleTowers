package com.cobbletowers.season;

import com.cobbletowers.TowerLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The season calendar's anchor and master switch (P36a), from {@code config/cobbletowers-seasons.json}: {@code
 * {"anchor": "2026-10-05", "enabled": true}}. The anchor should be a Monday. A missing file or bad value falls back
 * to the default and says so.
 */
public record SeasonConfig(LocalDate anchor, boolean enabled) {

    /** The first season starts Monday 2026-10-05. */
    public static final LocalDate DEFAULT_ANCHOR = LocalDate.of(2026, 10, 5);

    public static SeasonConfig standard() {
        return new SeasonConfig(DEFAULT_ANCHOR, true);
    }

    public static SeasonConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-seasons.json");
        if (!Files.exists(file)) return standard();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            LocalDate anchor = root.has("anchor") ? LocalDate.parse(root.get("anchor").getAsString()) : DEFAULT_ANCHOR;
            boolean enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            return new SeasonConfig(anchor, enabled);
        } catch (IOException | RuntimeException ex) {
            TowerLog.error("{} is not usable ({}); seasons use the default anchor {}", file, ex.toString(), DEFAULT_ANCHOR);
            return standard();
        }
    }
}
