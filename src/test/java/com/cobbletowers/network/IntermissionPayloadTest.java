package com.cobbletowers.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The intermission's cards carry their real description, risk and scene key across the wire intact. */
class IntermissionPayloadTest {

    @Test
    @DisplayName("a draft with described cards survives the wire, and an offer-less one does too")
    void roundTrip() {
        var card = new IntermissionStatePayload.Card(ResourceLocation.fromNamespaceAndPath("cobbletowers", "downpour"), "Downpour", 2, 1,
                "weather:raindance", List.of("Risk: moderate / Stack limit: 1", "Battle weather: raindance", "No direct reward amount multiplier."));
        var event = new IntermissionStatePayload.Card(ResourceLocation.fromNamespaceAndPath("cobbletowers", "shrine"), "Shrine", 0, -1,
                "event", List.of("Shrine"));
        var sent = new IntermissionStatePayload(4, new IntermissionStatePayload.Draft(1, List.of(card, event), -1, 0),
                List.of(new IntermissionStatePayload.Member("Alex", true, false)), -1, "", true);
        assertEquals(sent, roundTrip(sent));

        var empty = new IntermissionStatePayload(4, new IntermissionStatePayload.Draft(0, List.of(), -1, -1), List.of(), 3, "hi", false);
        assertEquals(empty, roundTrip(empty));
    }

    private static IntermissionStatePayload roundTrip(IntermissionStatePayload sent) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        IntermissionStatePayload.STREAM_CODEC.encode(buffer, sent);
        IntermissionStatePayload received = IntermissionStatePayload.STREAM_CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "nothing left over");
        return received;
    }
}
