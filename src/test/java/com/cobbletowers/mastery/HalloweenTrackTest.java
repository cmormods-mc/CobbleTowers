package com.cobbletowers.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.MasteryTrackDefinition;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HalloweenTrackTest {

    private static final ResourceLocation HALLOWEEN = ResourceLocation.parse("cobbletowers:halloween");
    private static final ResourceLocation TIDEFORGE = ResourceLocation.parse("cobbletowers:tideforge");

    @AfterEach
    void reset() {
        MasteryTracks.setLoaded(Map.of());
    }

    private static MasteryTrackDefinition shipped() throws Exception {
        return read("halloween");
    }

    private static MasteryTrackDefinition read(String name) throws Exception {
        try (Reader reader = new InputStreamReader(HalloweenTrackTest.class
                .getResourceAsStream("/data/cobbletowers/cobbletowers/mastery_tracks/" + name + ".json"), StandardCharsets.UTF_8)) {
            return MasteryTrackDefinition.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    @Test
    @DisplayName("the Halloween file has a reward at each of its 20 levels, and only the Halloween tower sees it")
    void twentyLevelsForHalloweenOnly() throws Exception {
        MasteryTrackDefinition file = shipped();
        assertEquals(HALLOWEEN, file.tower().orElseThrow());
        assertEquals(20, file.levels().size());
        for (int level = 1; level <= 20; level++) {
            final int at = level;
            assertTrue(file.levels().stream().anyMatch(l -> l.level() == at && !l.grants().isEmpty()), "grant at " + level);
        }
        MasteryTracks.setLoaded(Map.of(ResourceLocation.parse("cobbletowers:default"), read("default"),
                ResourceLocation.parse("cobbletowers:halloween"), file));
        assertEquals("Ghost Hunter", MasteryTracks.forTower(HALLOWEEN).rankOf(12));
        assertEquals("Gold", MasteryTracks.forTower(TIDEFORGE).rankOf(12));
    }

    @Test
    @DisplayName("the Halloween track grants no perks, so it cannot raise a rate")
    void noPerks() throws Exception {
        for (MasteryTrackDefinition.Level level : shipped().levels()) {
            assertTrue(level.vendorDiscountPercent().isEmpty() && level.cobbleDollarBonusPercent().isEmpty()
                    && level.raidPointsBonusPercent().isEmpty(), "perk at " + level.level());
        }
    }
}
