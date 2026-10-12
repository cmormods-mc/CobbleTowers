package com.cobbletowers.webgui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a screen payload record into the JSON a WebGUI page reads. Reflective on purpose: the payload records already
 * are the screen contract, so a new field reaches the page the moment it exists, and there is no second list of fields
 * to keep in step with them.
 *
 * <p>Rules: records become objects keyed by component name, lists become arrays, enums become their constant name,
 * anything else without structure (a {@code UUID}, a {@code ResourceLocation}) becomes its {@code toString}. Nothing
 * here touches Minecraft, so it is plain-JUnit testable.
 */
public final class PayloadJson {

    /** Deepest nesting followed; payloads are three or four deep, so this only ever cuts off a cycle. */
    private static final int MAX_DEPTH = 8;

    private PayloadJson() {}

    public static JsonElement toJson(Object value) {
        return convert(value, 0);
    }

    /** The name the page switches on: the record's simple name without a trailing {@code Payload}. */
    public static String kindOf(Class<?> type) {
        String name = type.getSimpleName();
        return name.endsWith("Payload") ? name.substring(0, name.length() - "Payload".length()) : name;
    }

    /** {@code {"kind":"IntermissionState","data":{...}}}: the one shape every server-to-page message has. */
    public static JsonObject envelope(Object payload) {
        JsonObject out = new JsonObject();
        out.addProperty("kind", kindOf(payload.getClass()));
        out.add("data", toJson(payload));
        return out;
    }

    private static JsonElement convert(Object value, int depth) {
        if (value == null || depth > MAX_DEPTH) return JsonNull.INSTANCE;
        if (value instanceof JsonElement element) return element;
        if (value instanceof String string) return new JsonPrimitive(string);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Character character) return new JsonPrimitive(character.toString());
        if (value instanceof Enum<?> constant) return new JsonPrimitive(constant.name());
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? convert(optional.get(), depth + 1) : JsonNull.INSTANCE;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            map.forEach((key, entry) -> object.add(String.valueOf(key), convert(entry, depth + 1)));
            return object;
        }
        if (value instanceof Iterable<?> iterable) {
            JsonArray array = new JsonArray();
            for (Object element : iterable) array.add(convert(element, depth + 1));
            return array;
        }
        if (value.getClass().isArray()) {
            JsonArray array = new JsonArray();
            for (int i = 0; i < Array.getLength(value); i++) array.add(convert(Array.get(value, i), depth + 1));
            return array;
        }
        if (value.getClass().isRecord()) {
            JsonObject object = new JsonObject();
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                try {
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    object.add(component.getName(), convert(accessor.invoke(value), depth + 1));
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // One unreadable component must not cost the page the rest of the screen.
                    object.add(component.getName(), JsonNull.INSTANCE);
                }
            }
            return object;
        }
        return new JsonPrimitive(value.toString());
    }
}
