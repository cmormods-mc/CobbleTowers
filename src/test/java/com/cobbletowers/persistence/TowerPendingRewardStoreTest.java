package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the store owes the rest of the mod, the same way {@link TowerRunStoreTest} does for runs: a
 * player's queue comes back as it went in, and one bad record cannot take the others with it.
 */
class TowerPendingRewardStoreTest {

    private static final UUID PLAYER_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID PLAYER_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");
    private static final ResourceLocation ITEM = ResourceLocation.fromNamespaceAndPath("minecraft", "diamond");

    @Test
    @DisplayName("queued rewards survive a save and load")
    void roundTrip() {
        TowerPendingRewardStore store = new TowerPendingRewardStore();
        store.add(PLAYER_A, new PendingTowerReward(UUID.randomUUID(), 5, ITEM, 3, 1L));
        store.add(PLAYER_A, new PendingTowerReward(UUID.randomUUID(), 10, ITEM, 2, 2L));
        store.add(PLAYER_B, new PendingTowerReward(UUID.randomUUID(), 5, ITEM, 1, 1L));

        TowerPendingRewardStore restored = TowerPendingRewardStore.load(store.save(new CompoundTag(), null), null);

        assertEquals(store.queueFor(PLAYER_A), restored.queueFor(PLAYER_A));
        assertEquals(2, restored.queueFor(PLAYER_A).size());
        assertEquals(1, restored.queueFor(PLAYER_B).size());
    }

    @Test
    @DisplayName("draining removes and returns a player's whole queue")
    void draining() {
        TowerPendingRewardStore store = new TowerPendingRewardStore();
        store.add(PLAYER_A, new PendingTowerReward(UUID.randomUUID(), 5, ITEM, 3, 1L));

        assertEquals(1, store.drain(PLAYER_A).size());
        assertEquals(0, store.queueFor(PLAYER_A).size(), "draining is exhaustive");
        assertEquals(0, store.drain(PLAYER_A).size(), "draining twice finds nothing left");
    }

    @Test
    @DisplayName("one unreadable reward is dropped, and the rest still load")
    void oneBadRecordDoesNotTakeTheOthers() {
        TowerPendingRewardStore store = new TowerPendingRewardStore();
        store.add(PLAYER_A, new PendingTowerReward(UUID.randomUUID(), 5, ITEM, 3, 1L));
        CompoundTag tag = store.save(new CompoundTag(), null);

        CompoundTag broken = new PendingTowerReward(UUID.randomUUID(), 1, ITEM, 1, 0L).toTag();
        broken.putString("item", "not a valid id");
        CompoundTag playerTag = tag.getList("players", Tag.TAG_COMPOUND).getCompound(0);
        playerTag.getList("queue", Tag.TAG_COMPOUND).add(broken);

        TowerPendingRewardStore restored = TowerPendingRewardStore.load(tag, null);

        assertEquals(1, restored.queueFor(PLAYER_A).size(), "the good entry still loaded");
    }

    @Test
    @DisplayName("an empty store writes an empty list rather than nothing")
    void emptyStore() {
        CompoundTag tag = new TowerPendingRewardStore().save(new CompoundTag(), null);

        assertTrue(tag.contains("players", Tag.TAG_LIST));
        assertEquals(0, ((ListTag) tag.get("players")).size());
        assertEquals(0, TowerPendingRewardStore.load(tag, null).queueFor(PLAYER_A).size());
    }
}
