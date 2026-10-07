package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A request to buy one vendor service for {@code targetPlayerId} (TDS #18: payer and target are independent). The
 * server re-validates affordability, eligibility and run state.
 */
public record VendorPurchasePayload(ResourceLocation serviceId, UUID targetPlayerId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VendorPurchasePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "vendor_purchase"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VendorPurchasePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, VendorPurchasePayload::serviceId,
            UUIDUtil.STREAM_CODEC, VendorPurchasePayload::targetPlayerId,
            VendorPurchasePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
