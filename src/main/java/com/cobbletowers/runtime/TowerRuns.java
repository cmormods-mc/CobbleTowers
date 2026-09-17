package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerRunStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * The live index of tower runs: by run id, and by the player in one.
 *
 * <p>Indexed rather than searched. TDS §11 forbids per-tick scans of tower players, so "which run is
 * this player in" is a map hit and never a walk over every run.
 *
 * <p>A run here <b>is</b> its {@link PersistedRun} record. A separate mutable run object beside it
 * would be a second source of truth and somewhere for the two to disagree; a mutation replaces the
 * record instead. When P3 gives a run an instance handle that must never be written to disk, that is
 * the point at which a wrapper earns its place.
 *
 * <p>The maps are static and the class is not per-server, so they are cleared on SERVER_STOPPED: an
 * integrated client keeps this JVM across worlds, and anything left here would be read back against
 * the next one.
 */
public final class TowerRuns {

    private static final Map<UUID, PersistedRun> BY_ID = new LinkedHashMap<>();
    private static final Map<UUID, UUID> RUN_BY_PLAYER = new HashMap<>();

    private TowerRuns() {}

    /**
     * Loads every stored run into the index, retiring the ones nothing will read again.
     *
     * <p>Returns how many runs are now indexed. Recovery runs separately, after this, so that
     * loading and classifying are not one step that half-fails.
     */
    public static int load(MinecraftServer server, long now) {
        BY_ID.clear();
        RUN_BY_PLAYER.clear();
        TowerRunStore store = TowerRunStore.get(server);
        int retired = store.retireOldRuns(now);
        for (PersistedRun run : store.runs().values()) index(run);
        if (retired > 0) {
            TowerLog.info("Retired {} finished tower run(s) older than the retention window.", retired);
        }
        return BY_ID.size();
    }

    public static Optional<PersistedRun> get(UUID runId) {
        return Optional.ofNullable(BY_ID.get(runId));
    }

    /** The run this player belongs to, if any. One player is in at most one run. */
    public static Optional<PersistedRun> forPlayer(UUID playerId) {
        UUID runId = RUN_BY_PLAYER.get(playerId);
        return runId == null ? Optional.empty() : get(runId);
    }

    /** Every indexed run, in load order. */
    public static List<PersistedRun> all() {
        return List.copyOf(BY_ID.values());
    }

    /**
     * Writes a run to the index and to the store together, so the two cannot disagree.
     *
     * <p>Order matters: the store's in-memory map is updated before the disk write is attempted, so
     * a failing flush leaves a consistent index and store that are merely not yet on disk -- rather
     * than an index holding a state nothing else has. The flush failure is reported and not
     * swallowed, because a checkpoint that did not reach disk is exactly what the caller thought it
     * was buying.
     */
    public static void save(MinecraftServer server, PersistedRun run, boolean forceCheckpoint) {
        index(run);
        TowerRunStore store = TowerRunStore.get(server);
        if (!forceCheckpoint) {
            store.put(run);
            return;
        }
        try {
            store.checkpoint(server, run);
        } catch (RuntimeException ex) {
            // checkpoint() stores before it writes, so the run is held in memory either way.
            TowerLog.error("Checkpoint for tower run {} could not be written to disk; it will be saved"
                    + " at the next world save instead", run.runId(), ex);
        }
    }

    /** Drops a run from the index and from disk. */
    public static void forget(MinecraftServer server, UUID runId) {
        PersistedRun run = BY_ID.remove(runId);
        if (run != null) {
            for (PersistedParticipant participant : run.participants()) {
                RUN_BY_PLAYER.remove(participant.playerId(), runId);
            }
        }
        TowerRunStore.get(server).remove(runId);
    }

    /** Memory hygiene at shutdown. Returns how many runs were dropped. */
    public static int onServerStopped() {
        int size = BY_ID.size();
        BY_ID.clear();
        RUN_BY_PLAYER.clear();
        return size;
    }

    private static void index(PersistedRun run) {
        BY_ID.put(run.runId(), run);
        for (PersistedParticipant participant : run.participants()) {
            if (run.isRetired() || !participant.state().isInRun()) {
                // A finished run, or someone who left it, must not keep a player from starting
                // another one. Removed by value so a player already in a newer run is left alone.
                RUN_BY_PLAYER.remove(participant.playerId(), run.runId());
            } else {
                RUN_BY_PLAYER.put(participant.playerId(), run.runId());
            }
        }
    }

    /** Test seam: the index without a server. */
    static void resetForTests(List<PersistedRun> runs) {
        BY_ID.clear();
        RUN_BY_PLAYER.clear();
        for (PersistedRun run : new ArrayList<>(runs)) index(run);
    }
}
