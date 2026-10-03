package com.cobbletowers.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RewardTableDefinition;
import com.cobbletowers.reward.RewardCatalogCheck.Problem;
import com.cobbletowers.reward.RewardCatalogCheck.Severity;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The startup check that stops a typo, or a missing optional mod, silently costing players a drop. */
class RewardCatalogCheckTest {

    private static RewardTableDefinition table(String json) {
        return RewardTableDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("cobbletowers", "t"),
                JsonParser.parseString(json).getAsJsonObject());
    }

    private static final Set<String> REAL = Set.of("minecraft:diamond", "cobblemon:potion");
    private static final Set<String> LOADED = Set.of("minecraft", "cobblemon");

    private static List<Problem> check(String json) {
        return RewardCatalogCheck.check(List.of(table(json)), id -> REAL.contains(id.toString()), LOADED::contains);
    }

    @Test
    @DisplayName("items that exist raise nothing")
    void cleanTable() {
        assertEquals(List.of(), check("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"floor_cleared": [{"item": "minecraft:diamond"}, {"item": "cobblemon:potion"}]}}"""));
    }

    @Test
    @DisplayName("a missing item in a namespace whose mod is loaded is an error: that is a typo")
    void typoIsAnError() {
        List<Problem> problems = check("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"floor_cleared": [{"item": "cobblemon:potoin"}]}}""");

        assertEquals(1, problems.size());
        assertEquals(Severity.ERROR, problems.get(0).severity());
        assertEquals("tiers.floor_cleared", problems.get(0).where());
    }

    @Test
    @DisplayName("a missing item from a mod that is not installed is only a note: an optional dependency")
    void absentModIsANote() {
        List<Problem> problems = check("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"boss_defeated": [{"item": "cobblemon-cards:booster_pack"}]}}""");

        assertEquals(1, problems.size());
        assertEquals(Severity.NOTE, problems.get(0).severity());
    }

    @Test
    @DisplayName("the two reserved currency ids are never flagged: they are credited, not given")
    void currenciesAreNeverFlagged() {
        assertEquals(List.of(), check("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"opponent_defeated": [{"item": "cobbletowers:cobble_dollar"}, {"item": "cobbleraids:raid_points"}]}}"""));
    }

    @Test
    @DisplayName("milestone guaranteed items and bonus pools are checked too, and say where")
    void milestonesAreChecked() {
        List<Problem> problems = check("""
                {"schema_version": 1, "display_name": "x", "tiers": {},
                 "milestones": {"champion": {
                    "guaranteed": [{"item": "cobblemon:exp_candy_xxl"}],
                    "bonus_rolls": 1, "bonus_pool": [{"item": "minecraft:diamnod"}]}}}""");

        assertEquals(2, problems.size());
        assertTrue(problems.stream().anyMatch(p -> p.where().equals("milestones.champion.guaranteed")));
        assertTrue(problems.stream().anyMatch(p -> p.where().equals("milestones.champion.bonus_pool")));
    }
}
