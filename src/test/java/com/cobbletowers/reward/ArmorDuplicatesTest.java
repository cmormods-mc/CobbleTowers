package com.cobbletowers.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ArmorDuplicatesTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    @Test
    void aSetIsItsFourPieces() {
        assertEquals(List.of(id("tideforge_helmet"), id("tideforge_chestplate"), id("tideforge_leggings"), id("tideforge_boots")),
                ArmorDuplicates.setOf(id("tideforge_chestplate")));
        assertTrue(ArmorDuplicates.setOf(id("tower_key")).isEmpty());
        assertTrue(ArmorDuplicates.setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_helmet")).isEmpty());
    }

    @Test
    void aNewPieceIsGivenAsRolled() {
        assertEquals(id("tideforge_boots"), ArmorDuplicates.choose(id("tideforge_boots"), Set.of(id("tideforge_helmet")), 7));
    }

    @Test
    void aDuplicateBecomesAMissingPieceOfTheSameSet() {
        Set<ResourceLocation> owned = Set.of(id("tideforge_chestplate"), id("tideforge_helmet"));
        for (long seed = 0; seed < 200; seed++) {
            ResourceLocation chosen = ArmorDuplicates.choose(id("tideforge_chestplate"), owned, seed);
            assertFalse(owned.contains(chosen));
            assertTrue(ArmorDuplicates.setOf(id("tideforge_chestplate")).contains(chosen));
        }
    }

    @Test
    void theSwapRepeatsForTheSameSeed() {
        Set<ResourceLocation> owned = Set.of(id("rootvale_boots"));
        assertEquals(ArmorDuplicates.choose(id("rootvale_boots"), owned, 42), ArmorDuplicates.choose(id("rootvale_boots"), owned, 42));
    }

    @Test
    void onceTheSetIsWholeADuplicateStands() {
        Set<ResourceLocation> owned = new HashSet<>(ArmorDuplicates.setOf(id("duskvale_helmet")));
        assertEquals(id("duskvale_helmet"), ArmorDuplicates.choose(id("duskvale_helmet"), owned, 3));
    }

    @Test
    void anotherSetsPiecesDoNotCount() {
        Set<ResourceLocation> owned = Set.of(id("rootvale_chestplate"));
        assertNotEquals(null, ArmorDuplicates.choose(id("tideforge_chestplate"), owned, 1));
        assertEquals(id("tideforge_chestplate"), ArmorDuplicates.choose(id("tideforge_chestplate"), owned, 1));
    }
}
