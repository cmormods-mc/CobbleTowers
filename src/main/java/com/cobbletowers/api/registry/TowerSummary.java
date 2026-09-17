package com.cobbletowers.api.registry;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * What a loaded tower is, without exposing how it is stored.
 *
 * <p>{@code revision} is the author's number and {@code contentDigest} is derived from the file, so a
 * run can pin both: the digest tells "edited since this run started" apart from "the same content,
 * renumbered" (TDS #40).
 *
 * @param floorIds        every floor in order
 * @param milestoneFloors the 1-based indices of milestone floors, ascending
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
