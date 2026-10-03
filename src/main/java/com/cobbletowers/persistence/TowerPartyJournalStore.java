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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Disk storage for the party journal (P18): one entry per player whose Pokemon a run has moved and not yet
 * put back. Copies {@link TowerPendingRewardStore}'s exact shape, with one deliberate difference.
 *
 * <p>An entry that cannot be read is <b>kept</b>, raw, and written back out unchanged, where the reward
 * store drops what it cannot read. A dropped reward is an inconvenience; a dropped journal is a player
 * whose Pokemon are never put back. The unreadable entry is reported loudly so someone can act on it.
 */
public final class TowerPartyJournalStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_party_journal";
    private static final String ENTRIES = "entries";

    private final Map<UUID, PartyJournalEntry> entries = new LinkedHashMap<>();
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public static SavedData.Factory<TowerPartyJournalStore> factory() {
        return new SavedData.Factory<>(TowerPartyJournalStore::new, TowerPartyJournalStore::load, DataFixTypes.LEVEL);
    }

    public static TowerPartyJournalStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public Optional<PartyJournalEntry> entryFor(UUID player) {
        return Optional.ofNullable(entries.get(player));
    }

    public List<PartyJournalEntry> all() {
        return List.copyOf(entries.values());
    }

    public int unreadableCount() {
        return unreadable.size();
    }

    /** Records a player's journal and marks the file dirty. Callers flush with {@link #checkpoint}. */
    public void put(PartyJournalEntry entry) {
        entries.put(entry.player(), entry);
        setDirty();
    }

    public void remove(UUID player) {
        if (entries.remove(player) != null) setDirty();
    }

    /**
     * Writes the file to disk immediately. The journal is written <em>before</em> any Pokemon moves, so this
     * is what makes "crash after the moves" recoverable -- a journal still waiting for the next autosave
     * would be no journal at all.
     */
    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    static TowerPartyJournalStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerPartyJournalStore store = new TowerPartyJournalStore();
        ListTag list = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raw = list.getCompound(i);
            try {
                PartyJournalEntry entry = PartyJournalEntry.fromTag(raw);
                store.entries.put(entry.player(), entry);
            } catch (RuntimeException ex) {
                store.unreadable.add(raw.copy());
                TowerLog.error("A party journal entry could not be read and was kept as it is; a player's"
                        + " Pokemon may need putting back by hand: {}", raw, ex);
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PartyJournalEntry entry : entries.values()) list.add(entry.toTag());
        for (CompoundTag raw : unreadable) list.add(raw.copy());
        tag.put(ENTRIES, list);
        return tag;
    }
}
