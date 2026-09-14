package com.cobbletowers.persistence;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.instance.TowerDimensionContract;
import com.cobbletowers.instance.TowerInstanceAllocator;
import com.cobbletowers.run.TowerRun;
import com.cobbletowers.run.TowerRunManager;
import com.cobbletowers.run.TowerRunSnapshot;
import com.cobbletowers.run.TowerRunState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Bridges Minecraft's global SavedData storage to the pure Tower run/instance model.
 *
 * <p>This class deliberately does not choose policy for a newer incompatible schema and does not
 * perform world/template/entity reconciliation. Callers inspect compatibility first, then restore
 * runtime ownership. Conflicting snapshots are reported and retained in SavedData for forensic/admin
 * recovery until product policy explicitly chooses otherwise.
 */
public final class TowerPersistenceRuntime {
    private TowerPersistenceRuntime() {}

    public static TowerSavedData loadGlobal(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(TowerSavedData.factory(), TowerSavedData.STORAGE_ID);
    }

    public static RestoreResult restoreCompatible(TowerSavedData savedData) {
        Objects.requireNonNull(savedData, "savedData");
        if (savedData.incompatibleNewerSchema()) {
            throw new IllegalStateException(
                    "Cannot restore Tower runtime from newer schema " + savedData.loadedSchemaVersion());
        }

        TowerInstanceAllocator allocator = new TowerInstanceAllocator(
                TowerDimensionContract.DEFAULT_INSTANCE_STRIDE,
                TowerDimensionContract.DEFAULT_MAX_INSTANCES
        );
        TowerRunManager manager = new TowerRunManager(allocator, savedData::put, savedData::remove);
        List<UUID> rejectedRuns = new ArrayList<>();

        savedData.snapshots().stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.runId().toString()))
                .forEach(snapshot -> restoreOne(manager, snapshot, rejectedRuns));

        return new RestoreResult(manager, List.copyOf(rejectedRuns));
    }

    private static void restoreOne(
            TowerRunManager manager,
            TowerRunSnapshot snapshot,
            List<UUID> rejectedRuns
    ) {
        try {
            TowerRun run = manager.restore(snapshot);
            if (run.state() == TowerRunState.BOSS_BATTLE) {
                // Showdown/Cobblemon live battle internals are intentionally not persisted.
                // Recovery returns the floor to a state where its boss can be spawned at full HP.
                run.recoverInterruptedBossBattle();
            }
            // PREPARING_NEXT_FLOOR remains durable until the future world reconciler verifies/rebuilds
            // the target floor. Terminal states remain bound until teardown succeeds and release() runs.
        } catch (RuntimeException ex) {
            rejectedRuns.add(snapshot.runId());
            CobbleTowers.LOGGER.error(
                    "Could not restore Tower run {}. Its persisted snapshot has been retained for recovery.",
                    snapshot.runId(),
                    ex
            );
        }
    }

    public record RestoreResult(TowerRunManager manager, List<UUID> rejectedRunIds) {
        public RestoreResult {
            Objects.requireNonNull(manager, "manager");
            rejectedRunIds = List.copyOf(rejectedRunIds);
        }
    }
}
