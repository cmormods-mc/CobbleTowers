package com.cobbletowers.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * Each player's contract progress (P32c) by slot ({@code daily:2026-10-05:0}) and which slot of a period they
 * rerolled. The slot's template is kept beside the progress so a changed template (reroll, edited pack) restarts it.
 */
public final class TowerContractStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_contracts";

    public record Progress(String templateId, int count, boolean done) {}

    private static final class Player {
        final Map<String, Progress> slots = new LinkedHashMap<>();
        /** Period key to the slot rerolled in it. */
        final Map<String, Integer> rerolled = new LinkedHashMap<>();
    }

    private final Map<UUID, Player> players = new LinkedHashMap<>();

    public static TowerContractStore get(MinecraftServer server) {
        return open(server, TowerContractStore::new, TowerContractStore::load, FILE_ID);
    }

    public Optional<Progress> progressOf(UUID player, String slotKey) {
        Player known = players.get(player);
        return known == null ? Optional.empty() : Optional.ofNullable(known.slots.get(slotKey));
    }

    public void set(UUID player, String slotKey, Progress progress) {
        players.computeIfAbsent(player, id -> new Player()).slots.put(slotKey, progress);
        setDirty();
    }

    /** The slot this player rerolled in the period, or -1 if they have not. One reroll per period. */
    public int rerolledSlot(UUID player, String periodKey) {
        Player known = players.get(player);
        return known == null ? -1 : known.rerolled.getOrDefault(periodKey, -1);
    }

    public void markReroll(UUID player, String periodKey, int slot) {
        players.computeIfAbsent(player, id -> new Player()).rerolled.put(periodKey, slot);
        setDirty();
    }

    /** Drops every entry whose period key the predicate calls stale. */
    public void prune(Predicate<String> stalePeriod) {
        boolean changed = false;
        for (Player player : players.values()) {
            changed |= player.slots.keySet().removeIf(slotKey -> stalePeriod.test(periodOf(slotKey)));
            changed |= player.rerolled.keySet().removeIf(stalePeriod);
        }
        if (changed) setDirty();
    }

    /** {@code daily:2026-10-05:0} names period {@code daily:2026-10-05}. */
    static String periodOf(String slotKey) {
        int last = slotKey.lastIndexOf(':');
        return last < 0 ? slotKey : slotKey.substring(0, last);
    }

    /** An operator's tool: forgets a player's contracts. */
    public void reset(UUID player) {
        if (players.remove(player) != null) setDirty();
    }

    static TowerContractStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerContractStore store = new TowerContractStore();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            Player player = new Player();
            ListTag slots = item.getList("slots", Tag.TAG_COMPOUND);
            for (int j = 0; j < slots.size(); j++) {
                CompoundTag one = slots.getCompound(j);
                player.slots.put(one.getString("key"), new Progress(one.getString("template"), one.getInt("count"), one.getBoolean("done")));
            }
            ListTag rerolled = item.getList("rerolled", Tag.TAG_COMPOUND);
            for (int j = 0; j < rerolled.size(); j++) {
                CompoundTag one = rerolled.getCompound(j);
                player.rerolled.put(one.getString("period"), one.getInt("slot"));
            }
            store.players.put(item.getUUID("player"), player);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Player> entry : players.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", entry.getKey());
            ListTag slots = new ListTag();
            for (Map.Entry<String, Progress> slot : entry.getValue().slots.entrySet()) {
                CompoundTag one = new CompoundTag();
                one.putString("key", slot.getKey());
                one.putString("template", slot.getValue().templateId());
                one.putInt("count", slot.getValue().count());
                one.putBoolean("done", slot.getValue().done());
                slots.add(one);
            }
            item.put("slots", slots);
            ListTag rerolled = new ListTag();
            for (Map.Entry<String, Integer> slot : entry.getValue().rerolled.entrySet()) {
                CompoundTag one = new CompoundTag();
                one.putString("period", slot.getKey());
                one.putInt("slot", slot.getValue());
                rerolled.add(one);
            }
            item.put("rerolled", rerolled);
            list.add(item);
        }
        tag.put("players", list);
        return tag;
    }
}
