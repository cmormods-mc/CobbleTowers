package com.cobbletowers.api.registry;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * What a loaded tower is, without how it is stored. {@code revision} is the author's number and {@code contentDigest}
 * is derived from the file, so a run can pin both (TDS #40).
 * @param floorIds every floor in order
 * @param milestoneFloors 1-based indices of milestone floors, ascending
 */
public record TowerSummary(
        ResourceLocation id,
        String displayName,
        int schemaVersion,
        int revision,
        String contentDigest,
        ResourceLocation rulesetId,
        List<ResourceLocation> floorIds,
        List<Integer> milestoneFloors) {

    public TowerSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(rulesetId, "rulesetId");
        floorIds = List.copyOf(floorIds);
        milestoneFloors = List.copyOf(milestoneFloors);
    }

    public int floorCount() {
        return floorIds.size();
    }
}
