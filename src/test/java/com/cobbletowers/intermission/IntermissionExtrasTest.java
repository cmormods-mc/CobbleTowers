package com.cobbletowers.intermission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobbletowers.network.TowerHallStatePayload.Mon;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntermissionExtrasTest {
    @Test
    void floorWithinTheCycleCountsUpAgainInLaterCycles() {
        assertEquals(1, IntermissionExtras.of("T", 1, 10, List.of(5, 10), List.of()).cycleFloor());
        assertEquals(10, IntermissionExtras.of("T", 10, 10, List.of(5, 10), List.of()).cycleFloor());
        assertEquals(1, IntermissionExtras.of("T", 11, 10, List.of(5, 10), List.of()).cycleFloor());
        assertEquals(4, IntermissionExtras.of("T", 24, 10, List.of(5, 10), List.of()).cycleFloor());
    }

    @Test
    void unknownLengthAndNullNameAreSafe() {
        var e = IntermissionExtras.of(null, 3, 0, List.of(), List.of());
        assertEquals(0, e.cycleFloor());
        assertEquals("", e.tower());
        assertEquals(0, IntermissionExtras.of("T", 0, 10, List.of(), List.of()).cycleFloor());
    }

    @Test
    void partyIsCappedAtSix() {
        List<Mon> party = new ArrayList<>();
        for (int i = 0; i < 8; i++) party.add(new Mon("M" + i, "m", 50, false, 100, List.of()));
        assertEquals(6, IntermissionExtras.of("T", 2, 10, List.of(), party).party().size());
    }
}
