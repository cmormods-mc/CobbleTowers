package com.cobbletowers.armor.flare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlarePlanTest {

    /** Every particle id the service understands: a typo here would otherwise only show up as a skipped particle on a live server. */
    private static final Set<String> KNOWN = Set.of("cloud", "bubble", "bubble_pop", "electric_spark", "block:azalea_leaves",
            "happy_villager", "end_rod", "dust_transition", "soul");

    @Test
    void chestplateIdsMapToTheirSet() {
        assertEquals("tideforge", FlarePlan.setOf("cobbletowers", "tideforge_chestplate"));
        assertEquals("rootvale", FlarePlan.setOf("cobbletowers", "rootvale_chestplate"));
        assertEquals("duskvale", FlarePlan.setOf("cobbletowers", "duskvale_chestplate"));
        assertNull(FlarePlan.setOf("cobbletowers", "tideforge_helmet"), "only the chestplate carries the back attachment");
        assertNull(FlarePlan.setOf("cobbletowers", "challenger_chestplate"), "sets without an attachment get no flare");
        assertNull(FlarePlan.setOf("minecraft", "tideforge_chestplate"));
    }

    @Test
    void everySetEmitsAndStaysWithinBudget() {
        for (String set : FlarePlan.SETS) {
            for (boolean water : new boolean[] {false, true}) {
                for (boolean sprint : new boolean[] {false, true}) {
                    int total = 0;
                    for (long pass = 0; pass < 600; pass++) {
                        List<FlarePlan.Emit> emits = FlarePlan.at(set, pass, 7, water, sprint);
                        assertTrue(emits.size() <= 4, set + " emits too much in one pass: " + emits.size());
                        total += emits.size();
                        for (FlarePlan.Emit e : emits) {
                            assertTrue(KNOWN.contains(e.particle()), set + " uses an unknown particle " + e.particle());
                            assertTrue(Math.abs(e.right()) <= 1.0 && e.up() >= 0 && e.up() <= 2.4 && e.back() >= 0 && e.back() <= 0.8,
                                    set + " places a particle off the body: " + e);
                            assertTrue(e.count() >= 0 && e.count() <= 3, set + " count " + e.count());
                        }
                    }
                    assertTrue(total > 40, set + " (water=" + water + ", sprint=" + sprint + ") barely emits: " + total);
                    assertTrue(total < 600 * 2.2, set + " is too busy: " + total + " emits in 600 passes");
                }
            }
        }
    }

    @Test
    void unknownSetEmitsNothing() {
        assertTrue(FlarePlan.at("challenger", 12, 0, false, false).isEmpty());
    }

    @Test
    void tideforgeBubblesOnlyUnderWater() {
        boolean wet = false;
        for (long pass = 0; pass < 120; pass++) {
            for (FlarePlan.Emit e : FlarePlan.at("tideforge", pass, 0, false, true)) {
                assertFalse(e.particle().equals("bubble"), "plain bubbles die out of water; use puffs on land");
            }
            for (FlarePlan.Emit e : FlarePlan.at("tideforge", pass, 0, true, false)) wet |= e.particle().equals("bubble");
        }
        assertTrue(wet, "under water the valves release bubbles");
    }

    @Test
    void phasesStaggerWearersAndAreStablePerPlayer() {
        Set<Long> a = new HashSet<>();
        Set<Long> b = new HashSet<>();
        for (long pass = 0; pass < 60; pass++) {
            if (!FlarePlan.at("duskvale", pass, 0, false, false).isEmpty()) a.add(pass);
            if (!FlarePlan.at("duskvale", pass, 5, false, false).isEmpty()) b.add(pass);
        }
        assertFalse(a.equals(b), "two wearers must not pulse in step");
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertEquals(FlarePlan.phaseOf(id), FlarePlan.phaseOf(id));
        assertTrue(FlarePlan.phaseOf(id) >= 0 && FlarePlan.phaseOf(id) < 24);
    }

    @Test
    void configDefaultsParseAndRejectNonsense() {
        assertEquals(FlareConfig.DEFAULT, FlareConfig.parse("{}"));
        assertEquals(new FlareConfig(false, 8), FlareConfig.parse("{\"flares\":{\"enabled\":false,\"max_wearers\":8}}"));
        assertEquals(FlareConfig.DEFAULT, FlareConfig.parse("{\"enabled\":true}"), "the worn-parts keys are not ours");
        assertThrows(IllegalArgumentException.class, () -> FlareConfig.parse("{\"flares\":{\"max_wearers\":0}}"));
        assertThrows(IllegalArgumentException.class, () -> FlareConfig.parse("{\"flares\":{\"max_wearers\":500}}"));
    }
}
