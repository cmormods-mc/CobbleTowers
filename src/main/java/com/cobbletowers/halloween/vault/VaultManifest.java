package com.cobbletowers.halloween.vault;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the vault's animation and event manifests from the jar. Pure; no Minecraft types. */
public final class VaultManifest {

    public static final String ID = "halloween/jack_o_lantern_vault";
    private static final String BASE = "/data/cobbletowers/" + ID + "/";

    private final JsonObject animation;
    private final JsonObject events;
    private final VaultTimeline timeline;

    private VaultManifest(JsonObject animation, JsonObject events) {
        this.animation = animation;
        this.events = events;
        this.timeline = VaultTimeline.parse(animation);
    }

    public static VaultManifest load() {
        return new VaultManifest(read("animation.json"), read("events.json"));
    }

    private static JsonObject read(String name) {
        try (InputStream in = VaultManifest.class.getResourceAsStream(BASE + name)) {
            if (in == null) throw new IOException("missing resource " + BASE + name);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load the Jack-o'-Lantern Vault manifest " + name, ex);
        }
    }

    public JsonObject animation() {
        return animation;
    }

    public JsonObject eventsJson() {
        return events;
    }

    public VaultTimeline timeline() {
        return timeline;
    }

    public List<VaultEvent> events() {
        List<VaultEvent> out = new ArrayList<>();
        events.getAsJsonArray("events").forEach(e -> out.add(VaultEvent.parse(e.getAsJsonObject())));
        return out;
    }

    public VaultMachine newMachine() {
        return new VaultMachine(timeline, animation, events());
    }

    /** An event anchor (model units, vault space) such as "plate" or "pile". */
    public double[] anchor(String name) {
        var a = events.getAsJsonObject("anchors").getAsJsonArray(name);
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }
}
