package com.cobbletowers.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.mastery.MasteryBadge.Badge;
import com.cobbletowers.mastery.MasteryBadge.Standing;
import com.cobbletowers.season.Cosmetics;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The mastery badge rule (P47a). */
class MasteryBadgeTest {

    private static ResourceLocation tower(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    private static Map<ResourceLocation, Standing> standings(Object... pairs) {
        Map<ResourceLocation, Standing> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(tower((String) pairs[i]), (Standing) pairs[i + 1]);
        return map;
    }

    @Test
    @DisplayName("no levels anywhere gives no badge")
    void none() {
        assertTrue(MasteryBadge.of(Map.of()).isEmpty());
        assertTrue(MasteryBadge.of(standings("neutral", new Standing(0, 0), "rootvale", new Standing(0, 0))).isEmpty());
    }

    @Test
    @DisplayName("the highest level wins, whatever the order")
    void highest() {
        Map<ResourceLocation, Standing> a = standings("neutral", new Standing(4, 900), "rootvale", new Standing(12, 100), "duskvale", new Standing(7, 500));
        Map<ResourceLocation, Standing> b = standings("duskvale", new Standing(7, 500), "rootvale", new Standing(12, 100), "neutral", new Standing(4, 900));
        assertEquals(Optional.of(new Badge(tower("rootvale"), 12)), MasteryBadge.of(a));
        assertEquals(MasteryBadge.of(a), MasteryBadge.of(b));
    }

    @Test
    @DisplayName("a tie goes to the tower with the newest achievement, then to the lower id")
    void ties() {
        assertEquals(tower("tideforge"), MasteryBadge.of(standings("neutral", new Standing(9, 100), "tideforge", new Standing(9, 200))).orElseThrow().tower());
        assertEquals(tower("neutral"), MasteryBadge.of(standings("tideforge", new Standing(9, 100), "neutral", new Standing(9, 100))).orElseThrow().tower());
        assertEquals(tower("neutral"), MasteryBadge.of(standings("neutral", new Standing(9, 100), "tideforge", new Standing(9, 100))).orElseThrow().tower());
    }

    @Test
    @DisplayName("each shipped rank has a colour, an unknown rank is white")
    void colours() {
        for (String rank : new String[] {"Bronze", "Silver", "Gold", "Platinum", "Diamond", "Master", "Champion"}) {
            assertTrue(!MasteryBadge.colorOf(rank).equals("white"), rank);
        }
        assertEquals("white", MasteryBadge.colorOf("Apprentice"));
        assertEquals("gold", MasteryBadge.colorOf("GOLD"));
    }

    @Test
    @DisplayName("a segment may name a font; the two-argument form uses the default one")
    void segmentFont() {
        assertTrue(new Cosmetics.Segment("x", "gold").font().isEmpty());
        assertEquals(Optional.of("cobbletowers:tower_icons"), new Cosmetics.Segment("x", "gold", Optional.of("cobbletowers:tower_icons")).font());
        assertEquals(new Cosmetics.Segment("a", "red"), new Cosmetics.Segment("a", "red", null));
    }
}
