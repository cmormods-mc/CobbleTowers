package com.cobbletowers.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.instance.TowerInstanceSlot;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class TowerRunTest {

    @Test
    void advancesOnlyThroughValidStates() {
        UUID runId = UUID.randomUUID();
        TowerRun run = new TowerRun(runId, 7L, 10, new TowerInstanceSlot(runId, 0, 0, 0),
                List.of(participant(UUID.randomUUID())));

        assertEquals(TowerRunState.PREPARING, run.state());
        assertThrows(IllegalStateException.class, run::advanceFloor);

        run.activateFirstFloor();
        run.beginUpgradeChoice();
        run.finishUpgradeChoice();
        run.advanceFloor();

        assertEquals(2, run.currentFloor());
        assertEquals(TowerRunState.FLOOR_ACTIVE, run.state());
    }

    @Test
    void lastActiveParticipantEliminationFailsRun() {
        UUID runId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        TowerRun run = new TowerRun(runId, 1L, 10, new TowerInstanceSlot(runId, 0, 0, 0),
                List.of(participant(player)));
        run.activateFirstFloor();

        assertTrue(run.eliminate(player));
        assertEquals(TowerRunState.FAILED, run.state());
        assertTrue(run.activeParticipantIds().isEmpty());
        assertFalse(run.eliminate(player));
    }

    @Test
    void onePlayerCanBeEliminatedWhilePartyContinues() {
        UUID runId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TowerRun run = new TowerRun(runId, 1L, 10, new TowerInstanceSlot(runId, 0, 0, 0),
                List.of(participant(first), participant(second)));
        run.activateFirstFloor();

        assertTrue(run.eliminate(first));
        assertEquals(TowerRunState.FLOOR_ACTIVE, run.state());
        assertEquals(java.util.Set.of(second), run.activeParticipantIds());
    }

    @Test
    void snapshotRoundTripPreservesRunAndModifierState() {
        UUID runId = UUID.randomUUID();
        TowerInstanceSlot slot = new TowerInstanceSlot(runId, 3, 576, 0);
        TowerRun original = new TowerRun(runId, 1234L, 10, slot, List.of(participant(UUID.randomUUID())));
        original.modifiers().acceptTemporary(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobbletowers", "hardy"));
        TowerRunSnapshot snapshot = original.snapshot();

        TowerRun restored = TowerRun.restore(snapshot, slot);

        assertEquals(original.runId(), restored.runId());
        assertEquals(original.seed(), restored.seed());
        assertEquals(original.currentFloor(), restored.currentFloor());
        assertEquals(original.state(), restored.state());
        assertEquals(original.modifiers().pendingTemporary(), restored.modifiers().pendingTemporary());
        assertEquals(original.modifiers().promotionChoicesForPersistence(), restored.modifiers().promotionChoicesForPersistence());
    }

    private static TowerParticipant participant(UUID playerId) {
        return new TowerParticipant(playerId,
                new TowerReturnLocation("minecraft:overworld", 1.0, 64.0, 1.0, 0f, 0f), true);
    }
}
