package com.cobbletowers.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobbletowers.definition.EncounterPoolDefinition;
import com.cobbletowers.definition.RegionalThemeDefinition;
import com.google.gson.JsonParser;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The one place a jersey entry's weight rises with floor depth (TDS #73). */
class RegionalWeightingTest {

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static EncounterPoolDefinition pool() {
        return EncounterPoolDefinition.fromJson(id("cobbletowers", "pool"), JsonParser.parseString("""
                {"schema_version":1,"entries":[
                  {"species":"cobblemon:gyarados","weight":100},
                  {"species":"cobblemon:magikarp","weight":100}]}""").getAsJsonObject());
    }

    private static RegionalThemeDefinition theme(int growthPercentPerFloor) {
        return RegionalThemeDefinition.fromJson(id("cobbletowers", "tideforge"), JsonParser.parseString("""
                {"schema_version":1,"display_name":"Tideforge","doctrine":"Momentum",
                 "jersey_weight_growth_percent_per_floor":%d,
                 "jersey_signatures":[
                   {"species":"cobblemon:gyarados"},{"species":"cobblemon:lanturn"},
                   {"species":"cobblemon:kingdra"},{"species":"cobblemon:empoleon"},
                   {"species":"cobblemon:milotic"}]}""".formatted(growthPercentPerFloor)).getAsJsonObject());
    }

    @Test
    @DisplayName("no theme leaves every weight exactly as authored")
    void noThemeIsANoOp() {
        EncounterPoolDefinition pool = pool();
        for (EncounterPoolDefinition.Entry entry : pool.entries()) {
            assertEquals(entry.weight(), RegionalWeighting.weightFor(entry, Optional.empty(), 7));
        }
        assertEquals(pool.totalWeight(), RegionalWeighting.totalWeight(pool, Optional.empty(), 7));
    }

    @Test
    @DisplayName("a pool naming a theme with zero growth also leaves weights untouched")
    void zeroGrowthIsANoOp() {
        EncounterPoolDefinition pool = pool();
        Optional<RegionalThemeDefinition> theme = Optional.of(theme(0));
        for (EncounterPoolDefinition.Entry entry : pool.entries()) {
            assertEquals(entry.weight(), RegionalWeighting.weightFor(entry, theme, 9));
        }
    }

    @Test
    @DisplayName("a jersey entry's weight rises with floor depth; a non-jersey entry's does not")
    void jerseyWeightRisesWithDepth() {
        EncounterPoolDefinition pool = pool();
        EncounterPoolDefinition.Entry jersey = pool.entries().get(0);   // gyarados: a jersey signature
        EncounterPoolDefinition.Entry plain = pool.entries().get(1);    // magikarp: not one
        Optional<RegionalThemeDefinition> theme = Optional.of(theme(20));

        assertEquals(100, RegionalWeighting.weightFor(jersey, theme, 0));
        assertEquals(120, RegionalWeighting.weightFor(jersey, theme, 1));
        assertEquals(180, RegionalWeighting.weightFor(jersey, theme, 4));

        assertEquals(100, RegionalWeighting.weightFor(plain, theme, 0));
        assertEquals(100, RegionalWeighting.weightFor(plain, theme, 1));
        assertEquals(100, RegionalWeighting.weightFor(plain, theme, 4));
    }

    @Test
    @DisplayName("the adjusted total is the sum a themed draw actually divides by")
    void totalWeightReflectsTheAdjustment() {
        EncounterPoolDefinition pool = pool();
        Optional<RegionalThemeDefinition> theme = Optional.of(theme(20));

        // gyarados 100 -> 120 (jersey, floor 1), magikarp stays 100.
        assertEquals(220, RegionalWeighting.totalWeight(pool, theme, 1));
    }
}
