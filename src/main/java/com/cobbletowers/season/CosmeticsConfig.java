package com.cobbletowers.season;

import com.cobbletowers.TowerLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Cosmetics settings (P36d) from {@code config/cobbletowers-cosmetics.json}: {@code chat_tags} toggles the title and
 * club tag before names; {@code on_earn_commands} are console commands run per newly earned cosmetic, with {@code
 * {player}}, {@code {uuid}}, {@code {id}}, {@code {kind}} and {@code {season}} filled in. A bad file falls back to
 * defaults and says so.
 */
public final class CosmeticsConfig {

    private static volatile boolean chatTags = true;
    private static volatile List<String> onEarnCommands = List.of();

    private CosmeticsConfig() {}

    public static void install() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
    }

    public static boolean chatTags() {
        return chatTags;
    }

    public static List<String> onEarnCommands() {
        return onEarnCommands;
    }

    /** Test seam and operator reload. */
    public static void set(boolean tags, List<String> commands) {
        chatTags = tags;
        onEarnCommands = List.copyOf(commands);
    }

    static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-cosmetics.json");
        if (!Files.exists(file)) {
            set(true, List.of());
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            boolean tags = !root.has("chat_tags") || root.get("chat_tags").getAsBoolean();
            List<String> commands = new ArrayList<>();
            if (root.has("on_earn_commands")) {
                JsonArray array = root.getAsJsonArray("on_earn_commands");
                for (JsonElement element : array) {
                    String command = element.getAsString().trim();
                    if (!command.isEmpty()) commands.add(command.startsWith("/") ? command.substring(1) : command);
                }
            }
            set(tags, commands);
            TowerLog.info("Cosmetics config: chat tags {}, {} earn command(s)", tags, commands.size());
        } catch (IOException | RuntimeException ex) {
            TowerLog.error("{} is not usable ({}); cosmetics use the defaults (chat tags on, no earn commands)", file, ex.toString());
            set(true, List.of());
        }
    }
}
