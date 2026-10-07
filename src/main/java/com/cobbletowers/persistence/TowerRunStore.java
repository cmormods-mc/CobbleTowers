package com.cobbletowers.persistence;

import com.cobbletowers.TowerLog;
import com.cobbletowers.migration.RunMigrations;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Disk storage for tower runs. {@link #put} marks the data dirty for the next autosave; {@link #checkpoint} writes it
 * now (TDS #5C). An unreadable record is dropped with a warning so one bad run cannot stop a server starting.
 */
public final class TowerRunStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_runs";
    private static final String RUNS = "runs";

    /** How long a finished run is kept; the whole file is rewritten on every save, so old runs are retired. */
    public static final long TERMINAL_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final Map<UUID, PersistedRun> runs = new LinkedHashMap<>();

    /** The store for this server. Created empty on a world that has never had a run. */
    public static TowerRunStore get(MinecraftServer server) {
        return open(server, TowerRunStore::new, TowerRunStore::load, FILE_ID);
    }

    /** Every stored run, in insertion order. A copy: callers must not mutate the store's map. */
    public Map<UUID, PersistedRun> runs() {
        return Map.copyOf(runs);
    }

    public PersistedRun get(UUID runId) {
        return runs.get(runId);
    }

    /** Stores a run and marks the file dirty, to be written at the next autosave. */
    public void put(PersistedRun run) {
        runs.put(run.runId(), run);
        setDirty();
    }

    public void remove(UUID runId) {
        if (runs.remove(runId) != null) setDirty();
    }

    /** Stores a run and writes the file immediately. One small write, a handful of times per floor. */
    public void checkpoint(MinecraftServer server, PersistedRun run) {
        put(run);
        checkpoint(server);
    }

    /**
     * Drops finished runs older than {@link #TERMINAL_RETENTION_MILLIS} and returns how many. Takes the time so a
     * test can age a run.
     */
    public int retireOldRuns(long now) {
        int before = runs.size();
        runs.entrySet().removeIf(entry -> {
            PersistedRun run = entry.getValue();
            return run.isRetired() && now - run.updatedAt() > TERMINAL_RETENTION_MILLIS;
        });
        int removed = before - runs.size();
        if (removed > 0) setDirty();
        return removed;
    }

    /** Package-private rather than private so a test can round-trip the file without a server. */
    static TowerRunStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerRunStore store = new TowerRunStore();
        ListTag stored = tag.getList(RUNS, Tag.TAG_COMPOUND);
        int dropped = 0;
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag runTag = stored.getCompound(i);
            try {
                PersistedRun run = PersistedRun.fromTag(RunMigrations.toCurrent(runTag));
                store.runs.put(run.runId(), run);
            } catch (RuntimeException ex) {
                dropped++;
                // Named, because a run that vanishes without explanation is indistinguishable from
                // one that was never saved -- and someone was playing it.
                TowerLog.error("Dropping unreadable tower run {}: {}",
                        runTag.hasUUID("run") ? runTag.getUUID("run").toString() : "<no id>", ex.toString());
            }
        }
        if (dropped > 0) {
            TowerLog.error("{} stored tower run(s) could not be read and were discarded.", dropped);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag stored = new ListTag();
        for (PersistedRun run : runs.values()) stored.add(run.toTag());
        tag.put(RUNS, stored);
        return tag;
    }
}
