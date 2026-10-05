package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerLibSettlementStoreTest {

    private static PendingLibSettlement settlement(UUID encounter, PendingLibSettlement.Kind kind, UUID... players) {
        return new PendingLibSettlement(encounter, kind, "VICTORY", 1, 5, false, List.of(players), 0L, 0);
    }

    @Test
    @DisplayName("queued settlements survive a save and load")
    void roundTrip() {
        var store = new TowerLibSettlementStore();
        store.put(settlement(UUID.randomUUID(), PendingLibSettlement.Kind.MILESTONE, new UUID(1, 1), new UUID(2, 2)));
        store.put(settlement(UUID.randomUUID(), PendingLibSettlement.Kind.SCOUTER_DROPS, new UUID(3, 3)));

        var restored = TowerLibSettlementStore.load(store.save(new CompoundTag(), null), null);

        assertEquals(store.all(), restored.all());
    }

    @Test
    @DisplayName("one encounter has one entry per kind, and a put replaces it")
    void keyedByEncounterAndKind() {
        var store = new TowerLibSettlementStore();
        var encounter = UUID.randomUUID();
        store.put(settlement(encounter, PendingLibSettlement.Kind.MILESTONE, new UUID(1, 1), new UUID(2, 2)));
        store.put(settlement(encounter, PendingLibSettlement.Kind.SCOUTER_DROPS, new UUID(1, 1)));
        store.put(settlement(encounter, PendingLibSettlement.Kind.MILESTONE, new UUID(2, 2)));

        assertEquals(2, store.size());
        store.remove(settlement(encounter, PendingLibSettlement.Kind.MILESTONE, new UUID(2, 2)));
        assertEquals(1, store.size());
    }

    @Test
    @DisplayName("one unreadable entry cannot take the others with it")
    void badEntryIsDropped() {
        var store = new TowerLibSettlementStore();
        store.put(settlement(UUID.randomUUID(), PendingLibSettlement.Kind.MILESTONE, new UUID(1, 1)));
        CompoundTag tag = store.save(new CompoundTag(), null);
        ((ListTag) tag.get("entries")).add(new CompoundTag());

        assertEquals(1, TowerLibSettlementStore.load(tag, null).size());
    }
}
