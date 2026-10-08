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
import java.util.UUID;
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
            assertEquals(set.moves(), card.details().moves().stream().map(RentalDraftPayload.Move::id).toList());
            assertEquals(set.level(), card.details().level());
            assertEquals(set.ability(), card.details().ability());
        }
    }

    @Test
    @DisplayName("a card's moves carry the type and category the source gives, and survive the wire; unknown moves carry none")
    void moveTypes() throws IOException {
        RentalDraft draft = new RentalDraft(RentalDraw.draw(pool(), 11, false));
        RentalDraftPayload typed = RentalDraftPayload.of(draft, "",
                id -> new RentalDraftPayload.Move(id, "fire", "physical"));
        for (RentalDraftPayload.Card card : typed.packs().get(0).cards()) {
            for (RentalDraftPayload.Move move : card.details().moves()) {
                assertEquals("fire", move.type());
                assertEquals("physical", move.category());
            }
        }
        assertEquals(typed, roundTrip(RentalDraftPayload.STREAM_CODEC, typed));
        RentalDraftPayload plain = RentalDraftPayload.of(draft, "");
        assertEquals("", plain.packs().get(0).cards().get(0).details().moves().get(0).type());
        assertEquals(plain, roundTrip(RentalDraftPayload.STREAM_CODEC, plain));
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

    @Test
    @DisplayName("the draft says which kept Pokemon came from a God Pack")
    void godFlags() throws IOException {
        List<RentalSetDefinition> pool = pool();
        for (long seed = 0; seed < 5000; seed++) {
            RentalDraw.Offer offer = RentalDraw.draw(pool, seed, true);
            if (!offer.hasGodPack()) continue;
            RentalDraft draft = new RentalDraft(offer);
            for (int pack = 0; pack < 3; pack++) {
                int a = 0;
                int b = 1;
                // keep two that are not both legendary, so the cap never interferes
                List<RentalSetDefinition> cards = offer.packs().get(pack).cards();
                for (int i = 0; i < cards.size() && draft.currentPack() == pack; i++) {
                    for (int j = i + 1; j < cards.size() && draft.currentPack() == pack; j++) {
                        draft.pick(pack, List.of(i, j));
                    }
                }
            }
            if (!draft.complete()) continue;
            List<Boolean> flags = draft.godFlags();
            assertEquals(6, flags.size());
            for (int pack = 0; pack < 3; pack++) {
                assertEquals(offer.packs().get(pack).god(), flags.get(pack * 2));
                assertEquals(offer.packs().get(pack).god(), flags.get(pack * 2 + 1));
            }
            assertEquals(flags, draft.finish(UUID::randomUUID).god());
            return;
        }
        throw new AssertionError("no seed gave a God Pack and a legal team");
    }

    @Test
    @DisplayName("a reward reveal names a card by its label, and a plain item by nothing, over the wire")
    void rewardLabel() {
        RewardRevealPayload sent = new RewardRevealPayload(3, List.of(
                new RewardRevealPayload.Grant(ResourceLocation.fromNamespaceAndPath("cobblemon-cards", "card"), 1, "Garchomp card (epic)"),
                new RewardRevealPayload.Grant(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"), 2)));
        RewardRevealPayload back = roundTrip(RewardRevealPayload.STREAM_CODEC, sent);
        assertEquals(sent, back);
        assertEquals("Garchomp card (epic)", back.grants().get(0).label());
        assertEquals("", back.grants().get(1).label());
    }
}
