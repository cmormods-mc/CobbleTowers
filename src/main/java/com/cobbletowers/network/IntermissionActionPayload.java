package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One thing a player did on the intermission screen. {@code argument} is the card index for
 * {@code PICK_CARD} and unused otherwise. Re-checked by the server like every other C2S payload.
 */
public record IntermissionActionPayload(Action action, int argument) implements CustomPacketPayload {

    public enum Action { PICK_CARD, READY, UNREADY, CASH_OUT, STAY, VENDOR, REFRESH }

    public static final CustomPacketPayload.Type<IntermissionActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "intermission_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IntermissionActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.idMapper(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
                    IntermissionActionPayload::action,
                    ByteBufCodecs.VAR_INT, IntermissionActionPayload::argument,
                    IntermissionActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
