package com.cobbletowers.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/** A player's CobbleDollar balance (TDS #18): keyed by player, outliving any run. */
public final class TowerWalletStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_wallets";
    private static final String WALLETS = "wallets";
    private static final String PLAYER_ID = "player";
    private static final String BALANCE = "balance";

    private final Map<UUID, Long> balances = new LinkedHashMap<>();

    /** The store for this server. Created empty on a world that has never granted a CobbleDollar. */
    public static TowerWalletStore get(MinecraftServer server) {
        return open(server, TowerWalletStore::new, TowerWalletStore::load, FILE_ID);
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
     * Takes {@code amount} from a balance if covered, never leaving it negative.
     * @return true if debited; false, unchanged, if it did not cover
     */
    public boolean debit(UUID playerId, long amount) {
        if (amount <= 0) return true;
        long balance = balanceOf(playerId);
        if (balance < amount) return false;
        balances.put(playerId, balance - amount);
        setDirty();
        return true;
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
