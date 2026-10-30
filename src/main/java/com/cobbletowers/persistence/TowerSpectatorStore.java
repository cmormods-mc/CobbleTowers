package com.cobbletowers.persistence;

import com.cobbletowers.TowerLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/** Written before changing game mode, so a restart cannot strand a Tower spectator in that mode. */
public final class TowerSpectatorStore extends TowerStore {
    private final Map<UUID, SpectatorRestore> entries = new LinkedHashMap<>();
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public static TowerSpectatorStore get(MinecraftServer server) {
        return open(server, TowerSpectatorStore::new, TowerSpectatorStore::load, "cobbletowers_spectator_restore");
    }

    public Optional<SpectatorRestore> entryFor(UUID player) {
        if (unreadable.stream().anyMatch(raw -> raw.hasUUID("player") && player.equals(raw.getUUID("player")))) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(player));
    }

    public boolean hasPending(UUID player) {
        return entries.containsKey(player) || unreadable.stream()
                .anyMatch(raw -> raw.hasUUID("player") && player.equals(raw.getUUID("player")));
    }

    public void put(UUID player, SpectatorRestore entry) {
        if (hasPending(player)) throw new IllegalStateException("An original spectator mode is still pending for " + player);
        entries.put(player, entry);
        setDirty();
    }

    public void remove(UUID player) { if (entries.remove(player) != null) setDirty(); }

    static TowerSpectatorStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerSpectatorStore store = new TowerSpectatorStore();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raw = list.getCompound(i);
            try {
                UUID player = raw.getUUID("player");
                if (store.entries.containsKey(player)) throw new IllegalArgumentException("Duplicate spectator restore");
                store.entries.put(player, SpectatorRestore.fromTag(raw.getCompound("restore")));
            } catch (RuntimeException ex) {
                store.unreadable.add(raw.copy());
                TowerLog.error("Kept an unreadable spectator restore record; original mode needs repair: {}", raw, ex);
            }
        }
        return store;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        entries.forEach((player, restore) -> {
            CompoundTag raw = new CompoundTag();
            raw.putUUID("player", player);
            raw.put("restore", restore.toTag());
            list.add(raw);
        });
        for (CompoundTag raw : unreadable) list.add(raw.copy());
        tag.put("entries", list);
        return tag;
    }
}
