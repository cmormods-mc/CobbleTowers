package com.cobbletowers.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.BossPoolDefinition;
import com.cobbletowers.definition.RulesetDefinition;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which boss finishes a floor, and the one thing it must not be: predictable from the trash. */
class BossDrawTest {

    private static final long RUN_SEED = 4_815_162_342L;
    private static final List<Integer> PARTY = List.of(48, 52, 50, 50, 46, 54);  // mean 50

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static BossPoolDefinition pool() {
        return BossPoolDefinition.fromJson(id("cobbletowers", "early"), JsonParser.parseString("""
                {"schema_version":1,"revision":1,"entries":[
                  {"definition":"cobbleraids:decidueye","weight":100},
                  {"definition":"cobbleraids:blastoise","weight":100},
                  {"definition":"cobbleraids:charizard","weight":80},
                  {"definition":"cobbleraids:chesnaught","weight":80,"level_offset":3}]}""").getAsJsonObject());
    }

    private static RulesetDefinition ruleset() {
        return RulesetDefinition.fromJson(id("cobbletowers", "standard"), JsonParser.parseString(
                "{\"schema_version\":1,\"revision\":1,\"enemy_level\":{\"min\":5,\"max\":90}}").getAsJsonObject());
    }

    @Test
    @DisplayName("the same run and floor always meet the same boss")
    void deterministic() {
        BossDraw.Boss first = BossDraw.draw(Optional.of(pool()), Optional.empty(),
                RUN_SEED, 3, PARTY, ruleset()).orElseThrow();
        BossDraw.Boss again = BossDraw.draw(Optional.of(pool()), Optional.empty(),
                RUN_SEED, 3, PARTY, ruleset()).orElseThrow();

        assertEquals(first, again, "a resumed run must face the boss it left");
    }

    @Test
    @DisplayName("the boss cannot be read off the floor's first ordinary opponent")
    void notCorrelatedWithTheTrash() {
        // If the boss were simply ordinal zero of the same sequence, a party could look at what they
        // fought first and know what was waiting at the end. Different runs must disagree about the
        // two independently.
        int agreements = 0;
        for (long seed = 0; seed < 300; seed++) {
            String boss = BossDraw.pick(pool(), EncounterSeed.of(seed, 1, 1_000_003)).definition().getPath();
            String firstOpponent = BossDraw.pick(pool(), EncounterSeed.of(seed, 1, 0)).definition().getPath();
            if (boss.equals(firstOpponent)) agreements++;
        }
        // With four entries, chance alone pairs them about a quarter of the time; a correlated draw
        // would pair them every time.
        assertTrue(agreements < 200, "the boss tracks the first opponent too closely: " + agreements + "/300");
    }

    @Test
    @DisplayName("a milestone floor takes its handpicked boss and draws nothing")
    void milestoneBossWins() {
        ResourceLocation handpicked = id("cobbleraids", "lucario");

        BossDraw.Boss boss = BossDraw.draw(Optional.of(pool()), Optional.of(handpicked),
                RUN_SEED, 5, PARTY, ruleset()).orElseThrow();

        assertEquals(handpicked, boss.definition(),
                "floors 5 and 10 are special through content, which is exactly this");
    }

    @Test
    @DisplayName("a milestone boss is used even when the floor has no pool at all")
    void milestoneNeedsNoPool() {
        BossDraw.Boss boss = BossDraw.draw(Optional.empty(), Optional.of(id("cobbleraids", "arceus")),
                RUN_SEED, 10, PARTY, ruleset()).orElseThrow();

        assertEquals(id("cobbleraids", "arceus"), boss.definition());
    }

    @Test
    @DisplayName("a floor with neither a pool nor a milestone boss draws nothing, rather than guessing")
    void noBossAtAll() {
        assertEquals(Optional.empty(),
                BossDraw.draw(Optional.empty(), Optional.empty(), RUN_SEED, 1, PARTY, ruleset()));
    }

    @Test
    @DisplayName("the boss level comes from the same policy as everything else on the floor")
    void levelsMatchThePolicy() {
        // Party mean 50 plus the floor's step: the same arithmetic the ordinary opponents use, which
        // is the point of there being one policy class (TDS #45).
        BossDraw.Boss boss = BossDraw.draw(Optional.of(pool()), Optional.of(id("cobbleraids", "lucario")),
                RUN_SEED, 4, PARTY, ruleset()).orElseThrow();

        assertEquals(TowerLevelPolicy.levelFor(PARTY, 4, 0, ruleset()).orElseThrow(), boss.level());
        assertTrue(boss.level() <= 90, "and the ruleset's ceiling still holds");
    }

    @Test
    @DisplayName("weights are respected across many seeds")
    void weighted() {
        Map<String, Integer> counts = new HashMap<>();
        for (int seed = 0; seed < 2000; seed++) {
            counts.merge(BossDraw.pick(pool(), EncounterSeed.of(seed, 1, 1_000_003)).definition().getPath(),
                    1, Integer::sum);
        }

        assertEquals(4, counts.size(), "every boss should be reachable: " + counts);
        assertTrue(counts.get("decidueye") > counts.get("chesnaught"),
                "the heavier entry should come up more often: " + counts);
    }

    @Test
    @DisplayName("a different floor faces a different boss")
    void floorsDiffer() {
        assertNotEquals(EncounterSeed.of(RUN_SEED, 1, 1_000_003), EncounterSeed.of(RUN_SEED, 2, 1_000_003));
    }
}
