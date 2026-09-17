package com.cobbletowers.api.registry;

import com.cobbletowers.api.rules.RulesetView;
import com.cobbletowers.api.tower.FloorView;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Read-only access to the tower content a server has loaded.
 *
 * <p>Everything here is a snapshot of the last datapack reload, and all of it is immutable. An addon
 * that wants to add content ships a datapack; this interface is for reading what exists.
 */
public interface TowerRegistryView {

    /** Every loaded tower id, sorted. */
    List<ResourceLocation> towerIds();

    Optional<TowerSummary> tower(ResourceLocation towerId);

    /** A tower's floors, in order. Empty if the tower is not loaded. */
    List<FloorView> floors(ResourceLocation towerId);

    Optional<RulesetView> ruleset(ResourceLocation rulesetId);

    /**
     * Problems found when the definitions were last loaded: a dangling floor id, a missing pool, a
     * milestone slot with no definition. Reported rather than thrown, so one broken datapack cannot
     * stop a server starting; empty means the content is consistent.
     */
    List<String> loadProblems();
}
