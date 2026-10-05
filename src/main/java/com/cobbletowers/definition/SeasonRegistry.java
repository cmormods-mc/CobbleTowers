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
 * Loads the authored seasons (P36a) from {@code data/<namespace>/cobbletowers/seasons/*.json} on every datapack reload. The same rule
 * as every registry here: a malformed file is skipped with a message that names it, never thrown. A season with no file is
 * generated, see {@link SeasonDefinition#generated}.
 */
public final class SeasonRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, SeasonDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "seasons");
    private static final String FOLDER = "cobbletowers/seasons";

    private static volatile Map<ResourceLocation, SeasonDefinition> LOADED = Map.of();

    /** The authored season with this number, if any. */
    public static Optional<SeasonDefinition> get(int number) {
        return LOADED.values().stream().filter(season -> season.number() == number).findFirst();
    }

    /** The season's definition: authored if there is one, otherwise generated. */
    public static SeasonDefinition of(int number) {
        return get(number).orElseGet(() -> SeasonDefinition.generated(number));
    }

    /** Every authored season, in id order. */
    public static List<SeasonDefinition> all() {
        return List.copyOf(LOADED.values());
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, SeasonDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, SeasonDefinition> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, SeasonDefinition.fromJson(id, json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed season {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} season file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, SeasonDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        LOADED = new LinkedHashMap<>(prepared);
        TowerLog.info("Loaded {} authored season(s).", prepared.size());
    }
}
