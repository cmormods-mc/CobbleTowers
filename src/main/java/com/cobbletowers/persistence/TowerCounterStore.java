package com.cobbletowers.persistence;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * Small named tallies the balance questions need (docs/design/tuning-with-real-data.md): how often each contract is
 * completed, each modifier offered and taken, what the risk bonus paid, which track nodes were claimed. Telemetry, not
 * logical state: it is saved with the world's normal autosave and never checkpointed. Capped at {@link #MAX_KEYS}.
 */
public final class TowerCounterStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_counters";
    private static final String ENTRIES = "entries";

    /** A key past this many is not recorded, so a bad caller cannot grow the file without bound. */
    public static final int MAX_KEYS = 4000;

    private final Map<String, Long> counts = new TreeMap<>();

    public static TowerCounterStore get(MinecraftServer server) {
        return open(server, TowerCounterStore::new, TowerCounterStore::load, FILE_ID);
    }

    public void add(String key, long amount) {
        if (key.isEmpty() || amount == 0) return;
        if (!counts.containsKey(key) && counts.size() >= MAX_KEYS) return;
        counts.merge(key, amount, Long::sum);
        setDirty();
    }

    public long count(String key) {
        return counts.getOrDefault(key, 0L);
    }

    public Map<String, Long> all() {
        return new TreeMap<>(counts);
    }

    /** Test seam and the admin reset. */
    public void clear() {
        counts.clear();
        setDirty();
    }

    static TowerCounterStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerCounterStore store = new TowerCounterStore();
        ListTag list = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            store.counts.put(entry.getString("k"), entry.getLong("v"));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("k", entry.getKey());
            item.putLong("v", entry.getValue());
            list.add(item);
        }
        tag.put(ENTRIES, list);
        return tag;
    }
}
