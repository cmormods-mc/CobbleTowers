package com.cobbletowers.definition;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.registry.TowerRegistryView;
import com.cobbletowers.api.registry.TowerSummary;
import com.cobbletowers.api.rules.RulesetView;
import com.cobbletowers.api.tower.FloorView;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads tower content from {@code data/<namespace>/cobbletowers/...} on every datapack reload.
 *
 * <p>A malformed file is skipped with a message naming it, never thrown: a dedicated server that
 * cannot finish its initial reload refuses to start, so one bad JSON in any datapack -- a namespace
 * anyone can write to -- would take the server down rather than take itself out of the pool. That is
 * CobbleRaids' rule, learned there the hard way, and it applies identically here.
 *
 * <p>Cross-reference problems are reported the same way, so a tower with a dangling floor id is
 * unplayable and visible rather than fatal.
 */
public final class TowerDefinitionRegistry
        extends SimplePreparableReloadListener<TowerContent> implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tower_definitions");

    private static volatile TowerContent CONTENT = TowerContent.EMPTY;
    private static final TowerRegistryView VIEW = new View();

    /** The content of the last completed reload. Never null. */
    public static TowerContent content() {
        return CONTENT;
    }

    /** The public, read-only face of the same content. */
    public static TowerRegistryView view() {
        return VIEW;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected TowerContent prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<DefinitionKey, String> digests = new LinkedHashMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        Map<ResourceLocation, TowerDefinition> towers =
                load(manager, "towers", TowerDefinition::fromJson, digests, rejected);
        Map<ResourceLocation, FloorDefinition> floors =
                load(manager, "floors", FloorDefinition::fromJson, digests, rejected);
        Map<ResourceLocation, EncounterPoolDefinition> pools =
                load(manager, "encounter_pools", EncounterPoolDefinition::fromJson, digests, rejected);
        Map<ResourceLocation, RulesetDefinition> rulesets =
                load(manager, "rulesets", RulesetDefinition::fromJson, digests, rejected);
        Map<ResourceLocation, MilestoneDefinition> milestones =
                load(manager, "milestones", MilestoneDefinition::fromJson, digests, rejected);
        Map<ResourceLocation, BossPoolDefinition> bossPools =
                load(manager, "boss_pools", BossPoolDefinition::fromJson, digests, rejected);
        Map<ResourceLocation, ModifierDefinition> modifiers =
                load(manager, "modifiers", ModifierDefinition::fromJson, digests, rejected);

        if (!rejected.isEmpty()) {
            // A summary as well as the per-file errors: those are easy to scroll past, and an operator
            // whose tower never appears needs to find out here rather than in game.
            TowerLog.error("{} tower definition file(s) were skipped as malformed and will not load: {}",
                    rejected.size(), rejected.stream().map(ResourceLocation::toString).sorted()
                            .collect(Collectors.joining(", ")));
        }
        return TowerContent.of(towers, floors, pools, rulesets, milestones, bossPools, modifiers, digests);
    }

    private static <T> Map<ResourceLocation, T> load(ResourceManager manager, String folder,
                                                     BiFunction<ResourceLocation, JsonObject, T> parser,
                                                     Map<DefinitionKey, String> digests,
                                                     List<ResourceLocation> rejected) {
        String prefix = "cobbletowers/" + folder + "/";
        Map<ResourceLocation, T> loaded = new LinkedHashMap<>();
        manager.listResources(prefix.substring(0, prefix.length() - 1),
                path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(prefix.length(), path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, parser.apply(id, json));
                digests.put(DefinitionKey.of(folder, id), ContentDigest.of(json));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed {} definition {}: {}", folder, id, ex.toString());
            }
        });
        return loaded;
    }

    @Override
    protected void apply(TowerContent prepared, ResourceManager manager, ProfilerFiller profiler) {
        CONTENT = prepared;
        if (prepared.problems().isEmpty()) {
            TowerLog.info("Loaded {} tower definition(s) across {} file(s), all references resolved.",
                    prepared.towers().size(), prepared.definitionCount());
        } else {
            TowerLog.warn("Loaded {} tower definition(s) across {} file(s) with {} problem(s):",
                    prepared.towers().size(), prepared.definitionCount(), prepared.problems().size());
            for (String problem : prepared.problems()) {
                TowerLog.warn("  {}", problem);
            }
        }
    }

    /** The API view, reading whatever the last reload produced. */
    private static final class View implements TowerRegistryView {
        @Override public List<ResourceLocation> towerIds() { return CONTENT.sortedTowerIds(); }

        @Override public Optional<TowerSummary> tower(ResourceLocation towerId) { return CONTENT.summary(towerId); }

        @Override public List<FloorView> floors(ResourceLocation towerId) { return CONTENT.floorViews(towerId); }

        @Override public Optional<RulesetView> ruleset(ResourceLocation rulesetId) {
            return CONTENT.rulesetView(rulesetId);
        }

        @Override public List<String> loadProblems() { return CONTENT.problems(); }
    }
}
