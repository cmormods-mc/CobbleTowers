package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.rental.RentalDraft;
import com.cobbletowers.rental.RentalDraw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A player's rental draft for the pack-opening screen (P33): the three packs, which cards were kept from each, and the sentence for
 * the message line. Display data only, resolved on the server: the client has no rental sets of its own and never decides anything.
 */
public record RentalDraftPayload(List<Pack> packs, int current, boolean complete, String message) implements CustomPacketPayload {

    /** What the card face shows beyond its name: the set's level, ability, nature, held item, role and moves. */
    public record Details(int level, String ability, String nature, String item, String role, List<String> moves) {
        static final StreamCodec<RegistryFriendlyByteBuf, Details> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Details::level,
                ByteBufCodecs.STRING_UTF8, Details::ability,
                ByteBufCodecs.STRING_UTF8, Details::nature,
                ByteBufCodecs.STRING_UTF8, Details::item,
                ByteBufCodecs.STRING_UTF8, Details::role,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Details::moves,
                Details::new);
    }

    /** One card: the set's id, its species (for the model and the name) and its rarity ({@code common} ... {@code mythic}). */
    public record Card(ResourceLocation set, String species, String name, String rarity, Details details) {
        static final StreamCodec<RegistryFriendlyByteBuf, Card> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Card::set,
                ByteBufCodecs.STRING_UTF8, Card::species,
                ByteBufCodecs.STRING_UTF8, Card::name,
                ByteBufCodecs.STRING_UTF8, Card::rarity,
                Details.STREAM_CODEC, Card::details,
                Card::new);
    }

    /** One pack: its five cards in the order they are revealed, whether it is the God Pack, and the two indices kept (empty if not yet). */
    public record Pack(boolean god, List<Card> cards, List<Integer> kept) {
        static final StreamCodec<RegistryFriendlyByteBuf, Pack> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Pack::god,
                Card.STREAM_CODEC.apply(ByteBufCodecs.list()), Pack::cards,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Pack::kept,
                Pack::new);
    }

    /** A draft as the screen shows it. Pure: it only reads the draft and the sets it holds. */
    public static RentalDraftPayload of(RentalDraft draft, String message) {
        List<Pack> packs = new ArrayList<>();
        List<List<Integer>> picks = draft.picks();
        for (int i = 0; i < draft.offer().packs().size(); i++) {
            RentalDraw.Pack pack = draft.offer().packs().get(i);
            List<Card> cards = new ArrayList<>();
            for (RentalSetDefinition set : pack.cards()) {
                cards.add(new Card(set.id(), set.species(), set.displayName(), set.rarity().lower(),
                        new Details(set.level(), set.ability(), set.nature(), set.item().map(item -> item.getPath()).orElse(""),
                                set.role(), set.moves())));
            }
            packs.add(new Pack(pack.god(), cards, i < picks.size() ? picks.get(i) : List.of()));
        }
        return new RentalDraftPayload(packs, draft.currentPack(), draft.complete(), message);
    }

    public static final CustomPacketPayload.Type<RentalDraftPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "rental_draft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RentalDraftPayload> STREAM_CODEC = StreamCodec.composite(
            Pack.STREAM_CODEC.apply(ByteBufCodecs.list()), RentalDraftPayload::packs,
            ByteBufCodecs.VAR_INT, RentalDraftPayload::current,
            ByteBufCodecs.BOOL, RentalDraftPayload::complete,
            ByteBufCodecs.STRING_UTF8, RentalDraftPayload::message,
            RentalDraftPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
