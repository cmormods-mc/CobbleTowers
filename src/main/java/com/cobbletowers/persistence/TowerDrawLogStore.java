package com.cobbletowers.persistence;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/** Which Pokemon each run has already put up, so a run does not meet the same one twice. Oldest runs are dropped past a cap. */
public final class TowerDrawLogStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_draw_log";
    private static final int MAX_RUNS = 200;

    /** Per run: "floor:ordinal" to the Pokemon's name. */
    private final Map<UUID, Map<String, String>> runs = new LinkedHashMap<>();

    public static TowerDrawLogStore get(MinecraftServer server) {
        return open(server, TowerDrawLogStore::new, TowerDrawLogStore::load, FILE_ID);
    }

    /** The names drawn before this slot: earlier floors, and earlier ordinals on the same floor. */
    public Set<String> seenBefore(UUID run, int floor, int ordinal) {
        Set<String> out = new HashSet<>();
        Map<String, String> log = runs.get(run);
        if (log == null) return out;
        for (Map.Entry<String, String> entry : log.entrySet()) {
            int split = entry.getKey().indexOf(':');
            int f = Integer.parseInt(entry.getKey().substring(0, split));
            int o = Integer.parseInt(entry.getKey().substring(split + 1));
            if (f < floor || (f == floor && o < ordinal)) out.add(entry.getValue());
        }
        return out;
    }

    public void record(UUID run, int floor, int ordinal, String name) {
        Map<String, String> log = runs.computeIfAbsent(run, id -> new LinkedHashMap<>());
        String previous = log.put(floor + ":" + ordinal, name);
        if (name.equals(previous)) return;
        while (runs.size() > MAX_RUNS) {
            Iterator<UUID> oldest = runs.keySet().iterator();
            if (oldest.next().equals(run)) break;
            oldest.remove();
        }
        setDirty();
    }

    static TowerDrawLogStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerDrawLogStore store = new TowerDrawLogStore();
        ListTag list = tag.getList("runs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            Map<String, String> log = new LinkedHashMap<>();
            CompoundTag drawn = item.getCompound("d");
            for (String key : drawn.getAllKeys()) log.put(key, drawn.getString(key));
            store.runs.put(item.getUUID("r"), log);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<String, String>> run : runs.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("r", run.getKey());
            CompoundTag drawn = new CompoundTag();
            run.getValue().forEach(drawn::putString);
            item.put("d", drawn);
            list.add(item);
        }
        tag.put("runs", list);
        return tag;
    }
}
