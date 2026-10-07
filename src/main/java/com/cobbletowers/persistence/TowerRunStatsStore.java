package com.cobbletowers.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * What a run has been doing, for mastery (P31): players started with, and for the cycle in progress, floor durations
 * and whether a player Pokemon fainted. Beside the run, not in {@code PersistedRun}; dropped when the run ends.
 * Persisted so a crash costs at most the floor in progress.
 */
public final class TowerRunStatsStore extends TowerStore {

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
        /**
         * {@link #faints} when the floor now being fought began, so the floor's own faints are the difference (P32c).
         */
        public int faintsAtFloorStart;
        /** Floors in a row with no faint, and the most there has been in the run (the Run Report, P32d). */
        public int flawlessStreak;
        public int bestFlawlessStreak;

        Stats(int startSize) {
            this.startSize = startSize;
        }
    }

    private final Map<UUID, Stats> byRun = new LinkedHashMap<>();

    public static TowerRunStatsStore get(MinecraftServer server) {
        return open(server, TowerRunStatsStore::new, TowerRunStatsStore::load, FILE_ID);
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
            stats.faintsAtFloorStart = item.getInt("faints_at_floor_start");
            stats.flawlessStreak = item.getInt("flawless_streak");
            stats.bestFlawlessStreak = item.getInt("best_flawless_streak");
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
            item.putInt("faints_at_floor_start", entry.getValue().faintsAtFloorStart);
            item.putInt("flawless_streak", entry.getValue().flawlessStreak);
            item.putInt("best_flawless_streak", entry.getValue().bestFlawlessStreak);
            list.add(item);
        }
        tag.put("runs", list);
        return tag;
    }
}
