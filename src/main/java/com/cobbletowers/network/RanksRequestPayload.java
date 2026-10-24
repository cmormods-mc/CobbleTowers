package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The Ranks tab asking for a duel: two region ids, either of which may be empty. The server fills what is empty (the
 * viewer's own region against its closest rival) and answers with a {@link RanksStatePayload}.
 */
public record RanksRequestPayload(String left, String right) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RanksRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            CobbleTowers.id("ranks_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RanksRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RanksRequestPayload::left,
            ByteBufCodecs.STRING_UTF8, RanksRequestPayload::right,
            RanksRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
