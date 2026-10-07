package com.cobbletowers.season;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Which season a day falls in (P36a), a pure function of anchor and date. A cycle is {@value #CYCLE_DAYS} days:
 * {@value #ACTIVE_DAYS} of season then {@value #OFF_DAYS} off. Seasons are numbered from 1 at the anchor; days are
 * trial days, so a season turns over at the reset hour.
 */
public final class SeasonSchedule {

    public static final int ACTIVE_DAYS = 42;
    public static final int OFF_DAYS = 7;
    public static final int CYCLE_DAYS = ACTIVE_DAYS + OFF_DAYS;
    public static final int WEEKS = ACTIVE_DAYS / 7;

    private SeasonSchedule() {}

    /** Where a day sits in the calendar. */
    public sealed interface Phase permits Before, Active, OffSeason {}

    /** The anchor has not arrived. */
    public record Before(LocalDate anchor, long daysUntil) implements Phase {}

    /** Season {@code number} is running; {@code week} is 1..6; {@code daysLeft} counts today (1 on the last day). */
    public record Active(int number, int week, LocalDate startDay, LocalDate lastDay, long daysLeft) implements Phase {}

    /**
     * Between seasons: {@code endedNumber} has finished, {@code nextNumber} starts on {@code nextStartDay}; {@code
     * daysLeft} counts today.
     */
    public record OffSeason(int endedNumber, int nextNumber, LocalDate nextStartDay, long daysLeft) implements Phase {}

    public static Phase at(LocalDate anchor, LocalDate day) {
        long since = ChronoUnit.DAYS.between(anchor, day);
        if (since < 0) return new Before(anchor, -since);
        int number = (int) (since / CYCLE_DAYS) + 1;
        int inCycle = (int) (since % CYCLE_DAYS);
        LocalDate start = startOf(anchor, number);
        if (inCycle < ACTIVE_DAYS) {
            return new Active(number, inCycle / 7 + 1, start, lastDayOf(anchor, number), ACTIVE_DAYS - inCycle);
        }
        return new OffSeason(number, number + 1, startOf(anchor, number + 1), CYCLE_DAYS - inCycle);
    }

    /** The first day of season {@code number}. */
    public static LocalDate startOf(LocalDate anchor, int number) {
        return anchor.plusDays((long) (number - 1) * CYCLE_DAYS);
    }

    /** The last day of season {@code number}'s play (the off-season follows). */
    public static LocalDate lastDayOf(LocalDate anchor, int number) {
        return startOf(anchor, number).plusDays(ACTIVE_DAYS - 1);
    }

    /** The id a season's boards use: {@code s3}. */
    public static String idOf(int number) {
        return "s" + number;
    }

    public static Optional<Integer> numberOf(String id) {
        if (id == null || !id.matches("s[1-9]\\d{0,5}")) return Optional.empty();
        return Optional.of(Integer.parseInt(id.substring(1)));
    }

    /** The newest season that has finished by {@code day} (its last day is past), or 0 if none has. */
    public static int lastEnded(LocalDate anchor, LocalDate day) {
        long since = ChronoUnit.DAYS.between(anchor, day);
        if (since < 0) return 0;
        long cycles = since / CYCLE_DAYS;
        return (int) (since % CYCLE_DAYS >= ACTIVE_DAYS ? cycles + 1 : cycles);
    }
}
