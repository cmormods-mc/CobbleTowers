package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything the play screen shows: the towers on offer, the lobby the player is in (if any), their own
 * party's levels, and the last thing the server wanted to say. Sent on {@code /cobbletowers play} and
 * again whenever the lobby changes, so the open screen redraws rather than being replaced.
 *
 * @param open true only when the player asked for the screen: a lobby change pushed to someone mid-game
 *             must update a screen they already have open, never open one over what they are doing
 */
public record PlayStatePayload(List<Tower> towers, Lobby lobby, List<Integer> partyLevels, String message,
                               boolean open) implements CustomPacketPayload {

    public record Tower(ResourceLocation id, String displayName) {
        static final StreamCodec<RegistryFriendlyByteBuf, Tower> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Tower::id,
                ByteBufCodecs.STRING_UTF8, Tower::displayName,
                Tower::new);
    }

    public record Member(String name, boolean accepted) {
        static final StreamCodec<RegistryFriendlyByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Member::name,
                ByteBufCodecs.BOOL, Member::accepted,
                Member::new);
    }

    /**
     * The player's lobby.
     *
     * @param role      0 = not in a lobby, 1 = host, 2 = invited and not yet answered, 3 = on the team
     * @param selected  the lobby's tower id, or empty when there is no lobby
     * @param countdown whole seconds until the run starts, or -1
     */
    /**
     * The Ascension (P30) the lobby will start at, and the deepest the whole team may choose.
     *
     * @param offered whether the selected tower ascends at all; when false the picker is not shown
     */
    public record Depth(int chosen, int max, boolean offered) {
        static final StreamCodec<RegistryFriendlyByteBuf, Depth> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Depth::chosen,
                ByteBufCodecs.VAR_INT, Depth::max,
                ByteBufCodecs.BOOL, Depth::offered,
                Depth::new);

        public static Depth none() {
            return new Depth(0, 0, false);
        }
    }

    public record Lobby(int role, String selected, String hostName, List<Member> members, int countdown, Depth depth) {
        static final StreamCodec<RegistryFriendlyByteBuf, Lobby> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Lobby::role,
                ByteBufCodecs.STRING_UTF8, Lobby::selected,
                ByteBufCodecs.STRING_UTF8, Lobby::hostName,
                Member.STREAM_CODEC.apply(ByteBufCodecs.list()), Lobby::members,
                ByteBufCodecs.VAR_INT, Lobby::countdown,
                Depth.STREAM_CODEC, Lobby::depth,
                Lobby::new);

        public static Lobby none() {
            return new Lobby(0, "", "", List.of(), -1, Depth.none());
        }
    }

    public static final CustomPacketPayload.Type<PlayStatePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "play_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayStatePayload> STREAM_CODEC = StreamCodec.composite(
            Tower.STREAM_CODEC.apply(ByteBufCodecs.list()), PlayStatePayload::towers,
            Lobby.STREAM_CODEC, PlayStatePayload::lobby,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), PlayStatePayload::partyLevels,
            ByteBufCodecs.STRING_UTF8, PlayStatePayload::message,
            ByteBufCodecs.BOOL, PlayStatePayload::open,
            PlayStatePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
