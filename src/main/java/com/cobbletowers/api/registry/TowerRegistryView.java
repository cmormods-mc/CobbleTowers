package com.cobbletowers.api.registry;

import com.cobbletowers.api.rules.RulesetView;
import com.cobbletowers.api.tower.FloorView;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Read-only access to the tower content last loaded. Immutable; addons add content with a datapack. */
public interface TowerRegistryView {

    /** Every loaded tower id, sorted. */
    List<ResourceLocation> towerIds();

    Optional<TowerSummary> tower(ResourceLocation towerId);

    /** A tower's floors, in order. Empty if the tower is not loaded. */
    List<FloorView> floors(ResourceLocation towerId);

    Optional<RulesetView> ruleset(ResourceLocation rulesetId);

    /**
     * Problems found at the last load (dangling ids, missing pools, milestone slots with no definition). Reported,
     * not thrown; empty means consistent.
     */
    List<String> loadProblems();
}
