package com.cobbletowers.persistence;

import com.cobbletowers.TowerLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * Disk storage for unconfirmed AscensionLib payouts (see {@link PendingLibSettlement}), per world like the library's
 * own store.
 */
public final class TowerLibSettlementStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_lib_settlements";
    private static final String ENTRIES = "entries";

    private final Map<String, PendingLibSettlement> pending = new LinkedHashMap<>();

    public static TowerLibSettlementStore get(MinecraftServer server) {
        return open(server, TowerLibSettlementStore::new, TowerLibSettlementStore::load, FILE_ID);
    }

    /** Adds the settlement, or replaces the one with the same key. */
    public void put(PendingLibSettlement settlement) {
        pending.put(settlement.key(), settlement);
        setDirty();
    }

    public void remove(PendingLibSettlement settlement) {
        if (pending.remove(settlement.key()) != null) setDirty();
    }

    /** A copy, so a caller can settle and edit the store while walking it. */
    public List<PendingLibSettlement> all() {
        return List.copyOf(pending.values());
    }

    public int size() {
        return pending.size();
    }

    static TowerLibSettlementStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerLibSettlementStore store = new TowerLibSettlementStore();
        ListTag entries = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        int dropped = 0;
        for (int i = 0; i < entries.size(); i++) {
            try {
                PendingLibSettlement settlement = PendingLibSettlement.fromTag(entries.getCompound(i));
                store.pending.put(settlement.key(), settlement);
            } catch (RuntimeException ex) {
                dropped++;
            }
        }
        if (dropped > 0) TowerLog.error("Dropped {} unreadable pending AscensionLib settlement(s).", dropped);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (PendingLibSettlement settlement : new ArrayList<>(pending.values())) entries.add(settlement.toTag());
        tag.put(ENTRIES, entries);
        return tag;
    }
}
