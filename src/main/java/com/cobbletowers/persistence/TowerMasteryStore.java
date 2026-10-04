package com.cobbletowers.persistence;

import java.util.Collections;
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
 * Each player's mastery in each tower (P31): how many cycles they have cleared, the deepest Ascension they have reached, and
 * which achievements they hold (with when). Their mastery level in a tower is the number of achievements held there.
 * Per player and per tower, never per run, so it outlives every run. Copies {@link TowerWalletStore}'s shape.
 */
public final class TowerMasteryStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_mastery";

    /** A player's standing in one tower. Immutable; the store hands out copies. */
    public record Progress(int cyclesCleared, int ascensionReached, Map<ResourceLocation, Long> unlocked) {
        public static final Progress EMPTY = new Progress(0, 0, Map.of());

        public Progress {
            unlocked = Collections.unmodifiableMap(new LinkedHashMap<>(unlocked));
        }

        /** One level per achievement held. */
        public int level() {
            return unlocked.size();
        }
    }

    private static final class Entry {
        int cycles;
        int depth;
        final Map<ResourceLocation, Long> unlocked = new LinkedHashMap<>();
    }

    private final Map<UUID, Map<ResourceLocation, Entry>> entries = new LinkedHashMap<>();

    public static SavedData.Factory<TowerMasteryStore> factory() {
        return new SavedData.Factory<>(TowerMasteryStore::new, TowerMasteryStore::load, DataFixTypes.LEVEL);
    }

    public static TowerMasteryStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public Progress progressOf(UUID player, ResourceLocation tower) {
        Entry entry = entries.getOrDefault(player, Map.of()).get(tower);
        return entry == null ? Progress.EMPTY : new Progress(entry.cycles, entry.depth, entry.unlocked);
    }

    /** One more cycle cleared; returns the new total. */
    public int addCycle(UUID player, ResourceLocation tower) {
        Entry entry = entryFor(player, tower);
        entry.cycles++;
        setDirty();
        return entry.cycles;
    }

    /** Raises the deepest Ascension reached, if {@code ascension} is deeper. */
    public void raiseDepth(UUID player, ResourceLocation tower, int ascension) {
        Entry entry = entryFor(player, tower);
        if (ascension <= entry.depth) return;
        entry.depth = ascension;
        setDirty();
    }

    /** Records an achievement; false if the player already holds it. */
    public boolean unlock(UUID player, ResourceLocation tower, ResourceLocation achievement, long at) {
        Entry entry = entryFor(player, tower);
        if (entry.unlocked.containsKey(achievement)) return false;
        entry.unlocked.put(achievement, at);
        setDirty();
        return true;
    }

    /** An operator's tool: forgets everything a player has in a tower. */
    public void reset(UUID player, ResourceLocation tower) {
        Map<ResourceLocation, Entry> byTower = entries.get(player);
        if (byTower != null && byTower.remove(tower) != null) setDirty();
    }

    /** Written at once: an unlock lost to a crash would be an achievement a player was already told they had. */
    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    private Entry entryFor(UUID player, ResourceLocation tower) {
        return entries.computeIfAbsent(player, id -> new LinkedHashMap<>()).computeIfAbsent(tower, id -> new Entry());
    }

    static TowerMasteryStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerMasteryStore store = new TowerMasteryStore();
        ListTag list = tag.getList("progress", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            ResourceLocation tower = ResourceLocation.tryParse(item.getString("tower"));
            if (tower == null) continue;
            Entry entry = store.entryFor(item.getUUID("player"), tower);
            entry.cycles = item.getInt("cycles");
            entry.depth = item.getInt("depth");
            ListTag unlocked = item.getList("unlocked", Tag.TAG_COMPOUND);
            for (int j = 0; j < unlocked.size(); j++) {
                CompoundTag one = unlocked.getCompound(j);
                ResourceLocation id = ResourceLocation.tryParse(one.getString("id"));
                if (id != null) entry.unlocked.put(id, one.getLong("at"));
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<ResourceLocation, Entry>> player : entries.entrySet()) {
            for (Map.Entry<ResourceLocation, Entry> byTower : player.getValue().entrySet()) {
                CompoundTag item = new CompoundTag();
                item.putUUID("player", player.getKey());
                item.putString("tower", byTower.getKey().toString());
                item.putInt("cycles", byTower.getValue().cycles);
                item.putInt("depth", byTower.getValue().depth);
                ListTag unlocked = new ListTag();
                for (Map.Entry<ResourceLocation, Long> one : byTower.getValue().unlocked.entrySet()) {
                    CompoundTag oneTag = new CompoundTag();
                    oneTag.putString("id", one.getKey().toString());
                    oneTag.putLong("at", one.getValue());
                    unlocked.add(oneTag);
                }
                item.put("unlocked", unlocked);
                list.add(item);
            }
        }
        tag.put("progress", list);
        return tag;
    }
}
