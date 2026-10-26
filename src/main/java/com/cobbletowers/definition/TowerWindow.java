package com.cobbletowers.definition;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * When an event tower can be entered (P54): {@code start} inclusive, {@code end} exclusive, epoch millis. A run already
 * started keeps going past the end; only new runs are refused.
 */
public record TowerWindow(long startMillis, long endMillis) {

    public TowerWindow {
        if (endMillis <= startMillis) throw new IllegalArgumentException("window end must be after its start");
    }

    public boolean contains(long nowMillis) {
        return nowMillis >= startMillis && nowMillis < endMillis;
    }

    /** From ISO instants such as {@code 2026-10-31T00:00:00Z}. */
    public static TowerWindow parse(String start, String end) {
        try {
            return new TowerWindow(Instant.parse(start).toEpochMilli(), Instant.parse(end).toEpochMilli());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("window needs ISO instants like 2026-10-31T00:00:00Z: " + ex.getMessage());
        }
    }
}
