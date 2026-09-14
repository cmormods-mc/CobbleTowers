package com.cobbletowers.structure;

import java.util.UUID;

/**
 * Executes exactly one bounded world mutation for one run/section.
 *
 * <p>Implementations run on the Minecraft server thread. They must not scan the whole Tower
 * dimension, allocate unbounded work, or release instance ownership. Expected missing/corrupt asset
 * conditions should return {@link TowerStructureOperationResult#ASSET_FAULT}.
 */
@FunctionalInterface
public interface TowerStructureExecutor {
    TowerStructureOperationResult execute(
            UUID runId,
            int slotIndex,
            TowerStructureSection section,
            TowerStructureOperation operation
    );
}
