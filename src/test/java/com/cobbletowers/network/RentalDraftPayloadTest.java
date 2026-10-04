package com.cobbletowers.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.rental.RentalDraft;
import com.cobbletowers.rental.RentalDraw;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The pack-opening screen's payloads (P33): the draft as the client is shown it, and the buttons it can press. */
class RentalDraftPayloadTest {

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T sent) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        codec.encode(buffer, sent);
        T received = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "nothing left over");
        return received;
    }

    private static List<RentalSetDefinition> pool() throws IOException {
        List<RentalSetDefinition> all = new ArrayList<>();
        Path dir = Paths.get("src/main/resources/data/cobbletowers/cobbletowers/rental_sets");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", file.getFileName().toString().replace(".json", ""));
                try (Reader reader = Files.newBufferedReader(file)) {
                    all.add(RentalSetDefinition.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
        }
        return all;
    }

    @Test
    @DisplayName("a draft survives the wire at every stage: nothing picked, one pack kept, finished")
    void draftSurvivesTheWire() throws IOException {
        RentalDraft draft = new RentalDraft(RentalDraw.draw(pool(), 11, false));
        RentalDraftPayload fresh = RentalDraftPayload.of(draft, "");
        assertEquals(fresh, roundTrip(RentalDraftPayload.STREAM_CODEC, fresh));
        assertEquals(3, fresh.packs().size());
        assertEquals(5, fresh.packs().get(0).cards().size());
        assertEquals(0, fresh.current());
        assertFalse(fresh.complete());
        assertTrue(fresh.packs().stream().allMatch(pack -> pack.kept().isEmpty()));

        draft.pick(0, List.of(1, 3));
        RentalDraftPayload one = RentalDraftPayload.of(draft, "Two kept.");
        assertEquals(one, roundTrip(RentalDraftPayload.STREAM_CODEC, one));
        assertEquals(1, one.current());
        assertEquals(List.of(1, 3), one.packs().get(0).kept());
        assertEquals("Two kept.", one.message());

        draft.pick(1, List.of(0, 1));
        draft.pick(2, List.of(2, 4));
        RentalDraftPayload done = RentalDraftPayload.of(draft, "");
        assertEquals(done, roundTrip(RentalDraftPayload.STREAM_CODEC, done));
        assertTrue(done.complete());
        assertEquals(List.of(2, 4), done.packs().get(2).kept());
    }

    @Test
    @DisplayName("a card carries everything its face shows, and the order matches the draw")
    void cardFace() throws IOException {
        RentalDraft draft = new RentalDraft(RentalDraw.draw(pool(), 11, false));
        RentalDraw.Pack pack = draft.offer().packs().get(0);
        RentalDraftPayload payload = RentalDraftPayload.of(draft, "");
        for (int i = 0; i < 5; i++) {
            RentalSetDefinition set = pack.cards().get(i);
            RentalDraftPayload.Card card = payload.packs().get(0).cards().get(i);
            assertEquals(set.species(), card.species());
            assertEquals(set.displayName(), card.name());
            assertEquals(set.rarity().lower(), card.rarity());
            assertEquals(set.moves(), card.details().moves());
            assertEquals(set.level(), card.details().level());
            assertEquals(set.ability(), card.details().ability());
        }
    }

    @Test
    @DisplayName("the screen's buttons survive the wire, and an out-of-range action number never throws")
    void action() {
        for (RentalDraftActionPayload.Action what : RentalDraftActionPayload.Action.values()) {
            RentalDraftActionPayload sent = new RentalDraftActionPayload(what, 1, 4);
            assertEquals(sent, roundTrip(RentalDraftActionPayload.STREAM_CODEC, sent));
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        buffer.writeVarInt(99);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        RentalDraftActionPayload bent = RentalDraftActionPayload.STREAM_CODEC.decode(buffer);
        assertTrue(bent.action() != null);
    }
}
