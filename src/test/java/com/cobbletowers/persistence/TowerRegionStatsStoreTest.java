package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The region standings store: totals, records and champions per scope. */
class TowerRegionStatsStoreTest {

    @Test
    @DisplayName("region totals, records and champions survive a save and load")
    void store() {
        TowerRegionStatsStore store = new TowerRegionStatsStore();
        UUID mira = new UUID(1, 1), tarn = new UUID(2, 2);
        store.recordClear("all", "tideforge", mira, "Mira", false, 0, 700_000);
        store.recordClear("all", "tideforge", mira, "Mira", true, 2, 650_000);
        store.recordClear("all", "tideforge", tarn, "Tarn", true, 1, 0);
        store.recordTrial("all", "tideforge");
        store.remember(mira, "tideforge");

        TowerRegionStatsStore back = TowerRegionStatsStore.load(store.save(new CompoundTag(), null), null);
        TowerRegionStatsStore.Tally tally = back.tally("all", "tideforge");
        assertEquals(3, tally.clears());
        assertEquals(2, tally.away());
        assertEquals(1, tally.trials());
        assertEquals(2, tally.bestAscension());
        assertEquals(700_000, tally.fastestMs(), "only a base cycle sets the fastest time");
        assertEquals(2, tally.players());
        assertEquals("Mira", back.champions("all", "tideforge", 3).get(0).name());
        assertEquals(Optional.of("tideforge"), back.homeOf(mira));
        assertEquals(TowerRegionStatsStore.Tally.NONE, back.tally("all", "rootvale"));
    }
}
