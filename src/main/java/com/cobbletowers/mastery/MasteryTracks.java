package com.cobbletowers.mastery;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.MasteryTrackDefinition;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * The mastery tracks in force (P37): datapack files, then the owner's config, merged per tower. With no datapack
 * loaded, the shipped default is read from the jar so ranks and perks never go missing.
 */
public final class MasteryTracks {

    private static final String BUILT_IN = "/data/cobbletowers/cobbletowers/mastery_tracks/default.json";

    private static volatile Map<ResourceLocation, MasteryTrackDefinition> loaded = Map.of();
    private static volatile Optional<MasteryTrackDefinition> config = Optional.empty();
    private static final Map<Optional<ResourceLocation>, MasteryTrack> CACHE = new ConcurrentHashMap<>();
    private static volatile MasteryTrackDefinition builtIn;

    private MasteryTracks() {}

    /** Called on every datapack reload with the files the registry parsed (id order). */
    public static void setLoaded(Map<ResourceLocation, MasteryTrackDefinition> files) {
        loaded = new TreeMap<>(files);
        CACHE.clear();
    }

    /** The server owner's config block, merged after every datapack file. */
    public static void setConfig(Optional<MasteryTrackDefinition> file) {
        config = file;
        CACHE.clear();
    }

    /** The track of a tower; with no tower given, the one every tower shares. */
    public static MasteryTrack forTower(ResourceLocation tower) {
        return CACHE.computeIfAbsent(Optional.ofNullable(tower), key -> MasteryTrack.merge(files(tower)));
    }

    private static List<MasteryTrackDefinition> files(ResourceLocation tower) {
        List<MasteryTrackDefinition> files = new ArrayList<>();
        Map<ResourceLocation, MasteryTrackDefinition> source = loaded;
        if (source.isEmpty()) files.add(builtIn());
        // A file for every tower first, then the ones for this tower on top of them; ids sorted within each group.
        for (MasteryTrackDefinition file : source.values()) if (file.tower().isEmpty()) files.add(file);
        if (tower != null) for (MasteryTrackDefinition file : source.values()) if (file.tower().isPresent() && file.appliesTo(tower)) files.add(file);
        config.filter(file -> tower == null ? file.tower().isEmpty() : file.appliesTo(tower)).ifPresent(files::add);
        return files;
    }

    private static MasteryTrackDefinition builtIn() {
        MasteryTrackDefinition cached = builtIn;
        if (cached != null) return cached;
        try (Reader reader = new InputStreamReader(MasteryTracks.class.getResourceAsStream(BUILT_IN), StandardCharsets.UTF_8)) {
            cached = MasteryTrackDefinition.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception ex) {
            TowerLog.error("The built-in mastery track could not be read ({}); mastery has no ranks or perks", ex.toString());
            cached = new MasteryTrackDefinition(Optional.empty(), List.of(), List.of());
        }
        return builtIn = cached;
    }
}
