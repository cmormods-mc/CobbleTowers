package com.cobbletowers.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The payloads Ascension (P30) and mastery (P31) added survive the wire, with nothing left over. */
class MasteryPayloadTest {

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T sent) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        codec.encode(buffer, sent);
        T received = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "nothing left over");
        return received;
    }

    private static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "test");

    @Test
    @DisplayName("the mastery screen payload, achievements and boards included, survives the wire")
    void screen() {
        MasteryScreenPayload mastery = new MasteryScreenPayload(
                List.of(new MasteryScreenPayload.Tower(TOWER, "Test Tower", 8, "Silver")), TOWER.toString(), "mastery",
                new MasteryScreenPayload.Mastery("level 8 (Silver)", "vendor prices -3%",
                        List.of(new MasteryScreenPayload.Achievement("First Ascent", "Clear a cycle.", true),
                                new MasteryScreenPayload.Achievement("Eternal", "Clear 100 cycles.", false))),
                MasteryScreenPayload.Board.none(), true);
        assertEquals(mastery, roundTrip(MasteryScreenPayload.STREAM_CODEC, mastery));

        MasteryScreenPayload board = new MasteryScreenPayload(List.of(), "", "speed", MasteryScreenPayload.Mastery.none(),
                new MasteryScreenPayload.Board("Fastest cycle", true, List.of("#1 Ash  9:00"), List.of("#1 Ash, Misty  7:30")), false);
        assertEquals(board, roundTrip(MasteryScreenPayload.STREAM_CODEC, board));
    }

    @Test
    @DisplayName("the request survives the wire")
    void request() {
        MasteryRequestPayload sent = new MasteryRequestPayload(TOWER.toString(), "difficulty");
        assertEquals(sent, roundTrip(MasteryRequestPayload.STREAM_CODEC, sent));
    }

    @Test
    @DisplayName("the play screen's state, with the Ascension picker nested in the lobby, survives the wire")
    void playState() {
        PlayStatePayload sent = new PlayStatePayload(
                List.of(new PlayStatePayload.Tower(TOWER, "Test Tower")),
                new PlayStatePayload.Lobby(1, TOWER.toString(), "Host", List.of(new PlayStatePayload.Member("Friend", true, true)), -1,
                        new PlayStatePayload.Options(new PlayStatePayload.Depth(2, 5, true),
                                new PlayStatePayload.Modes(List.of("monotype"), List.of("Monotype"), "monotype", false, new PlayStatePayload.Readiness(true, true, false, true)))),
                List.of(50, 60), "hello", true);
        assertEquals(sent, roundTrip(PlayStatePayload.STREAM_CODEC, sent));
        assertEquals(new PlayStatePayload.Depth(2, 5, true), sent.lobby().options().depth());
        assertEquals("monotype", sent.lobby().options().modes().chosen());
    }
}
