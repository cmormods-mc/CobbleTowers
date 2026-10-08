package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunModifierFloorsTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    @Test
    @DisplayName("the floor a modifier or relic was taken on is kept in order and survives saving")
    void recordedAndSaved() {
        RunModifierState state = RunModifierState.EMPTY.accumulating(id("a"), 3).accumulating(id("b"), 5).withRelic(id("r1"), 4).withRelic(id("r2"), 9);
        assertEquals(3, state.modifierFloor(0));
        assertEquals(5, state.modifierFloor(1));
        assertEquals(4, state.relicFloor(0));
        RunModifierState back = RunModifierState.fromTag(state.toTag());
        assertEquals(state, back);
        assertEquals(9, back.relicFloor(1));
    }

    @Test
    @DisplayName("losing a relic drops its floor with it, so the rest stay lined up")
    void lostRelicKeepsAlignment() {
        RunModifierState state = RunModifierState.EMPTY.withRelic(id("r1"), 2).withRelic(id("r2"), 6).withRelic(id("r3"), 8).withoutRelic(id("r2"));
        assertEquals(2, state.relicFloor(0));
        assertEquals(8, state.relicFloor(1));
        assertEquals(2, state.relics().size());
    }

    @Test
    @DisplayName("a run saved before floors were recorded reads back with unknown floors, not an error")
    void olderRunsReadAsUnknown() {
        CompoundTag old = RunModifierState.EMPTY.accumulating(id("a")).withRelic(id("r")).toTag();
        old.remove("accumulated_floors");
        old.remove("relic_floors");
        RunModifierState back = RunModifierState.fromTag(old);
        assertEquals(0, back.modifierFloor(0));
        assertEquals(0, back.relicFloor(0));
        // Taking another one after an old, unrecorded one still lines up.
        RunModifierState more = back.accumulating(id("b"), 7);
        assertEquals(0, more.modifierFloor(0));
        assertEquals(7, more.modifierFloor(1));
    }
}
