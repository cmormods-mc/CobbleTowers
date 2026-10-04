package com.cobbletowers.persistence;

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
 * What a run has been doing, for mastery (P31): how many players it started with, and, for the cycle in progress, how long its
 * floors have taken and whether any player Pokemon has fainted. Kept beside the run rather than inside {@code PersistedRun},
 * so recording it changes no run schema; an entry is dropped when its run ends.
 *
 * <p>Persisted so a crash costs at most the floor in progress. Mutations are synchronous and cheap; the file is written with
 * the world's autosave.
 */
public final class TowerRunStatsStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_run_stats";

    /** One run's figures. */
    public static final class Stats {
        public int startSize;
        /** Active fighting time of the current cycle, in milliseconds. */
        public long activeMillis;
        /** When the floor now being fought began, or 0 when none is. */
        public long floorStartedAt;
        /** Player Pokemon that have fainted in the current cycle. */
        public int faints;

        Stats(int startSize) {
            this.startSize = startSize;
        }
    }

    private final Map<UUID, Stats> byRun = new LinkedHashMap<>();

    public static SavedData.Factory<TowerRunStatsStore> factory() {
        return new SavedData.Factory<>(TowerRunStatsStore::new, TowerRunStatsStore::load, DataFixTypes.LEVEL);
    }

    public static TowerRunStatsStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** The run's stats, created with {@code partySize} the first time they are asked for. */
    public Stats of(UUID run, int partySize) {
        return byRun.computeIfAbsent(run, id -> {
            setDirty();
            return new Stats(Math.max(1, partySize));
        });
    }

    public Stats peek(UUID run) {
        return byRun.get(run);
    }

    public void touch() {
        setDirty();
    }

    public void remove(UUID run) {
        if (byRun.remove(run) != null) setDirty();
    }

    public int size() {
        return byRun.size();
    }

    static TowerRunStatsStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerRunStatsStore store = new TowerRunStatsStore();
        ListTag list = tag.getList("runs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            Stats stats = new Stats(Math.max(1, item.getInt("start_size")));
            stats.activeMillis = item.getLong("active");
            stats.floorStartedAt = item.getLong("floor_started");
            stats.faints = item.getInt("faints");
            store.byRun.put(item.getUUID("run"), stats);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Stats> entry : byRun.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("run", entry.getKey());
            item.putInt("start_size", entry.getValue().startSize);
            item.putLong("active", entry.getValue().activeMillis);
            item.putLong("floor_started", entry.getValue().floorStartedAt);
            item.putInt("faints", entry.getValue().faints);
            list.add(item);
        }
        tag.put("runs", list);
        return tag;
    }
}
