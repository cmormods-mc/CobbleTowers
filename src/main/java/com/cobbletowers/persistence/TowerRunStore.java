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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Disk storage for tower runs, attached to the overworld's data storage so one file covers the
 * server rather than one per dimension.
 *
 * <p>Two write paths, and the difference between them is TDS #5C:
 * <ul>
 *   <li>{@link #put} marks the data dirty and lets Minecraft write it at the next autosave;</li>
 *   <li>{@link #checkpoint} writes it to disk <b>now</b>.</li>
 * </ul>
 * A forced checkpoint is what makes a crash lose nothing after an important transition. CobbleRaids
 * proved the same mechanism live: with only a dirty flag, a hard kill lost a granted reward; with
 * the synchronous save, it survived.
 *
 * <p>A record that cannot be read is dropped with a warning, never thrown. A dedicated server that
 * fails its data load refuses to start, and one unreadable run must not be able to do that.
 */
public final class TowerRunStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_runs";
    private static final String RUNS = "runs";

    /**
     * How long a finished run is kept. The whole file is rewritten on every save, so runs that
     * nothing will ever read again are retired rather than carried forever -- CobbleRaids' player
     * record file grows without bound and is a known "deal with it if it ever hurts", which is
     * cheaper to simply not repeat.
     *
     * <p>A constant rather than config: this mod has no config system yet, and inventing one for a
     * single number is worse than one named constant with one caller.
     */
    public static final long TERMINAL_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final Map<UUID, PersistedRun> runs = new LinkedHashMap<>();

    public static SavedData.Factory<TowerRunStore> factory() {
        return new SavedData.Factory<>(TowerRunStore::new, TowerRunStore::load, DataFixTypes.LEVEL);
    }

    /** The store for this server. Created empty on a world that has never had a run. */
    public static TowerRunStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
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

    /**
     * Stores a run and writes the file to disk immediately.
     *
     * <p>{@link SavedData#save} returns early for a file that is not dirty and
     * {@code NbtIo.writeCompressed} streams gzip without an fsync, so this costs one small write --
     * a handful of times per floor, never per tick.
     */
    public void checkpoint(MinecraftServer server, PersistedRun run) {
        put(run);
        server.overworld().getDataStorage().save();
    }

    /**
     * Drops finished runs older than {@link #TERMINAL_RETENTION_MILLIS}. Returns how many went.
     *
     * <p>Takes the time rather than reading the clock, so a test can age a run without waiting a
     * week for it.
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
