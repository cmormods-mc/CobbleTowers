package com.cobbletowers.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.api.modifier.RiskTier;
import com.cobbletowers.definition.AchievementDefinition;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MasteryRulesTest {

    private static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral");

    private static List<AchievementDefinition> shipped() throws IOException {
        Path dir = Paths.get("src/main/resources/data/cobbletowers/cobbletowers/achievements");
        List<AchievementDefinition> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers",
                        file.getFileName().toString().replace(".json", ""));
                try (Reader reader = Files.newBufferedReader(file)) {
                    all.add(AchievementDefinition.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
        }
        return all;
    }

    private static CycleResult clear(int ascension, boolean solo, long seconds, boolean flawless, int severe, int score) {
        return new CycleResult(UUID.randomUUID(), TOWER, ascension, List.of(UUID.randomUUID()), solo, seconds * 1000L,
                flawless, severe, score, 1, 1, "digest", 1000L);
    }

    private static AchievementDefinition parse(String json) {
        return AchievementDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("cobbletowers", "x"),
                JsonParser.parseString(json).getAsJsonObject());
    }

    // ---- the shipped content --------------------------------------------------------------------------------------

    @Test
    @DisplayName("thirty achievements ship, each with a name and a description, and all parse")
    void thirtyShip() throws IOException {
        List<AchievementDefinition> all = shipped();
        assertEquals(30, all.size());
        assertTrue(all.stream().allMatch(a -> !a.displayName().isBlank() && !a.description().isBlank()));
        assertEquals(30, all.stream().map(AchievementDefinition::displayName).distinct().count(), "names are distinct");
    }

    @Test
    @DisplayName("every shipped achievement can be unlocked by some clear, and a perfect deep clear unlocks all the clear ones")
    void everythingIsReachable() throws IOException {
        List<AchievementDefinition> all = shipped();
        CycleResult perfect = clear(20, true, 60, true, 8, 500);
        List<AchievementDefinition> got = MasteryEvaluator.unlocked(all, Set.of(), perfect, new MasteryEvaluator.Lifetime(100, 20));
        assertEquals(30, got.size(), "a perfect deep solo clear with lifetime totals met unlocks everything");
    }

    // ---- the evaluator
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a clear unlocks only what it meets; a base-cycle solo clear in ten minutes is not an Ascension achievement")
    void evaluatesConstraints() throws IOException {
        List<AchievementDefinition> all = shipped();
        CycleResult plain = clear(0, true, 590, false, 0, 15);
        List<String> ids = MasteryEvaluator.unlocked(all, Set.of(), plain, new MasteryEvaluator.Lifetime(1, 0)).stream()
                .map(a -> a.id().getPath()).toList();
        assertTrue(ids.containsAll(List.of("first_ascent", "solo_0", "speed_25", "speed_15", "speed_10")), ids.toString());
        assertFalse(ids.contains("speed_7"), "ten minutes is not under seven");
        assertFalse(ids.contains("flawless_0"), "not flawless");
        assertFalse(ids.contains("depth_1"));
        assertFalse(ids.contains("challenger_2"));
        assertFalse(ids.contains("score_40"));
    }

    @Test
    @DisplayName("what a player already holds is never granted again, and the order is stable")
    void neverTwice() throws IOException {
        List<AchievementDefinition> all = shipped();
        CycleResult plain = clear(0, true, 590, true, 0, 15);
        MasteryEvaluator.Lifetime life = new MasteryEvaluator.Lifetime(1, 0);
        List<AchievementDefinition> first = MasteryEvaluator.unlocked(all, Set.of(), plain, life);
        Set<ResourceLocation> held = new java.util.HashSet<>();
        first.forEach(a -> held.add(a.id()));
        assertTrue(MasteryEvaluator.unlocked(all, held, plain, life).isEmpty());
        assertEquals(first, MasteryEvaluator.unlocked(all, Set.of(), plain, life));
        assertEquals(first.stream().map(a -> a.id().toString()).sorted().toList(), first.stream().map(a -> a.id().toString()).toList());
    }

    @Test
    @DisplayName("lifetime achievements unlock by depth alone, and clear ones never do")
    void depthOnly() throws IOException {
        List<AchievementDefinition> all = shipped();
        List<String> ids = MasteryEvaluator.unlockedByDepth(all, Set.of(), new MasteryEvaluator.Lifetime(0, 3)).stream()
                .map(a -> a.id().getPath()).toList();
        assertEquals(List.of("depth_1", "depth_3"), ids);
    }

    // ---- definitions
    // -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a bad achievement file is refused: unknown type, missing count, negative number, wrong schema")
    void refusesBadFiles() {
        assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"schema_version\":1,\"display_name\":\"X\",\"condition\":{\"type\":\"win_everything\"}}"));
        assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"schema_version\":1,\"display_name\":\"X\",\"condition\":{\"type\":\"cycles_cleared\"}}"));
        assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"schema_version\":1,\"display_name\":\"X\",\"condition\":{\"type\":\"clear\",\"max_seconds\":-5}}"));
        assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"schema_version\":2,\"display_name\":\"X\",\"condition\":{\"type\":\"clear\"}}"));
        assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"schema_version\":1,\"display_name\":\"X\",\"condition\":{\"type\":\"ascension_reached\",\"level\":0}}"));
    }

    // ---- difficulty, ranks, perks
    // ----------------------------------------------------------------------------------

    @Test
    @DisplayName("difficulty score: risk points, five per Ascension, five per missing player")
    void difficulty() {
        assertEquals(0, DifficultyScore.of(List.of(), 0, 4));
        assertEquals(15, DifficultyScore.of(List.of(), 0, 1), "a solo run with nothing");
        assertEquals(1 + 3 + 6 + 6, DifficultyScore.of(List.of(RiskTier.MINOR, RiskTier.MODERATE, RiskTier.SEVERE, RiskTier.SEVERE), 0, 4));
        assertEquals(36 + 10 + 5, DifficultyScore.of(List.of(RiskTier.SEVERE, RiskTier.SEVERE, RiskTier.SEVERE,
                RiskTier.SEVERE, RiskTier.SEVERE, RiskTier.SEVERE), 2, 3));
        assertEquals(2, DifficultyScore.severeIn(List.of(RiskTier.SEVERE, RiskTier.MINOR, RiskTier.SEVERE)));
        assertEquals(0, DifficultyScore.of(List.of(), -4, 9), "no negative contributions");
    }

    @Test
    @DisplayName("ranks change at 1, 5, 10, 15, 20, 25 and 30 and nowhere else")
    void ranks() {
        assertEquals("Unranked", MasteryPerks.rankOf(0));
        assertEquals("Bronze", MasteryPerks.rankOf(1));
        assertEquals("Bronze", MasteryPerks.rankOf(4));
        assertEquals("Silver", MasteryPerks.rankOf(5));
        assertEquals("Gold", MasteryPerks.rankOf(10));
        assertEquals("Platinum", MasteryPerks.rankOf(15));
        assertEquals("Diamond", MasteryPerks.rankOf(20));
        assertEquals("Master", MasteryPerks.rankOf(25));
        assertEquals("Champion", MasteryPerks.rankOf(30));
        assertEquals("Champion", MasteryPerks.rankOf(99), "clamped");
        assertEquals(5, MasteryPerks.nextRankAt(1));
        assertEquals(-1, MasteryPerks.nextRankAt(30));
    }

    @Test
    @DisplayName("perks are small, cumulative and never decrease with level")
    void perks() {
        assertEquals(MasteryPerks.Perks.NONE, MasteryPerks.at(0));
        assertEquals(MasteryPerks.Perks.NONE, MasteryPerks.at(4));
        assertEquals(3, MasteryPerks.at(5).vendorDiscountPercent());
        assertEquals(5, MasteryPerks.at(10).cobbleDollarBonusPercent());
        assertEquals(6, MasteryPerks.at(15).vendorDiscountPercent());
        assertEquals(10, MasteryPerks.at(25).raidPointsBonusPercent());
        assertEquals(new MasteryPerks.Perks(10, 15, 10), MasteryPerks.at(30));
        MasteryPerks.Perks last = MasteryPerks.Perks.NONE;
        for (int level = 0; level <= 30; level++) {
            MasteryPerks.Perks now = MasteryPerks.at(level);
            assertTrue(now.vendorDiscountPercent() >= last.vendorDiscountPercent());
            assertTrue(now.cobbleDollarBonusPercent() >= last.cobbleDollarBonusPercent());
            assertTrue(now.raidPointsBonusPercent() >= last.raidPointsBonusPercent());
            assertTrue(now.vendorDiscountPercent() <= 10 && now.cobbleDollarBonusPercent() <= 15, "small");
            last = now;
        }
    }
}
