package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The mastery screen asking for another view (P31): a tower id and a tab ({@code "mastery"} or a board name). The
 * server resolves both and answers with a {@link MasteryScreenPayload}.
 */
public record MasteryRequestPayload(String tower, String tab) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MasteryRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            CobbleTowers.id("mastery_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MasteryRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MasteryRequestPayload::tower,
            ByteBufCodecs.STRING_UTF8, MasteryRequestPayload::tab,
            MasteryRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
