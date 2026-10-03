package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Where a player goes home to must survive a save, and one bad entry must not take the others. */
class TowerReturnStoreTest {

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");
    private static final ReturnPoint HOME = new ReturnPoint("minecraft:overworld", 12.5, 64.0, -30.25, 90.0f, -10.0f);

    @Test
    @DisplayName("a return point survives a save and load exactly")
    void roundTrip() {
        TowerReturnStore store = new TowerReturnStore();
        store.put(PLAYER, HOME);

        TowerReturnStore restored = TowerReturnStore.load(store.save(new CompoundTag(), null), null);

        assertEquals(HOME, restored.pointFor(PLAYER).orElseThrow());
    }

    @Test
    @DisplayName("a later departure replaces the earlier one rather than adding a second")
    void replaced() {
        TowerReturnStore store = new TowerReturnStore();
        store.put(PLAYER, HOME);
        ReturnPoint elsewhere = new ReturnPoint("minecraft:the_nether", 1, 2, 3, 0, 0);
        store.put(PLAYER, elsewhere);

        assertEquals(elsewhere, store.pointFor(PLAYER).orElseThrow());
        assertEquals(1, store.size());
    }

    @Test
    @DisplayName("removing a point removes it")
    void removing() {
        TowerReturnStore store = new TowerReturnStore();
        store.put(PLAYER, HOME);
        store.remove(PLAYER);

        assertTrue(store.pointFor(PLAYER).isEmpty());
    }

    @Test
    @DisplayName("one unreadable entry does not take a good one with it")
    void goodSurvivesBad() {
        TowerReturnStore store = new TowerReturnStore();
        store.put(PLAYER, HOME);
        CompoundTag file = store.save(new CompoundTag(), null);
        ListTag list = file.getList("entries", Tag.TAG_COMPOUND);
        CompoundTag bad = new CompoundTag();
        bad.putUUID("player", OTHER);   // no point at all
        list.add(bad);

        TowerReturnStore loaded = TowerReturnStore.load(file, null);

        assertEquals(HOME, loaded.pointFor(PLAYER).orElseThrow());
        assertTrue(loaded.pointFor(OTHER).isEmpty());
    }
}
