package com.cobbletowers.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/** Tower keys held back from a death drop until the player is back; kept on disk so a restart in between loses none. */
public final class TowerKeyHoldStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_key_holds";

    private final Map<UUID, Integer> held = new HashMap<>();

    public static TowerKeyHoldStore get(MinecraftServer server) {
        return open(server, TowerKeyHoldStore::new, TowerKeyHoldStore::load, FILE_ID);
    }

    public void add(UUID player, int count) {
        held.merge(player, count, Integer::sum);
        setDirty();
    }

    /** Takes and forgets what is held for the player; 0 when nothing is. */
    public int take(UUID player) {
        Integer count = held.remove(player);
        if (count == null) return 0;
        setDirty();
        return count;
    }

    static TowerKeyHoldStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerKeyHoldStore store = new TowerKeyHoldStore();
        ListTag list = tag.getList("held", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            store.held.put(item.getUUID("p"), item.getInt("n"));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> entry : held.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("p", entry.getKey());
            item.putInt("n", entry.getValue());
            list.add(item);
        }
        tag.put("held", list);
        return tag;
    }
}
