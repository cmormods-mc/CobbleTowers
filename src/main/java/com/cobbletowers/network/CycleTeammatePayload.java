package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A spectator's keybind asking to follow the next or previous still-active teammate (TDS #25).
 *
 * <p>The server, not the client, decides who is a legal target: a request from a player who is not
 * actually spectating is dropped and logged, never trusted, the same posture every other C2S handler
 * in this mod already takes toward a client's claim about run state.
 */
public record CycleTeammatePayload(boolean next) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CycleTeammatePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "cycle_teammate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleTeammatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CycleTeammatePayload::next,
            CycleTeammatePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
