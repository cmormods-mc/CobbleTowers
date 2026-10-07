package com.cobbletowers.diagnostics;

/**
 * TDS #33's performance budgets as named constants (no config system), calibrated from live runs: cell preparation
 * 100-450 ms, encounter construction 31-90 ms, a four-player transition about 76 ms (up to 205 ms). A budget crossed
 * logs a warning through {@link TowerMetrics}; nothing throttles.
 */
public final class DiagnosticBudgets {

    public static final long ALLOCATION_BUDGET_MILLIS = 1000;
    public static final long ENCOUNTER_CONSTRUCTION_BUDGET_MILLIS = 1500;
    public static final long TRANSITION_BUDGET_MILLIS = 250;
    public static final long CLEANUP_BUDGET_MILLIS = 500;
    public static final long TICK_BUDGET_MILLIS = 50;

    private DiagnosticBudgets() {}
}
