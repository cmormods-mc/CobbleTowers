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
 * The cosmetics settings (P36d), read at server start from {@code config/cobbletowers-cosmetics.json}:
 *
 * <pre>{"chat_tags": true, "on_earn_commands": ["lp user {player} permission set cobbletowers.cosmetic.{id} true"]}</pre>
 *
 * {@code chat_tags} switches the title and club tag in front of names on or off. {@code on_earn_commands} are console commands run once for each
 * cosmetic that is newly earned, with {@code {player}}, {@code {uuid}}, {@code {id}}, {@code {kind}} and {@code {season}} filled in; the list is empty by
 * default, so nothing runs until a server adds some. A bad file falls back to the defaults and says so in the log.
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
