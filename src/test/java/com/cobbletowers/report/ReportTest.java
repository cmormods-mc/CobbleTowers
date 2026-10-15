package com.cobbletowers.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunOptions;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Player reports and run telemetry (P48): the pure parts. */
class ReportTest {

    @Test
    @DisplayName("report text becomes one bounded clean line")
    void clean() {
        assertEquals("floor 3 froze", ReportFormat.clean("  floor 3\n\nfroze\t "));
        assertEquals("", ReportFormat.clean(null));
        assertEquals("", ReportFormat.clean("   \n  "));
        assertEquals(ReportFormat.MAX_TEXT, ReportFormat.clean("x".repeat(5000)).length());
        assertFalse(ReportFormat.clean("a\u0000b\u0007c").chars().anyMatch(Character::isISOControl));
    }

    @Test
    @DisplayName("a report reads as a header, the text and the run with its recent lines; a player outside a run says so")
    void format() {
        var facts = new ReportFormat.RunFacts("run-1", "cobbletowers:neutral", 3, "ENCOUNTER_ACTIVE", "daily:2026-10-14",
                List.of("11111111", "22222222"), 42L, List.of("a -> b on X", "b -> c on Y"));
        String text = ReportFormat.format(1_700_000_000_000L, "R1", "Alex", "uuid-1", "0.28.1", "stuck", Optional.of(facts));
        assertTrue(text.startsWith("[2023-11-14T22:13:20Z] R1 Alex (uuid-1) v0.28.1\n"), text);
        assertTrue(text.contains("  text: stuck\n"));
        assertTrue(text.contains("run: run-1 tower cobbletowers:neutral floor 3 ENCOUNTER_ACTIVE trial daily:2026-10-14 seed 42 players 11111111,22222222"));
        assertTrue(text.contains("    a -> b on X\n    b -> c on Y\n"));
        assertTrue(text.endsWith("\n\n"));
        String none = ReportFormat.format(0L, "R0", "Sam", "u", "v", "", Optional.empty());
        assertTrue(none.contains("text: (no text)") && none.contains("run: none"));
    }

    @Test
    @DisplayName("report ids are short and rise with time")
    void ids() {
        assertTrue(ReportFormat.idOf(1_700_000_000_000L).startsWith("R"));
        assertTrue(ReportFormat.idOf(1_700_000_100_000L).compareTo(ReportFormat.idOf(1_700_000_000_000L)) >= 0);
        assertTrue(ReportFormat.idOf(1_700_000_000_000L).length() < 10);
    }

    @Test
    @DisplayName("a run keeps its last twelve lines, and the oldest runs are dropped past two hundred")
    void history() {
        RunHistory.clear();
        UUID run = UUID.randomUUID();
        for (int i = 0; i < 30; i++) RunHistory.note(run, "line " + i);
        List<String> recent = RunHistory.recent(run);
        assertEquals(RunHistory.PER_RUN, recent.size());
        assertEquals("line 18", recent.get(0));
        assertEquals("line 29", recent.get(recent.size() - 1));
        assertTrue(RunHistory.recent(UUID.randomUUID()).isEmpty());

        UUID first = UUID.randomUUID();
        RunHistory.note(first, "first");
        for (int i = 0; i < RunHistory.RUNS; i++) RunHistory.note(UUID.randomUUID(), "x");
        assertTrue(RunHistory.recent(first).isEmpty(), "the oldest run was dropped");
        RunHistory.clear();
    }

    @Test
    @DisplayName("run starts are tallied by kind and floors sort in order")
    void telemetryKeys() {
        PersistedRun plain = TestRuns.fresh(UUID.randomUUID());
        assertEquals("plain", RunTelemetry.startKind(plain));
        assertEquals("trial_daily", RunTelemetry.startKind(plain.withOptions(new RunOptions(Optional.empty(), Optional.of("daily:2026-10-14"), 5, true, 0), 1L)));
        assertEquals("trial_weekly", RunTelemetry.startKind(plain.withOptions(new RunOptions(Optional.empty(), Optional.of("weekly:2026-w41"), 10, true, 0), 1L)));
        assertEquals("rental", RunTelemetry.startKind(plain.withOptions(RunOptions.of(Optional.of(ResourceLocation.fromNamespaceAndPath("cobbletowers", "rental"))), 1L)));
        assertEquals("playlist", RunTelemetry.startKind(plain.withOptions(RunOptions.of(Optional.of(ResourceLocation.fromNamespaceAndPath("cobbletowers", "monotype"))), 1L)));
        assertEquals("f05", RunTelemetry.floorKey(5));
        assertEquals("f10", RunTelemetry.floorKey(10));
        assertTrue(RunTelemetry.floorKey(9).compareTo(RunTelemetry.floorKey(10)) < 0);
        assertEquals("f99", RunTelemetry.floorKey(500));
        assertEquals("f00", RunTelemetry.floorKey(-3));
    }
}
