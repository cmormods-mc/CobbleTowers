package com.cobbletowers.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The deepest Ascension each player has reached in each tower (P30). Per player and per tower, not per run, so it outlives
 * any one run and is what gates a direct start at a higher Ascension. Mastery and leaderboards (TDS #89/#90) would read it
 * too. Copies {@link TowerWalletStore}'s shape.
 *
 * <p>Only ever goes up: {@link #record} keeps the larger of what is stored and what it is given.
 */
public final class TowerAscensionStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_ascension";
    private static final String RECORDS = "records";
    private static final String PLAYER_ID = "player";
    private static final String TOWER_ID = "tower";
    private static final String ASCENSION = "ascension";

    private final Map<UUID, Map<ResourceLocation, Integer>> records = new LinkedHashMap<>();

    public static SavedData.Factory<TowerAscensionStore> factory() {
        return new SavedData.Factory<>(TowerAscensionStore::new, TowerAscensionStore::load, DataFixTypes.LEVEL);
    }

    /** The store for this server. Created empty on a world where nobody has ascended. */
    public static TowerAscensionStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** The deepest Ascension this player has reached in this tower; 0 for nobody who has not ascended. */
    public int recordOf(UUID playerId, ResourceLocation towerId) {
        return records.getOrDefault(playerId, Map.of()).getOrDefault(towerId, 0);
    }

    /** Raises the player's record to {@code ascension} if that is deeper than it. Returns whether it was a new record. */
    public boolean record(UUID playerId, ResourceLocation towerId, int ascension) {
        if (ascension <= recordOf(playerId, towerId)) return false;
        records.computeIfAbsent(playerId, id -> new LinkedHashMap<>()).put(towerId, ascension);
        setDirty();
        return true;
    }

    /** Written at once: a record lost to a crash would lock a player out of a start they had earned. */
    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    static TowerAscensionStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerAscensionStore store = new TowerAscensionStore();
        ListTag list = tag.getList(RECORDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation tower = ResourceLocation.tryParse(entry.getString(TOWER_ID));
            if (tower == null) continue;
            store.records.computeIfAbsent(entry.getUUID(PLAYER_ID), id -> new LinkedHashMap<>())
                    .put(tower, entry.getInt(ASCENSION));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<ResourceLocation, Integer>> player : records.entrySet()) {
            for (Map.Entry<ResourceLocation, Integer> entry : player.getValue().entrySet()) {
                CompoundTag record = new CompoundTag();
                record.putUUID(PLAYER_ID, player.getKey());
                record.putString(TOWER_ID, entry.getKey().toString());
                record.putInt(ASCENSION, entry.getValue());
                list.add(record);
            }
        }
        tag.put(RECORDS, list);
        return tag;
    }
}
