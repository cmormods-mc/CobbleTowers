package com.cobbletowers.definition;

import com.cobbletowers.api.registry.TowerSummary;
import com.cobbletowers.api.rules.RulesetView;
import com.cobbletowers.api.tower.FloorView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything one datapack reload produced: the definition maps, their digests (keyed by {@link DefinitionKey}) and
 * cross-reference problems. Immutable and swapped in whole by {@link TowerDefinitionRegistry}. Problems are carried,
 * not thrown, so one bad id cannot stop the server.
 */
public record TowerContent(
        Map<ResourceLocation, TowerDefinition> towers,
        Map<ResourceLocation, FloorDefinition> floors,
        Map<ResourceLocation, EncounterPoolDefinition> pools,
        Map<ResourceLocation, RulesetDefinition> rulesets,
        Map<ResourceLocation, MilestoneDefinition> milestones,
        Map<ResourceLocation, BossPoolDefinition> bossPools,
        Map<ResourceLocation, ModifierDefinition> modifiers,
        Map<ResourceLocation, RewardTableDefinition> rewardTables,
        Map<ResourceLocation, RegionalThemeDefinition> regionalThemes,
        Map<ResourceLocation, VendorServiceDefinition> vendorServices,
        Map<ResourceLocation, ScoutingProfileDefinition> scoutingProfiles,
        Map<DefinitionKey, String> digests,
        List<String> problems,
        List<ResourceLocation> sortedTowerIds) {

    public static final TowerContent EMPTY = new TowerContent(
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
            Map.of(), Map.of(), List.of(), List.of());

    public TowerContent {
        towers = Map.copyOf(towers);
        floors = Map.copyOf(floors);
        pools = Map.copyOf(pools);
        rulesets = Map.copyOf(rulesets);
        milestones = Map.copyOf(milestones);
        bossPools = Map.copyOf(bossPools);
        modifiers = Map.copyOf(modifiers);
        rewardTables = Map.copyOf(rewardTables);
        regionalThemes = Map.copyOf(regionalThemes);
        vendorServices = Map.copyOf(vendorServices);
        scoutingProfiles = Map.copyOf(scoutingProfiles);
        digests = Map.copyOf(digests);
        problems = List.copyOf(problems);
        sortedTowerIds = List.copyOf(sortedTowerIds);
    }

    /**
     * Builds the loaded content, running every cross-reference check once. Sorted ids are computed here, not per
     * lookup.
     */
    public static TowerContent of(Map<ResourceLocation, TowerDefinition> towers,
                                  Map<ResourceLocation, FloorDefinition> floors,
                                  Map<ResourceLocation, EncounterPoolDefinition> pools,
                                  Map<ResourceLocation, RulesetDefinition> rulesets,
                                  Map<ResourceLocation, MilestoneDefinition> milestones,
                                  Map<ResourceLocation, BossPoolDefinition> bossPools,
                                  Map<ResourceLocation, ModifierDefinition> modifiers,
                                  Map<ResourceLocation, RewardTableDefinition> rewardTables,
                                  Map<ResourceLocation, RegionalThemeDefinition> regionalThemes,
                                  Map<ResourceLocation, VendorServiceDefinition> vendorServices,
                                  Map<ResourceLocation, ScoutingProfileDefinition> scoutingProfiles,
                                  Map<DefinitionKey, String> digests) {
        List<String> problems = new ArrayList<>();
        problems.addAll(modifierProblems(modifiers));
        problems.addAll(regionalPoolProblems(pools, regionalThemes));
        for (TowerDefinition tower : towers.values()) {
            if (!rulesets.containsKey(tower.rulesetId())) {
                problems.add(tower.id() + " names ruleset " + tower.rulesetId() + ", which is not loaded");
            }
            if (!rewardTables.containsKey(tower.rewardTableId())) {
                problems.add(tower.id() + " names reward table " + tower.rewardTableId() + ", which is not loaded");
            }
            tower.regionalTheme().ifPresent(themeId -> {
                if (!regionalThemes.containsKey(themeId)) {
                    problems.add(tower.id() + " names regional theme " + themeId + ", which is not loaded");
                }
            });
            tower.scoutingProfile().ifPresent(profileId -> {
                if (!scoutingProfiles.containsKey(profileId)) {
                    problems.add(tower.id() + " names scouting profile " + profileId + ", which is not loaded");
                }
            });
            List<Integer> indices = new ArrayList<>();
            for (ResourceLocation floorId : tower.floorIds()) {
                FloorDefinition floor = floors.get(floorId);
                if (floor == null) {
                    problems.add(tower.id() + " names floor " + floorId + ", which is not loaded");
                    continue;
                }
                indices.add(floor.index());
                if (!pools.containsKey(floor.encounterPoolId())) {
                    problems.add(floor.id() + " names encounter pool " + floor.encounterPoolId()
                            + ", which is not loaded");
                }
                floor.bossPoolId().ifPresent(bossPoolId -> {
                    if (!bossPools.containsKey(bossPoolId)) {
                        problems.add(floor.id() + " names boss pool " + bossPoolId + ", which is not loaded");
                    }
                });
                floor.rulesetOverride().ifPresent(override -> {
                    if (!rulesets.containsKey(override)) {
                        problems.add(floor.id() + " names ruleset override " + override + ", which is not loaded");
                    }
                });
                // Reserved since P1 and carried untouched ever since; P8 is where it finally resolves.
                for (ResourceLocation modifierId : floor.modifierIds()) {
                    if (!modifiers.containsKey(modifierId)) {
                        problems.add(floor.id() + " names modifier " + modifierId + ", which is not loaded");
                    }
                }
            }
            problems.addAll(floorNumbering(tower, indices));
            problems.addAll(milestoneProblems(tower, floors, milestones));
        }
        List<ResourceLocation> sorted = towers.keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        return new TowerContent(towers, floors, pools, rulesets, milestones, bossPools, modifiers, rewardTables,
                regionalThemes, vendorServices, scoutingProfiles, digests, problems, sorted);
    }

    /** Every loaded pool's {@code regional_pool}, if it names one, must resolve (P10). */
    private static List<String> regionalPoolProblems(Map<ResourceLocation, EncounterPoolDefinition> pools,
                                                      Map<ResourceLocation, RegionalThemeDefinition> regionalThemes) {
        List<String> problems = new ArrayList<>();
        for (EncounterPoolDefinition pool : pools.values()) {
            pool.regionalPool().ifPresent(themeId -> {
                if (!regionalThemes.containsKey(themeId)) {
                    problems.add(pool.id() + " names regional pool " + themeId + ", which is not loaded");
                }
            });
        }
        return problems;
    }

    /**
     * A modifier's own references (TDS #58): dangling ids and prerequisite chains that can never be satisfied.
     * Single-file contradictions are refused by the record itself.
     */
    private static List<String> modifierProblems(Map<ResourceLocation, ModifierDefinition> modifiers) {
        List<String> problems = new ArrayList<>();
        for (ModifierDefinition modifier : modifiers.values()) {
            for (ResourceLocation excluded : modifier.excludes()) {
                if (!modifiers.containsKey(excluded)) {
                    problems.add(modifier.id() + " excludes " + excluded + ", which is not loaded");
                }
            }
            for (ResourceLocation required : modifier.requires()) {
                if (!modifiers.containsKey(required)) {
                    problems.add(modifier.id() + " requires " + required + ", which is not loaded");
                }
            }
        }
        return problems;
    }

    /** Floors must be numbered 1..n in listed order: a gap or a repeat means a floor nobody reaches. */
    private static List<String> floorNumbering(TowerDefinition tower, List<Integer> indices) {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < indices.size(); i++) {
            int expected = i + 1;
            if (indices.get(i) != expected) {
                problems.add(tower.id() + " lists floor " + (i + 1) + " with index " + indices.get(i)
                        + "; floors must be numbered 1.." + indices.size() + " in order");
                break;
            }
        }
        return problems;
    }

    /** A milestone must land on a floor the tower has, and that floor must be marked as one. */
    private static List<String> milestoneProblems(TowerDefinition tower,
                                                  Map<ResourceLocation, FloorDefinition> floors,
                                                  Map<ResourceLocation, MilestoneDefinition> milestones) {
        List<String> problems = new ArrayList<>();
        Map<Integer, FloorDefinition> byIndex = new LinkedHashMap<>();
        for (ResourceLocation floorId : tower.floorIds()) {
            FloorDefinition floor = floors.get(floorId);
            if (floor != null) byIndex.put(floor.index(), floor);
        }
        for (ResourceLocation milestoneId : tower.milestoneIds()) {
            MilestoneDefinition milestone = milestones.get(milestoneId);
            if (milestone == null) {
                problems.add(tower.id() + " names milestone " + milestoneId + ", which is not loaded");
                continue;
            }
            FloorDefinition floor = byIndex.get(milestone.floorIndex());
            if (floor == null) {
                problems.add(milestoneId + " is for floor " + milestone.floorIndex() + ", which " + tower.id()
                        + " does not have");
            } else if (floor.milestone().isEmpty()) {
                problems.add(floor.id() + " is used by milestone " + milestoneId
                        + " but is not marked as a milestone floor");
            } else if (floor.milestone().get() != milestone.kind()) {
                problems.add(floor.id() + " is marked " + floor.milestone().get() + " but milestone "
                        + milestoneId + " is " + milestone.kind());
            }
        }
        return problems;
    }

    public Optional<TowerSummary> summary(ResourceLocation towerId) {
        TowerDefinition tower = towers.get(towerId);
        if (tower == null) return Optional.empty();
        List<Integer> milestoneFloors = tower.milestoneIds().stream()
                .map(milestones::get)
                .filter(java.util.Objects::nonNull)
                .map(MilestoneDefinition::floorIndex)
                .sorted()
                .toList();
        return Optional.of(new TowerSummary(tower.id(), tower.displayName(), tower.schemaVersion(), tower.revision(),
                digests.getOrDefault(DefinitionKey.tower(tower.id()), ""), tower.rulesetId(), tower.floorIds(), milestoneFloors));
    }

    /** The floor a run on this tower is standing on, by its 1-based index. */
    public Optional<FloorDefinition> floorAt(ResourceLocation towerId, int index) {
        TowerDefinition tower = towers.get(towerId);
        if (tower == null) return Optional.empty();
        // A floor past the last is a later cycle of an ascending tower (P30): the same floor as ever, harder.
        int contentIndex = tower.contentFloor(index);
        for (ResourceLocation floorId : tower.floorIds()) {
            FloorDefinition floor = floors.get(floorId);
            if (floor != null && floor.index() == contentIndex) return Optional.of(floor);
        }
        return Optional.empty();
    }

    /** A tower's floors in play order; empty when the tower is unknown or a floor is missing. */
    public List<FloorView> floorViews(ResourceLocation towerId) {
        TowerDefinition tower = towers.get(towerId);
        if (tower == null) return List.of();
        List<FloorView> views = new ArrayList<>(tower.floorIds().size());
        for (ResourceLocation floorId : tower.floorIds()) {
            FloorDefinition floor = floors.get(floorId);
            if (floor != null) views.add(floor);
        }
        return List.copyOf(views);
    }

    public Optional<RulesetView> rulesetView(ResourceLocation rulesetId) {
        return Optional.ofNullable(rulesets.get(rulesetId));
    }

    /** One regional theme by id. */
    public Optional<RegionalThemeDefinition> regionalTheme(ResourceLocation themeId) {
        return Optional.ofNullable(regionalThemes.get(themeId));
    }

    /** One modifier by id. */
    public Optional<ModifierDefinition> modifier(ResourceLocation modifierId) {
        return Optional.ofNullable(modifiers.get(modifierId));
    }

    /** One reward table by id. */
    public Optional<RewardTableDefinition> rewardTable(ResourceLocation rewardTableId) {
        return Optional.ofNullable(rewardTables.get(rewardTableId));
    }

    /** One vendor service by id. */
    public Optional<VendorServiceDefinition> vendorService(ResourceLocation serviceId) {
        return Optional.ofNullable(vendorServices.get(serviceId));
    }

    /** Every vendor service, in a stable order -- the shop's catalog is the same list for everyone. */
    public List<VendorServiceDefinition> vendorCatalog() {
        return vendorServices.values().stream()
                .sorted(Comparator.comparing(service -> service.id().toString()))
                .toList();
    }

    /** One scouting profile by id. */
    public Optional<ScoutingProfileDefinition> scoutingProfile(ResourceLocation profileId) {
        return Optional.ofNullable(scoutingProfiles.get(profileId));
    }

    /** The scouting profile a tower actually uses, if it names one. */
    public Optional<ScoutingProfileDefinition> scoutingProfileFor(ResourceLocation towerId) {
        TowerDefinition tower = towers.get(towerId);
        if (tower == null) return Optional.empty();
        return tower.scoutingProfile().flatMap(this::scoutingProfile);
    }

    /** The milestone landing on this floor of this tower, if there is one. */
    /** Whether a milestone is a boss or a champion, by its id. Empty for an id no tower names. */
    public Optional<com.cobbletowers.api.tower.MilestoneKind> milestoneKindOf(ResourceLocation milestoneId) {
        MilestoneDefinition milestone = milestones().get(milestoneId);
        return milestone == null ? Optional.empty() : Optional.of(milestone.kind());
    }

    public Optional<MilestoneDefinition> milestoneAt(ResourceLocation towerId, int floorIndex) {
        TowerDefinition tower = towers.get(towerId);
        if (tower == null) return Optional.empty();
        for (ResourceLocation milestoneId : tower.milestoneIds()) {
            MilestoneDefinition milestone = milestones.get(milestoneId);
            if (milestone != null && milestone.floorIndex() == tower.contentFloor(floorIndex)) return Optional.of(milestone);
        }
        return Optional.empty();
    }

    /**
     * The modifiers a floor may offer, in a stable order. A floor that names none draws from every loaded modifier.
     */
    /** Every loaded relic, in a stable order (a draw walks this list subtracting weights). */
    public List<ModifierDefinition> relicPool() {
        List<ModifierDefinition> pool = new ArrayList<>();
        for (ModifierDefinition modifier : modifiers.values()) {
            if (modifier.relic()) pool.add(modifier);
        }
        pool.sort(Comparator.comparing(modifier -> modifier.id().toString()));
        return List.copyOf(pool);
    }

    public List<ModifierDefinition> draftablePool(ResourceLocation towerId, int floorIndex) {
        List<ResourceLocation> named = floorAt(towerId, floorIndex)
                .map(FloorDefinition::modifierIds)
                .orElse(List.of());
        List<ModifierDefinition> pool = new ArrayList<>();
        if (named.isEmpty()) {
            pool.addAll(modifiers.values());
        } else {
            for (ResourceLocation id : named) {
                ModifierDefinition modifier = modifiers.get(id);
                if (modifier != null) pool.add(modifier);
            }
        }
        // A relic (P34) is found after a milestone boss, never drafted as an ordinary challenge.
        pool.removeIf(ModifierDefinition::relic);
        // Sorted because a map's iteration order is not a contract and a draw walks this list.
        pool.sort(Comparator.comparing(modifier -> modifier.id().toString()));
        return List.copyOf(pool);
    }

    public int definitionCount() {
        return towers.size() + floors.size() + pools.size() + rulesets.size() + milestones.size()
                + bossPools.size() + modifiers.size() + rewardTables.size() + regionalThemes.size()
                + vendorServices.size() + scoutingProfiles.size();
    }
}
