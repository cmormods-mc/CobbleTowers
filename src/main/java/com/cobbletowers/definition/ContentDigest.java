package com.cobbletowers.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * A definition's content, as a stable fingerprint.
 *
 * <p>A run records the digest of the content it started with, so a later load can tell "the tower was
 * edited" apart from "the tower is the same, renumbered" and refuse or migrate deliberately rather
 * than silently running different content (TDS #40).
 *
 * <p>Canonical before hashing -- object keys sorted, no whitespace -- because reformatting a file or
 * reordering its keys does not change what it says. Without that the digest would report an edit
 * every time someone ran a formatter over the datapack.
 */
public final class ContentDigest {

    private ContentDigest() {}

    /** Lowercase hex SHA-256 of the canonical form of {@code json}. */
    public static String of(JsonElement json) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(canonical(json).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // Every Java platform is required to provide SHA-256.
            throw new IllegalStateException(ex);
        }
    }

    /** The canonical text: sorted keys, no insignificant whitespace. Visible for testing. */
    static String canonical(JsonElement json) {
        StringBuilder out = new StringBuilder();
        write(json, out);
        return out.toString();
    }

    private static void write(JsonElement json, StringBuilder out) {
        if (json == null || json.isJsonNull()) {
            out.append("null");
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            List<String> keys = new ArrayList<>(object.keySet());
            keys.sort(String::compareTo);
            out.append('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                String key = keys.get(i);
                writeString(key, out);
                out.append(':');
                write(object.get(key), out);
            }
            out.append('}');
        } else if (json.isJsonArray()) {
            // Order is meaning in an array -- floors are played in the order they are listed -- so it
            // is preserved, unlike object keys.
            JsonArray array = json.getAsJsonArray();
            out.append('[');
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) out.append(',');
                write(array.get(i), out);
            }
            out.append(']');
        } else if (json.getAsJsonPrimitive().isString()) {
            writeString(json.getAsString(), out);
        } else {
            out.append(json.getAsJsonPrimitive().getAsString());
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        out.append('"');
    }

    /** Digests for a whole map of definitions, keyed the same way. */
    public static <K> Map<K, String> ofAll(Map<K, JsonObject> sources) {
        return sources.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> of(entry.getValue())));
    }
}
