package com.cobbletowers.intermission;

import com.cobbletowers.network.IntermissionStatePayload.Extras;
import com.cobbletowers.network.TowerHallStatePayload.Mon;
import java.util.List;

/** What the Frontier Desk intermission shows around the draft. Pure: the service reads the world, this shapes it. */
public final class IntermissionExtras {

    private IntermissionExtras() {}

    /**
     * @param runFloor the run floor just cleared (later cycles of an ascending tower keep counting up)
     * @param cycleLength floors in one cycle, or 0 when unknown
     * @param milestones the cycle's milestone floors
     */
    public static Extras of(String tower, int runFloor, int cycleLength, List<Integer> milestones, List<Mon> party) {
        int cycleFloor = cycleLength > 0 && runFloor > 0 ? (runFloor - 1) % cycleLength + 1 : 0;
        return new Extras(tower == null ? "" : tower, cycleFloor, Math.max(0, cycleLength), List.copyOf(milestones),
                party.size() > 6 ? List.copyOf(party.subList(0, 6)) : List.copyOf(party));
    }
}
