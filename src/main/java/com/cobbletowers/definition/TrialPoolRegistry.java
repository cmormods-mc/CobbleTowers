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
 * Loads trial pools (P32) from {@code data/<namespace>/cobbletowers/trial_pools/*.json}. A malformed file is skipped
 * with a message naming it.
 */
public final class TrialPoolRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, TrialPoolDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = CobbleTowers.id("trial_pools");
    private static final String FOLDER = "cobbletowers/trial_pools";

    private static volatile Map<ResourceLocation, TrialPoolDefinition> LOADED = Map.of();

    public static List<TrialPoolDefinition> all() {
        return List.copyOf(LOADED.values());
    }

    /**
     * The pool for a kind: the first loaded one of that kind (a server with several should name one; the default pack
     * ships one each).
     */
    public static Optional<TrialPoolDefinition> ofKind(TrialPoolDefinition.Kind kind) {
        return LOADED.values().stream().filter(pool -> pool.kind() == kind).findFirst();
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, TrialPoolDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, TrialPoolDefinition> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, TrialPoolDefinition.fromJson(id, json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed trial pool {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} trial pool file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, TrialPoolDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        LOADED = new LinkedHashMap<>(prepared);
        TowerLog.info("Loaded {} trial pool(s).", prepared.size());
    }
}
