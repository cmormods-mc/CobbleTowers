package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What the pack-opening screen asks of the server (P33). {@code a} and {@code b} are 0-based card indices of the current pack, used
 * only by {@link Action#PICK}. Nothing here is trusted: the draft checks every pick.
 */
public record RentalDraftActionPayload(Action action, int a, int b) implements CustomPacketPayload {

    public enum Action { OPEN, PICK, RESTART }

    public static final CustomPacketPayload.Type<RentalDraftActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "rental_draft_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RentalDraftActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> Action.values()[Math.floorMod(index, Action.values().length)], Action::ordinal),
            RentalDraftActionPayload::action,
            ByteBufCodecs.VAR_INT, RentalDraftActionPayload::a,
            ByteBufCodecs.VAR_INT, RentalDraftActionPayload::b,
            RentalDraftActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
