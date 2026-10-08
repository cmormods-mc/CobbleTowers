package com.cobbletowers.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TuningCountersTest {

    @Test
    @DisplayName("an empty store says so")
    void empty() {
        assertEquals(List.of("Counters: nothing recorded yet."), TuningCounters.lines(Map.of()));
    }

    @Test
    @DisplayName("tallies are grouped by the part of the key before the first dot")
    void groups() {
        List<String> lines = TuningCounters.lines(new TreeMap<>(Map.of("contract_completed.a", 3L, "contract_completed.b", 1L, "claim_mastery.neutral:1", 2L)));
        assertTrue(lines.contains("  contract_completed"));
        assertTrue(lines.contains("    a: 3"));
        assertTrue(lines.contains("    neutral:1: 2"));
    }

    @Test
    @DisplayName("a modifier's pick rate is taken over offered")
    void pickRate() {
        List<String> lines = TuningCounters.lines(new TreeMap<>(Map.of("draft_offered.cobbletowers:x", 8L, "draft_taken.cobbletowers:x", 2L)));
        assertTrue(lines.contains("    draft cobbletowers:x: taken 2 of 8 offers (25%)"), lines.toString());
    }

    @Test
    @DisplayName("floor time and the risk bonus are averaged")
    void averages() {
        List<String> lines = TuningCounters.lines(new TreeMap<>(Map.of("floor_time.floors", 2L, "floor_time.millis_sum", 120_000L,
                "risk_bonus.payouts_with_bonus", 2L, "risk_bonus.percent_sum", 20L, "risk_bonus.final_payouts", 5L)));
        assertTrue(lines.contains("    floor time: 60 s on average over 2 cleared floor(s)"), lines.toString());
        assertTrue(lines.contains("    risk bonus: 10% on average over 2 of 5 final payouts"), lines.toString());
    }
}
