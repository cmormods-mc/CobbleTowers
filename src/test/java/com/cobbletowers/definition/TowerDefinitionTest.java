package com.cobbletowers.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.api.tower.MilestoneKind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Parsing, and every rule that makes a definition invalid. */
class TowerDefinitionTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "test");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    @DisplayName("a tower reads its floors in order and its reserved theme")
    void towerParses() {
        TowerDefinition tower = TowerDefinition.fromJson(ID, json("""
                {"schema_version": 1, "revision": 3, "display_name": "Neutral",
                 "ruleset": "cobbletowers:standard", "reward_table": "cobbletowers:standard",
                 "floors": ["cobbletowers:f1", "cobbletowers:f2"],
                 "milestones": ["cobbletowers:m5"],
                 "regional_theme": "cobbletowers:tideforge"}"""));

        assertEquals("Neutral", tower.displayName());
        assertEquals(2, tower.floorCount());
        assertEquals(ResourceLocation.fromNamespaceAndPath("cobbletowers", "f1"), tower.floorIds().get(0));
        assertEquals(ResourceLocation.fromNamespaceAndPath("cobbletowers", "standard"), tower.rewardTableId());
        assertEquals(Optional.of(ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge")),
                tower.regionalTheme(), "a reserved field is carried, not resolved");
    }

    @Test
    @DisplayName("a tower is refused when it is empty, repeats a floor, or comes from a newer schema")
    void towerValidation() {
        assertThrows(IllegalArgumentException.class, () -> TowerDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "x", "ruleset": "a:b", "reward_table": "a:b",
                 "floors": []}""")));
        assertThrows(IllegalArgumentException.class, () -> TowerDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "x", "ruleset": "a:b", "reward_table": "a:b",
                 "floors": ["a:f1", "a:f1"]}""")));
        assertThrows(IllegalArgumentException.class, () -> TowerDefinition.fromJson(ID, json("""
                {"schema_version": 2, "display_name": "x", "ruleset": "a:b", "reward_table": "a:b",
                 "floors": ["a:f1"]}""")));
        assertThrows(IllegalArgumentException.class, () -> TowerDefinition.fromJson(ID, json("""
                {"schema_version": 1, "display_name": "  ", "ruleset": "a:b", "reward_table": "a:b",
                 "floors": ["a:f1"]}""")));
    }

    @Test
    @DisplayName("a missing required field names itself")
    void missingFieldMessage() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> TowerDefinition.fromJson(ID, json("""
                        {"schema_version": 1, "display_name": "x", "floors": ["a:f1"]}""")));
        assertTrue(thrown.getMessage().contains("ruleset"), thrown.getMessage());

        IllegalArgumentException rewardTableMissing = assertThrows(IllegalArgumentException.class,
                () -> TowerDefinition.fromJson(ID, json("""
                        {"schema_version": 1, "display_name": "x", "ruleset": "a:b",
                         "floors": ["a:f1"]}""")));
        assertTrue(rewardTableMissing.getMessage().contains("reward_table"), rewardTableMissing.getMessage());
    }

    @Test
    @DisplayName("a floor reads its pool and milestone mark")
    void floorParses() {
        FloorDefinition floor = FloorDefinition.fromJson(ID, json("""
                {"index": 5, "encounter_pool": "cobbletowers:neutral_common", "milestone": "boss",
                 "modifiers": ["cobbletowers:double_trouble"]}"""));

        assertEquals(5, floor.index());
        assertEquals(Optional.of(MilestoneKind.BOSS), floor.milestone());
        assertEquals(1, floor.modifierIds().size(), "a reserved modifier id is carried");
    }

    @Test
    @DisplayName("a floor is refused for a bad index, an unknown milestone word or an unparseable id")
    void floorValidation() {
        assertThrows(IllegalArgumentException.class, () -> FloorDefinition.fromJson(ID, json("""
                {"index": 0, "encounter_pool": "a:b"}""")));
        assertThrows(IllegalArgumentException.class, () -> FloorDefinition.fromJson(ID, json("""
                {"index": 1, "encounter_pool": "a:b", "milestone": "finale"}""")));
        assertThrows(IllegalArgumentException.class, () -> FloorDefinition.fromJson(ID, json("""
                {"index": 1, "encounter_pool": "NOT AN ID"}""")));
    }

    @Test
    @DisplayName("an encounter pool weights its entries")
    void poolParses() {
        EncounterPoolDefinition pool = EncounterPoolDefinition.fromJson(ID, json("""
                {"schema_version": 1, "entries": [
                   {"species": "cobblemon:machoke", "weight": 100},
                   {"species": "cobblemon:lucario", "weight": 40, "level_offset": 3,
                    "aspects": ["shiny"]}]}"""));

        assertEquals(2, pool.entries().size());
        assertEquals(140, pool.totalWeight());
        assertEquals(3, pool.entries().get(1).levelOffset());
        assertEquals("shiny", pool.entries().get(1).aspects().get(0));
    }

    @Test
    @DisplayName("an encounter pool is refused when empty or weighted at zero")
    void poolValidation() {
        assertThrows(IllegalArgumentException.class, () -> EncounterPoolDefinition.fromJson(ID, json("""
                {"schema_version": 1, "entries": []}""")));
        assertThrows(IllegalArgumentException.class, () -> EncounterPoolDefinition.fromJson(ID, json("""
                {"schema_version": 1, "entries": [{"species": "a:b", "weight": 0}]}""")));
    }

    @Test
    @DisplayName("a ruleset defaults to the tower's own rules and refuses impossible bounds")
    void rulesetParsesAndValidates() {
        RulesetDefinition ruleset = RulesetDefinition.fromJson(ID, json("""
                {"schema_version": 1, "enemy_level": {"min": 5, "max": 80}}"""));

        assertEquals(5, ruleset.minEnemyLevel());
        assertEquals(80, ruleset.maxEnemyLevel());
        assertEquals(6, ruleset.registeredPartySize());
        assertTrue(ruleset.carriesHealthBetweenFloors(), "healing is bought, not given");

        assertThrows(IllegalArgumentException.class, () -> RulesetDefinition.fromJson(ID, json("""
                {"schema_version": 1, "enemy_level": {"min": 50, "max": 10}}""")));
        assertThrows(IllegalArgumentException.class, () -> RulesetDefinition.fromJson(ID, json("""
                {"schema_version": 1, "registered_party_size": 7}""")));
    }

    @Test
    @DisplayName("every milestone must name the raid definition its floor is finished by")
    void milestoneValidation() {
        // Both kinds now, not only BOSS. Every floor ends in a CobbleRaids boss, and a milestone floor
        // takes the one named here instead of drawing from a pool -- so a milestone without a
        // definition is a floor nobody can complete. The shipped champion on floor 10 was in exactly
        // that state, and the new definition check caught it the first time it ran.
        assertThrows(IllegalArgumentException.class, () -> MilestoneDefinition.fromJson(ID, json("""
                {"floor": 10, "kind": "champion"}""")));

        MilestoneDefinition champion = MilestoneDefinition.fromJson(ID, json("""
                {"floor": 10, "kind": "champion", "raid_definition": "cobbleraids:arceus"}"""));
        assertEquals(MilestoneKind.CHAMPION, champion.kind());
        assertEquals(ResourceLocation.fromNamespaceAndPath("cobbleraids", "arceus"),
                champion.raidDefinitionId().orElseThrow());

        assertThrows(IllegalArgumentException.class, () -> MilestoneDefinition.fromJson(ID, json("""
                {"floor": 5, "kind": "boss"}""")));
        assertEquals(ResourceLocation.fromNamespaceAndPath("cobbleraids", "lucario"),
                MilestoneDefinition.fromJson(ID, json("""
                        {"floor": 5, "kind": "boss", "raid_definition": "cobbleraids:lucario"}"""))
                        .raidDefinitionId().orElseThrow());
    }
}
