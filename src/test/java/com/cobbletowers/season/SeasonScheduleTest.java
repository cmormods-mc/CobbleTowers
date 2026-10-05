package com.cobbletowers.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.SeasonDefinition;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The season calendar (P36a): a pure function of the anchor and the day. */
class SeasonScheduleTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 10, 5);

    @Test
    @DisplayName("the default anchor is a Monday, so seasons and the weekly trial turn over together")
    void anchorIsMonday() {
        assertEquals(DayOfWeek.MONDAY, SeasonConfig.DEFAULT_ANCHOR.getDayOfWeek());
    }

    @Test
    @DisplayName("before the anchor there is no season, and it says how long to wait")
    void before() {
        var phase = (SeasonSchedule.Before) SeasonSchedule.at(ANCHOR, ANCHOR.minusDays(3));
        assertEquals(3, phase.daysUntil());
    }

    @Test
    @DisplayName("season 1 runs 42 days from the anchor: week 1 on day 0, week 6 on day 41, and the last day is day 41")
    void activeWindow() {
        var first = (SeasonSchedule.Active) SeasonSchedule.at(ANCHOR, ANCHOR);
        assertEquals(1, first.number());
        assertEquals(1, first.week());
        assertEquals(42, first.daysLeft());
        assertEquals(LocalDate.of(2026, 11, 15), first.lastDay(), "season 1 ends on Sunday 15 November");

        var week2 = (SeasonSchedule.Active) SeasonSchedule.at(ANCHOR, ANCHOR.plusDays(7));
        assertEquals(2, week2.week());

        var last = (SeasonSchedule.Active) SeasonSchedule.at(ANCHOR, ANCHOR.plusDays(41));
        assertEquals(6, last.week());
        assertEquals(1, last.daysLeft(), "today counts: one day left on the last day");
        assertEquals(last.lastDay(), ANCHOR.plusDays(41));
    }

    @Test
    @DisplayName("the seven days after the last are the off-season, and the day after that starts season 2")
    void offSeason() {
        var off = (SeasonSchedule.OffSeason) SeasonSchedule.at(ANCHOR, ANCHOR.plusDays(42));
        assertEquals(1, off.endedNumber());
        assertEquals(2, off.nextNumber());
        assertEquals(LocalDate.of(2026, 11, 23), off.nextStartDay());
        assertEquals(7, off.daysLeft());

        var lastOff = (SeasonSchedule.OffSeason) SeasonSchedule.at(ANCHOR, ANCHOR.plusDays(48));
        assertEquals(1, lastOff.daysLeft());

        var two = (SeasonSchedule.Active) SeasonSchedule.at(ANCHOR, ANCHOR.plusDays(49));
        assertEquals(2, two.number());
        assertEquals(1, two.week());
        assertEquals(LocalDate.of(2026, 11, 23), two.startDay());
    }

    @Test
    @DisplayName("every day maps to exactly one phase and the number and week never go backwards, across a year boundary")
    void monotone() {
        int number = 0;
        int week = 0;
        for (int day = 0; day < 49 * 12; day++) {
            var phase = SeasonSchedule.at(ANCHOR, ANCHOR.plusDays(day));
            int n = phase instanceof SeasonSchedule.Active active ? active.number()
                    : ((SeasonSchedule.OffSeason) phase).endedNumber();
            assertTrue(n >= number, "season numbers only rise");
            if (n != number) {
                number = n;
                week = 0;
            }
            if (phase instanceof SeasonSchedule.Active active) {
                assertTrue(active.week() >= week, "weeks only rise within a season");
                week = active.week();
            }
        }
        assertEquals(12, number);
    }

    @Test
    @DisplayName("lastEnded is the newest season whose last day is past: 0 during season 1, 1 once it ends, and through the off-season")
    void lastEnded() {
        assertEquals(0, SeasonSchedule.lastEnded(ANCHOR, ANCHOR.minusDays(1)));
        assertEquals(0, SeasonSchedule.lastEnded(ANCHOR, ANCHOR.plusDays(41)));
        assertEquals(1, SeasonSchedule.lastEnded(ANCHOR, ANCHOR.plusDays(42)));
        assertEquals(1, SeasonSchedule.lastEnded(ANCHOR, ANCHOR.plusDays(48)));
        assertEquals(1, SeasonSchedule.lastEnded(ANCHOR, ANCHOR.plusDays(49)), "season 2 is running, season 1 is the last to have ended");
        assertEquals(2, SeasonSchedule.lastEnded(ANCHOR, ANCHOR.plusDays(49 + 42)));
    }

    @Test
    @DisplayName("a season's id is s<number> and reads back, and nothing else reads as one")
    void ids() {
        assertEquals("s3", SeasonSchedule.idOf(3));
        assertEquals(Optional.of(3), SeasonSchedule.numberOf("s3"));
        assertEquals(Optional.empty(), SeasonSchedule.numberOf(""));
        assertEquals(Optional.empty(), SeasonSchedule.numberOf("s0"));
        assertEquals(Optional.empty(), SeasonSchedule.numberOf("daily:2026-10-05"));
        assertEquals(Optional.empty(), SeasonSchedule.numberOf(null));
    }

    @Test
    @DisplayName("a season with no authored file is named Season N and rotates the spotlight through the regions")
    void generated() {
        assertEquals("Season 4", SeasonDefinition.generated(4).name());
        assertEquals(SeasonDefinition.REGIONS.get(0), SeasonDefinition.generated(1).spotlight().orElseThrow());
        assertEquals(SeasonDefinition.REGIONS.get(1), SeasonDefinition.generated(2).spotlight().orElseThrow());
        assertEquals(SeasonDefinition.REGIONS.get(2), SeasonDefinition.generated(3).spotlight().orElseThrow());
        assertEquals(SeasonDefinition.REGIONS.get(0), SeasonDefinition.generated(4).spotlight().orElseThrow(), "and round again");
    }
}
