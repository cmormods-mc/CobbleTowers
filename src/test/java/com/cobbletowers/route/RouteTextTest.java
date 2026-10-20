package com.cobbletowers.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobbletowers.persistence.RunModifierState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RouteTextTest {
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath("cobbletowers", "rootvale");

    @Test
    void stopsReadAsRoomNamesAndNameTheBuildingOfADetour() {
        RunModifierState state = RunModifierState.EMPTY.withRouteStep(1, 0, "BATTLE").withRouteStep(2, 1, "POOL").withRouteStep(3, 2, "")
                .withArena(2, ROOT).withRouteStep(4, 0, "FORGE");
        assertEquals(List.of("Battle", "Mystic Pool", "Stop", "Forge"),
                RouteText.stops(state, place -> "Rootvale").stream().map(s -> s.replace(" (Rootvale)", "")).toList());
        // floor 1's step is fought on floor 2, which was sent to Rootvale
        assertEquals("Battle (Rootvale)", RouteText.stops(state, place -> "Rootvale").get(0));
    }

    @Test
    void aLongRouteIsCutAndAnEmptyOneIsEmpty() {
        assertEquals("", RouteText.line(List.of()));
        assertEquals("Battle > Rest", RouteText.line(List.of("Battle", "Rest")));
        List<String> twelve = new ArrayList<>();
        for (int i = 0; i < 12; i++) twelve.add("Battle");
        String line = RouteText.line(twelve);
        assertEquals(RouteText.LINE_MAX - 1, line.chars().filter(c -> c == '>').count());
        assertEquals(true, line.endsWith("(+2 more)"));
    }
}
