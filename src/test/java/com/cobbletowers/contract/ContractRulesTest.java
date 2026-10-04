package com.cobbletowers.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.api.modifier.RiskTier;
import com.cobbletowers.definition.ContractTemplateDefinition;
import com.cobbletowers.definition.ContractTemplateDefinition.Condition;
import com.cobbletowers.definition.ContractTemplateDefinition.Kind;
import com.cobbletowers.definition.ContractTemplateDefinition.Period;
import com.cobbletowers.events.TowerEvent;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contracts (P32c): what an event is worth, which contracts a day holds, and the shipped templates. */
class ContractRulesTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID RUN = UUID.randomUUID();
    private static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral");
    private static final Path DIR = Paths.get("src/main/resources/data/cobbletowers/cobbletowers/contract_templates");

    private static List<ContractTemplateDefinition> shipped() throws IOException {
        List<ContractTemplateDefinition> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", file.getFileName().toString().replace(".json", ""));
                try (Reader reader = Files.newBufferedReader(file)) {
                    all.add(ContractTemplateDefinition.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
        }
        return all;
    }

    private static Condition cond(Kind kind) {
        return new Condition(kind, 3, 0, false, false, 0);
    }

    private static TowerEvent.FloorCleared floor(int index, long millis, boolean flawless, boolean solo, UUID... players) {
        return new TowerEvent.FloorCleared(RUN, List.of(players), TOWER, index, millis, flawless, solo);
    }

    // ---- events ----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a cleared floor counts for everyone on the team, and for nobody who was not on it")
    void floorsCountForTheTeam() {
        TowerEvent.FloorCleared event = floor(3, 60_000, true, false, ME, OTHER);
        assertEquals(1, ContractRules.delta(cond(Kind.FLOORS_CLEARED), event, ME));
        assertEquals(1, ContractRules.delta(cond(Kind.FLOORS_CLEARED), event, OTHER));
        assertEquals(0, ContractRules.delta(cond(Kind.FLOORS_CLEARED), floor(3, 60_000, true, false, ME), OTHER));
    }

    @Test
    @DisplayName("a floor contract's constraints: a time limit, no faint, solo, and a minimum depth")
    void floorConstraints() {
        Condition quick = new Condition(Kind.FLOORS_CLEARED, 1, 90, false, false, 2);
        assertEquals(1, ContractRules.delta(quick, floor(2, 89_000, false, false, ME), ME));
        assertEquals(0, ContractRules.delta(quick, floor(2, 91_000, false, false, ME), ME), "too slow");
        assertEquals(0, ContractRules.delta(quick, floor(1, 10_000, false, false, ME), ME), "too shallow to farm");
        Condition spotless = new Condition(Kind.FLOORS_CLEARED, 2, 0, true, false, 0);
        assertEquals(1, ContractRules.delta(spotless, floor(1, 1, true, false, ME), ME));
        assertEquals(0, ContractRules.delta(spotless, floor(1, 1, false, false, ME), ME), "a faint spoils it");
        Condition solo = new Condition(Kind.FLOORS_CLEARED, 2, 0, false, true, 0);
        assertEquals(1, ContractRules.delta(solo, floor(1, 1, false, true, ME), ME));
        assertEquals(0, ContractRules.delta(solo, floor(1, 1, false, false, ME, OTHER), ME));
    }

    @Test
    @DisplayName("bosses, purchases, severe drafts and trials each count only their own event")
    void otherKinds() {
        TowerEvent boss = new TowerEvent.BossDefeated(RUN, List.of(ME), TOWER, 5, true);
        TowerEvent bought = new TowerEvent.Purchased(RUN, List.of(ME), ResourceLocation.fromNamespaceAndPath("cobbletowers", "full_heal"));
        TowerEvent severe = new TowerEvent.Drafted(RUN, List.of(ME), ResourceLocation.fromNamespaceAndPath("cobbletowers", "colossus"), RiskTier.SEVERE);
        TowerEvent minor = new TowerEvent.Drafted(RUN, List.of(ME), ResourceLocation.fromNamespaceAndPath("cobbletowers", "far_sight"), RiskTier.MINOR);
        TowerEvent daily = new TowerEvent.TrialFinished(RUN, List.of(ME), "daily:2026-10-05", true, true, true, 5);
        TowerEvent weekly = new TowerEvent.TrialFinished(RUN, List.of(ME), "weekly:2026-w41", false, true, true, 10);
        TowerEvent practice = new TowerEvent.TrialFinished(RUN, List.of(ME), "daily:2026-10-05", true, false, true, 5);
        TowerEvent unfinished = new TowerEvent.TrialFinished(RUN, List.of(ME), "daily:2026-10-05", true, true, false, 2);

        assertEquals(1, ContractRules.delta(cond(Kind.BOSSES_DEFEATED), boss, ME));
        assertEquals(0, ContractRules.delta(cond(Kind.BOSSES_DEFEATED), bought, ME));
        assertEquals(1, ContractRules.delta(cond(Kind.PURCHASES), bought, ME));
        assertEquals(1, ContractRules.delta(cond(Kind.SEVERE_DRAFTS), severe, ME));
        assertEquals(0, ContractRules.delta(cond(Kind.SEVERE_DRAFTS), minor, ME));
        assertEquals(1, ContractRules.delta(cond(Kind.DAILY_TRIALS_FINISHED), daily, ME));
        assertEquals(0, ContractRules.delta(cond(Kind.DAILY_TRIALS_FINISHED), weekly, ME));
        assertEquals(1, ContractRules.delta(cond(Kind.TRIALS_FINISHED), weekly, ME));
        assertEquals(0, ContractRules.delta(cond(Kind.TRIALS_FINISHED), practice, ME), "practice does not count");
        assertEquals(0, ContractRules.delta(cond(Kind.TRIALS_FINISHED), unfinished, ME), "an unfinished trial does not count");
        Condition soloBoss = new Condition(Kind.BOSSES_DEFEATED, 1, 0, false, true, 0);
        assertEquals(0, ContractRules.delta(soloBoss, new TowerEvent.BossDefeated(RUN, List.of(ME), TOWER, 5, false), ME));
    }

    // ---- the draw --------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a day's contracts are the same for everyone and different the next day; three daily and two weekly, never repeated within a set")
    void schedule() throws IOException {
        List<ContractTemplateDefinition> daily = shipped().stream().filter(t -> t.period() == Period.DAILY).toList();
        List<ContractTemplateDefinition> weekly = shipped().stream().filter(t -> t.period() == Period.WEEKLY).toList();
        long day = ContractSchedule.periodNumber(Period.DAILY, LocalDate.of(2026, 10, 5));
        List<ContractTemplateDefinition> a = ContractSchedule.pick(daily, Period.DAILY, day, new int[3]);
        assertEquals(a, ContractSchedule.pick(daily, Period.DAILY, day, new int[3]));
        assertEquals(3, a.size());
        assertEquals(3, new HashSet<>(a).size());
        assertNotEquals(a, ContractSchedule.pick(daily, Period.DAILY, day + 1, new int[3]));
        long week = ContractSchedule.periodNumber(Period.WEEKLY, LocalDate.of(2026, 10, 5));
        for (int d = 5; d <= 11; d++) {
            assertEquals(ContractSchedule.pick(weekly, Period.WEEKLY, week, new int[2]),
                    ContractSchedule.pick(weekly, Period.WEEKLY, ContractSchedule.periodNumber(Period.WEEKLY, LocalDate.of(2026, 10, d)), new int[2]));
        }
        assertEquals(2, ContractSchedule.pick(weekly, Period.WEEKLY, week, new int[2]).size());
    }

    @Test
    @DisplayName("over a year every daily template is handed out, and a set never holds the same one twice")
    void coverage() throws IOException {
        List<ContractTemplateDefinition> daily = shipped().stream().filter(t -> t.period() == Period.DAILY).toList();
        Set<ContractTemplateDefinition> seen = new HashSet<>();
        for (int d = 0; d < 365; d++) {
            List<ContractTemplateDefinition> day = ContractSchedule.pick(daily, Period.DAILY, 20_000 + d, new int[3]);
            assertEquals(3, new HashSet<>(day).size(), "day " + d);
            seen.addAll(day);
        }
        assertEquals(daily.size(), seen.size());
    }

    @Test
    @DisplayName("a reroll changes only the rerolled slot, to a template not already in the set")
    void reroll() throws IOException {
        List<ContractTemplateDefinition> daily = shipped().stream().filter(t -> t.period() == Period.DAILY).toList();
        for (int d = 0; d < 60; d++) {
            long day = 20_000 + d;
            List<ContractTemplateDefinition> plain = ContractSchedule.pick(daily, Period.DAILY, day, new int[3]);
            List<ContractTemplateDefinition> rerolled = ContractSchedule.pick(daily, Period.DAILY, day, new int[] {0, 1, 0});
            assertEquals(plain.get(0), rerolled.get(0));
            assertEquals(plain.get(2), rerolled.get(2));
            assertEquals(3, new HashSet<>(rerolled).size(), "no duplicates after a reroll, day " + d);
        }
    }

    @Test
    @DisplayName("a tiny pool hands out what it has instead of failing")
    void tinyPool() throws IOException {
        List<ContractTemplateDefinition> two = shipped().subList(0, 2);
        assertEquals(2, ContractSchedule.pick(two, Period.DAILY, 5, new int[3]).size());
    }

    // ---- the templates ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the shipped templates parse, have rewards within bounds, and there are enough of each period to draw from")
    void shippedTemplates() throws IOException {
        List<ContractTemplateDefinition> all = shipped();
        assertEquals(14, all.size());
        assertTrue(all.stream().filter(t -> t.period() == Period.DAILY).count() >= 6);
        assertTrue(all.stream().filter(t -> t.period() == Period.WEEKLY).count() >= 3);
        for (ContractTemplateDefinition template : all) {
            assertFalse(template.displayName().isBlank());
            assertFalse(template.description().isBlank());
            assertTrue(template.reward() > 0 && template.reward() <= 300, template.id() + " reward " + template.reward());
        }
        assertEquals(0, all.stream().filter(t -> t.period() == Period.DAILY && t.reward() > 100).count(), "daily rewards stay small");
    }

    @Test
    @DisplayName("a bad template is refused")
    void refusesBad() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", "x");
        assertThrows(IllegalArgumentException.class, () -> ContractTemplateDefinition.fromJson(id, JsonParser.parseString(
                "{\"schema_version\":1,\"display_name\":\"X\",\"period\":\"hourly\",\"condition\":{\"type\":\"floors_cleared\",\"count\":1}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ContractTemplateDefinition.fromJson(id, JsonParser.parseString(
                "{\"schema_version\":1,\"display_name\":\"X\",\"period\":\"daily\",\"condition\":{\"type\":\"win\",\"count\":1}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ContractTemplateDefinition.fromJson(id, JsonParser.parseString(
                "{\"schema_version\":1,\"display_name\":\"X\",\"period\":\"daily\",\"condition\":{\"type\":\"floors_cleared\",\"count\":0}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ContractTemplateDefinition.fromJson(id, JsonParser.parseString(
                "{\"schema_version\":1,\"display_name\":\"X\",\"period\":\"daily\",\"reward\":99999,\"condition\":{\"type\":\"floors_cleared\",\"count\":1}}").getAsJsonObject()));
    }

    @Test
    @DisplayName("stale periods are recognised: a daily older than three days and a weekly older than three weeks")
    void staleness() {
        LocalDate today = LocalDate.of(2026, 10, 12);
        assertFalse(ContractService.isStale("daily:2026-10-12", today));
        assertFalse(ContractService.isStale("daily:2026-10-09", today));
        assertTrue(ContractService.isStale("daily:2026-10-08", today));
        assertFalse(ContractService.isStale("weekly:2026-w41", today));
        assertTrue(ContractService.isStale("weekly:2026-w36", today));
        assertFalse(ContractService.isStale("garbage", today));
    }
}
