package com.cobbletowers.webgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.network.IntermissionActionPayload;
import com.cobbletowers.network.MasteryRequestPayload;
import com.cobbletowers.network.PlayActionPayload;
import com.cobbletowers.network.RentalDraftActionPayload;
import com.cobbletowers.network.TowerHallActionPayload;
import com.cobbletowers.network.TrackActionPayload;
import com.cobbletowers.network.VendorPurchasePayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A page message becomes the same action payload a native screen sends, or nothing at all. */
class PageActionsTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    @DisplayName("every action kind decodes to the payload the native screen would have sent")
    void decodesEveryKind() {
        assertEquals(new IntermissionActionPayload(IntermissionActionPayload.Action.PICK_CARD, 2),
                PageActions.decode(json("{\"kind\":\"IntermissionAction\",\"action\":\"PICK_CARD\",\"argument\":2}")).orElseThrow());
        assertEquals(new RentalDraftActionPayload(RentalDraftActionPayload.Action.PICK, 1, 3),
                PageActions.decode(json("{\"kind\":\"RentalDraftAction\",\"action\":\"pick\",\"a\":1,\"b\":3}")).orElseThrow());
        assertEquals(new PlayActionPayload(PlayActionPayload.Action.SET_PLAYLIST, "hardcore"),
                PageActions.decode(json("{\"kind\":\"PlayAction\",\"action\":\"SET_PLAYLIST\",\"argument\":\"hardcore\"}")).orElseThrow());
        assertEquals(new TowerHallActionPayload("open", "tideforge", "r1"),
                PageActions.decode(json("{\"kind\":\"TowerHallAction\",\"action\":\"open\",\"argument\":\"tideforge\",\"revision\":\"r1\"}")).orElseThrow());
        assertEquals(new TrackActionPayload("claim", "season", "", 7),
                PageActions.decode(json("{\"kind\":\"TrackAction\",\"action\":\"claim\",\"lane\":\"season\",\"number\":7}")).orElseThrow());
        assertEquals(new MasteryRequestPayload("cobbletowers:rootvale", "mastery"),
                PageActions.decode(json("{\"kind\":\"MasteryRequest\",\"tower\":\"cobbletowers:rootvale\"}")).orElseThrow());
        UUID me = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals(new VendorPurchasePayload(ResourceLocation.fromNamespaceAndPath("cobbletowers", "full_heal"), me),
                PageActions.decode(json("{\"kind\":\"VendorPurchase\",\"serviceId\":\"cobbletowers:full_heal\",\"targetPlayerId\":\"" + me + "\"}")).orElseThrow());
    }

    @Test
    @DisplayName("an action argument of the wrong kind or size is refused, never thrown")
    void refusesMalformed() {
        String[] bad = {
                "{}",
                "{\"kind\":\"NoSuchAction\"}",
                "{\"kind\":\"IntermissionAction\"}",
                "{\"kind\":\"IntermissionAction\",\"action\":\"DETONATE\"}",
                "{\"kind\":\"IntermissionAction\",\"action\":\"PICK_CARD\",\"argument\":9999}",
                "{\"kind\":\"IntermissionAction\",\"action\":\"PICK_CARD\",\"argument\":\"two\"}",
                "{\"kind\":\"PlayAction\",\"action\":\"SELECT_TOWER\",\"argument\":\"" + "x".repeat(300) + "\"}",
                "{\"kind\":\"VendorPurchase\",\"serviceId\":\"cobbletowers:full_heal\",\"targetPlayerId\":\"not-a-uuid\"}",
                "{\"kind\":\"VendorPurchase\",\"serviceId\":\"Not Valid!\",\"targetPlayerId\":\"00000000-0000-0000-0000-000000000001\"}",
                "{\"kind\":\"TowerHallAction\"}",
        };
        for (String message : bad) assertTrue(PageActions.decode(json(message)).isEmpty(), message);
    }
}
