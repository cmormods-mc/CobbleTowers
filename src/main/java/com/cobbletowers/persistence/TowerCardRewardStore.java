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
 * How many completed runs have earned each player real cards today (P33b), so a playlist can cap card rewards per day. Only the
 * current day is kept: a player whose last entry is from an earlier day has simply not earned any today.
 */
public final class TowerCardRewardStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_card_rewards";

    private record Day(String key, int runs) {}

    private final Map<UUID, Day> players = new LinkedHashMap<>();

    public static SavedData.Factory<TowerCardRewardStore> factory() {
        return new SavedData.Factory<>(TowerCardRewardStore::new, TowerCardRewardStore::load, DataFixTypes.LEVEL);
    }

    public static TowerCardRewardStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** How many completed runs have earned this player cards on the day with this key. */
    public int runsOn(UUID player, String dayKey) {
        Day day = players.get(player);
        return day != null && day.key().equals(dayKey) ? day.runs() : 0;
    }

    /** Records one more run that earned cards on that day. */
    public int record(UUID player, String dayKey) {
        int runs = runsOn(player, dayKey) + 1;
        players.put(player, new Day(dayKey, runs));
        setDirty();
        return runs;
    }

    /** An operator's tool, and what a test uses to start clean. */
    public void reset(UUID player) {
        if (players.remove(player) != null) setDirty();
    }

    /** Written at once: a card granted twice because a crash lost the count would be a real duplicate. */
    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    static TowerCardRewardStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerCardRewardStore store = new TowerCardRewardStore();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            store.players.put(item.getUUID("player"), new Day(item.getString("day"), item.getInt("runs")));
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
            item.putInt("runs", entry.getValue().runs());
            list.add(item);
        }
        tag.put("players", list);
        return tag;
    }
}
