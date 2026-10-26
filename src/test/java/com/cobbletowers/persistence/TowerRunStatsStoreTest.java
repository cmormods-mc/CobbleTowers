package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerRunStatsStoreTest {

    @Test
    @DisplayName("stats of runs that no longer exist are dropped, the rest kept")
    void retainOnly() {
        TowerRunStatsStore store = new TowerRunStatsStore();
        UUID kept = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        store.of(kept, 2);
        store.of(gone, 1);

        assertEquals(1, store.retainOnly(Set.of(kept)));

        assertNotNull(store.peek(kept));
        assertNull(store.peek(gone));
        assertEquals(0, store.retainOnly(Set.of(kept)));
    }
}
