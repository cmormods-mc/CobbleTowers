package com.cobbletowers.halloween.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The animation sequences from animation.json: keyframe tracks in server ticks, sampled per tick. Pure; no Minecraft types. */
public final class VaultTimeline {

    public static final String LID = "lid.rot_x_deg", PLATE = "face_plate.press_units", ROOT_X = "root.x_units";

    /** A key: {@code ease} shapes the segment that starts at this key. */
    public record Key(double tick, double value, String ease) {}

    public record Sequence(String name, int ticks, boolean loop, Map<String, List<Key>> tracks) {}

    private final Map<String, Sequence> sequences = new LinkedHashMap<>();

    public static VaultTimeline parse(JsonObject manifest) {
        VaultTimeline timeline = new VaultTimeline();
        for (Map.Entry<String, JsonElement> entry : manifest.getAsJsonObject("sequences").entrySet()) {
            JsonObject seq = entry.getValue().getAsJsonObject();
            Map<String, List<Key>> tracks = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> track : seq.getAsJsonObject("tracks").entrySet()) {
                List<Key> keys = new ArrayList<>();
                for (JsonElement k : track.getValue().getAsJsonArray()) {
                    JsonArray a = k.getAsJsonArray();
                    keys.add(new Key(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsString()));
                }
                tracks.put(track.getKey(), List.copyOf(keys));
            }
            timeline.sequences.put(entry.getKey(), new Sequence(entry.getKey(), seq.get("ticks").getAsInt(), seq.get("loop").getAsBoolean(), tracks));
        }
        return timeline;
    }

    public Sequence sequence(String name) {
        Sequence s = sequences.get(name);
        if (s == null) throw new IllegalArgumentException("no sequence " + name);
        return s;
    }

    public Map<String, Sequence> sequences() {
        return sequences;
    }

    /** All channels at {@code tick} of {@code name}: looped sequences wrap, others clamp to their last tick; untouched channels are at rest (0). */
    public VaultPose sample(String name, int tick) {
        Sequence s = sequence(name);
        double t = s.loop() ? tick % s.ticks() : Math.min(tick, s.ticks());
        return new VaultPose(track(s, LID, t), track(s, PLATE, t), track(s, ROOT_X, t));
    }

    private static double track(Sequence s, String channel, double tick) {
        List<Key> keys = s.tracks().get(channel);
        return keys == null ? 0 : sampleKeys(keys, tick);
    }

    /** A keyframe list at a (possibly fractional) tick: each key's ease shapes the segment to the next; clamped outside the range. */
    public static double sampleKeys(List<Key> keys, double tick) {
        if (tick <= keys.get(0).tick()) return keys.get(0).value();
        for (int i = 0; i + 1 < keys.size(); i++) {
            Key a = keys.get(i), b = keys.get(i + 1);
            if (tick <= b.tick()) {
                double f = (tick - a.tick()) / (b.tick() - a.tick());
                return a.value() + (b.value() - a.value()) * ease(a.ease(), f);
            }
        }
        return keys.get(keys.size() - 1).value();
    }

    static double ease(String name, double t) {
        return switch (name) {
            case "linear" -> t;
            case "in_quad" -> t * t;
            case "out_quad" -> 1 - (1 - t) * (1 - t);
            case "in_cubic" -> t * t * t;
            case "out_cubic" -> 1 - Math.pow(1 - t, 3);
            case "in_out_sine" -> -(Math.cos(Math.PI * t) - 1) / 2;
            default -> throw new IllegalArgumentException("unknown ease " + name);
        };
    }
}
