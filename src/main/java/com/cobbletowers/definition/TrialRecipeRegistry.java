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
 * Loads rotating trial recipes (P46) from {@code data/<namespace>/cobbletowers/trial_recipes/*.json}. A malformed file is skipped
 * with a message naming it.
 */
public final class TrialRecipeRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, RotatingRecipeDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = CobbleTowers.id("trial_recipes");
    private static final String FOLDER = "cobbletowers/trial_recipes";

    private static volatile Map<ResourceLocation, RotatingRecipeDefinition> LOADED = Map.of();

    public static List<RotatingRecipeDefinition> all() {
        return List.copyOf(LOADED.values());
    }

    /**
     * The pool for a kind: the first loaded one of that kind (a server with several should name one; the default pack
     * ships one each).
     */
    public static Optional<RotatingRecipeDefinition> ofKind(TrialPoolDefinition.Kind kind) {
        return LOADED.values().stream().filter(pool -> pool.kind() == kind).findFirst();
    }

    /** Test seam: the recipes a plain-JVM test runs against. */
    public static void setForTests(Map<ResourceLocation, RotatingRecipeDefinition> recipes) {
        LOADED = new LinkedHashMap<>(recipes);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, RotatingRecipeDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, RotatingRecipeDefinition> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, RotatingRecipeDefinition.fromJson(id, json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed trial recipe {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} trial recipe file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, RotatingRecipeDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        LOADED = new LinkedHashMap<>(prepared);
        TowerLog.info("Loaded {} trial recipe(s).", prepared.size());
    }
}
