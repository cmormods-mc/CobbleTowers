package com.cobbletowers.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Explicit Hall requests; the server revalidates the choice and trial revision. */
public record TowerHallActionPayload(String action, String argument, String revision) implements CustomPacketPayload {
    public static final Type<TowerHallActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("cobbletowers", "tower_hall_action_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TowerHallActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), TowerHallActionPayload::action,
            ByteBufCodecs.stringUtf8(256), TowerHallActionPayload::argument,
            ByteBufCodecs.stringUtf8(256), TowerHallActionPayload::revision, TowerHallActionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
