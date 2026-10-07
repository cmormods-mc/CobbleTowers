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
 * Loads rental sets (P33) from {@code data/<namespace>/cobbletowers/rental_sets/*.json}; malformed files are skipped
 * by name.
 */
public final class RentalSetRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, RentalSetDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = CobbleTowers.id("rental_sets");
    private static final String FOLDER = "cobbletowers/rental_sets";

    private static volatile Map<ResourceLocation, RentalSetDefinition> LOADED = Map.of();

    /** Every set, in a stable (id) order, which is what makes a seeded draw the same everywhere. */
    public static List<RentalSetDefinition> all() {
        return List.copyOf(LOADED.values());
    }

    public static Optional<RentalSetDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(LOADED.get(id));
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, RentalSetDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, RentalSetDefinition> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, RentalSetDefinition.fromJson(id, json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed rental set {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} rental set file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, RentalSetDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        LOADED = new LinkedHashMap<>(prepared);
        TowerLog.info("Loaded {} rental set(s).", prepared.size());
    }
}
