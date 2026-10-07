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
 * Loads mastery achievements (P31) from {@code data/<namespace>/cobbletowers/achievements/*.json} on every reload. A
 * malformed file is skipped with a message naming it, never thrown. Loaded on its own since achievements reference
 * nothing else.
 */
public final class AchievementRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, AchievementDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = CobbleTowers.id("achievements");
    private static final String FOLDER = "cobbletowers/achievements";

    private static volatile Map<ResourceLocation, AchievementDefinition> LOADED = Map.of();

    /** The achievements of the last completed reload, in a stable (id) order. */
    public static List<AchievementDefinition> all() {
        return List.copyOf(LOADED.values());
    }

    public static Optional<AchievementDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(LOADED.get(id));
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, AchievementDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, AchievementDefinition> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, AchievementDefinition.fromJson(id, json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed achievement {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} achievement file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, AchievementDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        LOADED = new LinkedHashMap<>(prepared);
        TowerLog.info("Loaded {} mastery achievement(s).", prepared.size());
    }
}
