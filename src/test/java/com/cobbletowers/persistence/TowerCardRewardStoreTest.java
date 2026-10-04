package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The per-day count of runs that earned a player real cards (P33b). */
class TowerCardRewardStoreTest {

    @Test
    @DisplayName("the daily card store counts runs for today only, and survives a save and load")
    void dailyStore() {
        TowerCardRewardStore store = new TowerCardRewardStore();
        UUID player = new UUID(9, 9);
        assertEquals(0, store.runsOn(player, "2026-10-05"));
        assertEquals(1, store.record(player, "2026-10-05"));
        assertEquals(2, store.record(player, "2026-10-05"));
        assertEquals(0, store.runsOn(player, "2026-10-06"), "a new day starts at nothing");
        assertEquals(1, store.record(player, "2026-10-06"));
        assertEquals(0, store.runsOn(player, "2026-10-05"), "and the old day is forgotten");

        TowerCardRewardStore restored = TowerCardRewardStore.load(store.save(new CompoundTag(), null), null);
        assertEquals(1, restored.runsOn(player, "2026-10-06"));
        restored.reset(player);
        assertEquals(0, restored.runsOn(player, "2026-10-06"));
    }
}
