package com.cobbletowers.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RewardTableDefinition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompletionExtrasTest {

    private static RewardTableDefinition table(String tower) {
        var stream = CompletionExtrasTest.class.getResourceAsStream("/data/cobbletowers/cobbletowers/reward_tables/" + tower + ".json");
        JsonObject root = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
        return RewardTableDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("cobbletowers", tower), root);
    }

    @Test
    @org.junit.jupiter.api.Disabled("armor sets are archived (archive/armor-sets)")
    @DisplayName("armor and booster packs come from the tower's own boss table, the same for a retry")
    void picksFromTheTowersTableAndIsStable() {
        var table = table("tideforge");
        var encounter = UUID.randomUUID();
        var player = UUID.randomUUID();
        var first = CompletionExtras.pick(table, encounter, player, 2, 1);
        assertEquals(first, CompletionExtras.pick(table, encounter, player, 2, 1));
        assertEquals(3, first.size());
        assertTrue(CompletionExtras.isArmor(first.get(0)) && CompletionExtras.isArmor(first.get(1)));
        assertTrue(CompletionExtras.isTicket(first.get(2)));
        assertEquals(2, new HashSet<>(first.subList(0, 2)).size(), "two pieces are never the same piece");
    }

    @Test
    @DisplayName("asking for nothing picks nothing, and a table with no such items picks fewer")
    void nothingAskedNothingPicked() {
        assertEquals(List.of(), CompletionExtras.pick(table("tideforge"), UUID.randomUUID(), UUID.randomUUID(), 0, 0));
    }

    @Test
    @org.junit.jupiter.api.Disabled("armor sets are archived (archive/armor-sets)")
    @DisplayName("every shipped tower can pay armor and a pack from a completion")
    void everyTowerOffersBoth() {
        for (String tower : List.of("tideforge", "rootvale", "duskvale")) {
            var picked = CompletionExtras.pick(table(tower), UUID.randomUUID(), UUID.randomUUID(), 1, 1);
            assertEquals(2, picked.size(), tower);
        }
    }
}
