package com.cobbletowers.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a floor declares about the building it is played in. */
class FloorLayoutTest {

    private static final String JSON = """
            {
              "index": 1,
              "encounter_pool": "cobbletowers:neutral_common",
              "layout": {
                "structure": "cobbletowers:arena_floor",
                "entry":        {"x": 25, "y": 2, "z": 6},
                "presentation": {"x": 25, "y": 2, "z": 25},
                "spectator":    {"x": 6,  "y": 2, "z": 25, "yaw": 90},
                "exit":         {"x": 25, "y": 2, "z": 44, "yaw": 180}
              }
            }
            """;

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    @Test
    @DisplayName("a floor reads its structure and all four anchors")
    void parses() {
        FloorDefinition floor = FloorDefinition.fromJson(id("floor_01"), json(JSON));
        FloorLayout layout = floor.layout().orElseThrow();

        assertEquals(id("arena_floor"), layout.structure());
        assertEquals(new FloorAnchor(25, 2, 6, 0f), layout.entry());
        assertEquals(90f, layout.spectator().yaw(), "a spectator is turned to face the fight");
        assertEquals(List.of("entry", "presentation", "spectator", "exit"), layout.anchorNames());
        assertEquals(4, layout.anchors().size());
    }

    @Test
    @DisplayName("a floor with no layout still loads, it just cannot be built")
    void layoutIsOptional() {
        // Content written before the arenas existed must not stop a datapack loading; it simply has
        // nothing to paste, which the lifecycle reports as a technical fault when someone tries.
        FloorDefinition floor = FloorDefinition.fromJson(id("floor_01"),
                json("{\"index\": 1, \"encounter_pool\": \"cobbletowers:neutral_common\"}"));

        assertTrue(floor.layout().isEmpty());
    }

    @Test
    @DisplayName("a layout missing an anchor is refused, not half-read")
    void missingAnchorIsRefused() {
        String broken = JSON.replace("\"spectator\":    {\"x\": 6,  \"y\": 2, \"z\": 25, \"yaw\": 90},", "");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> FloorDefinition.fromJson(id("floor_01"), json(broken)));
        assertTrue(thrown.getMessage().contains("spectator"), thrown.getMessage());
    }

    @Test
    @DisplayName("an anchor is relative, so the same floor works in whichever cell it is pasted into")
    void anchorsAreRelative() {
        // The point of storing them relative: a run is given whichever cell is free, and an anchor
        // in world coordinates would be right for exactly one of them.
        FloorAnchor anchor = new FloorAnchor(25, 2, 6, 0f);

        assertEquals(new BlockPos(1025, 66, 134), anchor.in(new BlockPos(1000, 64, 128)));
        assertEquals(new BlockPos(25, 2, 6), anchor.in(BlockPos.ZERO));
    }

    private static final String WITH_GATES = JSON.replace("\"exit\":         {\"x\": 25, \"y\": 2, \"z\": 44, \"yaw\": 180}",
            "\"exit\": {\"x\": 25, \"y\": 2, \"z\": 44, \"yaw\": 180},"
                    + "\"gate_a\": {\"min\": [20, 2, 30], \"max\": [26, 7, 31]},"
                    + "\"gate_b\": {\"min\": [40, 2, 9], \"max\": [41, 6, 13]}");

    @Test
    @DisplayName("a floor with no gates has none, and every existing layout keeps loading")
    void noGatesByDefault() {
        FloorLayout layout = FloorLayout.fromJson(json(JSON).getAsJsonObject("layout"));
        assertTrue(layout.gateA().isEmpty());
        assertTrue(layout.gateB().isEmpty());
    }

    @Test
    @DisplayName("gate boxes are read as inclusive corners and placed from the paste origin")
    void gatesAreBoxes() {
        FloorLayout layout = FloorLayout.fromJson(json(WITH_GATES).getAsJsonObject("layout"));
        FloorBox a = layout.gateA().orElseThrow();
        assertEquals(7 * 6 * 2, a.volume());
        assertEquals(new BlockPos(1020, 66, 530), a.minIn(new BlockPos(1000, 64, 500)));
        assertEquals(new BlockPos(1026, 71, 531), a.maxIn(new BlockPos(1000, 64, 500)));
        assertEquals(2 * 5 * 5, layout.gateB().orElseThrow().volume());
    }

    @Test
    @DisplayName("a box that is upside down or has the wrong shape is refused, naming the problem")
    void badBoxes() {
        assertThrows(IllegalArgumentException.class, () -> new FloorBox(5, 0, 0, 4, 1, 1));
        String bad = WITH_GATES.replace("[20, 2, 30]", "[20, 2]");
        assertThrows(IllegalArgumentException.class, () -> FloorLayout.fromJson(json(bad).getAsJsonObject("layout")));
        String missing = WITH_GATES.replace("\"min\": [20, 2, 30], ", "");
        assertThrows(IllegalArgumentException.class, () -> FloorLayout.fromJson(json(missing).getAsJsonObject("layout")));
    }

    private static String doorJson(int x) {
        return "{\"open\": {\"min\": [" + (x - 2) + ", 6, 21], \"max\": [" + (x + 2) + ", 11, 22]},"
                + " \"frame\": {\"min\": [" + (x - 3) + ", 12, 21], \"max\": [" + (x + 3) + ", 13, 22]},"
                + " \"pedestal\": {\"x\": " + x + ", \"y\": 6, \"z\": 27}}";
    }

    @Test
    @DisplayName("route doors are read in order, each with its bars, its frame and the pedestal in front of it")
    void doors() {
        String with = JSON.replace("\"exit\":         {\"x\": 25, \"y\": 2, \"z\": 44, \"yaw\": 180}",
                "\"exit\": {\"x\": 25, \"y\": 2, \"z\": 44, \"yaw\": 180}, \"doors\": [" + doorJson(36) + "," + doorJson(48) + "," + doorJson(60) + "]");
        FloorLayout layout = FloorLayout.fromJson(json(with).getAsJsonObject("layout"));
        assertEquals(3, layout.doors().size());
        assertEquals(36, layout.doors().get(0).pedestal().x());
        assertEquals(60, layout.doors().get(2).pedestal().x());
        assertEquals(5 * 6 * 2, layout.doors().get(1).open().volume());
        assertEquals(7 * 2 * 2, layout.doors().get(1).frame().volume());
        assertTrue(FloorLayout.fromJson(json(JSON).getAsJsonObject("layout")).doors().isEmpty(), "no doors by default");
    }

    @Test
    @DisplayName("more than three doors, or a door missing a part, is refused")
    void badDoors() {
        String four = JSON.replace("\"exit\":         {\"x\": 25, \"y\": 2, \"z\": 44, \"yaw\": 180}",
                "\"exit\": {\"x\": 25, \"y\": 2, \"z\": 44, \"yaw\": 180}, \"doors\": [" + doorJson(10) + "," + doorJson(20) + "," + doorJson(30) + "," + doorJson(40) + "]");
        assertThrows(IllegalArgumentException.class, () -> FloorLayout.fromJson(json(four).getAsJsonObject("layout")));
        String noFrame = JSON.replace("\"exit\":         {\"x\": 25, \"y\": 2, \"z\": 44, \"yaw\": 180}",
                "\"exit\": {\"x\": 25, \"y\": 2, \"z\": 44, \"yaw\": 180}, \"doors\": [{\"open\": {\"min\": [0,0,0], \"max\": [1,1,1]}, \"pedestal\": {\"x\": 1, \"y\": 1, \"z\": 1}}]");
        assertThrows(IllegalArgumentException.class, () -> FloorLayout.fromJson(json(noFrame).getAsJsonObject("layout")));
    }
}
