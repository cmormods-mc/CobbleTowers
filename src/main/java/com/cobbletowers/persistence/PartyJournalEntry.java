package com.cobbletowers.persistence;

import com.cobbletowers.storage.PartyArrangement.Original;
import com.cobbletowers.storage.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Where a player's Pokemon were before a run moved them (P18): the run that moved them, and the original
 * slot of every Pokemon that moved.
 *
 * <p>Identifiers and numbers only -- never a live Pokemon (TDS section 10). It is written to disk
 * <em>before</em> any move, which is the whole of the feature's safety argument: a crash at any later point
 * leaves a record that describes where everything started.
 */
public record PartyJournalEntry(UUID player, UUID runId, List<Original> originals) {

    public PartyJournalEntry {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(runId, "runId");
        originals = List.copyOf(originals);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", player);
        tag.putUUID("run", runId);
        ListTag list = new ListTag();
        for (Original original : originals) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("pokemon", original.pokemon());
            entry.putString("kind", original.slot().kind().name().toLowerCase(Locale.ROOT));
            entry.putInt("index", original.slot().index());
            entry.putInt("sub", original.slot().sub());
            list.add(entry);
        }
        tag.put("originals", list);
        return tag;
    }

    /** Throws on anything unreadable; the store keeps the raw tag rather than dropping a journal. */
    public static PartyJournalEntry fromTag(CompoundTag tag) {
        List<Original> originals = new ArrayList<>();
        ListTag list = tag.getList("originals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Slot.Kind kind = Slot.Kind.valueOf(entry.getString("kind").toUpperCase(Locale.ROOT));
            originals.add(new Original(entry.getUUID("pokemon"), new Slot(kind, entry.getInt("index"), entry.getInt("sub"))));
        }
        return new PartyJournalEntry(tag.getUUID("player"), tag.getUUID("run"), originals);
    }
}
