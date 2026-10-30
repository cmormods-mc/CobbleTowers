package com.cobbletowers.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.persistence.PersistedParticipant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloorBossRosterTest {
    private static final UUID FIRST = new UUID(0, 1);
    private static final UUID SECOND = new UUID(0, 2);
    private static final UUID THIRD = new UUID(0, 3);

    private static PersistedParticipant participant(UUID id, ParticipantState state) {
        return new PersistedParticipant(id, state, List.of(new UUID(1, id.getLeastSignificantBits())));
    }

    @Test
    void departedWinnerIsNotInvitedToBossEvenWhenOldRoundStillSaysCleared() {
        var members = List.of(participant(FIRST, ParticipantState.joined().left()),
                participant(SECOND, ParticipantState.joined()));
        var round = Map.of(FIRST, TowerEncounters.Status.CLEARED, SECOND, TowerEncounters.Status.CLEARED);
        assertEquals(List.of(SECOND), FloorBoss.standingParticipants(members, round));
        assertEquals(TowerEncounters.Status.CLEARED, round.get(FIRST));
    }

    @Test
    void disconnectedWinnerDoesNotPreventRemainingMemberFromStartingBoss() {
        var members = List.of(participant(FIRST, ParticipantState.joined().disconnected()),
                participant(SECOND, ParticipantState.joined()));
        assertEquals(List.of(SECOND), FloorBoss.standingParticipants(members,
                Map.of(FIRST, TowerEncounters.Status.CLEARED, SECOND, TowerEncounters.Status.CLEARED)));
    }

    @Test
    void knockedOutAndRevivingMembersWaitForIntermission() {
        var members = List.of(participant(FIRST, ParticipantState.joined().knockedOut().spectating()),
                participant(SECOND, ParticipantState.joined().revivePending()));
        assertEquals(List.of(), FloorBoss.standingParticipants(members,
                Map.of(FIRST, TowerEncounters.Status.CLEARED, SECOND, TowerEncounters.Status.CLEARED)));
    }

    @Test
    void requiresCurrentMembershipAndParticipationInThisRound() {
        var members = List.of(participant(FIRST, ParticipantState.joined()),
                participant(SECOND, ParticipantState.joined()));
        assertEquals(List.of(FIRST), FloorBoss.standingParticipants(members,
                Map.of(FIRST, TowerEncounters.Status.CLEARED, THIRD, TowerEncounters.Status.CLEARED)));
    }

    @Test
    void preservesStandingMembersAndExcludesOutcomesMarkedOut() {
        var members = List.of(participant(FIRST, ParticipantState.joined()),
                participant(SECOND, ParticipantState.joined()), participant(THIRD, ParticipantState.joined()));
        assertEquals(List.of(FIRST, SECOND), FloorBoss.standingParticipants(members,
                Map.of(FIRST, TowerEncounters.Status.CLEARED, SECOND, TowerEncounters.Status.FIGHTING,
                        THIRD, TowerEncounters.Status.OUT)));
    }
}
