package com.cobbletowers.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RulesetDefinition;
import com.google.gson.JsonArray;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionFxTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "test");

    private static RulesetDefinition rules(boolean heldItems, int drainStart, int drainEnd, List<String> statuses) {
        return new RulesetDefinition(ID, 1, 1, 5, 100, 6, true, 0, true, true, heldItems, drainStart, drainEnd, statuses);
    }

    @Test
    @DisplayName("a ruleset with no region rules asks for nothing")
    void plainAsksForNothing() {
        assertEquals(0, RegionFx.build(rules(true, 0, 0, List.of()), 3, 10, 7L, 3, true).size());
    }

    @Test
    @DisplayName("no held items puts one suppress operation on the player's own side")
    void noHeldItems() {
        JsonArray ops = RegionFx.build(rules(false, 0, 0, List.of()), 1, 10, 7L, 1, false);
        assertEquals(1, ops.size());
        assertEquals("suppress_items", ops.get(0).getAsJsonObject().get("op").getAsString());
        assertEquals("self", ops.get(0).getAsJsonObject().get("side").getAsString());
    }

    @Test
    @DisplayName("drain rises in a line from the start to the end percent over the tower's floors")
    void drainCurve() {
        RulesetDefinition rootvale = rules(true, 10, 20, List.of());
        assertEquals(10, rootvale.enemyDrainPercent(1, 10));
        assertEquals(20, rootvale.enemyDrainPercent(10, 10));
        assertTrue(rootvale.enemyDrainPercent(5, 10) < rootvale.enemyDrainPercent(6, 10));
        assertEquals(20, rootvale.enemyDrainPercent(1, 1), "a one-floor tower ends at the end percent");
        assertEquals(0, rules(true, 0, 0, List.of()).enemyDrainPercent(5, 10));
    }

    @Test
    @DisplayName("the drain is added by the enemy-side caller only, so a boss battle gets it once")
    void drainOnce() {
        RulesetDefinition rootvale = rules(true, 10, 20, List.of());
        assertEquals(1, RegionFx.build(rootvale, 1, 10, 7L, 1, true).size());
        assertEquals(0, RegionFx.build(rootvale, 1, 10, 7L, 1, false).size());
    }

    @Test
    @DisplayName("the floor's status is the same for the same seed and floor, and always one the ruleset lists")
    void statusDeterministic() {
        List<String> list = List.of("psn", "brn", "par", "slp", "frz");
        for (int floor = 1; floor <= 30; floor++) {
            String first = RegionFx.statusFor(list, 12345L, floor);
            assertEquals(first, RegionFx.statusFor(list, 12345L, floor));
            assertTrue(list.contains(first));
        }
        long distinct = java.util.stream.IntStream.rangeClosed(1, 30).mapToObj(floor -> RegionFx.statusFor(list, 12345L, floor)).distinct().count();
        assertTrue(distinct > 1, "different floors do not all roll the same status");
    }

    @Test
    @DisplayName("the status goes on every fight of the floor, for the player's side only, and is the same one all floor")
    void statusEveryFight() {
        RulesetDefinition duskvale = rules(true, 0, 0, List.of("psn", "brn"));
        JsonArray first = RegionFx.build(duskvale, 1, 10, 7L, 1, false);
        JsonArray second = RegionFx.build(duskvale, 1, 10, 7L, 1, true);
        assertEquals("status", first.get(0).getAsJsonObject().get("op").getAsString());
        assertEquals("self", first.get(0).getAsJsonObject().get("side").getAsString());
        assertEquals(first.get(0), second.get(0), "the boss fight asks for the same status as the opponent fight");
    }

    @Test
    @DisplayName("a ruleset refuses an unknown status or a drain over the cap")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> rules(true, 0, 0, List.of("confusion")));
        assertThrows(IllegalArgumentException.class, () -> rules(true, 10, 51, List.of()));
    }
}
