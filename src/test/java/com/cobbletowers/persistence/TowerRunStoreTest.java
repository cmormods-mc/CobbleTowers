package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.api.tower.RunState;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the store owes the rest of the mod: runs come back as they went in, one bad record cannot
 * take the others with it, and finished runs do not accumulate forever.
 *
 * <p>No server here -- {@code save} and {@code load} ignore the registry lookup, so the file format
 * is testable on its own.
 */
class TowerRunStoreTest {

    private static final UUID RUN_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID RUN_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    @DisplayName("runs survive a save and load")
    void roundTrip() {
        TowerRunStore store = new TowerRunStore();
        store.put(TestRuns.fresh(RUN_A));
        store.put(TestRuns.at(RUN_B, RunState.INTERMISSION, TestRuns.NOW));

        TowerRunStore restored = TowerRunStore.load(store.save(new CompoundTag(), null), null);

        assertEquals(2, restored.runs().size());
        assertEquals(RunState.INTERMISSION, restored.get(RUN_B).state());
        assertEquals(store.get(RUN_A), restored.get(RUN_A));
    }

    @Test
    @DisplayName("one unreadable run is dropped, and the rest still load")
    void oneBadRecordDoesNotTakeTheOthers() {
        TowerRunStore store = new TowerRunStore();
        store.put(TestRuns.fresh(RUN_A));
        CompoundTag tag = store.save(new CompoundTag(), null);

        // A record from a build that is not this one. Whatever the damage, the reason it must not
        // throw is the same: a dedicated server that cannot finish its data load refuses to start,
        // and one broken run must never be able to do that to a whole server.
        CompoundTag broken = TestRuns.fresh(RUN_B).toTag();
        broken.putString("tower", "not a valid id");
        tag.getList("runs", Tag.TAG_COMPOUND).add(broken);

        TowerRunStore restored = TowerRunStore.load(tag, null);

        assertEquals(1, restored.runs().size(), "the good run still loaded");
        assertTrue(restored.runs().containsKey(RUN_A));
        assertFalse(restored.runs().containsKey(RUN_B), "the unreadable run was dropped, not guessed at");
    }

    @Test
    @DisplayName("finished runs are retired once they are old, and live ones never are")
    void retention() {
        long now = TestRuns.NOW;
        TowerRunStore store = new TowerRunStore();
        store.put(TestRuns.at(RUN_A, RunState.COMPLETED, now - 30 * DAY));          // old and finished
        store.put(TestRuns.at(RUN_B, RunState.ENCOUNTER_ACTIVE, now - 365 * DAY));  // old and still live
        UUID recent = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
        store.put(TestRuns.at(recent, RunState.CASHED_OUT, now - DAY));             // finished, but recent

        assertEquals(1, store.retireOldRuns(now));

        assertFalse(store.runs().containsKey(RUN_A), "a finished run past the window is retired");
        assertTrue(store.runs().containsKey(RUN_B),
                "a run that never finished is kept however old: age is not evidence it is over");
        assertTrue(store.runs().containsKey(recent));
    }

    @Test
    @DisplayName("retirement is exactly at the window, not around it")
    void retentionBoundary() {
        long now = TestRuns.NOW;
        TowerRunStore store = new TowerRunStore();
        store.put(TestRuns.at(RUN_A, RunState.FAILED, now - TowerRunStore.TERMINAL_RETENTION_MILLIS));

        assertEquals(0, store.retireOldRuns(now), "a run exactly at the window is still inside it");
        assertEquals(1, store.retireOldRuns(now + 1));
    }

    @Test
    @DisplayName("an empty store writes an empty list rather than nothing")
    void emptyStore() {
        CompoundTag tag = new TowerRunStore().save(new CompoundTag(), null);

        assertTrue(tag.contains("runs", Tag.TAG_LIST));
        assertEquals(0, ((ListTag) tag.get("runs")).size());
        assertEquals(0, TowerRunStore.load(tag, null).runs().size());
    }
}
