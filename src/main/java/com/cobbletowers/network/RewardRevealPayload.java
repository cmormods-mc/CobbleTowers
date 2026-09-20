package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What a run just banked, for the reveal screen (docs/design/P9-economy.md, P11).
 *
 * <p>Sent alongside the existing chat line, not instead of it (see {@code RewardDelivery}): a client
 * without this channel registered still gets the grant, just as plain text.
 */
public record RewardRevealPayload(int floorIndex, List<Grant> grants) implements CustomPacketPayload {

    /** One item and how many of it, exactly as {@code RewardValuation.Grant} priced it. */
    public record Grant(ResourceLocation item, int amount) {
        static final StreamCodec<RegistryFriendlyByteBuf, Grant> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Grant::item,
                ByteBufCodecs.VAR_INT, Grant::amount,
                Grant::new);
    }

    public static final CustomPacketPayload.Type<RewardRevealPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "reward_reveal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RewardRevealPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RewardRevealPayload::floorIndex,
            Grant.STREAM_CODEC.apply(ByteBufCodecs.list()), RewardRevealPayload::grants,
            RewardRevealPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
