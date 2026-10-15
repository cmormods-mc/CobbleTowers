package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.mastery.MasteryBadge;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What the mastery store hands the badge (P47): standings per tower and the hide flag, both surviving a save. */
class TowerMasteryBadgeStoreTest {

    private static final UUID PLAYER = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    @Test
    @DisplayName("standings count the achievements held and the newest time, and skip towers with none")
    void standings() {
        TowerMasteryStore store = new TowerMasteryStore();
        store.unlock(PLAYER, id("neutral"), id("a"), 100L);
        store.unlock(PLAYER, id("neutral"), id("b"), 300L);
        store.unlock(PLAYER, id("neutral"), id("c"), 200L);
        store.unlock(PLAYER, id("rootvale"), id("a"), 50L);

        Map<ResourceLocation, MasteryBadge.Standing> standings = store.standingsOf(PLAYER, List.of(id("neutral"), id("rootvale"), id("duskvale")));
        assertEquals(new MasteryBadge.Standing(3, 300L), standings.get(id("neutral")));
        assertEquals(new MasteryBadge.Standing(1, 50L), standings.get(id("rootvale")));
        assertFalse(standings.containsKey(id("duskvale")));
        assertEquals(id("neutral"), MasteryBadge.of(standings).orElseThrow().tower());
        assertTrue(store.standingsOf(UUID.randomUUID(), List.of(id("neutral"))).isEmpty());
    }

    @Test
    @DisplayName("a pinned tower is remembered through a save and a load, and can be released")
    void pinned() {
        TowerMasteryStore store = new TowerMasteryStore();
        assertTrue(store.badgePickOf(PLAYER).isEmpty());
        assertTrue(store.setBadgePick(PLAYER, java.util.Optional.of(id("rootvale"))));
        assertFalse(store.setBadgePick(PLAYER, java.util.Optional.of(id("rootvale"))), "same pick, no change");
        assertTrue(store.setBadgePick(PLAYER, java.util.Optional.of(id("neutral"))));

        TowerMasteryStore restored = TowerMasteryStore.load(store.save(new CompoundTag(), null), null);
        assertEquals(java.util.Optional.of(id("neutral")), restored.badgePickOf(PLAYER));
        assertTrue(restored.setBadgePick(PLAYER, java.util.Optional.empty()));
        assertFalse(restored.setBadgePick(PLAYER, java.util.Optional.empty()));
        assertTrue(restored.badgePickOf(PLAYER).isEmpty());
    }

    @Test
    @DisplayName("hiding the badge is remembered through a save and a load, and can be undone")
    void hidden() {
        TowerMasteryStore store = new TowerMasteryStore();
        assertFalse(store.badgeHidden(PLAYER));
        assertTrue(store.setBadgeHidden(PLAYER, true));
        assertFalse(store.setBadgeHidden(PLAYER, true), "no change the second time");

        TowerMasteryStore restored = TowerMasteryStore.load(store.save(new CompoundTag(), null), null);
        assertTrue(restored.badgeHidden(PLAYER));
        assertTrue(restored.setBadgeHidden(PLAYER, false));
        assertFalse(restored.badgeHidden(PLAYER));
    }
}
