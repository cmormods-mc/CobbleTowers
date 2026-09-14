package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.instance.TowerInstanceSlot;
import com.cobbletowers.run.TowerParticipant;
import com.cobbletowers.run.TowerReturnLocation;
import com.cobbletowers.run.TowerRun;
import com.cobbletowers.run.TowerRunSnapshot;
import com.cobbletowers.run.TowerRunState;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class TowerRunNbtCodecTest {

    @Test
    void roundTripsRunSnapshotWithoutLiveRuntimeObjects() {
        UUID runId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        TowerParticipant participant = new TowerParticipant(
                playerId,
                new TowerReturnLocation("minecraft:overworld", 12.5, 72.0, -33.25, 90.0f, 12.0f),
                true
        ).disconnect();

        TowerRun run = new TowerRun(runId, 918273645L, 10, new TowerInstanceSlot(runId, 7, 384, 192), List.of(participant));
        run.activateFirstFloor();
        run.modifiers().acceptTemporary(ResourceLocation.fromNamespaceAndPath("cobbletowers", "fortified"));

        TowerRunSnapshot original = run.snapshot();
        TowerRunSnapshot decoded = TowerRunNbtCodec.decode(TowerRunNbtCodec.encode(original));

        assertEquals(original, decoded);
        assertFalse(decoded.participants().getFirst().connected());
        assertEquals(TowerParticipant.DEFAULT_RECONNECT_GRACE_TICKS,
                decoded.participants().getFirst().reconnectGraceTicksRemaining());
    }

    @Test
    void preservesInterruptedBossStateForRecovery() {
        UUID runId = UUID.randomUUID();
        TowerRun run = new TowerRun(
                runId,
                44L,
                10,
                new TowerInstanceSlot(runId, 0, 0, 0),
                List.of(new TowerParticipant(UUID.randomUUID(),
                        new TowerReturnLocation("minecraft:overworld", 0, 70, 0, 0, 0), true))
        );
        run.activateFirstFloor();
        run.beginBossBattle();

        TowerRun restored = TowerRun.restore(
                TowerRunNbtCodec.decode(TowerRunNbtCodec.encode(run.snapshot())),
                run.instanceSlot()
        );
        assertEquals(TowerRunState.BOSS_BATTLE, restored.state());

        restored.recoverInterruptedBossBattle();
        assertEquals(TowerRunState.FLOOR_ACTIVE, restored.state());
    }

    @Test
    void reconnectGraceSurvivesSnapshotAndExpiresOnlyWhenTicked() {
        UUID runId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        TowerRun run = new TowerRun(
                runId,
                9L,
                10,
                new TowerInstanceSlot(runId, 0, 0, 0),
                List.of(new TowerParticipant(playerId,
                        new TowerReturnLocation("minecraft:overworld", 1, 65, 1, 0, 0), true))
        );
        run.activateFirstFloor();
        assertTrue(run.disconnect(playerId));

        for (int i = 0; i < 25; i++) assertTrue(run.tickReconnectGrace());
        int remaining = run.participant(playerId).reconnectGraceTicksRemaining();

        TowerRun restored = TowerRun.restore(
                TowerRunNbtCodec.decode(TowerRunNbtCodec.encode(run.snapshot())),
                run.instanceSlot()
        );
        assertEquals(remaining, restored.participant(playerId).reconnectGraceTicksRemaining());
        assertTrue(restored.participant(playerId).active());
        assertFalse(restored.participant(playerId).connected());
    }

    @Test
    void twoPhaseFloorPreparationChangesFloorOnlyAfterPreparationCompletes() {
        UUID runId = UUID.randomUUID();
        TowerRun run = new TowerRun(
                runId,
                1L,
                10,
                new TowerInstanceSlot(runId, 0, 0, 0),
                List.of(new TowerParticipant(UUID.randomUUID(),
                        new TowerReturnLocation("minecraft:overworld", 0, 64, 0, 0, 0), true))
        );
        run.activateFirstFloor();
        run.beginUpgradeChoice();
        run.finishUpgradeChoice();
        run.beginPreparingNextFloor();

        assertEquals(1, run.currentFloor());
        assertEquals(TowerRunState.PREPARING_NEXT_FLOOR, run.state());

        run.finishPreparingNextFloor();
        assertEquals(2, run.currentFloor());
        assertEquals(TowerRunState.FLOOR_ACTIVE, run.state());
    }
}
