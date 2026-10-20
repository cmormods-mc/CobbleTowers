package com.cobbletowers.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobbletowers.network.TowerHallStatePayload.Mon;
import com.cobbletowers.runtime.PartyValidation.PartyMember;
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
        for (int i = 0; i < 9; i++) party.add(new Mon("M" + i, "m" + i, 50, false, 100, List.of("fire")));
        var extras = HallExtras.of(null, party, List.of());
        assertEquals(6, extras.party().size());
        assertEquals("M0", extras.party().get(0).name());
        assertEquals("M5", extras.party().get(5).name());
        assertEquals("", extras.runTower());
    }

    @Test
    void modeProblemsJudgeOnlyWhatTheTowerWouldRegister() {
        var cap = new com.cobbletowers.definition.PlaylistDefinition.Clauses(false, 50, false, java.util.Set.of(), 0);
        var party = List.of(
                new PartyMember(java.util.UUID.randomUUID(), 50, false, "arcanine", List.of("fire"), true, java.util.Set.of()),
                new PartyMember(java.util.UUID.randomUUID(), 72, false, "gliscor", List.of("ground"), true, java.util.Set.of()));
        assertEquals(1, HallExtras.modeProblems(party, 6, cap).size());
        assertEquals(0, HallExtras.modeProblems(party, 1, cap).size());
        assertEquals(0, HallExtras.modeProblems(party, 6, com.cobbletowers.definition.PlaylistDefinition.Clauses.NONE).size());
    }

    @Test
    void withModeKeepsTheRestAndNeverNull() {
        var base = HallExtras.of("x", List.of(), List.of());
        var with = HallExtras.withMode(base, null, List.of("a"));
        assertEquals("", with.modeName());
        assertEquals("x", with.runTower());
        assertEquals(List.of("a"), with.modeProblems());
    }

    @Test
    void spriteNameKeepsLettersAndDigitsOnly() {
        assertEquals("nidoranf", HallExtras.spriteName("nidoran_f"));
        assertEquals("mrmime", HallExtras.spriteName("mr_mime"));
        assertEquals("hooh", HallExtras.spriteName("ho-oh"));
        assertEquals("porygonz", HallExtras.spriteName("Porygon-Z"));
    }

    @Test
    void hpPercentIsWholeAndLivingNeverReadsEmpty() {
        assertEquals(100, HallExtras.hpPercent(80, 80));
        assertEquals(50, HallExtras.hpPercent(40, 80));
        assertEquals(1, HallExtras.hpPercent(1, 500));
        assertEquals(0, HallExtras.hpPercent(0, 80));
        assertEquals(0, HallExtras.hpPercent(10, 0));
        assertEquals(100, HallExtras.hpPercent(90, 80));
    }
}
