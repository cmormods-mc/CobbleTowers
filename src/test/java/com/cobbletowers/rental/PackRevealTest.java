package com.cobbletowers.rental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The pack opening timing and feel (P33), checked without a client. */
class PackRevealTest {

    private static final List<String> ALL = List.of("common", "uncommon", "rare", "epic", "legendary", "mythic");

    @Test
    @DisplayName("the rarity vocabulary ranks in order, and an unknown rarity is treated as common")
    void ranks() {
        assertEquals(0, PackReveal.rank("common"));
        assertEquals(3, PackReveal.rank("epic"));
        assertEquals(5, PackReveal.rank("mythic"));
        assertEquals(0, PackReveal.rank("brand-new-rarity"));
        assertEquals(0, PackReveal.rank(null));
    }

    @Test
    @DisplayName("every rarity has its own opaque frame colour, and only mythic changes over time")
    void colours() {
        assertEquals(6, ALL.stream().map(PackReveal::color).distinct().count());
        for (String rarity : ALL) {
            assertEquals(0xFF, PackReveal.color(rarity) >>> 24);
            assertEquals(0xFF, PackReveal.colorAt(rarity, 1234) >>> 24);
        }
        assertEquals(PackReveal.colorAt("epic", 0), PackReveal.colorAt("epic", 1500));
        assertNotEquals(PackReveal.colorAt("mythic", 0), PackReveal.colorAt("mythic", 1500));
    }

    @Test
    @DisplayName("cards turn over lowest rarity first, so the best card comes last; ties keep the pack order")
    void order() {
        assertEquals(List.of(1, 3, 0, 4, 2), PackReveal.revealOrder(List.of("rare", "common", "legendary", "common", "epic")));
        assertEquals(List.of(0, 1, 2), PackReveal.revealOrder(List.of("common", "common", "common")));
    }

    @Test
    @DisplayName("a flip eases from face down to face up and stays there")
    void flip() {
        assertEquals(0f, PackReveal.flip(-5));
        assertEquals(0f, PackReveal.flip(0));
        assertEquals(1f, PackReveal.flip(PackReveal.FLIP_MS));
        assertEquals(1f, PackReveal.flip(PackReveal.FLIP_MS * 3));
        float quarter = PackReveal.flip(PackReveal.FLIP_MS / 4);
        float half = PackReveal.flip(PackReveal.FLIP_MS / 2);
        assertTrue(quarter > 0f && quarter < half && half < 1f);
        assertEquals(0.5f, half, 0.001f);
    }

    @Test
    @DisplayName("the whole reveal takes four gaps and a flip, and each card starts after the one before")
    void timing() {
        assertEquals(0, PackReveal.totalMs(0));
        assertEquals(4 * PackReveal.CARD_GAP_MS + PackReveal.FLIP_MS, PackReveal.totalMs(5));
        for (int i = 1; i < 5; i++) assertTrue(PackReveal.flipStart(i) > PackReveal.flipStart(i - 1));
    }

    @Test
    @DisplayName("only epic and better cards shake the table, sparkles start at rare, and bigger rarities bring more")
    void feel() {
        assertEquals(0f, PackReveal.shakeAmplitude("common"));
        assertEquals(0f, PackReveal.shakeAmplitude("rare"));
        assertTrue(PackReveal.shakeAmplitude("epic") < PackReveal.shakeAmplitude("legendary"));
        assertTrue(PackReveal.shakeAmplitude("legendary") < PackReveal.shakeAmplitude("mythic"));
        assertEquals(0, PackReveal.particleCount("common"));
        assertTrue(PackReveal.particleCount("rare") > 0);
        assertTrue(PackReveal.particleCount("rare") < PackReveal.particleCount("epic"));
        assertTrue(PackReveal.particleCount("epic") < PackReveal.particleCount("mythic"));
        assertEquals(0f, PackReveal.shake("legendary", 601), "it fades away completely");
        assertEquals(0f, PackReveal.shake("common", 100));
        assertEquals(0f, PackReveal.shake("legendary", -1));
        assertEquals(0f, PackReveal.tearShake(PackReveal.TEAR_MS + 1, 5));
    }
}
