package com.cobbletowers.armor;

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
 * Loads armor sets from {@code data/<namespace>/cobbletowers/armor_sets/*.json} on every reload (P24). A malformed
 * file is skipped with a message naming it, never thrown, so the server can still start.
 */
public final class ArmorSetRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, ArmorSetDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "armor_sets");
    private static final String FOLDER = "cobbletowers/armor_sets";

    private static volatile Map<ResourceLocation, ArmorSetDefinition> SETS = Map.of();

    /** The sets of the last completed reload, in a stable (id) order. */
    public static List<ArmorSetDefinition> sets() {
        return List.copyOf(SETS.values());
    }

    public static Optional<ArmorSetDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(SETS.get(id));
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, ArmorSetDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ArmorSetDefinition> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, ArmorSetDefinition.fromJson(id, json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed armor set {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} armor set file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, ArmorSetDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        SETS = new LinkedHashMap<>(prepared);
        TowerLog.info("Loaded {} armor set(s).", prepared.size());
        // Players already wearing something keep their cached bonuses until the next periodic check, which is within
        // half a second; nothing needs to be pushed from here.
    }
}
