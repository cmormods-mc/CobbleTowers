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
                                       boolean open, Extras extras) implements CustomPacketPayload {

    /** The native screens do not use the extras (P51 slice 2), so they keep building the state without them. */
    public IntermissionStatePayload(int floor, Draft draft, List<Member> members, int countdown, String message, boolean open) {
        this(floor, draft, members, countdown, message, open, Extras.NONE);
    }

    /**
     * What the Frontier Desk page draws around the draft: the team's health and where the run is in its tower.
     * @param tower the tower's display name, or empty
     * @param cycleFloor the floor just cleared within the current cycle (1 to {@code cycleLength}); 0 when unknown
     * @param milestones the cycle's milestone floors (boss, champion)
     * @param party up to six Pokemon, party order
     */
    public record Extras(String tower, int cycleFloor, int cycleLength, List<Integer> milestones,
                         List<TowerHallStatePayload.Mon> party) {
        public static final Extras NONE = new Extras("", 0, 0, List.of(), List.of());
        static final StreamCodec<RegistryFriendlyByteBuf, Extras> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Extras::tower,
                ByteBufCodecs.VAR_INT, Extras::cycleFloor,
                ByteBufCodecs.VAR_INT, Extras::cycleLength,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), Extras::milestones,
                TowerHallStatePayload.Mon.CODEC.apply(ByteBufCodecs.list(6)), Extras::party, Extras::new);
    }

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
            CobbleTowers.id("intermission_state"));

    /** Seven fields is one more than StreamCodec.composite takes, so this one is written out. */
    public static final StreamCodec<RegistryFriendlyByteBuf, IntermissionStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.floor());
                Draft.STREAM_CODEC.encode(buf, p.draft());
                Member.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.members());
                ByteBufCodecs.VAR_INT.encode(buf, p.countdown());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.message());
                ByteBufCodecs.BOOL.encode(buf, p.open());
                Extras.CODEC.encode(buf, p.extras());
            },
            buf -> new IntermissionStatePayload(ByteBufCodecs.VAR_INT.decode(buf), Draft.STREAM_CODEC.decode(buf),
                    Member.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf), Extras.CODEC.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
