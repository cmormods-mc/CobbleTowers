package com.cobbletowers.battle.cobblemon;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BattleDeparturePolicyTest {
    @Test void sharedOwnerHandlesDepartureWithoutEndingTeammatesBattle() {
        List<String> actions = new ArrayList<>();
        BattleDeparturePolicy.depart(() -> { actions.add("withdraw"); return true; }, () -> actions.add("end"));
        assertEquals(List.of("withdraw"), actions);
    }

    @Test void standaloneBattleEndsAfterOwnershipIsChecked() {
        List<String> actions = new ArrayList<>();
        BattleDeparturePolicy.depart(() -> { actions.add("not-owned"); return false; }, () -> actions.add("end"));
        assertEquals(List.of("not-owned", "end"), actions);
    }

    @Test void ownerFailureNeverFallsBackToEndingTheSharedBattle() {
        RuntimeException failure = new IllegalStateException("owner cleanup failed");
        List<String> actions = new ArrayList<>();
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> BattleDeparturePolicy.depart(() -> { throw failure; }, () -> actions.add("end"))));
        assertTrue(actions.isEmpty());
    }
}
