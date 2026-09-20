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
 * A player's CobbleDollar balance (TDS #18): "individual wallets," not a run's own state, so this is
 * keyed by player and outlives any one run -- the same reasoning that keeps a bank balance open after
 * one shopping trip ends. Copies {@link TowerPendingRewardStore}'s exact shape.
 */
public final class TowerWalletStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_wallets";
    private static final String WALLETS = "wallets";
    private static final String PLAYER_ID = "player";
    private static final String BALANCE = "balance";

    private final Map<UUID, Long> balances = new LinkedHashMap<>();

    public static SavedData.Factory<TowerWalletStore> factory() {
        return new SavedData.Factory<>(TowerWalletStore::new, TowerWalletStore::load, DataFixTypes.LEVEL);
    }

    /** The store for this server. Created empty on a world that has never granted a CobbleDollar. */
    public static TowerWalletStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public long balanceOf(UUID playerId) {
        return balances.getOrDefault(playerId, 0L);
    }

    /** Adds to a player's balance and marks the file dirty, to be written at the next autosave. */
    public void credit(UUID playerId, long amount) {
        if (amount <= 0) return;
        balances.merge(playerId, amount, Long::sum);
        setDirty();
    }

    /**
     * Takes {@code amount} from a player's balance if they have it. Never leaves a balance negative --
     * a purchase either happens in full or not at all, the same all-or-nothing shape a vendor sale is.
     *
     * @return true if the balance covered it and was debited; false if it did not, unchanged
     */
    public boolean debit(UUID playerId, long amount) {
        if (amount <= 0) return true;
        long balance = balanceOf(playerId);
        if (balance < amount) return false;
        balances.put(playerId, balance - amount);
        setDirty();
        return true;
    }

    /** Writes the file to disk immediately: a debited purchase lost to a crash before the next
     * autosave is CobbleDollars a player was already charged and never got the service for. */
    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    static TowerWalletStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerWalletStore store = new TowerWalletStore();
        ListTag wallets = tag.getList(WALLETS, Tag.TAG_COMPOUND);
        for (int i = 0; i < wallets.size(); i++) {
            CompoundTag walletTag = wallets.getCompound(i);
            store.balances.put(walletTag.getUUID(PLAYER_ID), walletTag.getLong(BALANCE));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag wallets = new ListTag();
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            CompoundTag walletTag = new CompoundTag();
            walletTag.putUUID(PLAYER_ID, entry.getKey());
            walletTag.putLong(BALANCE, entry.getValue());
            wallets.add(walletTag);
        }
        tag.put(WALLETS, wallets);
        return tag;
    }
}
