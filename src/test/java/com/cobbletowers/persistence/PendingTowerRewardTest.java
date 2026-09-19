package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingTowerRewardTest {

    private static final UUID RUN = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final ResourceLocation ITEM = ResourceLocation.fromNamespaceAndPath("minecraft", "diamond");

    @Test
    @DisplayName("a pending reward survives a round trip")
    void roundTrip() {
        PendingTowerReward reward = new PendingTowerReward(RUN, 5, ITEM, 3, 1_726_000_000_000L);

        assertEquals(reward, PendingTowerReward.fromTag(reward.toTag()));
    }

    @Test
    @DisplayName("a reward is refused for a non-positive floor or amount")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new PendingTowerReward(RUN, 0, ITEM, 1, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PendingTowerReward(RUN, 1, ITEM, 0, 0L));
    }

    @Test
    @DisplayName("an invalid item id is refused rather than guessed at")
    void invalidItemId() {
        CompoundTag tag = new PendingTowerReward(RUN, 1, ITEM, 1, 0L).toTag();
        tag.putString("item", "not a valid id");

        assertThrows(IllegalArgumentException.class, () -> PendingTowerReward.fromTag(tag));
    }
}
