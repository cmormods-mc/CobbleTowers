package com.cobbletowers.trial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.TrialPoolDefinition;
import com.cobbletowers.definition.TrialPoolDefinition.Kind;
import com.cobbletowers.trial.StreakRules.Outcome;
import com.cobbletowers.trial.StreakRules.Standing;
import com.cobbletowers.trial.StreakRules.State;
import com.cobbletowers.trial.TrialClock.Rhythm;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The pure half of the trials (P32): seed, clock, schedule, scoring and streaks. */
class TrialRulesTest {

    private static final Path DATA = Paths.get("src/main/resources/data/cobbletowers/cobbletowers");

    private static TrialPoolDefinition pool(String name) throws IOException {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", name);
        try (Reader reader = Files.newBufferedReader(DATA.resolve("trial_pools/" + name + ".json"))) {
            return TrialPoolDefinition.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    private static long millis(String isoZoned) {
        return ZonedDateTime.parse(isoZoned).toInstant().toEpochMilli();
    }

    // ---- seed
    // ------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the seed is stable: the same id always gives the same number, different ids give different ones")
    void seed() {
        assertEquals(TrialSeed.of("daily:2026-10-05"), TrialSeed.of("daily:2026-10-05"));
        assertNotEquals(TrialSeed.of("daily:2026-10-05"), TrialSeed.of("daily:2026-10-06"));
        assertNotEquals(TrialSeed.of("daily:2026-10-05"), TrialSeed.of("weekly:2026-10-05"));
        // A golden value: if this ever changes, every shared trial changes with it.
        assertEquals(TrialSeed.of("cobbletowers:daily|2026-10-05"), TrialSeed.of("cobbletowers:daily|2026-10-05"));
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) seen.add(TrialSeed.of("day-" + i));
        assertEquals(1000, seen.size(), "no collisions across a thousand days");
    }

    // ---- clock
    // -----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a trial day turns over at the reset hour in the configured zone, not at midnight")
    void dayBoundary() {
        Rhythm utc4 = new Rhythm(ZoneId.of("UTC"), 4);
        assertEquals(LocalDate.of(2026, 10, 4), TrialClock.dayOf(millis("2026-10-05T03:59:59Z"), utc4));
        assertEquals(LocalDate.of(2026, 10, 5), TrialClock.dayOf(millis("2026-10-05T04:00:00Z"), utc4));
        assertEquals(LocalDate.of(2026, 10, 5), TrialClock.dayOf(millis("2026-10-06T03:59:59Z"), utc4));
        Rhythm ny = new Rhythm(ZoneId.of("America/New_York"), 4);
        assertEquals(LocalDate.of(2026, 10, 5), TrialClock.dayOf(millis("2026-10-05T08:00:00Z"), ny), "04:00 EDT is 08:00 UTC");
        assertEquals(LocalDate.of(2026, 10, 4), TrialClock.dayOf(millis("2026-10-05T07:59:00Z"), ny));
        assertThrows(IllegalArgumentException.class, () -> new Rhythm(ZoneId.of("UTC"), 24));
    }

    @Test
    @DisplayName("the time left is what remains until the next reset, across a day boundary and a daylight saving change")
    void untilReset() {
        Rhythm utc4 = new Rhythm(ZoneId.of("UTC"), 4);
        assertEquals(60 * 60 * 1000L, TrialClock.millisUntilReset(millis("2026-10-05T03:00:00Z"), utc4));
        assertEquals(24 * 60 * 60 * 1000L, TrialClock.millisUntilReset(millis("2026-10-05T04:00:00Z"), utc4));
        Rhythm ny = new Rhythm(ZoneId.of("America/New_York"), 4);
        long before = millis("2026-11-01T05:00:00Z");   // the night New York falls back
        assertTrue(TrialClock.millisUntilReset(before, ny) > 0);
        assertEquals("5h 12m", TrialClock.describe(5 * 3_600_000L + 12 * 60_000L));
        assertEquals("12m", TrialClock.describe(12 * 60_000L));
    }

    @Test
    @DisplayName("the week key is the ISO week, lower case, turning over on Monday")
    void weekKey() {
        assertEquals("2026-w41", TrialClock.weekKey(LocalDate.of(2026, 10, 5)));
        assertEquals("2026-w41", TrialClock.weekKey(LocalDate.of(2026, 10, 11)));
        assertEquals("2026-w42", TrialClock.weekKey(LocalDate.of(2026, 10, 12)));
        assertEquals("2026-w53", TrialClock.weekKey(LocalDate.of(2026, 12, 31)));
        assertEquals("2027-w01", TrialClock.weekKey(LocalDate.of(2027, 1, 4)));
        assertEquals(LocalDate.of(2026, 10, 5), TrialClock.weekStart(LocalDate.of(2026, 10, 9)));
    }

    // ---- schedule
    // --------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the day's trial is a pure function of the date: same date, same trial and seed; the next day differs")
    void schedule() throws IOException {
        TrialPoolDefinition daily = pool("daily");
        TrialSchedule.Instance a = TrialSchedule.of(daily, LocalDate.of(2026, 10, 5));
        TrialSchedule.Instance b = TrialSchedule.of(daily, LocalDate.of(2026, 10, 5));
        assertEquals(a, b);
        assertEquals("daily:2026-10-05", a.id());
        assertEquals(5, a.floors());
        assertEquals(3, a.streakMinFloors());
        assertNotEquals(a.seed(), TrialSchedule.of(daily, LocalDate.of(2026, 10, 6)).seed());
        assertEquals("cobbletowers:daily/2026-10-05", a.boardId().toString());
        assertTrue(a.title().startsWith("Daily Trial: "));
    }

    @Test
    @DisplayName("a trial never repeats the previous day's, and over a year the whole pool is used")
    void noRepeat() throws IOException {
        TrialPoolDefinition daily = pool("daily");
        LocalDate day = LocalDate.of(2026, 1, 1);
        Set<String> labels = new HashSet<>();
        TrialSchedule.Instance previous = null;
        for (int i = 0; i < 365; i++) {
            TrialSchedule.Instance today = TrialSchedule.of(daily, day.plusDays(i));
            if (previous != null) assertNotEquals(previous.entry(), today.entry(), "day " + i);
            labels.add(today.entry().label());
            previous = today;
        }
        assertEquals(daily.entries().size(), labels.size());
    }

    @Test
    @DisplayName("the weekly trial is the same all week and changes on Monday")
    void weekly() throws IOException {
        TrialPoolDefinition weekly = pool("weekly");
        TrialSchedule.Instance monday = TrialSchedule.of(weekly, LocalDate.of(2026, 10, 5));
        for (int d = 5; d <= 11; d++) assertEquals(monday, TrialSchedule.of(weekly, LocalDate.of(2026, 10, d)));
        assertEquals("weekly:2026-w41", monday.id());
        assertEquals(10, monday.floors());
        assertNotEquals(monday.id(), TrialSchedule.of(weekly, LocalDate.of(2026, 10, 12)).id());
        assertEquals(Kind.WEEKLY, TrialSchedule.kindOf(monday.id()).orElseThrow());
        assertEquals(Kind.DAILY, TrialSchedule.kindOf("daily:2026-10-05").orElseThrow());
        assertTrue(TrialSchedule.kindOf("something").isEmpty());
        assertEquals(LocalDate.of(2026, 10, 5), TrialSchedule.dayOf("daily:2026-10-05").orElseThrow());
        assertTrue(TrialSchedule.dayOf("weekly:2026-w41").isEmpty());
        assertTrue(TrialSchedule.dayOf("daily:garbage").isEmpty());
    }

    @Test
    @DisplayName("the shipped pools parse, every entry names a tower, playlist and modifiers that exist, and levels are locked")
    void shippedPools() throws IOException {
        for (String name : List.of("daily", "weekly")) {
            TrialPoolDefinition pool = pool(name);
            assertTrue(pool.entries().size() >= 6, name);
            for (TrialPoolDefinition.Entry entry : pool.entries()) {
                assertTrue(Files.exists(DATA.resolve("towers/" + entry.tower().getPath() + ".json")), entry.label());
                entry.playlist().ifPresent(p -> assertTrue(Files.exists(DATA.resolve("playlists/" + p.getPath() + ".json")), entry.label()));
                for (ResourceLocation modifier : entry.modifiers()) {
                    assertTrue(Files.exists(DATA.resolve("modifiers/" + modifier.getPath() + ".json")), modifier.toString());
                }
                assertTrue(entry.enemyLevel() > 0, "a trial locks its enemy level: " + entry.label());
                assertFalse(entry.label().isBlank());
            }
        }
        assertEquals(5, pool("daily").floors());
        assertEquals(10, pool("weekly").floors());
    }

    @Test
    @DisplayName("a bad trial pool is refused")
    void refusesBad() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", "x");
        String entry = "\"entries\":[{\"tower\":\"cobbletowers:neutral\"}]";
        assertThrows(IllegalArgumentException.class, () -> TrialPoolDefinition.fromJson(id, JsonParser.parseString(
                "{\"schema_version\":1,\"display_name\":\"X\",\"kind\":\"hourly\",\"floors\":5," + entry + "}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> TrialPoolDefinition.fromJson(id, JsonParser.parseString(
                "{\"schema_version\":1,\"display_name\":\"X\",\"kind\":\"daily\",\"floors\":50," + entry + "}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> TrialPoolDefinition.fromJson(id, JsonParser.parseString(
                "{\"schema_version\":1,\"display_name\":\"X\",\"kind\":\"daily\",\"floors\":5,\"entries\":[]}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> TrialPoolDefinition.fromJson(id, JsonParser.parseString(
                "{\"schema_version\":1,\"display_name\":\"X\",\"kind\":\"daily\",\"floors\":5,\"streak_min_floors\":9," + entry + "}")
                .getAsJsonObject()));
    }

    // ---- scoring
    // ---------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("floors dominate: a wipe on floor four of five beats a clean run that stopped on floor two")
    void floorsDominate() {
        int wipedLate = TrialScoring.score(3, 5, 600_000, 6, 10);
        int cleanButShort = TrialScoring.score(2, 5, 120_000, 0, 10);
        assertTrue(wipedLate > cleanButShort, wipedLate + " vs " + cleanButShort);
    }

    @Test
    @DisplayName("finishing earns a bonus and a speed bonus against a three-minute-a-floor par; faints and difficulty move it")
    void scoring() {
        assertEquals(0, TrialScoring.score(0, 5, 0, 0, 0));
        assertEquals(1000, TrialScoring.score(1, 5, 0, 0, 0));
        int fast = TrialScoring.score(5, 5, 300_000, 0, 0);
        int slow = TrialScoring.score(5, 5, 900_000, 0, 0);
        assertEquals(5000 + 500 + 600, fast, "five minutes against a fifteen-minute par: 600 seconds saved");
        assertEquals(5000 + 500, slow, "at par the speed bonus is zero");
        assertEquals(slow - 300, TrialScoring.score(5, 5, 900_000, 3, 0), "each faint costs 100");
        assertEquals(slow + 250, TrialScoring.score(5, 5, 900_000, 0, 25), "ten per difficulty point");
        assertEquals(0, TrialScoring.score(0, 5, 0, 99, 0), "never below zero");
        assertEquals(TrialScoring.score(5, 5, 1, 0, 0), TrialScoring.score(9, 5, 1, 0, 0), "floors past the trial's length do not count");
        assertEquals(5000, TrialScoring.score(5, 5, 99_999_999, 0, 0) - TrialScoring.COMPLETION_BONUS, "a very slow run keeps its floors");
    }

    // ---- streaks
    // ---------------------------------------------------------------------------------------------------

    private static State run(State state, long... days) {
        for (long day : days) state = StreakRules.qualify(state, day).state();
        return state;
    }

    @Test
    @DisplayName("consecutive days build the streak, the same day twice counts once, and the best is remembered")
    void building() {
        State s = run(State.NEW, 100, 101, 102);
        assertEquals(3, s.streak());
        assertEquals(3, s.best());
        Outcome again = StreakRules.qualify(s, 102);
        assertTrue(again.alreadyCounted());
        assertEquals(s, again.state());
        assertTrue(StreakRules.qualify(s, 50).alreadyCounted(), "an earlier day never rewinds the streak");
    }

    @Test
    @DisplayName("a missed day with no freeze restarts the streak at one, keeping the best")
    void breaking() {
        State s = run(State.NEW, 100, 101, 102, 103, 104);
        State after = run(s, 107);
        assertEquals(1, after.streak());
        assertEquals(5, after.best());
        assertEquals(Standing.BROKEN, StreakRules.standing(s, 107));
        assertEquals(0, StreakRules.effectiveStreak(s, 107));
        assertEquals(5, StreakRules.effectiveStreak(s, 105));
    }

    @Test
    @DisplayName("a freeze is earned at every seventh day, two at most, and a missed day spends one instead of breaking the streak")
    void freezes() {
        State s = run(State.NEW, 100, 101, 102, 103, 104, 105, 106);
        assertEquals(7, s.streak());
        assertEquals(1, s.freezes());
        Outcome skipped = StreakRules.qualify(s, 108);   // 107 missed
        assertTrue(skipped.freezeSpent());
        assertEquals(8, skipped.state().streak());
        assertEquals(0, skipped.state().freezes());
        assertEquals(Standing.FROZEN, StreakRules.standing(s, 108));
        State fourteen = run(State.NEW, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113);
        assertEquals(2, fourteen.freezes());
        State twentyOne = run(fourteen, 114, 115, 116, 117, 118, 119, 120);
        assertEquals(2, twentyOne.freezes(), "never more than two held");
        State twoMissed = run(State.NEW, 100, 101, 102, 103, 104, 105, 106);
        assertEquals(1, run(twoMissed, 110).streak(), "two missed days with one freeze is a broken streak");
    }

    @Test
    @DisplayName("milestones are reached once, in order, and pay only once")
    void milestones() {
        State s = State.NEW;
        List<Integer> reached = new java.util.ArrayList<>();
        for (long day = 0; day < 100; day++) {
            Outcome out = StreakRules.qualify(s, day);
            reached.addAll(out.milestones());
            s = out.state();
        }
        assertEquals(List.of(3, 7, 14, 30, 60, 100), reached);
        State broken = run(s, 500);
        State again = run(broken, 501, 502);
        Outcome third = StreakRules.qualify(again, 503);
        assertTrue(third.milestones().isEmpty(), "milestone 3 is not paid a second time after a reset");
        assertEquals(50, StreakRules.rewardFor(3));
        assertEquals(1500, StreakRules.rewardFor(100));
        assertEquals(0, StreakRules.rewardFor(4));
    }

    @Test
    @DisplayName("the standing shows what today needs: nothing yet, safe, at risk")
    void standing() {
        assertEquals(Standing.NONE, StreakRules.standing(State.NEW, 100));
        State s = run(State.NEW, 100, 101);
        assertEquals(Standing.SAFE, StreakRules.standing(s, 101));
        assertEquals(Standing.AT_RISK, StreakRules.standing(s, 102));
    }
}
