package com.cobbletowers.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.api.reward.RewardKind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Parsing a reward table, and every rule that makes one invalid. */
class RewardTableDefinitionTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "test");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    @DisplayName("a table reads each tier it is given")
    void parses() {
        RewardTableDefinition table = RewardTableDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "Rewards", "growth_percent_per_floor": 10,
                 "tiers": {
                   "opponent_defeated": [{"item": "minecraft:stone", "min_amount": 1, "max_amount": 3, "weight": 100}],
                   "boss_defeated": [{"item": "minecraft:diamond", "weight": 50}]
                 }}"""));

        assertEquals("Rewards", table.displayName());
        assertEquals(10, table.growthPercentPerFloor());
        assertEquals(1, table.entriesFor(RewardKind.OPPONENT_DEFEATED).size());
        assertEquals(3, table.entriesFor(RewardKind.OPPONENT_DEFEATED).get(0).maxAmount());
        assertEquals(1, table.entriesFor(RewardKind.BOSS_DEFEATED).size());
    }

    @Test
    @DisplayName("a tier nothing was written for is empty, not required")
    void absentTierIsEmpty() {
        RewardTableDefinition table = RewardTableDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "Rewards"}"""));

        assertEquals(List.of(), table.tier(RewardKind.OPPONENT_DEFEATED));
        assertEquals(List.of(), table.tier(RewardKind.BOSS_DEFEATED));
        assertEquals(List.of(), table.tier(RewardKind.FLOOR_CLEARED));
        assertEquals(0, table.growthPercentPerFloor());
    }

    @Test
    @DisplayName("a table is refused for a blank name or a newer schema")
    void tableValidation() {
        assertThrows(IllegalArgumentException.class, () -> RewardTableDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "  "}""")));
        assertThrows(IllegalArgumentException.class, () -> RewardTableDefinition.fromJson(ID, json("""
                {"schema_version": 2, "display_name": "x"}""")));
    }

    @Test
    @DisplayName("an entry is refused for a non-positive weight or an amount range out of order")
    void entryValidation() {
        assertThrows(IllegalArgumentException.class, () -> RewardTableDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"opponent_defeated": [{"item": "minecraft:stone", "weight": 0}]}}""")));
        assertThrows(IllegalArgumentException.class, () -> RewardTableDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"opponent_defeated": [{"item": "minecraft:stone", "min_amount": 5, "max_amount": 2}]}}""")));
    }

    @Test
    @DisplayName("a missing required field names itself")
    void missingFieldMessage() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> RewardTableDefinition.fromJson(ID, json("{\"schema_version\": 1}")));
        assertTrue(thrown.getMessage().contains("display_name"), thrown.getMessage());
    }
}
