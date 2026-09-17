package com.cobbletowers.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Reading definition JSON, in CobbleRaids' style: a default for anything optional, and a message
 * naming the field for anything required or malformed.
 *
 * <p>The messages matter more than they look. A definition that fails to parse is skipped by the
 * registry rather than taking the server down, so the log line is all an operator gets -- it has to
 * say which field, in which file, was wrong.
 */
public final class TowerJson {

    private TowerJson() {}

    public static JsonObject object(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
    }

    public static int integer(JsonObject root, String key, int fallback) {
        return root.has(key) ? root.get(key).getAsInt() : fallback;
    }

    public static boolean bool(JsonObject root, String key, boolean fallback) {
        return root.has(key) ? root.get(key).getAsBoolean() : fallback;
    }

    public static String string(JsonObject root, String key, String fallback) {
        return root.has(key) ? root.get(key).getAsString() : fallback;
    }

    public static int requireInt(JsonObject root, String key) {
        if (!root.has(key)) throw new IllegalArgumentException("missing required field '" + key + "'");
        return root.get(key).getAsInt();
    }

    public static String requireString(JsonObject root, String key) {
        if (!root.has(key)) throw new IllegalArgumentException("missing required field '" + key + "'");
        String value = root.get(key).getAsString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("field '" + key + "' must not be blank");
        return value;
    }

    /** A required id. An unparseable one is an error rather than a silently dropped reference. */
    public static ResourceLocation requireId(JsonObject root, String key) {
        return parseId(requireString(root, key), key);
    }

    public static Optional<ResourceLocation> optionalId(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) return Optional.empty();
        return Optional.of(parseId(root.get(key).getAsString().trim(), key));
    }

    /** A required array of ids, in file order. */
    public static List<ResourceLocation> requireIds(JsonObject root, String key) {
        if (!root.has(key)) throw new IllegalArgumentException("missing required field '" + key + "'");
        return ids(root, key);
    }

    /** An array of ids, or an empty list. Used for reserved fields nothing resolves yet. */
    public static List<ResourceLocation> ids(JsonObject root, String key) {
        if (!root.has(key)) return List.of();
        if (!root.get(key).isJsonArray()) throw new IllegalArgumentException("field '" + key + "' must be an array");
        List<ResourceLocation> parsed = new ArrayList<>();
        JsonArray array = root.getAsJsonArray(key);
        for (JsonElement element : array) {
            parsed.add(parseId(element.getAsString().trim(), key));
        }
        return List.copyOf(parsed);
    }

    public static List<String> strings(JsonObject root, String key) {
        if (!root.has(key)) return List.of();
        if (!root.get(key).isJsonArray()) throw new IllegalArgumentException("field '" + key + "' must be an array");
        List<String> parsed = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray(key)) {
            parsed.add(element.getAsString().trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(parsed);
    }

    private static ResourceLocation parseId(String raw, String key) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("field '" + key + "' is not a valid id: '" + raw + "'");
        return id;
    }
}
