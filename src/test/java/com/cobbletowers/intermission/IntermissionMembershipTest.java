package com.cobbletowers.intermission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.api.tower.participant.CombatState;
import com.cobbletowers.api.tower.participant.ConnectionState;
import com.cobbletowers.api.tower.participant.MembershipState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.runtime.ParticipantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntermissionMembershipTest {
    @Test void departedPlayersNeverReceiveIntermissionEvenIfTheirCombatStateStillLooksActive() {
        for (var connection : ConnectionState.values()) {
            for (var combat : CombatState.values()) {
                var run = ParticipantService.changed(TestRuns.fresh(UUID.randomUUID()), TestRuns.PLAYER,
                        old -> new ParticipantState(connection, combat, MembershipState.VOLUNTARILY_LEFT), TestRuns.NOW);
                assertTrue(IntermissionService.members(run).isEmpty(), connection + "/" + combat);
            }
        }
    }

    @Test void spectatorsAndDisconnectedMembersKeepTheirPlaceInTheIntermissionTeam() {
        for (var connection : ConnectionState.values()) {
            for (var combat : CombatState.values()) {
                var run = ParticipantService.changed(TestRuns.fresh(UUID.randomUUID()), TestRuns.PLAYER,
                        old -> new ParticipantState(connection, combat, MembershipState.MEMBER), TestRuns.NOW);
                assertEquals(run.participants(), IntermissionService.members(run), connection + "/" + combat);
            }
        }
    }
}
