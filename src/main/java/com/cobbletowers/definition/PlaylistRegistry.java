package com.cobbletowers.definition;

import com.cobbletowers.CobbleTowers;
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
 * Loads playlists (P32) from {@code data/<namespace>/cobbletowers/playlists/*.json} on every reload. A malformed file
 * is skipped with a message naming it.
 */
public final class PlaylistRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, PlaylistDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = CobbleTowers.id("playlists");
    private static final String FOLDER = "cobbletowers/playlists";

    private static volatile Map<ResourceLocation, PlaylistDefinition> LOADED = Map.of();

    /** The playlists of the last completed reload, in a stable (id) order. */
    public static List<PlaylistDefinition> all() {
        return List.copyOf(LOADED.values());
    }

    public static Optional<PlaylistDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(LOADED.get(id));
    }

    /** Whether a run's playlist closes the vendor (Hardcore). */
    public static boolean vendorClosed(com.cobbletowers.persistence.PersistedRun run) {
        return run.options().playlist().flatMap(PlaylistRegistry::get).map(PlaylistDefinition::vendorClosed).orElse(false);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, PlaylistDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, PlaylistDefinition> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, PlaylistDefinition.fromJson(id, json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed playlist {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} playlist file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, PlaylistDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        LOADED = new LinkedHashMap<>(prepared);
        TowerLog.info("Loaded {} playlist(s).", prepared.size());
    }
}
