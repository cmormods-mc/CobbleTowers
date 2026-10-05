package com.cobbletowers.battle.cobbleraids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RaidSpeciesTest {

    @Test
    @DisplayName("the species field of a raid definition is read")
    void readsSpecies() {
        assertEquals(Optional.of("cobblemon:arceus"),
                RaidSpecies.speciesOf(JsonParser.parseString("{\"species\": \"cobblemon:arceus\", \"level\": 70}")));
    }

    @Test
    @DisplayName("a definition without a usable species is simply unscoutable")
    void missingSpecies() {
        assertTrue(RaidSpecies.speciesOf(JsonParser.parseString("{}")).isEmpty());
        assertTrue(RaidSpecies.speciesOf(JsonParser.parseString("{\"species\": \"\"}")).isEmpty());
        assertTrue(RaidSpecies.speciesOf(JsonParser.parseString("{\"species\": {\"a\": 1}}")).isEmpty());
        assertTrue(RaidSpecies.speciesOf(JsonParser.parseString("[1]")).isEmpty());
    }
}
