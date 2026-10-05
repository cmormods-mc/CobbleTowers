package com.cobbletowers.runcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunCodeTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    @Test
    @DisplayName("a code decodes to exactly the run it was made from, for any seed including negative ones")
    void roundTrip() {
        Random random = new Random(1);
        for (int i = 0; i < 500; i++) {
            long seed = random.nextLong();
            Optional<ResourceLocation> mode = i % 2 == 0 ? Optional.of(id("monotype")) : Optional.empty();
            RunCode.Decoded decoded = RunCode.decode(RunCode.encode(id("tideforge"), mode, i % 7, seed)).orElseThrow();
            assertEquals(new RunCode.Decoded(id("tideforge"), mode, i % 7, seed), decoded);
        }
    }

    @Test
    @DisplayName("codes are read case-blind and with stray whitespace")
    void forgiving() {
        String code = RunCode.encode(id("test"), Optional.empty(), 0, 123456789L);
        assertTrue(RunCode.decode("  " + code.toLowerCase() + " ").isPresent());
        assertTrue(RunCode.decode(code.toUpperCase()).isPresent());
    }

    @Test
    @DisplayName("an id with a hyphen in it survives the trip, though the hyphen is the code's own separator")
    void hyphenatedIds() {
        ResourceLocation tower = ResourceLocation.fromNamespaceAndPath("some-addon", "ice-spire");
        ResourceLocation mode = id("fast-mode");
        String code = RunCode.encode(tower, Optional.of(mode), 3, 99L);
        RunCode.Decoded decoded = RunCode.decode(code).orElseThrow();
        assertEquals(tower, decoded.tower());
        assertEquals(Optional.of(mode), decoded.playlist());
        assertEquals(99L, decoded.seed());
    }

    @Test
    @DisplayName("another namespace is kept, and a default one is left out of the text")
    void namespaces() {
        ResourceLocation other = ResourceLocation.fromNamespaceAndPath("someaddon", "spire");
        String code = RunCode.encode(other, Optional.empty(), 1, 5L);
        assertEquals(other, RunCode.decode(code).orElseThrow().tower());
        assertTrue(!RunCode.encode(id("test"), Optional.empty(), 0, 5L).contains("cobbletowers"));
    }

    @Test
    @DisplayName("a mistyped, truncated or foreign code is refused rather than becoming a different run")
    void refusesDamage() {
        String code = RunCode.encode(id("tideforge"), Optional.empty(), 2, 987654321012345L);
        int refused = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char swapped = c == 'x' ? 'y' : 'x';
            if (c == '-' || c == swapped) continue;
            String damaged = code.substring(0, i) + swapped + code.substring(i + 1);
            if (RunCode.decode(damaged).isEmpty()) refused++;
        }
        assertTrue(refused > code.length() * 0.8, "most single-character slips are caught, got " + refused);
        assertTrue(RunCode.decode(code.substring(0, code.length() - 3)).isEmpty());
        assertTrue(RunCode.decode("hello").isEmpty());
        assertTrue(RunCode.decode("").isEmpty());
        assertTrue(RunCode.decode(null).isEmpty());
        assertTrue(RunCode.decode("XX1-test-std-0-5-0").isEmpty());
    }
}
