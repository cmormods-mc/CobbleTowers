package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.storage.PartyArrangement.Original;
import com.cobbletowers.storage.Slot;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The journal must come back exactly as written, and must never silently lose an entry. */
class TowerPartyJournalStoreTest {

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID RUN = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");

    private static PartyJournalEntry entry() {
        return new PartyJournalEntry(PLAYER, RUN, List.of(
                new Original(new UUID(0, 1), Slot.party(2)),
                new Original(new UUID(0, 2), Slot.pc(3, 17))));
    }

    @Test
    @DisplayName("a journal survives a save and load, party and box slots alike")
    void roundTrip() {
        TowerPartyJournalStore store = new TowerPartyJournalStore();
        store.put(entry());

        TowerPartyJournalStore restored = TowerPartyJournalStore.load(store.save(new CompoundTag(), null), null);

        assertEquals(entry(), restored.entryFor(PLAYER).orElseThrow());
    }

    @Test
    @DisplayName("the rentals a run was lent (P33) survive a save and load with the moved Pokemon")
    void rentalsRoundTrip() {
        PartyJournalEntry lent = new PartyJournalEntry(PLAYER, RUN, entry().originals(), List.of(new UUID(7, 1), new UUID(7, 2)));
        TowerPartyJournalStore store = new TowerPartyJournalStore();
        store.put(lent);

        PartyJournalEntry back = TowerPartyJournalStore.load(store.save(new CompoundTag(), null), null).entryFor(PLAYER).orElseThrow();

        assertEquals(lent, back);
        assertEquals(List.of(new UUID(7, 1), new UUID(7, 2)), back.rentals());
    }

    @Test
    @DisplayName("a journal written before rentals existed still loads, with none lent")
    void oldJournalHasNoRentals() {
        CompoundTag written = entry().toTag();
        written.remove("rentals");
        PartyJournalEntry back = PartyJournalEntry.fromTag(written);
        assertEquals(List.of(), back.rentals());
        assertEquals(entry(), back);
    }

    @Test
    @DisplayName("removing an entry removes it")
    void removing() {
        TowerPartyJournalStore store = new TowerPartyJournalStore();
        store.put(entry());
        store.remove(PLAYER);

        assertTrue(store.entryFor(PLAYER).isEmpty());
        assertEquals(List.of(), store.all());
    }

    @Test
    @DisplayName("an unreadable entry is kept and written back, not dropped")
    void unreadableIsKept() {
        CompoundTag file = new TowerPartyJournalStore().save(new CompoundTag(), null);
        ListTag list = new ListTag();
        CompoundTag bad = new CompoundTag();
        bad.putUUID("player", PLAYER);
        bad.putUUID("run", RUN);
        ListTag originals = new ListTag();
        CompoundTag slot = new CompoundTag();
        slot.putUUID("pokemon", new UUID(0, 9));
        slot.putString("kind", "attic");   // not a kind this version knows
        originals.add(slot);
        bad.put("originals", originals);
        list.add(bad);
        file.put("entries", list);

        TowerPartyJournalStore loaded = TowerPartyJournalStore.load(file, null);

        assertEquals(1, loaded.unreadableCount());
        assertTrue(loaded.entryFor(PLAYER).isEmpty(), "it is not offered as a journal to restore from");
        CompoundTag written = loaded.save(new CompoundTag(), null);
        assertEquals(1, written.getList("entries", Tag.TAG_COMPOUND).size(), "but it is still in the file");
    }

    @Test
    @DisplayName("one bad entry does not take a good one with it")
    void goodSurvivesBad() {
        TowerPartyJournalStore store = new TowerPartyJournalStore();
        store.put(entry());
        CompoundTag file = store.save(new CompoundTag(), null);
        ListTag list = file.getList("entries", Tag.TAG_COMPOUND);
        list.add(new CompoundTag());   // missing every field

        TowerPartyJournalStore loaded = TowerPartyJournalStore.load(file, null);

        assertEquals(entry(), loaded.entryFor(PLAYER).orElseThrow());
        assertEquals(1, loaded.unreadableCount());
    }
}
