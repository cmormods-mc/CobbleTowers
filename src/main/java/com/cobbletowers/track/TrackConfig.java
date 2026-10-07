package com.cobbletowers.track;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.MasteryTrackDefinition;
import com.cobbletowers.definition.SeasonTrackDefinition;
import com.cobbletowers.mastery.MasteryTracks;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The server owner's say over the tracks (P37), read from {@code config/cobbletowers-tracks.json}:
 * <pre>{"auto_claim": false, "mastery": {...one mastery track file...}, "season": {"add_steps": [...]}}</pre>
 * {@code auto_claim} grants a reached node at once; the {@code mastery} and {@code season} blocks are merged after every datapack file, so an
 * owner can add rewards or override a perk. A missing file changes nothing; a bad one is logged and ignored.
 */
public record TrackConfig(boolean autoClaim, Optional<MasteryTrackDefinition> mastery, List<SeasonTrackDefinition.AddStep> seasonSteps) {

    private static volatile TrackConfig CURRENT = new TrackConfig(false, Optional.empty(), List.of());

    public static TrackConfig current() {
        return CURRENT;
    }

    public static void install() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> reload());
    }

    /** Reads the file and applies it; also what a test calls. */
    public static void reload() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-tracks.json");
        TrackConfig next = new TrackConfig(false, Optional.empty(), List.of());
        if (Files.exists(file)) {
            try {
                next = parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            } catch (IOException | RuntimeException ex) {
                TowerLog.error("{} is not usable ({}); the tracks use the datapacks alone", file, ex.toString());
            }
        }
        set(next);
    }

    public static void set(TrackConfig next) {
        CURRENT = next;
        MasteryTracks.setConfig(next.mastery());
    }

    public static TrackConfig parse(JsonObject root) {
        boolean auto = root.has("auto_claim") && root.get("auto_claim").getAsBoolean();
        Optional<MasteryTrackDefinition> mastery = Optional.empty();
        if (root.has("mastery")) {
            JsonObject block = root.getAsJsonObject("mastery");
            if (!block.has("schema_version")) block.addProperty("schema_version", MasteryTrackDefinition.SUPPORTED_SCHEMA_VERSION);
            mastery = Optional.of(MasteryTrackDefinition.fromJson(block));
        }
        List<SeasonTrackDefinition.AddStep> steps = root.has("season")
                ? SeasonTrackDefinition.addSteps(root.getAsJsonObject("season")) : List.of();
        return new TrackConfig(auto, mastery, steps);
    }
}
