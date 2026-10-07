package com.cobbletowers.trial;

import com.cobbletowers.TowerLog;
import com.cobbletowers.trial.TrialClock.Rhythm;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The trial day's zone and reset hour (P32), from {@code config/cobbletowers-trials.json}: {@code {"zone":
 * "America/New_York", "reset_hour": 4}}. A missing file or bad value falls back to UTC 04:00 and says so, so a typo
 * cannot silently change the trial day.
 */
public final class TrialConfig {

    private TrialConfig() {}

    public static Rhythm load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-trials.json");
        if (!Files.exists(file)) return Rhythm.standard();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            ZoneId zone = root.has("zone") ? ZoneId.of(root.get("zone").getAsString()) : Rhythm.standard().zone();
            int hour = root.has("reset_hour") ? root.get("reset_hour").getAsInt() : Rhythm.standard().resetHour();
            return new Rhythm(zone, hour);
        } catch (IOException | RuntimeException ex) {
            TowerLog.error("{} is not usable ({}); the trial day uses UTC and 04:00", file, ex.toString());
            return Rhythm.standard();
        }
    }
}
