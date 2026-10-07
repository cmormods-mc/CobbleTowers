package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A request from the track screen (P37). {@code action} is {@code refresh}, {@code claim} or {@code claim_all};
 * {@code lane} is {@code mastery} or {@code season}; {@code tower} picks the mastery lane's tower; {@code number} is
 * the level or step. Revalidated by the server.
 */
public record TrackActionPayload(String action, String lane, String tower, int number) implements CustomPacketPayload {
    public static final Type<TrackActionPayload> TYPE = new Type<>(CobbleTowers.id("track_action_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrackActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), TrackActionPayload::action, ByteBufCodecs.stringUtf8(16), TrackActionPayload::lane,
            ByteBufCodecs.stringUtf8(128), TrackActionPayload::tower, ByteBufCodecs.VAR_INT, TrackActionPayload::number,
            TrackActionPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
