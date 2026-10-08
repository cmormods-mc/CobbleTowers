package com.cobbletowers.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunEffectsPayloadTest {

    private static RunEffectsPayload roundTrip(RunEffectsPayload sent) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        RunEffectsPayload.STREAM_CODEC.encode(buffer, sent);
        RunEffectsPayload received = RunEffectsPayload.STREAM_CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "nothing left over");
        return received;
    }

    @Test
    @DisplayName("what a run carries survives the wire, markers and floors included")
    void roundTrips() {
        RunEffectsPayload sent = new RunEffectsPayload(6, 16, List.of(
                new RunEffectsPayload.Item("Sharpened Claws", false, 2, 3, List.of("[-] Foes rise 3 levels above the tower.", "[+] Loot swells to x1.25 on every floor.")),
                new RunEffectsPayload.Item("War Banner", true, 1, 0, List.of())));
        assertEquals(sent, roundTrip(sent));
        RunEffectsPayload empty = new RunEffectsPayload(1, 0, List.of());
        assertEquals(empty, roundTrip(empty));
    }

    @Test
    @DisplayName("more items than the cap are refused on the wire, not truncated silently")
    void capped() {
        List<RunEffectsPayload.Item> many = new ArrayList<>();
        for (int i = 0; i < RunEffectsPayload.MAX_ITEMS + 1; i++) many.add(new RunEffectsPayload.Item("m" + i, false, 1, 0, List.of()));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        assertThrows(RuntimeException.class, () -> RunEffectsPayload.STREAM_CODEC.encode(buffer, new RunEffectsPayload(1, 0, many)));
    }
}
