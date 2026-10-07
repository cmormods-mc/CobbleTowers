package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A named menu operation, never a command string. */
public record TowerFeatureRequest(int requestId, String section, String action, String argument) implements CustomPacketPayload {
    public static final Type<TowerFeatureRequest> TYPE=new Type<>(CobbleTowers.id("feature_request_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TowerFeatureRequest> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,TowerFeatureRequest::requestId,
            ByteBufCodecs.stringUtf8(24),TowerFeatureRequest::section,
            ByteBufCodecs.stringUtf8(32),TowerFeatureRequest::action,
            ByteBufCodecs.stringUtf8(256),TowerFeatureRequest::argument,TowerFeatureRequest::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
