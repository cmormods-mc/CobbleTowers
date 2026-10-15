package com.cobbletowers.report;

import com.cobbletowers.TowerLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Beta settings (P48) from {@code config/cobbletowers-beta.json}: {@code report_prompt} (default true) says whether a run's end points at /tower report. */
public final class BetaConfig {

    private static volatile boolean reportPrompt = true;

    private BetaConfig() {}

    public static void install() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
    }

    public static boolean reportPrompt() {
        return reportPrompt;
    }

    public static void setReportPrompt(boolean on) {
        reportPrompt = on;
    }

    static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-beta.json");
        reportPrompt = true;
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            reportPrompt = !root.has("report_prompt") || root.get("report_prompt").getAsBoolean();
            TowerLog.info("Beta config: report prompt {}", reportPrompt);
        } catch (java.io.IOException | RuntimeException ex) {
            TowerLog.error("{} is not usable ({}); the report prompt stays on", file, ex.toString());
            reportPrompt = true;
        }
    }
}
