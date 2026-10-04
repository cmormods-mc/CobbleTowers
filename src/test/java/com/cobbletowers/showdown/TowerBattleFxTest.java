package com.cobbletowers.showdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TowerBattleFxTest {

    private static JsonArray ops(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    @Test
    void nothingIsHandedOverUnlessArmed() {
        UUID player = UUID.randomUUID();
        assertEquals(1, TowerBattleFx.queue(player, ops("[{\"op\":\"weather\",\"id\":\"raindance\"}]")));
        assertTrue(TowerBattleFx.fieldsFor(UUID.randomUUID(), List.of(player)).isEmpty(), "queued is not armed");
        TowerBattleFx.clearQueued(player);
    }

    @Test
    void armedEffectsAreHandedOverOnceAndResolvedToTheRightSides() {
        UUID player = UUID.randomUUID();
        TowerBattleFx.queue(player, ops("[{\"op\":\"boost\",\"side\":\"self\",\"stat\":\"atk\",\"stages\":1},"
                + "{\"op\":\"damage\",\"side\":\"foe\",\"type\":\"Fire\",\"percent\":50}]"));
        TowerBattleFx.armFloorBattle(player);
        Map<String, String> fields = TowerBattleFx.fieldsFor(UUID.randomUUID(), List.of(player));
        JsonArray sent = ops(fields.get("towerFx"));
        assertEquals("p1", sent.get(0).getAsJsonObject().getAsJsonArray("sides").get(0).getAsString());
        assertEquals("p2", sent.get(1).getAsJsonObject().getAsJsonArray("sides").get(0).getAsString());
        assertTrue(TowerBattleFx.fieldsFor(UUID.randomUUID(), List.of(player)).isEmpty(), "consumed once");
        TowerBattleFx.clearQueued(player);
    }

    @Test
    void disarmLeavesNothingForALaterBattle() {
        UUID player = UUID.randomUUID();
        TowerBattleFx.queue(player, ops("[{\"op\":\"hp\",\"side\":\"foe\",\"percent\":50}]"));
        TowerBattleFx.armFloorBattle(player);
        TowerBattleFx.disarm(player);
        assertTrue(TowerBattleFx.fieldsFor(UUID.randomUUID(), List.of(player)).isEmpty());
        TowerBattleFx.clearQueued(player);
    }

    @Test
    void bossBattleGivesEachPlayerTheirOwnSideAgainstTheBoss() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TowerBattleFx.queue(a, ops("[{\"op\":\"boost\",\"side\":\"self\",\"stat\":\"spe\",\"stages\":1}]"));
        TowerBattleFx.queue(b, ops("[{\"op\":\"resist\",\"side\":\"self\",\"percent\":80}]"));
        TowerBattleFx.armBossBattle(List.of(a, b));
        JsonArray sent = ops(TowerBattleFx.fieldsFor(UUID.randomUUID(), List.of(a, b)).get("towerFx"));
        assertEquals("p1", sent.get(0).getAsJsonObject().getAsJsonArray("sides").get(0).getAsString());
        assertEquals("p2", sent.get(1).getAsJsonObject().getAsJsonArray("sides").get(0).getAsString());
        TowerBattleFx.clearQueued(a);
        TowerBattleFx.clearQueued(b);
    }

    @Test
    void invalidOperationsAreDroppedAndValuesClamped() {
        UUID player = UUID.randomUUID();
        int kept = TowerBattleFx.queue(player, ops("[null, 5, {}, {\"op\":\"eval\"}, {\"op\":\"weather\",\"id\":\"primordialsea\"},"
                + "{\"op\":\"boost\",\"side\":\"self\",\"stat\":\"__proto__\",\"stages\":1},"
                + "{\"op\":\"damage\",\"side\":\"self\",\"type\":\"Pizza\",\"percent\":100},"
                + "{\"op\":\"damage\",\"side\":\"self\",\"percent\":1e308},"
                + "{\"op\":\"boost\",\"side\":\"self\",\"stat\":\"atk\",\"stages\":99}]"));
        assertEquals(2, kept);
        TowerBattleFx.armFloorBattle(player);
        JsonArray sent = ops(TowerBattleFx.fieldsFor(UUID.randomUUID(), List.of(player)).get("towerFx"));
        assertEquals(300, sent.get(0).getAsJsonObject().get("percent").getAsInt());
        assertEquals(6, sent.get(1).getAsJsonObject().get("stages").getAsInt());
        TowerBattleFx.clearQueued(player);
    }

    @Test
    void evsOperationsAreValidatedAndClamped() {
        UUID player = UUID.randomUUID();
        int kept = TowerBattleFx.queue(player, ops("[{\"op\":\"evs\",\"side\":\"foe\",\"amount\":99999},"
                + "{\"op\":\"evs\",\"side\":\"self\",\"amount\":40,\"stat\":\"hp\"},"
                + "{\"op\":\"evs\",\"side\":\"self\",\"amount\":40,\"stat\":\"accuracy\"},"
                + "{\"op\":\"evs\",\"side\":\"self\",\"amount\":\"many\"},"
                + "{\"op\":\"evs\",\"amount\":40}]"));
        assertEquals(2, kept);
        TowerBattleFx.armFloorBattle(player);
        JsonArray sent = ops(TowerBattleFx.fieldsFor(UUID.randomUUID(), List.of(player)).get("towerFx"));
        assertEquals(2000, sent.get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals("hp", sent.get(1).getAsJsonObject().get("stat").getAsString());
        TowerBattleFx.clearQueued(player);
    }

    @Test
    void neverMoreThanTheCap() {
        UUID player = UUID.randomUUID();
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 100; i++) many.append(i > 0 ? "," : "").append("{\"op\":\"status\",\"side\":\"foe\",\"status\":\"par\"}");
        assertEquals(TowerBattleFx.MAX_OPS, TowerBattleFx.queue(player, ops(many.append("]").toString())));
        TowerBattleFx.clearQueued(player);
    }

    @Test
    void theBundledModuleIsOnTheClasspath() throws Exception {
        try (var in = TowerShowdownFx.module()) {
            assertTrue(in != null && new String(in.readAllBytes()).contains("towerFx"));
        }
    }
}
