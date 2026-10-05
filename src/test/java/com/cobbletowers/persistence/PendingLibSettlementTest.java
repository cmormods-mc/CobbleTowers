package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingLibSettlementTest {

    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);
    private static final UUID C = new UUID(3, 3);

    private static PendingLibSettlement milestone() {
        return new PendingLibSettlement(UUID.randomUUID(), PendingLibSettlement.Kind.MILESTONE, "VICTORY", 6, 10, false,
                List.of(A, B, C), 1000L, 0);
    }

    @Test
    @DisplayName("a trial settlement keeps its rank through the disk round trip and retries like the others")
    void trialRankSurvivesTheTagAndRetries() {
        var trial = new PendingLibSettlement(UUID.randomUUID(), PendingLibSettlement.Kind.TRIAL, "VICTORY", 0, 2, false,
                List.of(A, B), 1000L, 0);
        assertEquals(trial, PendingLibSettlement.fromTag(trial.toTag()));
        assertEquals(List.of(B), trial.afterAttempt(Map.of(A, "GRANTED", B, "DISABLED")).orElseThrow().players());
    }

    @Test
    @DisplayName("confirmed players drop out and only the unconfirmed ones are retried")
    void keepsOnlyThePlayersStillOwed() {
        var next = milestone().afterAttempt(Map.of(A, "GRANTED", B, "REFUSED", C, "DISABLED")).orElseThrow();
        assertEquals(List.of(B, C), next.players());
        assertEquals(1, next.attempts());
    }

    @Test
    @DisplayName("every final status finishes a player, so nothing is retried forever")
    void finalStatusesAreDone() {
        assertTrue(milestone().afterAttempt(
                Map.of(A, "GRANTED", B, "ALREADY_GRANTED", C, "CONFLICT")).isEmpty());
        assertTrue(milestone().afterAttempt(Map.of(A, "NOT_PAID", B, "NOT_PAID", C, "NOT_PAID")).isEmpty());
    }

    @Test
    @DisplayName("a player the library did not list rolled no drop, and is finished")
    void absentPlayersAreDone() {
        var drops = new PendingLibSettlement(UUID.randomUUID(), PendingLibSettlement.Kind.SCOUTER_DROPS, "VICTORY", 0, 0,
                false, List.of(A, B), 0L, 0);
        assertTrue(drops.afterAttempt(Map.of()).isEmpty());
        assertEquals(List.of(B), drops.afterAttempt(Map.of(A, "GRANTED", B, "DISABLED")).orElseThrow().players());
    }

    @Test
    @DisplayName("a call that failed outright confirms nobody")
    void aFailedCallKeepsEveryone() {
        var next = milestone().afterAttempt(null).orElseThrow();
        assertEquals(List.of(A, B, C), next.players());
        assertEquals(1, next.attempts());
    }

    @Test
    @DisplayName("an unpaid settlement expires after a week and not before")
    void expiry() {
        var settlement = milestone();
        assertFalse(settlement.expired(1000L + PendingLibSettlement.EXPIRY_MILLIS));
        assertTrue(settlement.expired(1001L + PendingLibSettlement.EXPIRY_MILLIS));
    }

    @Test
    @DisplayName("a settlement survives a save and load, including its remaining players")
    void roundTrip() {
        var original = milestone().afterAttempt(Map.of(A, "GRANTED", B, "REFUSED", C, "REFUSED")).orElseThrow();
        assertEquals(original, PendingLibSettlement.fromTag(original.toTag()));
        var drops = new PendingLibSettlement(UUID.randomUUID(), PendingLibSettlement.Kind.SCOUTER_DROPS, "VICTORY", 0, 0,
                true, List.of(A), 5L, 2);
        assertEquals(drops, PendingLibSettlement.fromTag(drops.toTag()));
    }
}
