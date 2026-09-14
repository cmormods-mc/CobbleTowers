package com.cobbletowers.runtime;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.persistence.TowerPersistenceRuntime;
import com.cobbletowers.persistence.TowerSavedData;
import com.cobbletowers.run.TowerRunManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Owns server-lifetime CobbleTowers availability and restored run state.
 *
 * <p>The runtime is deliberately small: persistence loading happens once at server start, and all
 * gameplay services obtain the already-restored {@link TowerRunManager} from here. A newer unknown
 * save schema disables only CobbleTowers while preserving the underlying data untouched.
 */
public final class TowerServerRuntime {
    private TowerRunManager runManager;
    private String disabledReason;

    public void start(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (runManager != null || disabledReason != null) {
            throw new IllegalStateException("CobbleTowers runtime is already initialized");
        }

        TowerSavedData savedData = TowerPersistenceRuntime.loadGlobal(server);
        if (savedData.incompatibleNewerSchema()) {
            disabledReason = "save schema " + savedData.loadedSchemaVersion()
                    + " is newer than supported schema " + TowerSavedData.CURRENT_SCHEMA_VERSION;
            CobbleTowers.LOGGER.error(
                    "CobbleTowers is disabled for this server session because {}. Saved Tower data is preserved untouched.",
                    disabledReason
            );
            return;
        }

        TowerPersistenceRuntime.RestoreResult restored = TowerPersistenceRuntime.restoreCompatible(savedData);
        runManager = restored.manager();

        List<UUID> rejected = restored.rejectedRunIds();
        if (!rejected.isEmpty()) {
            CobbleTowers.LOGGER.error(
                    "CobbleTowers restored with {} rejected run(s): {}. Their snapshots remain on disk for recovery.",
                    rejected.size(),
                    rejected
            );
        }

        CobbleTowers.LOGGER.info(
                "CobbleTowers runtime enabled with {} restored Tower run(s).",
                runManager.activeRunCount()
        );
    }

    public void stop() {
        if (runManager != null) {
            // Runtime maps and allocator ownership are process-local. Persistent snapshots have already
            // been updated mutation-by-mutation through TowerRunManager's persistence sinks.
            runManager.clearRuntimeState();
        }
        runManager = null;
        disabledReason = null;
    }

    public boolean enabled() {
        return runManager != null;
    }

    public Optional<TowerRunManager> runManager() {
        return Optional.ofNullable(runManager);
    }

    public Optional<String> disabledReason() {
        return Optional.ofNullable(disabledReason);
    }
}
