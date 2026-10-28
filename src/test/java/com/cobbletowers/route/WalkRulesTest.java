package com.cobbletowers.route;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.FloorAnchor;
import com.cobbletowers.definition.FloorBox;
import com.cobbletowers.definition.FloorLayout;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WalkRulesTest {

    private static final FloorAnchor AT = new FloorAnchor(1, 2, 3, 0);
    private static final FloorBox BOX = new FloorBox(0, 0, 0, 1, 1, 1);

    private static FloorLayout layout(String structure, boolean stairGate) {
        return new FloorLayout(ResourceLocation.fromNamespaceAndPath("cobbletowers", structure), AT, AT, AT, AT,
                Optional.of(BOX), stairGate ? Optional.of(BOX) : Optional.empty());
    }

    @Test
    @DisplayName("a floor is left on foot only when it has a stair gate and the next floor is the same building")
    void onFoot() {
        assertTrue(WalkRules.onFoot(layout("hollow", true), Optional.of(layout("hollow", true))));
        assertFalse(WalkRules.onFoot(layout("hollow", false), Optional.of(layout("hollow", true))), "no stair gate: the last floor of a cycle");
        assertFalse(WalkRules.onFoot(layout("hollow", true), Optional.of(layout("duskvale", true))), "a detour to another building");
        assertFalse(WalkRules.onFoot(layout("hollow", true), Optional.empty()), "no next floor");
        assertFalse(WalkRules.onFoot(new FloorLayout(ResourceLocation.fromNamespaceAndPath("cobbletowers", "hollow"), AT, AT, AT, AT),
                Optional.of(layout("hollow", true))), "a tower with no gates is left by teleport as before");
    }

    @Test
    @DisplayName("arriving means within the radius across the floor and within three blocks of its height")
    void arrived() {
        assertTrue(WalkRules.arrived(24.5, 16, 48.5, 24.5, 16, 48.5));
        assertTrue(WalkRules.arrived(24.5 + 6.9, 16, 48.5, 24.5, 16, 48.5));
        assertFalse(WalkRules.arrived(24.5 + 7.1, 16, 48.5, 24.5, 16, 48.5));
        assertTrue(WalkRules.arrived(24.5, 19, 48.5, 24.5, 16, 48.5));
        assertFalse(WalkRules.arrived(24.5, 19.5, 48.5, 24.5, 16, 48.5), "the floor above is ten blocks up, never within reach");
        assertFalse(WalkRules.arrived(24.5, 6, 48.5, 24.5, 16, 48.5), "nor is the floor below");
    }

    @Test
    @DisplayName("a player on the spectator ledge is stranded; one in the arena or the vendor's room is not")
    void stranded() {
        FloorAnchor ledge = new FloorAnchor(48, 16, 84, 180);
        assertTrue(WalkRules.strandedAt(1000 + 48.5, 80, 500 + 84.5, ledge, 1000, 64, 500));
        assertTrue(WalkRules.strandedAt(1000 + 60, 81, 500 + 80, ledge, 1000, 64, 500));
        assertFalse(WalkRules.strandedAt(1000 + 48.5, 80, 500 + 48, ledge, 1000, 64, 500), "the arena is 36 blocks from the ledge");
        assertFalse(WalkRules.strandedAt(1000 + 48.5, 80, 500 + 5, ledge, 1000, 64, 500));
        assertFalse(WalkRules.strandedAt(1000 + 48.5, 90, 500 + 84.5, ledge, 1000, 64, 500), "the floor above the ledge");
    }
}
