package com.cobbletowers.definition;

import com.cobbletowers.TowerLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads the season track (P36b) from {@code data/<namespace>/cobbletowers/season_tracks/*.json} on every datapack reload. The same rule
 * as every registry here: a malformed file is skipped with a message that names it, never thrown. One track serves every season: the
 * first loaded, in id order (a server with several names which it wants by how its datapack is ordered).
 */
public final class SeasonTrackRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, SeasonTrackDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "season_tracks");
    private static final String FOLDER = "cobbletowers/season_tracks";

    private static volatile Map<ResourceLocation, SeasonTrackDefinition> LOADED = Map.of();

    /** The track every season uses, empty when none is loaded (then seasons run without a track). */
    public static Optional<SeasonTrackDefinition> current() {
        return LOADED.values().stream().findFirst();
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, SeasonTrackDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, SeasonTrackDefinition> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, SeasonTrackDefinition.fromJson(json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed season track {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} season track file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, SeasonTrackDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        LOADED = new LinkedHashMap<>(prepared);
        TowerLog.info("Loaded {} season track(s).", prepared.size());
    }
}
