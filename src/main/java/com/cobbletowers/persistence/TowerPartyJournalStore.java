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

/**
 * Disk storage for the party journal (P18): one entry per player whose Pokemon a run moved and has not put back. An
 * unreadable entry is kept raw and written back unchanged, unlike the reward store, because a dropped journal means
 * Pokemon never put back; it is reported loudly.
 */
public final class TowerPartyJournalStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_party_journal";
    private static final String ENTRIES = "entries";

    private final Map<UUID, PartyJournalEntry> entries = new LinkedHashMap<>();
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public static TowerPartyJournalStore get(MinecraftServer server) {
        return open(server, TowerPartyJournalStore::new, TowerPartyJournalStore::load, FILE_ID);
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
