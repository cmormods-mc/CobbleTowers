package com.cobbletowers.definition;

import com.cobbletowers.TowerLog;
import com.cobbletowers.mastery.MasteryTracks;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads the mastery tracks (P37) from {@code data/<namespace>/cobbletowers/mastery_tracks/*.json} on every datapack reload. A malformed file
 * is skipped with a message that names it, never thrown, like every registry here; what the files add up to is {@link MasteryTracks}.
 */
public final class MasteryTrackRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, MasteryTrackDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "mastery_tracks");
    private static final String FOLDER = "cobbletowers/mastery_tracks";

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, MasteryTrackDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, MasteryTrackDefinition> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, MasteryTrackDefinition.fromJson(json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed mastery track {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} mastery track file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, MasteryTrackDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        MasteryTracks.setLoaded(prepared);
        TowerLog.info("Loaded {} mastery track file(s).", prepared.size());
    }
}
