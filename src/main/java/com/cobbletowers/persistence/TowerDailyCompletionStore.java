package com.cobbletowers.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/** How many tower completions each player has banked today, for the daily loot budget. Only the current day is kept. */
public final class TowerDailyCompletionStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_daily_completions";

    private record Day(String key, int completions) {}

    private final Map<UUID, Day> players = new LinkedHashMap<>();

    public static TowerDailyCompletionStore get(MinecraftServer server) {
        return open(server, TowerDailyCompletionStore::new, TowerDailyCompletionStore::load, FILE_ID);
    }

    public int countOn(UUID player, String dayKey) {
        Day day = players.get(player);
        return day != null && day.key().equals(dayKey) ? day.completions() : 0;
    }

    public int record(UUID player, String dayKey) {
        int count = countOn(player, dayKey) + 1;
        players.put(player, new Day(dayKey, count));
        setDirty();
        return count;
    }

    /** An operator's tool, and what a test uses to start clean. */
    public void reset(UUID player) {
        if (players.remove(player) != null) setDirty();
    }

    static TowerDailyCompletionStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerDailyCompletionStore store = new TowerDailyCompletionStore();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            store.players.put(item.getUUID("player"), new Day(item.getString("day"), item.getInt("completions")));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Day> entry : players.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", entry.getKey());
            item.putString("day", entry.getValue().key());
            item.putInt("completions", entry.getValue().completions());
            list.add(item);
        }
        tag.put("players", list);
        return tag;
    }
}
