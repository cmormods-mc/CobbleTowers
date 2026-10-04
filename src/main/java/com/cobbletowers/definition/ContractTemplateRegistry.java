package com.cobbletowers.definition;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.ContractTemplateDefinition.Period;
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

/** Loads contract templates (P32c) from {@code data/<namespace>/cobbletowers/contract_templates/*.json}; malformed files are skipped by name. */
public final class ContractTemplateRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, ContractTemplateDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "contract_templates");
    private static final String FOLDER = "cobbletowers/contract_templates";

    private static volatile Map<ResourceLocation, ContractTemplateDefinition> LOADED = Map.of();

    public static List<ContractTemplateDefinition> all() {
        return List.copyOf(LOADED.values());
    }

    /** The templates of a period in a stable (id) order, which is what makes a day's draw the same everywhere. */
    public static List<ContractTemplateDefinition> of(Period period) {
        return LOADED.values().stream().filter(template -> template.period() == period).toList();
    }

    public static Optional<ContractTemplateDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(LOADED.get(id));
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, ContractTemplateDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ContractTemplateDefinition> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, ContractTemplateDefinition.fromJson(id, json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed contract template {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} contract template file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, ContractTemplateDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        LOADED = new LinkedHashMap<>(prepared);
        TowerLog.info("Loaded {} contract template(s).", prepared.size());
    }
}
