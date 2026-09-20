package com.cobbletowers.diagnostics;

/**
 * TDS #33's "performance budgets... are first-class features," as named constants rather than a
 * config system this codebase does not otherwise have (the same posture
 * {@code ParticipantService.RECONNECT_WINDOW_MILLIS} already takes for a number TDS also asks to be
 * configurable eventually).
 *
 * <p>Calibrated from real numbers, not guessed cold: cell preparation has run 100-450ms in every live
 * test this project has ever printed, and {@code party_load_test.py}'s four-player, 24-Pokemon load
 * (TDS #42) put encounter construction at 31-90ms and a full transition -- including the revival,
 * reward-bank and draft side effects a `Move` into `INTERMISSION` triggers -- at 76ms average, up to
 * 205ms under that load. The original transition guess of 100ms was found too tight by that data
 * before this constant ever shipped: over half the samples from a real four-player run crossed it,
 * which is a number nobody needed a warning about, not a budget. A budget crossed logs a warning
 * through {@link TowerMetrics}; nothing here throttles or refuses.
 */
public final class DiagnosticBudgets {

    public static final long ALLOCATION_BUDGET_MILLIS = 1000;
    public static final long ENCOUNTER_CONSTRUCTION_BUDGET_MILLIS = 1500;
    public static final long TRANSITION_BUDGET_MILLIS = 250;
    public static final long CLEANUP_BUDGET_MILLIS = 500;
    public static final long TICK_BUDGET_MILLIS = 50;

    private DiagnosticBudgets() {}
}
