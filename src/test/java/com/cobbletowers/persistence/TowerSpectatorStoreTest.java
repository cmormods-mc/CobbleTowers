package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

class TowerSpectatorStoreTest {
    private final UUID player = UUID.randomUUID();

    private SpectatorRestore original() {
        return new SpectatorRestore(GameType.ADVENTURE.getName(), false, true, true, false, false, .13f, .27f);
    }

    @Test void modeAndEveryAbilityRoundTripAcrossSaveAndLoad() {
        for (GameType mode : GameType.values()) for (int flags = 0; flags < 32; flags++) {
            var original = new SpectatorRestore(mode.getName(), (flags & 1) != 0, (flags & 2) != 0,
                    (flags & 4) != 0, (flags & 8) != 0, (flags & 16) != 0, .13f, .27f);
            TowerSpectatorStore store = new TowerSpectatorStore();
            store.put(player, original);
            var loaded = TowerSpectatorStore.load(store.save(new CompoundTag(), null), null);
            assertEquals(original, loaded.entryFor(player).orElseThrow());
        }
    }

    @Test void aSecondFollowCannotOverwriteTheOriginalMode() {
        var store = new TowerSpectatorStore();
        store.put(player, original());
        assertThrows(IllegalStateException.class, () -> store.put(player,
                new SpectatorRestore(GameType.SPECTATOR.getName(), true, true, true, false, false, .05f, .1f)));
        assertEquals(original(), store.entryFor(player).orElseThrow());
    }

    @Test void restorationRetiresThePendingEntry() {
        var store = new TowerSpectatorStore();
        store.put(player, original());
        assertTrue(store.hasPending(player));
        store.remove(player);
        var loaded = TowerSpectatorStore.load(store.save(new CompoundTag(), null), null);
        assertFalse(loaded.hasPending(player));
    }

    @Test void malformedOriginalIsPreservedAndCannotBeOverwrittenOrApplied() {
        CompoundTag raw = new CompoundTag();
        raw.putUUID("player", player);
        CompoundTag invalid = original().toTag();
        invalid.remove("mayfly");
        raw.put("restore", invalid);
        ListTag entries = new ListTag(); entries.add(raw);
        CompoundTag file = new CompoundTag(); file.put("entries", entries);
        var loaded = TowerSpectatorStore.load(file, null);
        assertTrue(loaded.hasPending(player));
        assertTrue(loaded.entryFor(player).isEmpty());
        assertThrows(IllegalStateException.class, () -> loaded.put(player, original()));
        assertEquals(raw, loaded.save(new CompoundTag(), null).getList("entries", Tag.TAG_COMPOUND).getCompound(0));
    }

    @Test void duplicateOriginalsAreAmbiguousAndMustNotBeApplied() {
        var store = new TowerSpectatorStore(); store.put(player, original());
        CompoundTag file = store.save(new CompoundTag(), null);
        file.getList("entries", Tag.TAG_COMPOUND).add(file.getList("entries", Tag.TAG_COMPOUND).getCompound(0).copy());
        var loaded = TowerSpectatorStore.load(file, null);
        assertTrue(loaded.hasPending(player));
        assertTrue(loaded.entryFor(player).isEmpty());
        assertEquals(2, loaded.save(new CompoundTag(), null).getList("entries", Tag.TAG_COMPOUND).size());
    }

    @Test void invalidModeAndNonFiniteSpeedsCannotBeUsedForRestoration() {
        CompoundTag bad = original().toTag(); bad.putString("mode", "unknown-mode");
        assertThrows(IllegalArgumentException.class, () -> SpectatorRestore.fromTag(bad));
        assertThrows(IllegalArgumentException.class, () -> new SpectatorRestore(GameType.SURVIVAL.getName(),
                false, false, false, false, true, Float.NaN, .1f));
    }
}
