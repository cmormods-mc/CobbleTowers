package com.cobbletowers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Names from the bundled en_us.json, so a client without the mod or its pack never sees a raw translation key (P43). */
public final class ServerLang {

    private static final Map<String, String> LINES = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private ServerLang() {}

    /** The English name of an item, or its id when the lang file has none. */
    public static Component itemName(ResourceLocation id) {
        load();
        String text = LINES.get("item." + id.getNamespace() + "." + id.getPath());
        return Component.literal(text != null ? text : id.toString());
    }

    private static synchronized void load() {
        if (loaded) return;
        try (InputStream in = ServerLang.class.getResourceAsStream("/assets/cobbletowers/lang/en_us.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                root.entrySet().forEach(e -> LINES.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (IOException | RuntimeException ex) {
            TowerLog.error("Could not read the bundled lang file: {}", ex.toString());
        }
        loaded = true;
    }
}
