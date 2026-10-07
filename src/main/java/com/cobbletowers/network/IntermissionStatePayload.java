package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What the intermission screen shows (P17): the draft with votes, who is ready or voted to cash out, and the
 * countdown.
 * @param open true to open the screen (arrival or request); false for an update to an open one
 */
public record IntermissionStatePayload(int floor, Draft draft, List<Member> members, int countdown, String message,
                                       boolean open) implements CustomPacketPayload {

    /**
     * One card on offer.
     * @param risk 0 minor, 1 moderate, 2 severe, -1 for a non-modifier card
     * @param theme the picture key the client paints it with ({@code ModifierArt}), never a rule
     * @param lines the authoritative description, one fact per line
     */
    public record Card(ResourceLocation id, String displayName, int votes, int risk, String theme, List<String> lines) {
        static final StreamCodec<RegistryFriendlyByteBuf, Card> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Card::id,
                ByteBufCodecs.STRING_UTF8, Card::displayName,
                ByteBufCodecs.VAR_INT, Card::votes,
                ByteBufCodecs.VAR_INT, Card::risk,
                ByteBufCodecs.STRING_UTF8, Card::theme,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(32)), Card::lines,
                Card::new);
    }

    /**
     * @param state 0 no draft, 1 open for votes, 2 settled
     * @param chosen the winning card's index once settled, else -1
     * @param myVote the viewer's own vote, or -1
     */
    public record Draft(int state, List<Card> cards, int chosen, int myVote) {
        static final StreamCodec<RegistryFriendlyByteBuf, Draft> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Draft::state,
                Card.STREAM_CODEC.apply(ByteBufCodecs.list()), Draft::cards,
                ByteBufCodecs.VAR_INT, Draft::chosen,
                ByteBufCodecs.VAR_INT, Draft::myVote,
                Draft::new);
    }

    public record Member(String name, boolean ready, boolean cashOut) {
        static final StreamCodec<RegistryFriendlyByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Member::name,
                ByteBufCodecs.BOOL, Member::ready,
                ByteBufCodecs.BOOL, Member::cashOut,
                Member::new);
    }

    public static final CustomPacketPayload.Type<IntermissionStatePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "intermission_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IntermissionStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, IntermissionStatePayload::floor,
                    Draft.STREAM_CODEC, IntermissionStatePayload::draft,
                    Member.STREAM_CODEC.apply(ByteBufCodecs.list()), IntermissionStatePayload::members,
                    ByteBufCodecs.VAR_INT, IntermissionStatePayload::countdown,
                    ByteBufCodecs.STRING_UTF8, IntermissionStatePayload::message,
                    ByteBufCodecs.BOOL, IntermissionStatePayload::open,
                    IntermissionStatePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
