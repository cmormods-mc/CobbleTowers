package com.cobbletowers.battle.cobblemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.cobbletowers.battle.cobblemon.CompletedPartyRecall.Action.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompletedPartyRecallTest {
    private final UUID finished = UUID.randomUUID();
    private final UUID entity = UUID.randomUUID();

    @Test void recallsOnlyTheIdleEntityAfterTheFinishedBattleReleasesIt() {
        assertEquals(RECALL, CompletedPartyRecall.action(finished, null, entity, entity, null, false));
    }

    @Test void waitsForRegistryAndEntityBattleReleaseAndRecallAnimation() {
        assertEquals(WAIT, CompletedPartyRecall.action(finished, finished, entity, entity, null, false));
        assertEquals(WAIT, CompletedPartyRecall.action(finished, null, entity, entity, finished, false));
        assertEquals(WAIT, CompletedPartyRecall.action(finished, null, entity, entity, null, true));
    }

    @Test void newerBattlesAndReplacementEntitiesCancelTheOldRecall() {
        assertEquals(FORGET, CompletedPartyRecall.action(finished, UUID.randomUUID(), entity, entity, null, false));
        assertEquals(FORGET, CompletedPartyRecall.action(finished, null, entity, entity, UUID.randomUUID(), false));
        assertEquals(FORGET, CompletedPartyRecall.action(finished, null, entity, UUID.randomUUID(), null, false));
        assertEquals(FORGET, CompletedPartyRecall.action(finished, null, entity, null, null, false));
    }
}
