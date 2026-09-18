package com.cobbletowers.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.EncounterPoolDefinition;
import com.cobbletowers.definition.RulesetDefinition;
import com.google.gson.JsonParser;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What TDS #29 actually claims: a crash cannot reroll an encounter.
 *
 * <p>Which means the draw has to be a function of the run, the floor and the ordinal, and of nothing
 * else -- no clock, no random source, no stored roll to lose.
 */
class EncounterDrawTest {

    private static final long RUN_SEED = -8_123_456_789_012_345L;
    private static final List<Integer> PARTY = List.of(48, 52, 50, 50, 46, 54);  // mean 50

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    private static EncounterPoolDefinition pool(String entries) {
        return EncounterPoolDefinition.fromJson(id("pool"), JsonParser.parseString(
                "{\"schema_version\":1,\"revision\":1,\"entries\":" + entries + "}").getAsJsonObject());
    }

    private static EncounterPoolDefinition shippedShape() {
        return pool("""
                [{"species":"cobblemon:machoke","weight":100},
                 {"species":"cobblemon:haunter","weight":100},
                 {"species":"cobblemon:lairon","weight":80},
                 {"species":"cobblemon:staraptor","weight":60,"level_offset":2}]""");
    }

    private static RulesetDefinition ruleset() {
        return RulesetDefinition.fromJson(id("standard"), JsonParser.parseString(
                "{\"schema_version\":1,\"revision\":1,\"enemy_level\":{\"min\":5,\"max\":90}}").getAsJsonObject());
    }

    @Test
    @DisplayName("the same run and floor always draw the same opponents, however often it is asked")
    void deterministic() {
        List<EncounterSnapshot> first =
                EncounterDraw.drawRound(shippedShape(), RUN_SEED, 3, 4, PARTY, ruleset());
        List<EncounterSnapshot> again =
                EncounterDraw.drawRound(shippedShape(), RUN_SEED, 3, 4, PARTY, ruleset());

        assertEquals(first, again, "a resumed run must meet the opponents it left, not new ones");
        assertEquals(4, first.size());
    }

    @Test
    @DisplayName("a different floor, ordinal or run draws differently")
    void differentInputsDiffer() {
        EncounterSnapshot base = EncounterDraw.draw(shippedShape(), RUN_SEED, 3, 0, PARTY, ruleset()).orElseThrow();

        // Mixed rather than added, so floor 2 opponent 3 is not floor 3 opponent 2 -- the pattern a
        // player would notice first.
        assertNotEquals(EncounterSeed.of(RUN_SEED, 2, 3), EncounterSeed.of(RUN_SEED, 3, 2));
        assertNotEquals(EncounterSeed.of(RUN_SEED, 3, 0), EncounterSeed.of(RUN_SEED + 1, 3, 0));
        assertNotEquals(base.level(),
                EncounterDraw.draw(shippedShape(), RUN_SEED, 9, 0, PARTY, ruleset()).orElseThrow().level(),
                "a later floor is a harder floor");
    }

    @Test
    @DisplayName("weights are respected across many seeds")
    void weighted() {
        // 100/100/80/60: machoke and haunter should come up appreciably more often than staraptor.
        EncounterPoolDefinition pool = shippedShape();
        Map<String, Integer> counts = new HashMap<>();
        for (int seed = 0; seed < 3400; seed++) {
            String species = EncounterDraw.pick(pool, EncounterSeed.of(seed, 1, 0)).species().getPath();
            counts.merge(species, 1, Integer::sum);
        }

        assertEquals(4, counts.size(), "every entry should be reachable: " + counts);
        assertTrue(counts.get("machoke") > counts.get("staraptor"),
                "the heavier entry should win more often: " + counts);
        // Not an exact ratio: this asserts the weighting works, not that the mixer is a good RNG.
        assertTrue(counts.get("staraptor") > 200, "the lightest entry is still common enough: " + counts);
    }

    @Test
    @DisplayName("the level comes from the party, the floor and the entry, and stays inside the ruleset")
    void levels() {
        RulesetDefinition ruleset = ruleset();

        // Party mean 50, floor 3 adds 3, staraptor's own offset adds 2.
        assertEquals(53, TowerLevelPolicy.levelFor(PARTY, 3, 0, ruleset).orElseThrow());
        assertEquals(55, TowerLevelPolicy.levelFor(PARTY, 3, 2, ruleset).orElseThrow());

        // The ruleset's ceiling holds even when the party is far above it.
        assertEquals(90, TowerLevelPolicy.levelFor(List.of(100, 100, 100), 10, 5, ruleset).orElseThrow());
        assertEquals(5, TowerLevelPolicy.levelFor(List.of(1, 1), 1, -50, ruleset).orElseThrow(),
                "and its floor holds when an offset would push below it");
    }

    @Test
    @DisplayName("a floor with nobody registered draws nothing rather than guessing a level")
    void noParty() {
        assertEquals(Optional.empty(),
                EncounterDraw.draw(shippedShape(), RUN_SEED, 1, 0, List.of(), ruleset()));
        assertEquals(List.of(),
                EncounterDraw.drawRound(shippedShape(), RUN_SEED, 1, 2, Collections.emptyList(), ruleset()));
    }

    @Test
    @DisplayName("editing a pool changes what a floor draws, which is what the pinned digest is for")
    void editingAPoolChangesDraws() {
        // Written first as "appending an entry leaves earlier draws alone", which is not achievable:
        // the roll is taken modulo the pool's total weight, so new weight shifts every roll, and any
        // weighted pick must take probability from somewhere to give it to a new entry.
        //
        // The real protection is elsewhere and already built: a run pins the content digest it
        // started with (TDS #40), so a pool edited under an in-flight run is detectable rather than
        // silent. This pins the actual behaviour so nobody re-derives the wrong expectation.
        EncounterPoolDefinition before = pool("""
                [{"species":"cobblemon:machoke","weight":100},
                 {"species":"cobblemon:haunter","weight":100}]""");
        EncounterPoolDefinition after = pool("""
                [{"species":"cobblemon:machoke","weight":100},
                 {"species":"cobblemon:haunter","weight":100},
                 {"species":"cobblemon:lairon","weight":1}]""");

        int moved = 0;
        for (int seed = 0; seed < 400; seed++) {
            long mixed = EncounterSeed.of(seed, 1, 0);
            if (!EncounterDraw.pick(before, mixed).species().equals(EncounterDraw.pick(after, mixed).species())) {
                moved++;
            }
        }
        assertTrue(moved > 0, "an edited pool draws differently; the digest is what makes that visible");

        // What does hold: the same pool and seed always give the same entry.
        for (int seed = 0; seed < 50; seed++) {
            long mixed = EncounterSeed.of(seed, 1, 0);
            assertEquals(EncounterDraw.pick(before, mixed), EncounterDraw.pick(before, mixed));
        }
    }

    @Test
    @DisplayName("a snapshot renders the properties Cobblemon parses")
    void properties() {
        EncounterSnapshot plain = new EncounterSnapshot(0, id("machoke"), List.of(), 42);
        assertEquals("cobbletowers:machoke level=42", plain.toProperties());

        EncounterSnapshot aspected = new EncounterSnapshot(1, id("vulpix"), List.of("alolan"), 30);
        assertEquals("cobbletowers:vulpix level=30 alolan", aspected.toProperties());
    }
}
