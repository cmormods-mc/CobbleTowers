package com.cobbletowers.trial;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.Locale;

/**
 * What day it is for the trials (P32): a configured zone and reset hour, so "today" starts at a quiet hour. Pure; the
 * instant is passed in.
 */
public final class TrialClock {

    /** A zone and the hour of the day (0-23, local) at which a new trial day begins. */
    public record Rhythm(ZoneId zone, int resetHour) {
        public Rhythm {
            if (resetHour < 0 || resetHour > 23) throw new IllegalArgumentException("reset hour must be 0..23, got " + resetHour);
        }

        /** UTC with a 04:00 reset: the default until a server says otherwise. */
        public static Rhythm standard() {
            return new Rhythm(ZoneId.of("UTC"), 4);
        }
    }

    private TrialClock() {}

    /** The trial day an instant falls in: the local date, but a day only turns over at the reset hour. */
    public static LocalDate dayOf(long epochMillis, Rhythm rhythm) {
        return Instant.ofEpochMilli(epochMillis).atZone(rhythm.zone()).minusHours(rhythm.resetHour()).toLocalDate();
    }

    /** {@code 2026-10-05}. */
    public static String dayKey(LocalDate day) {
        return day.toString();
    }

    /**
     * The ISO week of a trial day, as {@code 2026-w41}: lower case so it is a legal id path. The week turns over on
     * Monday's reset.
     */
    public static String weekKey(LocalDate day) {
        return String.format(Locale.ROOT, "%04d-w%02d", day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** The Monday a week key such as {@code 2026-w41} begins on, or empty if it is not one. */
    public static java.util.Optional<LocalDate> weekStartOf(String weekKey) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(\\d{4})-w(\\d{2})").matcher(weekKey);
        if (!match.matches()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(LocalDate.of(Integer.parseInt(match.group(1)), 1, 4)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, Long.parseLong(match.group(2))).with(DayOfWeek.MONDAY));
        } catch (java.time.DateTimeException ex) {
            return java.util.Optional.empty();
        }
    }

    /** The Monday of the week a trial day is in. */
    public static LocalDate weekStart(LocalDate day) {
        return day.with(DayOfWeek.MONDAY);
    }

    /** How long until the trial day turns over. */
    public static long millisUntilReset(long epochMillis, Rhythm rhythm) {
        LocalDate today = dayOf(epochMillis, rhythm);
        long next = today.plusDays(1).atStartOfDay(rhythm.zone()).plusHours(rhythm.resetHour()).toInstant().toEpochMilli();
        return Math.max(0, next - epochMillis);
    }

    /** {@code 5h 12m}, or {@code 12m} under an hour. */
    public static String describe(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        long hours = minutes / 60;
        return hours > 0 ? hours + "h " + (minutes % 60) + "m" : minutes + "m";
    }
}
