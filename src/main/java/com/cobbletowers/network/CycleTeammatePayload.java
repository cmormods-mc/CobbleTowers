package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A spectator's request to follow the next or previous active teammate (TDS #25). The server decides who is a legal
 * target; a request from a non-spectator is dropped and logged.
 */
public record CycleTeammatePayload(boolean next) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CycleTeammatePayload> TYPE = new CustomPacketPayload.Type<>(
            CobbleTowers.id("cycle_teammate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleTeammatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CycleTeammatePayload::next,
            CycleTeammatePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
