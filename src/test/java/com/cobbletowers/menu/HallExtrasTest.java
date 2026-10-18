package com.cobbletowers.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobbletowers.network.TowerHallStatePayload.Mon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class HallExtrasTest {
    private static final ResourceLocation TIDE = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge");

    @Test
    void gapToTheNextRankAndTheTopRank() {
        var mid = HallExtras.standing(TIDE, 5, 0, "Silver", 7);
        assertEquals(2, mid.toNext());
        assertEquals("Silver", mid.rank());
        var top = HallExtras.standing(TIDE, 20, 3, "Gold", -1);
        assertEquals(-1, top.toNext());
        assertEquals(3, top.ascension());
    }

    @Test
    void noNegativeNumbers() {
        var s = HallExtras.standing(TIDE, -2, -1, "Unranked", 3);
        assertEquals(0, s.level());
        assertEquals(0, s.ascension());
        assertEquals(3, s.toNext());
    }

    @Test
    void partyIsCappedAtSixInOrderAndNullRunIsEmpty() {
        List<Mon> party = new ArrayList<>();
        for (int i = 0; i < 9; i++) party.add(new Mon("M" + i, "m" + i, 50, false, List.of("fire")));
        var extras = HallExtras.of(null, party, List.of());
        assertEquals(6, extras.party().size());
        assertEquals("M0", extras.party().get(0).name());
        assertEquals("M5", extras.party().get(5).name());
        assertEquals("", extras.runTower());
    }
}
