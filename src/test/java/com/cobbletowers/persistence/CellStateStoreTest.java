package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Quarantines have to survive a restart, or a dirty cell comes back into service. */
class CellStateStoreTest {

    private static CompoundTag fileWith(CellQuarantine... entries) {
        ListTag list = new ListTag();
        for (CellQuarantine entry : entries) list.add(entry.toTag());
        CompoundTag tag = new CompoundTag();
        tag.put("quarantined", list);
        return tag;
    }

    @Test
    @DisplayName("a quarantine survives a save and load, reason included")
    void roundTrip() {
        CompoundTag saved = fileWith(new CellQuarantine(4, "2 entity/entities still inside", 1_726_000_000_000L));

        CellStateStore restored = CellStateStore.load(saved, null);

        assertTrue(restored.isQuarantined(4));
        assertEquals("2 entity/entities still inside", restored.quarantined().get(4).reason(),
                "the reason is the whole message to whoever has to decide the cell is clean");
        assertEquals(1_726_000_000_000L, restored.quarantined().get(4).since());
    }

    @Test
    @DisplayName("an unreadable entry is dropped rather than failing the load")
    void unreadableEntryIsDropped() {
        CompoundTag saved = fileWith(new CellQuarantine(4, "kept", 1L));
        CompoundTag broken = new CompoundTag();
        broken.putInt("cell", 9);
        broken.putString("reason", "");  // a quarantine with no reason is not one
        saved.getList("quarantined", Tag.TAG_COMPOUND).add(broken);

        CellStateStore restored = CellStateStore.load(saved, null);

        assertTrue(restored.isQuarantined(4), "the readable quarantine still holds");
        assertFalse(restored.isQuarantined(9));
    }

    @Test
    @DisplayName("a quarantine without a reason cannot be created at all")
    void reasonIsRequired() {
        // Enforced at construction so the store can never hold one: a cell marked bad with no
        // explanation tells an operator nothing about whether it is safe to clear.
        assertThrows(IllegalArgumentException.class, () -> new CellQuarantine(1, "", 0L));
        assertThrows(IllegalArgumentException.class, () -> new CellQuarantine(1, "   ", 0L));
        assertThrows(IllegalArgumentException.class, () -> new CellQuarantine(-1, "negative cell", 0L));
    }

    @Test
    @DisplayName("an empty file loads as no quarantines rather than failing")
    void emptyFile() {
        assertEquals(0, CellStateStore.load(new CompoundTag(), null).quarantined().size());
    }
}
